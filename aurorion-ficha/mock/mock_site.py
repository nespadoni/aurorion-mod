#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Mock local do site Aurorion para testar o aurorion-ficha (e a integração) sem o backend real.

Só biblioteca padrão (Python 3.12). Sobe em http://127.0.0.1:8787 e responde às rotas que os mods
chamam em ``/api/v1/integration/v1``:

- ``/rpg/sheet``, ``/rpg/candidates``, ``/rpg/bind``, ``/rpg/roll``, ``/rpg/peek`` — a ficha no jogo,
  com as mesmas regras, códigos, status HTTP e formato JSON de ``internal/rpg/game.go`` e
  ``game_handler.go`` (motor de d20, vantagem/desvantagem, CD, natural 20/1, fórmula livre NdM+K
  com kh/kl/dh/dl, idempotência por ``command_id``, mestre verificado por staff, NPC privado);
- ``/link/claim`` e ``/link/status`` — o ``/vincular`` (``internal/gameaccount``), com a resolução
  de sessão principal × alt do ``ResolveSession``;
- ``/diary/*`` — diário mínimo em memória, só para o aurorion-diario não sujar o log;
- ``/events/batch`` — aceita e conta os fatos (``accepted``/``duplicate`` por ``event_id``).

Em ``GET /`` fica o painel de controle (vincular, staff, estado da ficha, recursos, site fora do ar,
lento, ``busy``, log ao vivo). As rotas do painel ficam sob ``/mock/...``.

Uso: ``python -X utf8 mock_site.py [--porta 8787] [--abrir] [--sem-salvar]``.
"""
from __future__ import annotations

import argparse
import collections
import copy
import hashlib
import html
import json
import os
import random
import re
import socket
import sys
import threading
import time
import unicodedata
import uuid
import webbrowser
from datetime import datetime, timezone
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlparse

PREFIX = "/api/v1/integration/v1"
SCHEMA = 3  # muda quando o formato do mock_state.json muda (estado velho é ignorado)
BODY_LIMIT = 16 << 10  # gameBodyLimit
DIARY_LIMIT = 512 << 10
BATCH_LIMIT = 256 << 10
RNG = random.SystemRandom()
LOCK = threading.RLock()

# ── Catálogo (internal/rpg/rules/catalog.go) ─────────────────────────────────────────────────

ATTRIBUTES = ["for", "des", "vig", "sab", "int", "vit"]
ATTR_LABELS = {"for": "Força", "des": "Destreza", "vig": "Vigor", "sab": "Sabedoria", "int": "Intelecto", "vit": "Vitral"}
PHYSICAL = {"for", "des", "vig"}

SKILLS = [
    ("atletismo", "Atletismo", "for", []),
    ("corpo_a_corpo", "Corpo a Corpo", "for", ["corpo", "corpoacorpo", "melee"]),
    ("acrobacia", "Acrobacia", "des", []),
    ("furtividade", "Furtividade", "des", []),
    ("pontaria", "Pontaria", "des", []),
    ("resistencia", "Resistência", "vig", []),
    ("astros_e_mistos", "Astros & Mistos", "sab", ["astros", "astrosemistos", "astros_e_misticos", "astrosemisticos"]),
    ("historia", "História", "sab", []),
    ("ciencia", "Ciência", "sab", []),
    ("natureza", "Natureza", "sab", []),
    ("medicina", "Medicina", "sab", []),
    ("criacao", "Criação", "sab", []),
    ("investigacao", "Investigação", "int", []),
    ("linguistica", "Linguística", "int", []),
    ("psicologia", "Psicologia", "int", []),
    ("tecnologia", "Tecnologia", "int", []),
    ("diplomacia", "Diplomacia", "vit", []),
    ("enganacao", "Enganação", "vit", []),
    ("intimidacao", "Intimidação", "vit", []),
    ("procurar", "Procurar", "vit", []),
    ("intencao", "Intenção", "vit", []),
    ("vontade", "Vontade", "vit", []),
]
SKILL_BY_ID = {s[0]: s for s in SKILLS}

ELEMENTS = {"martirio": "Martírio", "anacronico": "Anacrônico", "quantico": "Quântico", "esoterico": "Esotérico",
            "minguantis": "Minguantis", "vazio": "Vazio", "determinacao": "Determinação", "rancor": "Rancor",
            "abissal": "Abissal", "solaris": "Solaris", "chaos": "Chaos"}
GRADE_LABELS = ["Sem Marca", "Marcado Desperto", "Marcado Vinculado", "Marcado Primaz", "Representante do Elemento"]

# Condições do catálogo: (id, rótulo, escopo de desvantagem em testes: "" | todos | fisicos | mentais)
CONDITIONS = [
    ("sangramento", "Sangramento", ""), ("lentidao", "Lentidão", ""), ("queimando", "Queimando", ""),
    ("ofuscado", "Ofuscado", ""), ("vulneravel", "Vulnerável", ""), ("exposto", "Exposto", ""),
    ("caido", "Caído", ""), ("agarrado", "Agarrado", ""), ("medo", "Medo", "todos"), ("panico", "Pânico", "todos"),
    ("confusao", "Confusão", "mentais"), ("envenenado", "Envenenado", "fisicos"), ("enfraquecido", "Enfraquecido", ""),
    ("exaustao", "Exaustão", "todos"), ("efeito_agudo", "Efeito agudo de Sanidade", "todos"),
    ("atordoado", "Atordoado", ""), ("fratura", "Fratura de Sanidade", ""), ("sem_decisao", "Sanidade em zero", ""),
    ("canalizando", "Canalizando", ""), ("transformado", "Transformado", ""),
]
CONDITION_SCOPE = {c[0]: c[2] for c in CONDITIONS}
VITALS = ["consciente", "queda", "estabilizado", "morto"]

MAX_EFFECTIVE_ATT = 7
VIDA_REF = [110, 170, 250, 320, 400, 500, 600, 720, 860, 1020, 1210, 1410, 1640, 1900, 2200, 2550, 2950, 3400,
            3920, 4520, 5250, 6150, 7250, 8550, 10050, 12050, 14550, 18050, 23050, 30050]


def normalize_key(s: str) -> str:
    """rules.NormalizeKey: minúsculas, sem acento, só letras e dígitos."""
    s = unicodedata.normalize("NFD", s)
    s = "".join(ch for ch in s if unicodedata.category(ch) != "Mn")
    s = unicodedata.normalize("NFC", s).lower()
    return "".join(ch for ch in s if ch.isalpha() or ch.isdigit())


_SKILL_INDEX: dict[str, tuple] = {}
for _s in SKILLS:
    _SKILL_INDEX[_s[0]] = _s
    _SKILL_INDEX[normalize_key(_s[0])] = _s
    _SKILL_INDEX[normalize_key(_s[1])] = _s
    for _a in _s[3]:
        _SKILL_INDEX[normalize_key(_a)] = _s
_ATTR_INDEX: dict[str, str] = {}
for _a in ATTRIBUTES:
    _ATTR_INDEX[_a] = _a
    _ATTR_INDEX[normalize_key(ATTR_LABELS[_a])] = _a
_ATTR_INDEX.update({"sabedoria": "sab", "intelecto": "int", "vitral": "vit"})


def lookup_skill(key: str):
    return _SKILL_INDEX.get(key) or _SKILL_INDEX.get(normalize_key(key))


def lookup_attribute(key: str):
    return _ATTR_INDEX.get(key) or _ATTR_INDEX.get(normalize_key(key))


# ── Fórmulas da ficha (rules/ruleset.go, rules/build.go) ────────────────────────────────────

def _floordiv(a: int, b: int) -> int:
    return a // b  # Python já arredonda para baixo, como floorDiv do Go


def vigor_ref(soul):
    return 2 if soul <= 5 else 3 if soul <= 10 else 4 if soul <= 15 else 5


def effective(build: dict, attr: str) -> int:
    v = build["attributes"].get(attr, 0) + build.get("mark", {}).get("extras", {}).get(attr, 0)
    return min(v, MAX_EFFECTIVE_ATT)


def test_bonus(build: dict, attr: str, skill: str) -> int:
    return effective(build, attr) + build["skills"].get(skill, 0)


def derive(build: dict) -> dict:
    soul = max(1, min(30, build.get("soul", 1)))
    ref = VIDA_REF[soul - 1]
    eff = {a: effective(build, a) for a in ATTRIBUTES}
    vida = max(1, _floordiv(ref * (25 + eff["vig"] - vigor_ref(soul)), 25)) + build.get("grants", {}).get("vida_bonus", 0)
    equip = build.get("equipment", {})
    pe = 0
    if build.get("uses_pe"):
        pe = max(5, min(10, 5 + _floordiv(soul - 3, 3)))
    return {
        "vida_max": vida,
        "stamina_max": 50 + 5 * eff["vig"] + 2 * eff["des"] + 3 * (soul - 1),
        "sanidade_max": 50 + 5 * (eff["sab"] + eff["vit"]),
        "pe_max": pe,
        "defesa": 10 + eff["des"] + equip.get("defense", 0),
        "rd": equip.get("rd", 0),
        "movimento_m": min(13, 6 + eff["des"]),
        "iniciativa_bonus": eff["des"] + build["skills"].get("intencao", 0),
        "fortitude": min(MAX_EFFECTIVE_ATT, -((-(eff["sab"] + eff["vit"])) // 2)),
        "mark_charges": build.get("mark", {}).get("grade", 0),
        "resonance_b": ((ref * 8 + 250) // 500) * 5,
    }


# ── Dados (rules/roll) ──────────────────────────────────────────────────────────────────────

class RollInvalid(Exception):
    """roll.ErrInvalid: a mensagem já sai como o Go formata ("rolagem inválida: ...")."""


def goq(s: str) -> str:
    """O %q do Go (aproximado): aspas e escapes de JSON, sem escapar acentos."""
    return json.dumps(s, ensure_ascii=False)


_ATOI = re.compile(r"^[+-]?[0-9]+$")


def atoi(s: str):
    if not _ATOI.match(s):
        raise ValueError(s)
    return int(s)


MAX_REPEAT = 10


def parse_formula(expr: str) -> list[dict]:
    """roll.ParseFormula sem a repetição (só os termos); ver parse_formula_full."""
    return parse_formula_full(expr)[0]


def parse_formula_full(expr: str) -> tuple[list[dict], int]:
    """roll.ParseFormula, mesma gramática e mesmas mensagens: (termos, repetições).

    "N#expr" (ou "Nxexpr") rola a expressão N vezes, 1 <= N <= 10; o teto de 200
    dados vale para as repetições somadas. "1#" é uma rolagem só.
    """
    s = expr.strip().replace(" ", "").lower()
    if s == "":
        raise RollInvalid("rolagem inválida: expressão vazia")
    if len(s.encode()) > 120:
        raise RollInvalid("rolagem inválida: expressão longa demais")
    times = 1
    k = min((s.find(c) for c in "#x" if c in s), default=-1)
    if k >= 0:
        head = s[:k]
        try:
            n = atoi(head)
        except ValueError:
            n = None
        if k == 0 or n is None or "+" in head or "-" in head:
            raise RollInvalid("rolagem inválida: a repetição vem antes da fórmula, como 2#d20+5")
        if n < 1 or n > MAX_REPEAT:
            raise RollInvalid(f"rolagem inválida: repetir de 1 a {MAX_REPEAT} vezes")
        times = n
        s = s[k + 1:]
        if "#" in s or "x" in s:
            raise RollInvalid("rolagem inválida: uma repetição só, no começo (como 2#d20+5)")
        if s == "":
            raise RollInvalid("rolagem inválida: falta a fórmula depois da repetição")
    terms: list[dict] = []
    sign, i, total_dice = 1, 0, 0
    while i < len(s):
        if s[i] in "+-":
            sign = -1 if s[i] == "-" else 1
            i += 1
        elif i > 0:
            raise RollInvalid(f"rolagem inválida: operador esperado em {goq(s[i:])}")
        j = i
        while j < len(s) and s[j] not in "+-":
            j += 1
        raw = s[i:j]
        if raw == "":
            raise RollInvalid("rolagem inválida: parcela vazia")
        t = _parse_term(raw)
        t["sign"] = sign
        total_dice += t["count"]
        terms.append(t)
        if len(terms) > 10:
            raise RollInvalid("rolagem inválida: mais de 10 parcelas")
        i, sign = j, 1
    if total_dice == 0:
        raise RollInvalid("rolagem inválida: a fórmula precisa de ao menos um dado")
    if total_dice * times > 200:
        raise RollInvalid(f"rolagem inválida: {total_dice * times} dados excede 200")
    return terms, times


def _parse_term(raw: str) -> dict:
    d = raw.find("d")
    if d < 0:
        try:
            n = atoi(raw)
        except ValueError:
            n = None
        if n is None or n > 100000:
            raise RollInvalid(f"rolagem inválida: constante {goq(raw)}")
        return {"count": 0, "sides": 0, "op": "", "op_n": 0, "const": n}
    t = {"count": 1, "sides": 0, "op": "", "op_n": 0, "const": 0}
    if d > 0:
        try:
            n = atoi(raw[:d])
        except ValueError:
            n = 0
        if n < 1:
            raise RollInvalid(f"rolagem inválida: quantidade {goq(raw[:d])}")
        t["count"] = n
    rest = raw[d + 1:]
    for op in ("kh", "kl", "dh", "dl"):
        k = rest.find(op)
        if k >= 0:
            try:
                n = atoi(rest[k + 2:])
            except ValueError:
                n = 0
            if n < 1:
                raise RollInvalid(f"rolagem inválida: operação {goq(rest[k:])}")
            t["op"], t["op_n"] = op, n
            rest = rest[:k]
            break
    if rest == "%":
        t["sides"] = 100
    else:
        try:
            n = atoi(rest)
        except ValueError:
            n = 0
        if n < 2 or n > 1000:
            raise RollInvalid(f"rolagem inválida: faces {goq(rest)}")
        t["sides"] = n
    if t["op"] in ("dh", "dl") and t["op_n"] >= t["count"]:
        raise RollInvalid(f"rolagem inválida: descartar {t['op_n']} de {t['count']} dados")
    if t["op"] and t["op_n"] > t["count"]:
        raise RollInvalid(f"rolagem inválida: manter {t['op_n']} de {t['count']} dados")
    return t


def formula_string(terms: list[dict], times: int = 1) -> str:
    out = [f"{times}#"] if times > 1 else []
    for i, t in enumerate(terms):
        if t["sign"] < 0:
            out.append("-")
        elif i > 0:
            out.append("+")
        if t["count"] == 0:
            out.append(str(t["const"]))
            continue
        out.append(f"{t['count']}d{t['sides']}")
        if t["op"]:
            out.append(f"{t['op']}{t['op_n']}")
    return "".join(out)


def roll_formula(terms: list[dict], times: int = 1) -> dict:
    """Roller.RollFormula: com repetição, "repeats" traz cada uma; dice/constant/total somam todas."""
    if times <= 1:
        return _roll_once(terms)
    out = {"formula": formula_string(terms, times), "dice": [], "constant": 0, "total": 0, "repeats": []}
    for _ in range(times):
        one = _roll_once(terms)
        out["repeats"].append({"dice": one["dice"], "constant": one["constant"], "total": one["total"]})
        out["dice"] += one["dice"]
        out["constant"] += one["constant"]
        out["total"] += one["total"]
    return out


def free_against(fr: dict, dc: int) -> dict:
    """FreeResult.Against: cada repetição compara com a CD; o success de cima é
    True/False só se todas concordam (misto = None)."""
    if dc <= 0:
        return fr
    fr["target"] = dc
    reps = fr.get("repeats")
    if not reps:
        fr["success"] = fr["total"] >= dc
        return fr
    for rp in reps:
        rp["success"] = rp["total"] >= dc
    hits = sum(1 for rp in reps if rp["success"])
    fr["success"] = None if 0 < hits < len(reps) else hits > 0
    return fr


def _roll_once(terms: list[dict]) -> dict:
    """Uma rolagem da expressão: dados preservados, keep/drop estável, constante uma vez."""
    dice, total, const = [], 0, 0
    for t in terms:
        if t["count"] == 0:
            const += t["sign"] * t["const"]
            continue
        start = len(dice)
        for _ in range(t["count"]):
            dice.append({"sides": t["sides"], "value": RNG.randint(1, t["sides"]), "kept": True})
        group = dice[start:]
        if t["op"]:
            idx = sorted(range(len(group)), key=lambda k: -group[k]["value"])  # sorted() é estável
            n = t["op_n"]
            drop = {"kh": idx[n:], "kl": idx[:len(idx) - n], "dh": idx[:n], "dl": idx[len(idx) - n:]}[t["op"]]
            for k in drop:
                group[k]["kept"] = False
        for dd in group:
            if dd["kept"]:
                total += t["sign"] * dd["value"]
    return {"formula": formula_string(terms), "dice": dice, "constant": const, "total": total + const}


def resolve_advantage(adv: int, dis: int) -> str:
    if adv > 0 and dis == 0:
        return "vantagem"
    if dis > 0 and adv == 0:
        return "desvantagem"
    return "normal"


def roll_d20(mode: str):
    if mode not in ("vantagem", "desvantagem"):
        v = RNG.randint(1, 20)
        return [{"sides": 20, "value": v, "kept": True}], "normal", v
    a, b = RNG.randint(1, 20), RNG.randint(1, 20)
    keep_first = a >= b if mode == "vantagem" else a <= b
    dice = [{"sides": 20, "value": a, "kept": keep_first}, {"sides": 20, "value": b, "kept": not keep_first}]
    return dice, mode, a if keep_first else b


# ── Utilidades ──────────────────────────────────────────────────────────────────────────────

def now_iso() -> str:
    return datetime.now(timezone.utc).astimezone().isoformat(timespec="seconds")


def cut_runes(v: str, n: int) -> str:
    v = (v or "").strip()
    return v[:n]


def first_text(*values) -> str:
    for v in values:
        v = (v or "").strip()
        if v:
            return v
    return ""


def norm_uuid(raw):
    """uuid.Parse do Go (forma canônica minúscula) ou None."""
    if not isinstance(raw, str):
        return None
    try:
        return str(uuid.UUID(raw.strip()))
    except (ValueError, AttributeError):
        return None


UUID_TAG = re.compile(r"^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")
CODE_ALPHABET = set("ABCDEFGHJKLMNPQRSTUVWXYZ23456789")


def normalize_code(raw: str) -> str:
    return "".join(ch for ch in (raw or "").upper() if ch in CODE_ALPHABET)


class HttpReply(Exception):
    """Resposta pronta (status + corpo), lançada de dentro de um handler."""

    def __init__(self, status: int, body):
        super().__init__(status)
        self.status, self.body = status, body


def bad_request():
    return HttpReply(400, {"code": "invalid_request"})


# Validação no estilo do json.Decoder + binding do gin: tipo errado ou regra quebrada = 400.

def f_str(body, name, *, required=False, maxlen=None, is_uuid=False, omitempty_uuid=False):
    v = body.get(name)
    if v is None:
        v = ""
    if not isinstance(v, str):
        raise bad_request()
    if required and v == "":
        raise bad_request()
    if maxlen is not None and len(v) > maxlen:
        raise bad_request()
    if (is_uuid or (omitempty_uuid and v != "")) and not UUID_TAG.match(v):
        raise bad_request()
    return v


def f_bool(body, name):
    v = body.get(name)
    if v is None:
        return False
    if not isinstance(v, bool):
        raise bad_request()
    return v


def f_int(body, name, *, unsigned=False, minv=None, maxv=None):
    v = body.get(name)
    if v is None:
        v = 0
    if isinstance(v, bool) or not isinstance(v, int):
        raise bad_request()
    if unsigned and v < 0:
        raise bad_request()
    if minv is not None and v < minv:
        raise bad_request()
    if maxv is not None and v > maxv:
        raise bad_request()
    return v


def f_flex(body, name):
    """flexCount: true → 1, false/null → 0, inteiro 0..5."""
    v = body.get(name)
    if v is None or v is False:
        return 0
    if v is True:
        return 1
    if isinstance(v, int) and 0 <= v <= 5:
        return v
    raise bad_request()


def identity(body):
    return {
        "profile_uuid": f_str(body, "profile_uuid", required=True, is_uuid=True),
        "account_uuid": f_str(body, "account_uuid", maxlen=36),
        "profile_name": f_str(body, "profile_name", maxlen=32),
        "character_id": f_str(body, "character_id", omitempty_uuid=True),
        "character_name": f_str(body, "character_name", maxlen=160),
    }


def master(body):
    return {"gm_profile_uuid": f_str(body, "gm_profile_uuid", maxlen=36),
            "gm_account_uuid": f_str(body, "gm_account_uuid", maxlen=36)}


# ── Dados de exemplo ────────────────────────────────────────────────────────────────────────

def skills_map(**vals):
    m = {s[0]: 0 for s in SKILLS}
    m.update(vals)
    return m


def full_resources(build, **over):
    d = derive(build)
    r = {"initialized": True, "needs_review": False, "vida": d["vida_max"], "stamina": d["stamina_max"],
         "sanidade": d["sanidade_max"], "pe": d["pe_max"], "temp_hp": 0, "vital": "consciente", "exhaustion": 0,
         "conditions": [], "charges_used": 0, "death_successes": 0, "death_failures": 0, "ultimate_available": True}
    r.update(over)
    return r


def blank_resources():
    return {"initialized": False, "needs_review": False, "vida": 0, "stamina": 0, "sanidade": 0, "pe": 0, "temp_hp": 0,
            "vital": "consciente", "exhaustion": 0, "conditions": [], "charges_used": 0, "death_successes": 0,
            "death_failures": 0, "ultimate_available": True}


def build_kaelen():
    return {"soul": 4, "attributes": {"for": 3, "des": 2, "vig": 3, "sab": 2, "int": 1, "vit": 2},
            "skills": skills_map(atletismo=2, corpo_a_corpo=3, acrobacia=1, furtividade=1, resistencia=2, astros_e_mistos=1,
                                 historia=1, natureza=1, medicina=1, investigacao=1, psicologia=1, diplomacia=1,
                                 intimidacao=2, procurar=1, intencao=1, vontade=2),
            "mark": {"element": "martirio", "grade": 2, "extras": {"for": 1, "vig": 1}},
            "grants": {"vida_bonus": 0}, "equipment": {"defense": 1, "rd": 2}, "uses_pe": True}


def build_iris():
    return {"soul": 3, "attributes": {"for": 1, "des": 3, "vig": 2, "sab": 3, "int": 2, "vit": 2},
            "skills": skills_map(acrobacia=2, furtividade=3, pontaria=2, astros_e_mistos=2, historia=1, medicina=2,
                                 investigacao=2, linguistica=1, diplomacia=1, enganacao=1, procurar=2, intencao=1, vontade=1),
            "mark": {"element": "solaris", "grade": 1, "extras": {"sab": 1}},
            "grants": {"vida_bonus": 0}, "equipment": {"defense": 0, "rd": 0}, "uses_pe": True}


def build_npc(soul, attrs, skills, element="", grade=0, extras=None, defense=0, rd=0, uses_pe=False):
    return {"soul": soul, "attributes": attrs, "skills": skills_map(**skills),
            "mark": {"element": element, "grade": grade, "extras": extras or {}},
            "grants": {"vida_bonus": 0}, "equipment": {"defense": defense, "rd": rd}, "uses_pe": uses_pe}


def mech(build=None, *, soul=0, legacy="", draft=None, resources=None, abilities=None, element="", grade=0,
         progression_pending=False):
    """A parte mecânica (rpg_characters + build aprovada + rascunho + resource_states + técnicas)."""
    if build is not None:
        soul = build["soul"]
        element = build["mark"]["element"]
        grade = build["mark"]["grade"]
    return {"soul": soul, "legacy_level": legacy, "element": element, "grade": grade, "build": build, "draft": draft,
            "resources": resources or (full_resources(build) if build else blank_resources()),
            "abilities": abilities or [], "progression_pending": progression_pending}


def ability(name, desc, element, rarity, level, equipped):
    return {"name": name, "short_description": desc, "element": element, "rarity": rarity, "ability_level": level,
            "equipped": equipped}


class State:
    """Estado em memória do site falso. Tudo protegido por LOCK."""

    def __init__(self):
        self.reset()

    # ── semente ────────────────────────────────────────────────────────────────────────────
    def reset(self):
        self.settings = {
            "down": "off",            # off | 503 | transporte | desativada
            "latency_ms": 0,
            "link_mode": "qualquer",  # qualquer (6+ caracteres) | fixo
            "fixed_code": "AURO-2026",
            "link_target": "auto",    # auto | id de usuário
            "auto_link": False,       # perfis principais novos já entram vinculados
            "token": "",              # vazio = aceita qualquer X-Game-Token não vazio
            "persist": True,
        }
        self.busy_next = 0
        self.seq = {"user": 0, "sheet": 40, "npc": 6, "rpg": 1200, "diary": 0}
        self.users: dict[str, dict] = {}
        self.sheets: dict[str, dict] = {}
        self.npcs: dict[str, dict] = {}
        self.rpg: dict[str, dict] = {}        # rpg_characters: id → {subject_type, subject_id, name}
        self.rpg_index: dict[str, int] = {}   # "sheet:41" → id
        self.links: dict[str, dict] = {}      # game_account_links: profile → {user_id, profile_name, linked_at}
        self.profiles: dict[str, dict] = {}   # perfis vistos (só o mock)
        self.game_chars: dict[str, dict] = {}  # game_characters
        self.bindings: dict[str, dict] = {}   # rpg_game_bindings
        self.actions: dict[str, dict] = {}    # idempotência de /roll
        self.diary: dict[str, dict] = {}
        self.events_seen: list[str] = []
        self.counters = {"calls": 0, "events": 0, "events_duplicate": 0, "rolls": 0}
        self.attempts: dict[str, list] = {}
        self.roll_window: dict[int, collections.deque] = {}
        self.log = collections.deque(maxlen=300)
        self.roll_log = collections.deque(maxlen=120)
        self.rev = 1
        self.logrev = 1
        self.started = time.time()

        me = self.new_user("Você (mock)", "voce.mock")
        other = self.new_user("Outra pessoa (mock)", "outra.pessoa", seed=False)
        oid = self.next_id("sheet")
        self.sheets[str(oid)] = {"id": oid, "user_id": other, "is_active": True, "kind": "completa", "nome": "Selene Arcanjo",
                                 "photo": self.portrait("selene"), "level": "entidade",
                                 "lore": {"idade": 31, "origem": "Torre de Ilmar", "alinhamento": "Neutra",
                                          "personalidade": "Calculista.", "descricao_fisica": "Cabelos prateados."},
                                 "mech": mech(build_iris(), legacy="entidade")}
        self.new_npc("Capitã Mirela", "Guarda da Coroa", other,
                     mech(build_npc(6, {"for": 3, "des": 3, "vig": 3, "sab": 2, "int": 2, "vit": 3},
                                    {"corpo_a_corpo": 3, "intimidacao": 3, "diplomacia": 2}, defense=2, rd=3)))
        # NPCs livres (staff pode pegar) e um arquivado (não aparece)
        self.new_npc("Guarda Tomás", "Sentinela da Muralha", None,
                     mech(build_npc(2, {"for": 3, "des": 2, "vig": 3, "sab": 1, "int": 1, "vit": 1},
                                    {"atletismo": 2, "corpo_a_corpo": 2, "procurar": 1}, defense=2, rd=2)))
        self.new_npc("Rosalind Vinhedo", "Taverneira do Cálice Partido", None,
                     mech(None, legacy="comum", abilities=[ability("Rodada da Casa", "Todos na taverna recuperam o fôlego.",
                                                                    "", 1, 1, True)]),
                     lore={"idade": 52, "origem": "Vaelor", "personalidade": "Fala alto e sabe de tudo."})
        self.new_npc("Arauto do Vazio", "Cultista sem rosto", None,
                     mech(build_npc(8, {"for": 2, "des": 4, "vig": 3, "sab": 3, "int": 3, "vit": 4},
                                    {"furtividade": 4, "enganacao": 4, "astros_e_mistos": 3, "vontade": 3},
                                    element="vazio", grade=3, extras={"des": 1, "vit": 2}, uses_pe=True),
                          abilities=[ability("Presença Ausente", "Some da percepção de todos até agir.", "vazio", 5, 3, True)]))
        self.new_npc("Velho Tobias", "Eremita das Colinas", None,
                     mech(build_npc(3, {"for": 1, "des": 1, "vig": 2, "sab": 4, "int": 3, "vit": 3},
                                    {"natureza": 3, "medicina": 3, "historia": 2, "astros_e_mistos": 2})))
        arch = self.new_npc("Lorde Caído", "Antigo regente", None, mech(None, legacy="lenda"))
        self.npcs[str(arch)]["status"] = "arquivado"
        self.sheet_state(me, "completa")
        self.rev += 1

    def next_id(self, kind):
        self.seq[kind] += 1
        return self.seq[kind]

    def portrait(self, key):
        return f"/uploads/retratos/{key}.svg"

    def new_user(self, name, tag, seed=True, staff=True):
        uid = self.next_id("user")
        self.users[str(uid)] = {"id": uid, "name": name, "discord_tag": tag, "role": "staff" if staff else "player",
                                "sheet_state": "completa"}
        if seed:
            self.seed_user(uid)
        return uid

    def seed_user(self, uid):
        """Três fichas (completa, antiga, aguardando aprovação) e dois NPCs ligados à pessoa."""
        k = self.next_id("sheet")
        self.sheets[str(k)] = {
            "id": k, "user_id": uid, "is_active": False, "kind": "completa", "nome": "Kaelen Rubraluz",
            "photo": self.portrait("kaelen"), "level": "entidade",
            "lore": {"idade": 24, "origem": "Vaelor, distrito das forjas",
                     "alinhamento": "Leal à própria palavra, implacável com quem a quebra.",
                     "personalidade": "Calado, teimoso e protetor. Paga as dívidas em sangue — o próprio, de preferência.",
                     "descricao_fisica": "Alto, cicatrizes rituais nos antebraços, olhos cor de brasa."},
            "mech": mech(build_kaelen(), legacy="entidade",
                         resources=full_resources(build_kaelen(), vida=280, stamina=61, sanidade=52, pe=3,
                                                  conditions=["sangramento"], charges_used=1),
                         abilities=[ability("Banquete Rubro", "Converte o sangue derramado em vigor: cura parte do dano causado.",
                                            "martírio", 4, 2, True),
                                    ability("Lâmina Votiva", "Corte que cobra Vida do próprio usuário para ignorar a RD do alvo.",
                                            "martirio", 3, 1, True),
                                    ability("Guarda de Ferro", "Postura defensiva: +2 de Defesa até o próximo turno.", "", 1, 1, False)])}
        k = self.next_id("sheet")
        self.sheets[str(k)] = {
            "id": k, "user_id": uid, "is_active": False, "kind": "antiga", "nome": "Corvo de Cinza",
            "photo": self.portrait("corvo"), "level": "anomalia",
            "lore": {"idade": 0, "origem": "Ninguém sabe; apareceu depois do Grande Incêndio.", "alinhamento": "Caótico",
                     "personalidade": "Fala por enigmas.", "descricao_fisica": "Manto cinza e máscara de bico."},
            "mech": mech(None, legacy="anomalia",
                         abilities=[ability("Voo de Cinzas", "Técnica da ficha antiga, ainda sem mecânica v0.3.", "vazio", 2, 0, True)])}
        k = self.next_id("sheet")
        self.sheets[str(k)] = {
            "id": k, "user_id": uid, "is_active": False, "kind": "aguardando", "nome": "Íris Solmar",
            "photo": self.portrait("iris"), "level": "desperta",
            "lore": {"idade": 19, "origem": "Mosteiro do Sol Poente", "alinhamento": "Bondosa",
                     "personalidade": "Curiosa e impaciente.", "descricao_fisica": "Sardas e um colar de âmbar."},
            "mech": mech(None, legacy="desperta", draft={"status": "submitted", "build": build_iris()},
                         abilities=[ability("Fogo Vivo", "Chama que expulsa o medo de um aliado.", "solaris", 3, 1, True)])}
        self.new_npc("Bram Ferrolho", "Ferreiro de Vaelor", uid,
                     mech(build_npc(5, {"for": 4, "des": 1, "vig": 3, "sab": 2, "int": 2, "vit": 1},
                                    {"atletismo": 2, "corpo_a_corpo": 2, "criacao": 4, "resistencia": 2, "intimidacao": 1},
                                    defense=1, rd=3),
                          abilities=[ability("Martelo Rubro", "Golpe que aquece o metal do alvo.", "martírio", 2, 1, True)]),
                     lore={"idade": 47, "origem": "Vaelor", "alinhamento": "Honesto",
                           "personalidade": "Rabugento e generoso.", "descricao_fisica": "Barba chamuscada."})
        self.new_npc("Sombra de Ilmar", "Espiã da Coroa", uid, mech(None, legacy="comum"),
                     lore={"idade": 0, "origem": "Ilmar", "personalidade": "Nunca diz o nome verdadeiro."})

    def new_npc(self, name, title, linked, m, lore=None):
        nid = self.next_id("npc")
        lore = lore or {}
        if not m["legacy_level"]:
            m["legacy_level"] = "lenda" if m["soul"] >= 8 else "comum"
        self.npcs[str(nid)] = {"id": nid, "name": name, "title": title, "photo": self.portrait(normalize_key(name)[:12]),
                               "status": "ativo", "linked_user_id": linked, "level": m["legacy_level"],
                               "lore": {"idade": lore.get("idade", 0), "origem": lore.get("origem", ""),
                                        "alinhamento": lore.get("alinhamento", ""),
                                        "personalidade": lore.get("personalidade", ""),
                                        "descricao_fisica": lore.get("descricao_fisica", "")},
                               "mech": m}
        return nid

    # ── persistência ───────────────────────────────────────────────────────────────────────
    PERSIST = ["settings", "seq", "users", "sheets", "npcs", "rpg", "rpg_index", "links", "profiles", "game_chars",
               "bindings", "diary", "events_seen", "counters"]

    def dump(self):
        out = {"schema": SCHEMA}
        for k in self.PERSIST:
            out[k] = getattr(self, k)
        return out

    def load(self, data):
        if data.get("schema") != SCHEMA:
            return False
        for k in self.PERSIST:
            if k in data:
                setattr(self, k, data[k])
        return True

    # ── acesso ─────────────────────────────────────────────────────────────────────────────
    def touch(self):
        self.rev += 1

    def user(self, uid):
        return self.users.get(str(uid))

    def is_staff(self, uid):
        u = self.user(uid)
        return bool(u) and u["role"] in ("staff", "admin", "dono")

    def subject(self, stype, sid):
        return (self.sheets if stype == "sheet" else self.npcs).get(str(sid))

    def ensure_character(self, stype, sid):
        key = f"{stype}:{sid}"
        rid = self.rpg_index.get(key)
        if rid is not None:
            return self.rpg[str(rid)]
        subj = self.subject(stype, sid)
        if subj is None:
            raise LookupError("not_found")
        rid = self.next_id("rpg")
        name = subj["nome"] if stype == "sheet" else subj["name"]
        self.rpg[str(rid)] = {"id": rid, "subject_type": stype, "subject_id": sid, "name": name}
        self.rpg_index[key] = rid
        return self.rpg[str(rid)]

    def subject_of(self, ch):
        return self.subject(ch["subject_type"], ch["subject_id"])

    def owned_by(self, uid, ch):
        subj = self.subject_of(ch)
        if subj is None:
            return False
        if ch["subject_type"] == "sheet":
            return subj["user_id"] == uid
        return subj.get("linked_user_id") == uid

    def active_sheet(self, uid):
        ids = [s["id"] for s in self.sheets.values() if s["user_id"] == uid and s["is_active"]]
        return self.sheets[str(max(ids))] if ids else None

    # ── vínculo da conta (gameaccount) ─────────────────────────────────────────────────────
    def resolve_session(self, account_raw, profile_raw):
        """ResolveSession: devolve (dono, conta, perfil) ou o código not_linked/foreign."""
        profile = norm_uuid(profile_raw)
        if profile is None:
            return None, "not_linked"
        account = profile
        if account_raw != "":
            account = norm_uuid(account_raw)
            if account is None:
                return None, "not_linked"
        link = self.links.get(account)
        if link is None:
            return None, "not_linked"
        if account != profile:
            active = self.links.get(profile)
            if active is not None and active["user_id"] != link["user_id"]:
                return None, "foreign"
        return {"user_id": link["user_id"], "account": account, "profile": profile, "main": account == profile}, None

    def touch_character(self, profile, uid, character_id, name):
        cid = norm_uuid(character_id)
        gc = self.game_chars.get(cid)
        if gc is None:
            self.game_chars[cid] = {"profile": profile, "user_id": uid, "name": cut_runes(name, 160),
                                    "first_seen": now_iso(), "last_seen": now_iso()}
            self.touch()
            return None
        if gc["user_id"] != uid:
            return "foreign"
        gc["last_seen"] = now_iso()
        if cut_runes(name, 160):
            gc["name"] = cut_runes(name, 160)
        return None

    def link_profile(self, profile, uid, name):
        old = self.links.get(profile)
        self.links[profile] = {"user_id": uid, "profile_name": cut_runes(name, 32) or (old or {}).get("profile_name", ""),
                               "linked_at": (old or {}).get("linked_at") or now_iso()}
        self.touch()

    def link_target(self, profile_name):
        """Para quem vai um vínculo novo: o alvo escolhido no painel ou, no automático, a primeira
        conta sem perfil vinculado (a sua) e, depois dela, uma conta nova com fichas de exemplo."""
        t = self.settings.get("link_target", "auto")
        if t != "auto" and self.user(t):
            return int(t)
        linked_users = {l["user_id"] for l in self.links.values()}
        if 1 not in linked_users:
            return 1
        uid = self.new_user(f"Conta de {profile_name or 'jogador'}", normalize_key(profile_name or "jogador") or "jogador")
        self.sheet_state(uid, "completa")
        return uid

    def see_profile(self, ident, route):
        """Registra o perfil (e a conta dona dele) a cada chamada."""
        profile = norm_uuid(ident.get("profile_uuid", ""))
        if profile is None:
            return
        account = norm_uuid(ident.get("account_uuid", "")) or profile
        p = self.profiles.get(profile)
        new = p is None
        if new:
            p = self.profiles[profile] = {"uuid": profile, "name": "", "account": account, "first_seen": now_iso()}
        if ident.get("profile_name"):
            p["name"] = ident["profile_name"]
        p["account"] = account
        p["last_seen"] = now_iso()
        p["last_route"] = route
        if ident.get("character_name"):
            p["character"] = ident["character_name"]
        if account != profile and account not in self.profiles:
            self.profiles[account] = {"uuid": account, "name": "", "account": account, "first_seen": now_iso(),
                                      "last_seen": now_iso(), "last_route": "(conta dona de um alt)"}
            new = True
        if self.settings.get("auto_link") and account not in self.links:
            self.link_profile(account, self.link_target(self.profiles[account].get("name", "")), self.profiles[account].get("name", ""))
        if new:
            self.touch()

    # ── estado da ficha (painel) ───────────────────────────────────────────────────────────
    def user_sheets(self, uid):
        return sorted((s for s in self.sheets.values() if s["user_id"] == uid), key=lambda s: s["id"])

    def sheet_state(self, uid, state):
        sheets = self.user_sheets(uid)
        if state == "aguardando":
            for s in sheets:
                if s["kind"] == "aguardando":
                    m = s["mech"]
                    m.update({"build": None, "element": "", "grade": 0, "soul": 0,
                              "draft": {"status": "submitted", "build": build_iris()}, "resources": blank_resources()})
        for s in sheets:
            s["is_active"] = state != "sem" and s["kind"] == state
        if state != "sem" and not any(s["is_active"] for s in sheets) and sheets:
            sheets[0]["is_active"] = True
        u = self.user(uid)
        if u:
            u["sheet_state"] = state
        self.touch()

    def approve(self, uid):
        """A staff aprova a construção enviada: has_build passa a true e os recursos começam cheios."""
        for s in self.user_sheets(uid):
            m = s["mech"]
            if m.get("draft") and m["draft"].get("status") == "submitted":
                b = m["draft"]["build"]
                m.update({"build": b, "draft": None, "soul": b["soul"], "element": b["mark"]["element"],
                          "grade": b["mark"]["grade"], "resources": full_resources(b)})
                self.touch()
                return s["nome"]
        return None

    # ── ficha do jogo (rpg/game.go) ────────────────────────────────────────────────────────
    def resolve_game(self, uid, profile, main):
        res = {"code": None, "source": "", "character": None, "explicit": None}
        b = self.bindings.get(profile)
        if b is not None and b["user_id"] == uid:
            ch = self.rpg.get(str(b["character_id"]))
            if ch is None:
                res["explicit"] = {"source": "explicit", "subject_type": "", "subject_id": 0,
                                   "rpg_character_id": b["character_id"], "stale": True}
            else:
                res["explicit"] = {"source": "explicit", "subject_type": ch["subject_type"], "subject_id": ch["subject_id"],
                                   "rpg_character_id": ch["id"]}
                if self.owned_by(uid, ch):
                    res.update(code="ok", source="explicit", character=ch)
                    return res
                res["explicit"]["stale"] = True
        if not main:
            res["code"] = "needs_binding"
            return res
        sh = self.active_sheet(uid)
        if sh is None:
            res["code"] = "no_sheet"
            return res
        res.update(code="ok", source="default", character=self.ensure_character("sheet", sh["id"]))
        return res

    @staticmethod
    def binding_view(res):
        if res["explicit"] is not None:
            return dict(res["explicit"])
        ch = res["character"]
        if ch is not None:
            return {"source": res["source"], "subject_type": ch["subject_type"], "subject_id": ch["subject_id"],
                    "rpg_character_id": ch["id"]}
        return None

    def subject_info(self, ch):
        subj = self.subject_of(ch) or {}
        lore = subj.get("lore", {})
        if ch["subject_type"] == "sheet":
            name, title, photo = first_text(subj.get("nome"), ch["name"]), "", subj.get("photo", "")
        else:
            name, title, photo = first_text(subj.get("name"), ch["name"]), subj.get("title", ""), subj.get("photo", "")
        lo = {"idade": lore.get("idade", 0), "origem": cut_runes(lore.get("origem", ""), 600),
              "alinhamento": cut_runes(lore.get("alinhamento", ""), 600),
              "personalidade": cut_runes(lore.get("personalidade", ""), 600),
              "aparencia": cut_runes(lore.get("descricao_fisica", ""), 600)}
        return name, title, photo, lo

    def game_sheet(self, ch, base_url):
        subj = self.subject_of(ch)
        m = subj["mech"]
        name, title, photo, lore = self.subject_info(ch)
        gs = {
            "rpg_character_id": ch["id"], "subject_type": ch["subject_type"], "subject_id": ch["subject_id"],
            "name": name, "title": title, "portrait_url": abs_url(photo, base_url),
            "site_path": f"/profile/rpg/ficha/{ch['id']}", "soul": m["soul"], "legacy_level": m["legacy_level"],
            "element": {"id": m["element"], "label": ELEMENTS.get(m["element"], "")} if m["element"] else None,
            "grade": {"value": m["grade"], "label": GRADE_LABELS[m["grade"]] if 0 <= m["grade"] < 5 else ""},
            "has_build": False, "progression_pending": bool(m.get("progression_pending")) and m["build"] is not None,
            "pending_review": bool(m.get("draft")) and m["draft"].get("status") == "submitted",
            "attributes": [], "skills": [], "derived": None,
            "resources": None, "abilities": [], "lore": lore, "version": "",
        }
        b = m["build"]
        if b is not None:
            d = derive(b)
            gs["has_build"] = True
            gs["attributes"] = [{"id": a, "label": ATTR_LABELS[a], "base": b["attributes"].get(a, 0), "effective": effective(b, a)}
                                for a in ATTRIBUTES]
            gs["skills"] = [{"id": sid, "label": lab, "attribute": attr, "value": b["skills"].get(sid, 0),
                             "bonus": test_bonus(b, attr, sid)} for sid, lab, attr, _ in SKILLS]
            gs["derived"] = d
        r = m["resources"]
        gs["resources"] = {"initialized": r["initialized"], "vida": r["vida"], "stamina": r["stamina"],
                           "sanidade": r["sanidade"], "pe": r["pe"], "temp_hp": r["temp_hp"], "vital": r["vital"],
                           "exhaustion": r["exhaustion"], "conditions": list(r["conditions"]),
                           "charges_used": r["charges_used"], "death_successes": r["death_successes"],
                           "death_failures": r["death_failures"], "ultimate_available": r["ultimate_available"]}
        gs["abilities"] = [{"name": a["name"], "short_description": cut_runes(a["short_description"], 600),
                            "element": a["element"], "rarity": a["rarity"], "ability_level": a["ability_level"],
                            "equipped": a["equipped"]} for a in m["abilities"]]
        raw = json.dumps(gs, ensure_ascii=False, separators=(",", ":")).encode()
        gs["version"] = hashlib.sha256(raw).hexdigest()[:16]
        return gs

    def candidates(self, uid, except_profile):
        u = self.user(uid)
        if u is None:
            raise LookupError("not_found")
        bound = collections.defaultdict(list)
        rows = []
        for p, b in self.bindings.items():
            if b["user_id"] != uid:
                continue
            ch = self.rpg.get(str(b["character_id"]))
            if ch is None:
                continue
            rows.append((b.get("profile_name", ""), p, ch["subject_type"], ch["subject_id"]))
        for name, p, st, sid in sorted(rows):
            if p == except_profile:
                continue
            bound[f"{st}:{sid}"].append(first_text(name, p))
        out = []

        def add(c):
            c["kind"] = c["subject_type"]
            c["bound_profiles"] = bound.get(f"{c['subject_type']}:{c['subject_id']}", [])
            out.append(c)

        for sh in sorted((s for s in self.sheets.values() if s["user_id"] == uid and s["is_active"]), key=lambda s: s["id"]):
            add({"subject_type": "sheet", "subject_id": sh["id"], "name": sh["nome"], "title": "", "portrait_url": sh["photo"],
                 "staff_only": False})
        for n in sorted((n for n in self.npcs.values() if n.get("linked_user_id") == uid), key=lambda n: n["name"]):
            add({"subject_type": "npc", "subject_id": n["id"], "name": n["name"], "title": n["title"], "portrait_url": n["photo"],
                 "staff_only": False})
        if self.is_staff(uid):
            free = sorted((n for n in self.npcs.values() if n.get("linked_user_id") is None and n["status"] == "ativo"),
                          key=lambda n: n["name"])[:200]
            for n in free:
                add({"subject_type": "npc", "subject_id": n["id"], "name": n["name"], "title": n["title"],
                     "portrait_url": n["photo"], "staff_only": True})
        # Ordem de campos do GameCandidate
        keys = ["subject_type", "subject_id", "kind", "name", "title", "portrait_url", "staff_only", "bound_profiles"]
        return [{k: c[k] for k in keys} for c in out]

    def game_sheet_for(self, uid, profile, main, with_candidates, base_url):
        res = self.resolve_game(uid, profile, main)
        out = {"code": res["code"]}
        if res["character"] is not None:
            out["sheet"] = self.game_sheet(res["character"], base_url)
        bv = self.binding_view(res)
        if bv is not None:
            out["binding"] = bv
        if with_candidates or res["code"] != "ok":
            out["candidates"] = fix_candidates(self.candidates(uid, profile), base_url)
        return out

    def bind_game(self, uid, profile, profile_name, stype, sid):
        """BindGame: devolve None ou (código, mensagem)."""
        u = self.user(uid)
        if u is None:
            return ("not_found", None)
        if stype == "sheet":
            sh = self.sheets.get(str(sid))
            if sh is None:
                return ("not_found", None)
            if sh["user_id"] != uid:
                return ("forbidden", "forbidden: a ficha é de outra pessoa")
        elif stype == "npc":
            n = self.npcs.get(str(sid))
            if n is None:
                return ("not_found", None)
            linked = n.get("linked_user_id")
            if linked is not None and linked == uid:
                pass
            elif linked is not None:
                return ("forbidden", "forbidden: o NPC está ligado a outro jogador")
            elif not self.is_staff(uid) or n["status"] != "ativo":
                return ("forbidden", "forbidden: NPC sem jogador só pela staff")
            else:
                n["linked_user_id"] = uid  # staff pega o NPC livre: liga a si na mesma "transação"
        else:
            return ("invalid", None)
        ch = self.ensure_character(stype, sid)
        old = self.bindings.get(profile, {})
        self.bindings[profile] = {"character_id": ch["id"], "user_id": uid,
                                  "profile_name": cut_runes(profile_name, 32) if profile_name else old.get("profile_name", ""),
                                  "created_at": old.get("created_at", now_iso()), "updated_at": now_iso()}
        self.touch()
        return None

    def clear_game(self, uid, profile):
        b = self.bindings.get(profile)
        if b is not None and b["user_id"] == uid:
            del self.bindings[profile]
            self.touch()

    # ── rolagens ───────────────────────────────────────────────────────────────────────────
    def allow_roll(self, uid):
        """Balde de /roll por dono: 240 por minuto, como h.rolls no backend."""
        q = self.roll_window.setdefault(uid, collections.deque())
        t = time.monotonic()
        while q and t - q[0] >= 60:
            q.popleft()
        if len(q) >= 240:
            return False
        q.append(t)
        return True

    def verified_gm(self, gm):
        if gm["gm_profile_uuid"].strip() == "":
            return False
        s, err = self.resolve_session(gm["gm_account_uuid"], gm["gm_profile_uuid"])
        if err:
            return False
        return self.is_staff(s["user_id"])

    def game_roll(self, uid, ch, inp):
        """gameRollCommand + Service.execute + flattenGameRoll. Devolve (corpo, ok)."""
        privileged = inp["advantage"] > 0 or inp["disadvantage"] > 0
        req = {"type": {"skill": "skill_test", "attribute": "attribute_test", "free": "free_roll"}[inp["kind"]],
               "secret": inp["secret"]}
        if inp["dc"] < 0 or inp["dc"] > 100:
            return err_body("invalid", "invalid: CD entre 0 e 100")
        kind = inp["kind"]
        if kind == "skill":
            sk = lookup_skill(inp["skill"].strip())
            if sk is None:
                return err_body("invalid", f"invalid: perícia {goq(inp['skill'])}")
            attr = ""
            if inp["attribute"].strip():
                a = lookup_attribute(inp["attribute"].strip())
                if a is None:
                    return err_body("invalid", f"invalid: atributo {goq(inp['attribute'])}")
                if a != sk[2]:
                    attr, privileged = a, True
            req["skill"] = {"actor_id": ch["id"], "skill": sk[0], "attribute": attr, "dc": inp["dc"],
                            "advantage": inp["advantage"], "disadvantage": inp["disadvantage"]}
        elif kind == "attribute":
            a = lookup_attribute(inp["attribute"].strip())
            if a is None:
                return err_body("invalid", f"invalid: atributo {goq(inp['attribute'])}")
            req["attribute"] = {"actor_id": ch["id"], "attribute": a, "dc": inp["dc"],
                                "advantage": inp["advantage"], "disadvantage": inp["disadvantage"]}
        else:
            if privileged:
                return err_body("invalid", "invalid: rolagem livre não tem vantagem")
            try:
                terms, times = parse_formula_full(inp["formula"])
            except RollInvalid as e:
                return err_body("invalid", str(e))
            req["actor_id"] = ch["id"]
            req["free"] = {"formula": inp["formula"], "label": cut_runes(inp["label"], 120)}
            if inp["dc"] > 0:
                req["free"]["dc"] = inp["dc"]
        if privileged and not inp["gm"]:
            return err_body("gm_required", "gm_required")

        command_id = inp["command_id"].strip()
        if command_id == "" or len(command_id) > 100:
            return err_body("invalid", "invalid: command_id obrigatório (até 100 caracteres)")
        key = f"game:{uid}:{command_id}"
        digest = hashlib.sha256(json.dumps(req, sort_keys=True, ensure_ascii=False).encode()).hexdigest()
        name = self.subject_info(ch)[0]
        prev = self.actions.get(key)
        if prev is not None:
            if prev["hash"] != digest:
                return err_body("invalid", "idempotency_key_reused: o command_id já foi usado com outro conteúdo")
            body = {"action_id": prev["action_id"], "character_name": name, "code": "ok", "replayed": True,
                    "roll": copy.deepcopy(prev["roll"])}
            return body, True

        subj = self.subject_of(ch)
        m = subj["mech"]
        if kind in ("skill", "attribute"):
            if m["build"] is None:
                return err_body("no_build", f"no_approved_build: {ch['name']} ainda não tem construção v0.3 aprovada")
            r = m["resources"]
            if not r["initialized"] or r.get("needs_review"):
                return err_body("no_build", f"resources_need_review: recursos de {ch['name']} aguardam revisão da staff")
            b = m["build"]
            if kind == "skill":
                sk = SKILL_BY_ID[req["skill"]["skill"]]
                attr = req["skill"]["attribute"] or sk[2]
                base, label = test_bonus(b, attr, sk[0]), f"{ATTR_LABELS[attr]} + {sk[1]}"
                cmd = req["skill"]
            else:
                attr = req["attribute"]["attribute"]
                base, label = effective(b, attr), ATTR_LABELS[attr]
                cmd = req["attribute"]
            roll = self.d20_test(m, ch, attr, base, label, cmd["dc"], cmd["advantage"], cmd["disadvantage"], kind)
        else:
            fr = free_against(roll_formula(terms, times), inp["dc"])
            roll = {"kind": "free", "label": req["free"]["label"] or fr["formula"],
                    "dice": [die_view(d) for d in fr["dice"]], "mode": "normal", "natural": 0, "bonus": fr["constant"],
                    "total": fr["total"], "target": fr.get("target", 0), "success": fr.get("success"), "critical": False,
                    "formula": fr["formula"]}
            if fr.get("repeats"):
                # GameRoll.Repeats: uma entrada por repetição (dados e total de cada).
                roll["repeats"] = [{"total": rp["total"], "bonus": rp["constant"], "dice": [die_view(d) for d in rp["dice"]],
                                    **({"success": rp["success"]} if "success" in rp else {})} for rp in fr["repeats"]]
        action_id = str(uuid.uuid4())
        self.actions[key] = {"hash": digest, "action_id": action_id, "roll": copy.deepcopy(roll)}
        return {"action_id": action_id, "character_name": name, "code": "ok", "roll": roll}, True

    def d20_test(self, m, ch, attr, base, label, dc, adv, dis, kind):
        r = m["resources"]
        conds = list(dict.fromkeys(r["conditions"] + (["exaustao"] if r["exhaustion"] > 0 else [])))
        disadvantaged = False
        for c in conds:
            scope = CONDITION_SCOPE.get(c, "")
            if scope == "todos" or (scope == "fisicos" and attr in PHYSICAL) or (scope == "mentais" and attr not in PHYSICAL):
                disadvantaged = True
        if disadvantaged:
            dis += 1
        dice, mode, natural = roll_d20(resolve_advantage(adv, dis))
        total = natural + base
        out = {"kind": kind, "label": label, "dice": [die_view(d) for d in dice], "mode": mode, "natural": natural,
               "bonus": total - natural, "total": total, "target": 0, "success": None,
               "critical": natural == 20, "formula": "1d20" if total - natural == 0 else f"1d20{total - natural:+d}"}
        if dc > 0:
            ok = total >= dc
            if natural == 20:
                ok = True
            elif natural == 1:
                ok = False
            out["target"], out["success"] = dc, ok
        if ch["subject_type"] == "npc":
            out["private"] = True  # bônus de NPC: o controlador vê; o mod esconde dos próximos
        return out


def die_view(d):
    out = {"sides": d["sides"], "value": d["value"]}
    if not d["kept"]:
        out["dropped"] = True
    return out


def err_body(code, message):
    return {"code": code, "message": message}, False


def abs_url(u, base_url):
    if u.startswith("/") and not u.startswith("//") and base_url:
        return base_url.rstrip("/") + u
    return u


def fix_candidates(cands, base_url):
    for c in cands:
        c["portrait_url"] = abs_url(c["portrait_url"], base_url)
    return cands


STATE = State()
STATE_FILE = os.path.join(os.path.dirname(os.path.abspath(__file__)), "mock_state.json")
PERSIST_ENABLED = True
_dirty = threading.Event()


def mark_dirty():
    STATE.touch()
    _dirty.set()


def saver_loop():
    while True:
        _dirty.wait()
        time.sleep(0.8)
        _dirty.clear()
        with LOCK:
            if not (PERSIST_ENABLED and STATE.settings.get("persist")):
                continue
            data = json.dumps(STATE.dump(), ensure_ascii=False, indent=1)
        tmp = STATE_FILE + ".tmp"
        try:
            with open(tmp, "w", encoding="utf-8") as f:
                f.write(data)
            os.replace(tmp, STATE_FILE)
        except OSError as e:
            print(f"[mock] não consegui salvar {STATE_FILE}: {e}", file=sys.stderr)


# ── Rotas do jogo ───────────────────────────────────────────────────────────────────────────

def h_session(ident):
    """session() do GameHandler: ResolveSession + TouchCharacter. Lança HttpReply com not_linked/foreign."""
    s, err = STATE.resolve_session(ident["account_uuid"], ident["profile_uuid"])
    if err:
        raise HttpReply(200, {"code": err})
    if ident["character_id"]:
        if STATE.touch_character(s["profile"], s["user_id"], ident["character_id"], ident["character_name"]):
            raise HttpReply(200, {"code": "foreign"})
    return s


def rpg_sheet(body, ctx):
    ident = identity(body)
    wc = f_bool(body, "with_candidates")
    ctx.see(ident)
    s = h_session(ident)
    return 200, STATE.game_sheet_for(s["user_id"], s["profile"], s["main"], wc, ctx.base)


def rpg_candidates(body, ctx):
    ident = identity(body)
    f_bool(body, "with_candidates")
    ctx.see(ident)
    s = h_session(ident)
    res = STATE.resolve_game(s["user_id"], s["profile"], s["main"])
    out = {"candidates": fix_candidates(STATE.candidates(s["user_id"], s["profile"]), ctx.base), "code": "ok"}
    bv = State.binding_view(res)
    if bv is not None:
        out["binding"] = bv
    return 200, dict(sorted(out.items()))


def rpg_bind(body, ctx):
    ident = identity(body)
    stype = f_str(body, "subject_type")
    if stype not in ("", "sheet", "npc"):
        raise bad_request()
    sid = f_int(body, "subject_id", unsigned=True)
    clear = f_bool(body, "clear")
    ctx.see(ident)
    if not clear and (stype == "" or sid == 0):
        raise bad_request()
    s = h_session(ident)
    if clear:
        STATE.clear_game(s["user_id"], s["profile"])
    else:
        err = STATE.bind_game(s["user_id"], s["profile"], ident["profile_name"], stype, sid)
        if err:
            code, msg = err
            if code == "invalid":
                raise bad_request()
            return 200, ({"code": code, "message": msg} if msg else {"code": code})
    return 200, STATE.game_sheet_for(s["user_id"], s["profile"], s["main"], False, ctx.base)


def rpg_roll(body, ctx):
    ident = identity(body)
    gm = master(body)
    inp = {
        "command_id": f_str(body, "command_id", required=True, maxlen=100),
        "kind": f_str(body, "kind", required=True),
        "skill": f_str(body, "skill", maxlen=40),
        "attribute": f_str(body, "attribute", maxlen=20),
        "formula": f_str(body, "formula", maxlen=120),
        "label": f_str(body, "label", maxlen=120),
        "dc": f_int(body, "dc", minv=0, maxv=100),
        "secret": f_bool(body, "secret"),
        "advantage": f_flex(body, "advantage"),
        "disadvantage": f_flex(body, "disadvantage"),
    }
    if inp["kind"] not in ("skill", "attribute", "free"):
        raise bad_request()
    ctx.see(ident)
    s = h_session(ident)
    if STATE.busy_next > 0:
        STATE.busy_next -= 1
        STATE.touch()
        return 200, {"code": "busy", "message": "muitas rolagens seguidas; tente de novo em instantes"}
    if not STATE.allow_roll(s["user_id"]):
        return 200, {"code": "busy", "message": "muitas rolagens seguidas; tente de novo em instantes"}
    inp["gm"] = STATE.verified_gm(gm)
    ctx.gm = inp["gm"]
    res = STATE.resolve_game(s["user_id"], s["profile"], s["main"])
    if res["code"] != "ok":
        return 200, {"code": res["code"]}
    out, ok = STATE.game_roll(s["user_id"], res["character"], inp)
    if ok:
        r = out["roll"]
        STATE.counters["rolls"] += 1
        STATE.roll_log.appendleft({
            "t": time.time(), "profile": ident["profile_name"] or ident["profile_uuid"][:8], "character": out["character_name"],
            "kind": inp["kind"], "label": r["label"], "dice": r["dice"], "mode": r["mode"], "natural": r["natural"],
            "bonus": r["bonus"], "total": r["total"], "target": r["target"], "success": r["success"],
            "critical": r["critical"], "formula": r["formula"], "secret": inp["secret"], "private": r.get("private", False),
            "replayed": out.get("replayed", False), "gm": bool(gm["gm_profile_uuid"]), "gm_verified": inp["gm"],
            "command_id": inp["command_id"][:40]})
        STATE.logrev += 1
        STATE.touch()
        return 200, out
    return 200, out


def rpg_peek(body, ctx):
    gm = master(body)
    tgt = {
        "target_profile_uuid": f_str(body, "target_profile_uuid", required=True, is_uuid=True),
        "target_account_uuid": f_str(body, "target_account_uuid", maxlen=36),
        "target_character_id": f_str(body, "target_character_id", omitempty_uuid=True),
        "target_character_name": f_str(body, "target_character_name", maxlen=160),
    }
    ctx.see({"profile_uuid": gm["gm_profile_uuid"], "account_uuid": gm["gm_account_uuid"]})
    if not STATE.verified_gm(gm):
        return 200, {"code": "gm_required"}
    s, err = STATE.resolve_session(tgt["target_account_uuid"], tgt["target_profile_uuid"])
    if err:
        return 200, {"code": err}
    out = STATE.game_sheet_for(s["user_id"], s["profile"], s["main"], False, ctx.base)
    out.pop("candidates", None)
    return 200, out


def link_claim(body, ctx):
    for k in ("code", "profile_uuid", "profile_name", "character_id", "character_name"):
        if body.get(k) is not None and not isinstance(body.get(k), str):
            raise bad_request()
    ident = {"profile_uuid": body.get("profile_uuid") or "", "account_uuid": body.get("profile_uuid") or "",
             "profile_name": body.get("profile_name") or "", "character_name": body.get("character_name") or ""}
    ctx.see(ident)
    profile = norm_uuid(ident["profile_uuid"])
    if profile is None:
        return 400, {"code": "invalid_profile"}
    t = time.monotonic()
    hits = [x for x in STATE.attempts.get(profile, []) if t - x < 600]
    if len(hits) >= 5:
        STATE.attempts[profile] = hits
        return 429, {"code": "too_many_attempts"}
    hits.append(t)
    STATE.attempts[profile] = hits
    code = normalize_code(body.get("code") or "")
    if STATE.settings["link_mode"] == "fixo":
        valid = code != "" and code == normalize_code(STATE.settings["fixed_code"])
    else:
        valid = len(code) >= 6
    if not valid:
        return 404, {"code": "invalid_code"}
    existing = STATE.links.get(profile)
    target = STATE.settings.get("link_target", "auto")
    if existing is not None:
        if target != "auto" and STATE.user(target) and int(target) != existing["user_id"]:
            return 409, {"code": "profile_taken"}
        uid = existing["user_id"]
    else:
        uid = STATE.link_target(ident["profile_name"])
    STATE.link_profile(profile, uid, ident["profile_name"])
    STATE.attempts.pop(profile, None)
    if body.get("character_id") and norm_uuid(body["character_id"]):
        STATE.touch_character(profile, uid, body["character_id"], body.get("character_name") or "")
    return 200, {"account": STATE.user(uid)["discord_tag"], "code": "linked"}


def link_status(body, ctx):
    p = body.get("profile_uuid")
    if p is not None and not isinstance(p, str):
        raise bad_request()
    link = STATE.links.get(norm_uuid(p or "") or "")
    if link is None:
        return 200, {"linked": False}
    return 200, {"account": STATE.user(link["user_id"])["discord_tag"], "linked": True}


# ── Diário mínimo (diary/handler.go) ────────────────────────────────────────────────────────

DIARY_FIELDS = {"account_uuid", "profile_uuid", "character_id", "character_name", "entry_id", "operation_id",
                "base_version", "version", "expected_publication_state", "title", "lore_date", "document"}


def diary_actor(body, ctx):
    if set(body) - DIARY_FIELDS:
        raise bad_request()  # DisallowUnknownFields
    ident = {"profile_uuid": f_str(body, "profile_uuid", required=True, is_uuid=True),
             "account_uuid": f_str(body, "account_uuid", maxlen=36),
             "character_id": f_str(body, "character_id", omitempty_uuid=True),
             "character_name": f_str(body, "character_name", maxlen=160), "profile_name": ""}
    f_int(body, "entry_id", unsigned=True)
    f_str(body, "operation_id", maxlen=64)
    f_int(body, "base_version")
    f_int(body, "version")
    f_str(body, "title", maxlen=200)
    f_str(body, "lore_date", maxlen=120)
    ctx.see(ident)
    if ident["character_id"] == "":
        raise HttpReply(200, {"code": "no_character"})
    s, err = STATE.resolve_session(ident["account_uuid"], ident["profile_uuid"])
    if err == "not_linked":
        raise HttpReply(200, {"code": "not_linked"})
    if err:
        raise HttpReply(200, {"code": "wrong_character"})
    if STATE.touch_character(s["profile"], s["user_id"], ident["character_id"], ident["character_name"]):
        raise HttpReply(200, {"code": "wrong_character"})
    return s, norm_uuid(ident["character_id"]), ident["character_name"]


def diary_entry(e):
    return {k: e[k] for k in ("id", "title", "lore_date", "document", "version", "origin", "updated_at", "character_id",
                              "character_name", "published", "published_version", "publication_state",
                              "has_unpublished_changes", "hidden", "profile_visible")}


def diary_list(body, ctx):
    s, cid, _ = diary_actor(body, ctx)
    items = [e for e in STATE.diary.values() if e["user_id"] == s["user_id"] and (not cid or e["character_id"] == cid)]
    items.sort(key=lambda e: e["updated_at"], reverse=True)
    out = [{"id": e["id"], "title": e["title"], "character_id": e["character_id"], "character_name": e["character_name"],
            "version": e["version"], "publication_state": e["publication_state"], "published": e["published"],
            "has_unpublished_changes": e["has_unpublished_changes"], "hidden": e["hidden"], "updated_at": e["updated_at"]}
           for e in items]
    return 200, {"code": "ok", "entries": out}


def diary_find(s, body, cid):
    e = STATE.diary.get(str(body.get("entry_id") or 0))
    if e is None or e["user_id"] != s["user_id"]:
        raise HttpReply(200, {"code": "not_found"})
    if e["character_id"] and e["character_id"] != cid:
        raise HttpReply(200, {"code": "wrong_character"})
    return e


def diary_get(body, ctx):
    s, cid, _ = diary_actor(body, ctx)
    return 200, {"code": "ok", "entry": diary_entry(diary_find(s, body, cid))}


def diary_save(body, ctx):
    s, cid, cname = diary_actor(body, ctx)
    doc = body.get("document")
    if not isinstance(doc, dict) or not isinstance(doc.get("blocks"), list):
        return 200, {"code": "invalid_document", "message": "documento inválido"}
    if not doc["blocks"]:
        return 200, {"code": "empty"}
    if (body.get("entry_id") or 0) == 0:
        eid = STATE.next_id("diary")
        e = STATE.diary[str(eid)] = {"id": eid, "user_id": s["user_id"], "title": "", "lore_date": "", "document": doc,
                                     "version": 0, "origin": "game", "updated_at": now_iso(), "character_id": cid,
                                     "character_name": cname, "published": False, "published_version": 0,
                                     "publication_state": 0, "has_unpublished_changes": False, "hidden": False,
                                     "profile_visible": True}
    else:
        e = diary_find(s, body, cid)
        if (body.get("base_version") or 0) != e["version"]:
            return 200, {"code": "conflict", "entry": diary_entry(e)}
    e.update(title=(body.get("title") or "").strip() or "Sem título", lore_date=body.get("lore_date") or "", document=doc,
             version=e["version"] + 1, updated_at=now_iso(), origin="game", has_unpublished_changes=e["published"])
    mark_dirty()
    return 200, {"code": "ok", "entry": diary_entry(e)}


def diary_publication(publish):
    def handler(body, ctx):
        s, cid, _ = diary_actor(body, ctx)
        e = diary_find(s, body, cid)
        exp = body.get("expected_publication_state")
        if isinstance(exp, int) and not isinstance(exp, bool) and exp != e["publication_state"]:
            return 200, {"code": "publication_changed"}
        if publish and (body.get("version") or e["version"]) != e["version"]:
            return 200, {"code": "stale"}
        e["published"] = publish
        e["publication_state"] += 1
        e["published_version"] = e["version"] if publish else e["published_version"]
        e["has_unpublished_changes"] = False
        mark_dirty()
        return 200, {"code": "ok", "entry": diary_entry(e)}
    return handler


def events_batch(body, ctx):
    events = body.get("events")
    if events is not None and not isinstance(events, list):
        return 400, {"error": "lote inválido"}
    events = events or []
    if len(events) > 50:
        return 413, {"error": "lote acima de 50 eventos"}
    results = []
    seen = set(STATE.events_seen)
    for ev in events:
        if not isinstance(ev, dict):
            results.append({"event_id": "", "result": "rejected", "code": "invalid_json"})
            continue
        eid = ev.get("event_id") if isinstance(ev.get("event_id"), str) else ""
        if not eid:
            results.append({"event_id": "", "result": "rejected", "code": "missing_event_id"})
            continue
        if eid in seen:
            STATE.counters["events_duplicate"] += 1
            results.append({"event_id": eid, "result": "duplicate"})
            continue
        seen.add(eid)
        STATE.events_seen.append(eid)
        STATE.counters["events"] += 1
        results.append({"event_id": eid, "result": "accepted"})
        ctx.detail = str(ev.get("type") or ev.get("kind") or "")[:40]
    if len(STATE.events_seen) > 5000:
        STATE.events_seen = STATE.events_seen[-4000:]
    if events:
        mark_dirty()
    return 200, {"results": results}


GAME_ROUTES = {
    "/rpg/sheet": (rpg_sheet, BODY_LIMIT),
    "/rpg/candidates": (rpg_candidates, BODY_LIMIT),
    "/rpg/bind": (rpg_bind, BODY_LIMIT),
    "/rpg/roll": (rpg_roll, BODY_LIMIT),
    "/rpg/peek": (rpg_peek, BODY_LIMIT),
    "/link/claim": (link_claim, BODY_LIMIT),
    "/link/status": (link_status, BODY_LIMIT),
    "/diary/list": (diary_list, DIARY_LIMIT),
    "/diary/get": (diary_get, DIARY_LIMIT),
    "/diary/save": (diary_save, DIARY_LIMIT),
    "/diary/publish": (diary_publication(True), DIARY_LIMIT),
    "/diary/unpublish": (diary_publication(False), DIARY_LIMIT),
    "/events/batch": (events_batch, BATCH_LIMIT),
}


class Ctx:
    def __init__(self, base, route):
        self.base, self.route = base, route
        self.who = ""
        self.gm = None
        self.detail = ""

    def see(self, ident):
        if ident.get("profile_name"):
            self.who = ident["profile_name"]
        elif ident.get("profile_uuid"):
            p = STATE.profiles.get(norm_uuid(ident["profile_uuid"]) or "")
            self.who = (p or {}).get("name") or ident["profile_uuid"][:8]
        STATE.see_profile(ident, self.route)


# ── Painel: estado e ações ──────────────────────────────────────────────────────────────────

def panel_state(base):
    s = STATE
    profiles = []
    for p in sorted(s.profiles.values(), key=lambda p: p.get("first_seen", "")):
        uid_link = s.links.get(p["uuid"])
        main = p["account"] == p["uuid"]
        sess, err = s.resolve_session("" if main else p["account"], p["uuid"])
        row = {"uuid": p["uuid"], "name": p.get("name") or "", "account": p["account"], "main": main,
               "account_name": (s.profiles.get(p["account"]) or {}).get("name", ""),
               "linked_user": uid_link["user_id"] if uid_link else None, "character": p.get("character", ""),
               "last_seen": p.get("last_seen", ""), "last_route": p.get("last_route", ""), "session_error": err}
        if sess:
            uid = sess["user_id"]
            row["owner"] = uid
            row["owner_name"] = s.user(uid)["name"]
            row["staff"] = s.is_staff(uid)
            res = s.resolve_game(uid, p["uuid"], sess["main"])
            row["code"] = res["code"]
            row["binding"] = State.binding_view(res)
            row["candidates"] = s.candidates(uid, p["uuid"])
            if res["character"]:
                gs = s.game_sheet(res["character"], base)
                row["sheet"] = {"name": gs["name"], "subject_type": gs["subject_type"], "subject_id": gs["subject_id"],
                                "has_build": gs["has_build"], "pending_review": gs["pending_review"],
                                "version": gs["version"], "resources": gs["resources"], "derived": gs["derived"],
                                "source": res["source"]}
        profiles.append(row)
    users = []
    for u in sorted(s.users.values(), key=lambda u: u["id"]):
        sheets = s.user_sheets(u["id"])
        users.append({"id": u["id"], "name": u["name"], "tag": u["discord_tag"], "staff": s.is_staff(u["id"]),
                      "sheet_state": u.get("sheet_state", ""),
                      "pending": any(x["mech"].get("draft") for x in sheets),
                      "sheets": [{"id": x["id"], "name": x["nome"], "kind": x["kind"], "active": x["is_active"],
                                  "has_build": x["mech"]["build"] is not None,
                                  "pending": bool(x["mech"].get("draft"))} for x in sheets],
                      "profiles": [k for k, l in s.links.items() if l["user_id"] == u["id"]]})
    npcs = [{"id": n["id"], "name": n["name"], "title": n["title"], "status": n["status"], "linked": n.get("linked_user_id"),
             "has_build": n["mech"]["build"] is not None} for n in sorted(s.npcs.values(), key=lambda n: n["name"])]
    return {
        "rev": s.rev, "logrev": s.logrev, "settings": s.settings, "busy_next": s.busy_next, "counters": s.counters,
        "uptime": int(time.time() - s.started), "diary_entries": len(s.diary), "profiles": profiles, "users": users,
        "npcs": npcs, "log": list(s.log)[:150], "rolls": list(s.roll_log)[:60],
        "conditions": [[c[0], c[1]] for c in CONDITIONS], "vitals": VITALS, "base": base,
        "state_file": STATE_FILE if (PERSIST_ENABLED and s.settings.get("persist")) else "",
    }


def _int(v, default=0):
    try:
        return int(v)
    except (TypeError, ValueError):
        return default


def panel_action(name, data):
    s = STATE
    if name == "settings":
        st = s.settings
        if data.get("down") in ("off", "503", "transporte", "desativada"):
            st["down"] = data["down"]
        if "latency_ms" in data:
            st["latency_ms"] = max(0, min(30000, _int(data["latency_ms"])))
        if data.get("link_mode") in ("qualquer", "fixo"):
            st["link_mode"] = data["link_mode"]
        if isinstance(data.get("fixed_code"), str) and data["fixed_code"].strip():
            st["fixed_code"] = data["fixed_code"].strip()[:32]
        if "link_target" in data:
            st["link_target"] = str(data["link_target"]) if s.user(data["link_target"]) else "auto"
        for k in ("auto_link", "persist"):
            if k in data:
                st[k] = bool(data[k])
        if isinstance(data.get("token"), str):
            st["token"] = data["token"].strip()
        return "Configurações aplicadas."
    if name == "busy":
        s.busy_next += max(1, _int(data.get("n"), 1))
        return f"As próximas {s.busy_next} rolagem(ns) vão voltar busy."
    if name == "busy_clear":
        s.busy_next = 0
        return "busy cancelado."
    if name == "link":
        prof = norm_uuid(data.get("profile", ""))
        if not prof:
            return "Perfil inválido."
        target = data.get("user_id")
        p = s.profiles.get(prof, {})
        uid = int(target) if s.user(target) else s.new_user(f"Conta de {p.get('name') or prof[:8]}",
                                                            normalize_key(p.get("name") or "jogador") or "jogador")
        if not s.user(target):
            s.sheet_state(uid, "completa")
        s.link_profile(prof, uid, p.get("name", ""))
        return f"{p.get('name') or prof[:8]} vinculado a {s.user(uid)['name']}."
    if name == "unlink":
        prof = norm_uuid(data.get("profile", ""))
        if prof in s.links:
            del s.links[prof]
            s.touch()
        return "Vínculo removido."
    if name == "foreign":
        prof = norm_uuid(data.get("profile", ""))
        others = [u["id"] for u in s.users.values() if u["id"] != 1]
        uid = 2 if s.user(2) else (others[0] if others else s.new_user("Outra pessoa (mock)", "outra.pessoa", seed=False))
        p = s.profiles.get(prof, {})
        acc_link = s.links.get(p.get("account", ""))
        if acc_link and acc_link["user_id"] == uid:
            uid = 1
        s.link_profile(prof, uid, p.get("name", ""))
        return "O alt agora está vinculado a OUTRA conta: as chamadas dele devem voltar foreign."
    if name == "staff":
        u = s.user(data.get("user_id"))
        if u:
            u["role"] = "staff" if data.get("staff") else "player"
            s.touch()
        return "Cargo atualizado."
    if name == "sheet_state":
        uid = _int(data.get("user_id"))
        if s.user(uid) and data.get("state") in ("completa", "antiga", "aguardando", "sem"):
            s.sheet_state(uid, data["state"])
            return "Estado da ficha trocado (a version muda: o mod percebe que a ficha mudou no site)."
        return "Estado inválido."
    if name == "approve":
        got = s.approve(_int(data.get("user_id")))
        return f"Construção de {got} aprovada." if got else "Nada aguardando aprovação nesta conta."
    if name == "resources":
        subj = s.subject(data.get("subject_type"), data.get("subject_id"))
        if not subj:
            return "Ficha não encontrada."
        r = subj["mech"]["resources"]
        for k in ("vida", "stamina", "sanidade", "pe", "temp_hp", "exhaustion", "charges_used"):
            if k in data:
                r[k] = max(0, _int(data[k], r[k]))
        if data.get("vital") in VITALS:
            r["vital"] = data["vital"]
        if isinstance(data.get("conditions"), list):
            known = {c[0] for c in CONDITIONS}
            r["conditions"] = [c for c in data["conditions"] if c in known]
        if "initialized" in data:
            r["initialized"] = bool(data["initialized"])
        s.touch()
        return "Recursos salvos (version nova)."
    if name == "bind":
        prof = norm_uuid(data.get("profile", ""))
        p = s.profiles.get(prof)
        if not p:
            return "Perfil desconhecido."
        sess, err = s.resolve_session("" if p["account"] == prof else p["account"], prof)
        if err:
            return f"Sem dono válido ({err})."
        e = s.bind_game(sess["user_id"], prof, p.get("name", ""), data.get("subject_type"), _int(data.get("subject_id")))
        return f"Recusado: {e[0]} {e[1] or ''}" if e else "Vínculo de ficha gravado."
    if name == "unbind":
        prof = norm_uuid(data.get("profile", ""))
        b = s.bindings.get(prof)
        if b:
            s.clear_game(b["user_id"], prof)
        return "Vínculo de ficha removido."
    if name == "npc_link":
        n = s.npcs.get(str(_int(data.get("npc_id"))))
        if n:
            uid = data.get("user_id")
            n["linked_user_id"] = int(uid) if s.user(uid) else None
            s.touch()
        return "NPC atualizado."
    if name == "clear_log":
        s.log.clear()
        s.roll_log.clear()
        s.logrev += 1
        return "Log limpo."
    if name == "reset":
        s.reset()
        if os.path.exists(STATE_FILE):
            try:
                os.remove(STATE_FILE)
            except OSError:
                pass
        return "Tudo voltou ao estado inicial."
    return "Ação desconhecida."


# ── Servidor HTTP ───────────────────────────────────────────────────────────────────────────

class Handler(BaseHTTPRequestHandler):
    server_version = "AurorionMock/1.0"
    protocol_version = "HTTP/1.1"

    def log_message(self, fmt, *args):  # o log que importa vai para o painel
        pass

    def base_url(self):
        host = self.headers.get("Host") or f"{self.server.server_address[0]}:{self.server.server_address[1]}"
        return f"http://{host}"

    def send(self, status, body, ctype="application/json; charset=utf-8", extra=None):
        if isinstance(body, (dict, list)):
            data = json.dumps(body, ensure_ascii=False, separators=(",", ":")).encode()
        elif isinstance(body, str):
            data = body.encode()
        else:
            data = body
        self.send_response(status)
        self.send_header("Content-Type", ctype)
        self.send_header("Content-Length", str(len(data)))
        for k, v in (extra or {}).items():
            self.send_header(k, v)
        self.end_headers()
        if self.command != "HEAD":
            self.wfile.write(data)

    def read_body(self, limit):
        n = _int(self.headers.get("Content-Length"), 0)
        if n > limit:
            # MaxBytesReader: corpo grande demais falha a leitura → 400 (o resto é descartado)
            self.rfile.read(n)
            return None, True
        return self.rfile.read(n) if n > 0 else b"", False

    # ── GET ────────────────────────────────────────────────────────────────────────────────
    def do_GET(self):
        path = urlparse(self.path).path
        if path in ("/", "/mock", "/mock/"):
            return self.send(200, PANEL_HTML, "text/html; charset=utf-8", {"Cache-Control": "no-store"})
        if path == "/mock/api/state":
            with LOCK:
                st = panel_state(self.base_url())
            return self.send(200, st, extra={"Cache-Control": "no-store"})
        if path.startswith("/uploads/retratos/") and path.endswith(".svg"):
            return self.send(200, portrait_svg(path.rsplit("/", 1)[-1][:-4]), "image/svg+xml; charset=utf-8")
        m = re.match(r"^/profile/rpg/ficha/(\d+)$", path)
        if m or path == "/profile/rpg":
            with LOCK:
                page = sheet_page(_int(m.group(1)) if m else 0, self.base_url())
            return self.send(200, page, "text/html; charset=utf-8")
        if path == "/favicon.ico":
            return self.send(204, b"", "image/x-icon")
        self.send(404, "404 page not found", "text/plain")

    # ── POST ───────────────────────────────────────────────────────────────────────────────
    def do_POST(self):
        path = urlparse(self.path).path
        if path.startswith("/mock/api/"):
            raw, _ = self.read_body(1 << 20)
            try:
                data = json.loads(raw or b"{}")
            except ValueError:
                data = {}
            with LOCK:
                msg = panel_action(path[len("/mock/api/"):], data if isinstance(data, dict) else {})
                mark_dirty()
                st = panel_state(self.base_url())
            return self.send(200, {"message": msg, "state": st})
        if not path.startswith(PREFIX + "/") or path[len(PREFIX):] not in GAME_ROUTES:
            return self.send(404, "404 page not found", "text/plain")
        route = path[len(PREFIX):]
        fn, limit = GAME_ROUTES[route]
        started = time.monotonic()
        raw, too_big = self.read_body(limit)
        st = STATE.settings
        if st["latency_ms"] > 0:
            time.sleep(st["latency_ms"] / 1000)
        status, body, ctx, note = 0, None, Ctx(self.base_url(), route), ""
        if st["down"] == "transporte":
            with LOCK:
                self.record(route, 0, "", started, ctx, "conexão derrubada (site fora: sem resposta)")
            self.close_connection = True
            try:
                self.connection.shutdown(socket.SHUT_RDWR)
            except OSError:
                pass
            return
        token = self.headers.get("X-Game-Token") or ""
        if st["down"] == "desativada":
            status, body, note = 503, {"code": "integration_disabled", "error": "integração do jogo desativada"}, "GAME_API_TOKEN vazio no site"
        elif st["down"] == "503":
            status, body, note = 503, {"code": "unavailable"}, "site fora do ar (503)"
        elif token == "" or (st["token"] and token != st["token"]):
            status, body = 403, {"code": "invalid_game_token", "error": "token do jogo inválido"}
        elif too_big:
            status, body = (413, {"error": "lote grande demais"}) if route == "/events/batch" else (400, {"code": "invalid_request"})
        else:
            try:
                parsed = json.loads(raw.decode("utf-8")) if raw else None
            except (ValueError, UnicodeDecodeError):
                parsed = None
            if not isinstance(parsed, dict):
                status, body = (400, {"error": "lote inválido"}) if route == "/events/batch" else (400, {"code": "invalid_request"})
            else:
                with LOCK:
                    try:
                        status, body = fn(parsed, ctx)
                    except HttpReply as r:
                        status, body = r.status, r.body
                    except Exception as e:  # erro do próprio mock: vira 503 unavailable, como o backend
                        status, body, note = 503, {"code": "unavailable"}, f"erro interno do mock: {e!r}"
                        import traceback
                        traceback.print_exc()
        with LOCK:
            self.record(route, status, body.get("code", "") if isinstance(body, dict) else "", started, ctx, note or ctx.detail)
        extra = {"Cache-Control": "private, no-store"} if route.startswith(("/rpg/", "/diary/")) else None
        self.send(status, body, extra=extra)

    def record(self, route, status, code, started, ctx, note):
        STATE.counters["calls"] += 1
        msg = note
        if isinstance(code, str) and code == "" and status == 200 and route == "/events/batch":
            code = "results"
        STATE.log.appendleft({"t": time.time(), "route": route, "status": status, "code": code,
                              "ms": int((time.monotonic() - started) * 1000), "who": ctx.who, "note": msg,
                              "gm": ctx.gm})
        STATE.logrev += 1


def portrait_svg(key):
    h = int(hashlib.md5(key.encode()).hexdigest()[:6], 16)
    hue = h % 360
    letter = html.escape((key[:1] or "?").upper())
    return (f'<svg xmlns="http://www.w3.org/2000/svg" width="128" height="128" viewBox="0 0 128 128">'
            f'<defs><linearGradient id="g" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="hsl({hue},45%,32%)"/>'
            f'<stop offset="1" stop-color="#1b1030"/></linearGradient></defs><rect width="128" height="128" rx="18" fill="url(#g)"/>'
            f'<circle cx="64" cy="64" r="50" fill="none" stroke="#d9b45a" stroke-width="3"/>'
            f'<text x="64" y="80" font-family="Georgia,serif" font-size="52" text-anchor="middle" fill="#f3d98b">{letter}</text></svg>')


def sheet_page(rid, base):
    ch = STATE.rpg.get(str(rid))
    body = "<p>Esta é a página falsa do site. A ficha de verdade fica em Perfil → RPG no site.</p>"
    title = "Perfil → RPG (mock)"
    if ch and STATE.subject_of(ch):
        gs = STATE.game_sheet(ch, base)
        title = gs["name"]
        body = f"<pre>{html.escape(json.dumps(gs, ensure_ascii=False, indent=2))}</pre>"
    return (f"<!doctype html><meta charset=utf-8><title>{html.escape(title)}</title>"
            f"<body style='background:#140b24;color:#eadff7;font-family:system-ui;padding:24px'>"
            f"<h1 style='color:#e6c068;font-family:Georgia,serif'>{html.escape(title)}</h1>{body}"
            f"<p><a style='color:#e6c068' href='/'>← painel do mock</a></p></body>")


# ── Painel (HTML/CSS/JS embutidos) ──────────────────────────────────────────────────────────

PANEL_HTML = r"""<!doctype html>
<html lang="pt-BR">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Aurorion · Mock do site</title>
<style>
:root{
  --bg:#120a20; --bg2:#1a0f2e; --card:#221338; --card2:#2b1a47; --line:#3d2861; --line2:#55397f;
  --ink:#ece3f7; --mute:#a99bc2; --faint:#7a6b95;
  --gold:#e3bc5f; --gold2:#f5dc94; --goldd:#9c7a2c;
  --ok:#6fd39a; --bad:#ff7a8a; --warn:#ffb55e; --info:#8fb8ff; --vio:#b18cff;
}
*{box-sizing:border-box}
body{margin:0;background:radial-gradient(1200px 700px at 10% -10%,#2c1650 0,transparent 60%),radial-gradient(900px 600px at 110% 10%,#24123f 0,transparent 55%),var(--bg);
  color:var(--ink);font:14px/1.45 system-ui,-apple-system,"Segoe UI",Roboto,sans-serif;min-height:100vh}
h1,h2,h3{font-family:Georgia,"Times New Roman",serif;font-weight:600;letter-spacing:.3px;margin:0}
h2{font-size:18px;color:var(--gold2);display:flex;align-items:center;gap:8px}
h2::before{content:"";width:6px;height:18px;background:linear-gradient(var(--gold),var(--goldd));border-radius:2px}
a{color:var(--gold)}
header{position:sticky;top:0;z-index:5;background:linear-gradient(180deg,rgba(18,10,32,.97),rgba(18,10,32,.88));backdrop-filter:blur(6px);
  border-bottom:1px solid var(--line);padding:12px 20px;display:flex;flex-wrap:wrap;gap:12px 20px;align-items:center}
.brand{display:flex;align-items:center;gap:10px}
.sigil{width:34px;height:34px;border-radius:50%;border:2px solid var(--gold);display:grid;place-items:center;color:var(--gold2);
  font-family:Georgia,serif;font-size:18px;box-shadow:0 0 18px rgba(227,188,95,.25) inset}
.brand h1{font-size:20px;color:var(--gold2)}
.brand small{display:block;color:var(--mute);font-size:12px;font-family:system-ui}
.pills{display:flex;flex-wrap:wrap;gap:8px;margin-left:auto}
.pill{border:1px solid var(--line2);border-radius:999px;padding:3px 10px;font-size:12px;color:var(--mute);background:var(--bg2);white-space:nowrap}
.pill b{color:var(--ink)}
.pill.ok{border-color:#2f7a52;color:var(--ok)} .pill.bad{border-color:#8a2d3b;color:var(--bad)} .pill.warn{border-color:#8a5a22;color:var(--warn)}
main{max-width:1500px;margin:0 auto;padding:18px 20px 60px;display:grid;gap:18px;grid-template-columns:minmax(0,1fr) 430px}
@media (max-width:1100px){main{grid-template-columns:1fr}}
section{background:linear-gradient(180deg,var(--card),var(--bg2));border:1px solid var(--line);border-radius:14px;padding:16px;
  box-shadow:0 10px 30px rgba(0,0,0,.25)}
.col{display:grid;gap:18px;align-content:start;min-width:0}
.row{display:flex;flex-wrap:wrap;gap:8px;align-items:center}
.grid{display:grid;gap:12px;grid-template-columns:repeat(auto-fill,minmax(330px,1fr));margin-top:12px}
.card{background:var(--card2);border:1px solid var(--line);border-radius:12px;padding:12px;display:grid;gap:8px;min-width:0}
.card.alt{border-color:#5b3d8a}
.card .top{display:flex;gap:10px;align-items:center}
.head{width:40px;height:40px;border-radius:8px;background:#000 center/cover;border:1px solid var(--line2);image-rendering:pixelated;flex:none}
.name{font-weight:650;font-size:15px}
.uuid{color:var(--faint);font:11px ui-monospace,Consolas,monospace;word-break:break-all}
.tag{font-size:11px;border-radius:6px;padding:1px 7px;border:1px solid var(--line2);color:var(--mute);white-space:nowrap}
.tag.gold{border-color:var(--goldd);color:var(--gold2)} .tag.ok{border-color:#2f7a52;color:var(--ok)}
.tag.bad{border-color:#8a2d3b;color:var(--bad)} .tag.warn{border-color:#8a5a22;color:var(--warn)} .tag.vio{border-color:#6a4aa8;color:var(--vio)}
.kv{display:grid;grid-template-columns:110px minmax(0,1fr);gap:4px 10px;font-size:13px}
.kv>span:nth-child(odd){color:var(--mute)}
button,select,input{font:inherit;color:var(--ink);background:#170d29;border:1px solid var(--line2);border-radius:8px;padding:5px 9px}
input[type=number]{width:74px} input[type=text]{min-width:0}
input:focus,select:focus{outline:2px solid rgba(227,188,95,.45);border-color:var(--gold)}
button{cursor:pointer;background:linear-gradient(180deg,#352156,#261640);transition:.12s}
button:hover{border-color:var(--gold);color:var(--gold2)}
button.gold{background:linear-gradient(180deg,#c99d3e,#8e6a22);border-color:#e0b85a;color:#1b1030;font-weight:650}
button.gold:hover{color:#000;filter:brightness(1.08)}
button.danger{border-color:#8a2d3b;color:#ffb3bd} button.danger:hover{background:#4a1726;color:#fff}
button.sm{padding:2px 8px;font-size:12px}
.seg{display:inline-flex;border:1px solid var(--line2);border-radius:9px;overflow:hidden}
.seg button{border:0;border-radius:0;background:#170d29;padding:5px 10px}
.seg button.on{background:linear-gradient(180deg,#c99d3e,#8e6a22);color:#1b1030;font-weight:650}
.res{display:grid;grid-template-columns:repeat(auto-fill,minmax(130px,1fr));gap:6px}
.res label{display:flex;align-items:center;justify-content:space-between;gap:6px;font-size:12px;color:var(--mute);background:#170d29;
  border:1px solid var(--line);border-radius:8px;padding:3px 6px}
.res label input{width:62px;padding:2px 5px}
.conds{display:flex;flex-wrap:wrap;gap:4px}
.conds label{font-size:11px;border:1px solid var(--line);border-radius:999px;padding:1px 8px;cursor:pointer;user-select:none;color:var(--mute)}
.conds input{display:none}
.conds label.on{border-color:var(--warn);color:var(--warn);background:rgba(255,181,94,.08)}
.bar{height:6px;border-radius:9px;background:#140b22;overflow:hidden;border:1px solid var(--line)}
.bar i{display:block;height:100%;background:linear-gradient(90deg,var(--goldd),var(--gold))}
details summary{cursor:pointer;color:var(--gold);font-size:13px}
.empty{color:var(--mute);padding:18px;text-align:center;border:1px dashed var(--line2);border-radius:12px;margin-top:12px}
table{width:100%;border-collapse:collapse;font-size:12.5px}
th{text-align:left;color:var(--mute);font-weight:500;border-bottom:1px solid var(--line);padding:4px 6px;position:sticky;top:0;background:var(--card)}
td{border-bottom:1px solid #2a1a43;padding:4px 6px;vertical-align:top}
.log{max-height:430px;overflow:auto;margin-top:8px;border-radius:8px}
.mono{font-family:ui-monospace,Consolas,monospace}
.c-ok{color:var(--ok)} .c-bad{color:var(--bad)} .c-warn{color:var(--warn)} .c-info{color:var(--info)} .c-mute{color:var(--mute)}
.roll{display:grid;grid-template-columns:56px minmax(0,1fr);gap:10px;align-items:center;padding:8px;border:1px solid var(--line);border-radius:10px;background:var(--card2);margin-top:8px}
.d20{width:56px;height:56px;display:grid;place-items:center;font:700 20px Georgia,serif;color:var(--gold2);
  background:radial-gradient(circle at 30% 30%,#4a2f7a,#1c1033);clip-path:polygon(50% 0,93% 25%,93% 75%,50% 100%,7% 75%,7% 25%)}
.d20.crit{color:#1b1030;background:radial-gradient(circle at 30% 30%,#ffe9a8,#c99d3e)} .d20.fumble{color:#fff;background:radial-gradient(circle at 30% 30%,#ff8796,#6b1424)}
.dice span{display:inline-block;min-width:22px;text-align:center;border:1px solid var(--line2);border-radius:5px;margin:1px;font:12px ui-monospace,monospace;padding:0 3px}
.dice span.drop{opacity:.4;text-decoration:line-through}
.toast{position:fixed;right:16px;bottom:16px;background:#2b1a47;border:1px solid var(--gold);color:var(--gold2);padding:10px 14px;border-radius:10px;
  box-shadow:0 10px 30px rgba(0,0,0,.4);opacity:0;transform:translateY(10px);transition:.2s;max-width:420px;z-index:9}
.toast.show{opacity:1;transform:none}
.help{color:var(--mute);font-size:12.5px}
.help code,.mono-inline{background:#170d29;border:1px solid var(--line);border-radius:5px;padding:0 5px;font:12px ui-monospace,monospace;color:var(--gold2)}
.users{display:grid;gap:10px;margin-top:12px}
.sheets{display:flex;flex-wrap:wrap;gap:4px}
@media (max-width:560px){header{padding:10px 12px}main{padding:12px}.kv{grid-template-columns:92px minmax(0,1fr)}.grid{grid-template-columns:1fr}}
</style>
</head>
<body>
<header>
  <div class="brand"><div class="sigil">A</div><div><h1>Aurorion · mock do site</h1><small>ficha no jogo, /vincular, diário e eventos — tudo local</small></div></div>
  <div class="pills" id="pills"></div>
</header>
<main>
  <div class="col">
    <section>
      <h2>Perfis Minecraft vistos</h2>
      <p class="help">Qualquer perfil que chamar o mock aparece aqui sozinho. Principal = conta da sessão (<code>profile_uuid == account_uuid</code>); alt = perfil de <code>/personagem alt</code>, dono resolvido pela conta principal.</p>
      <div id="profiles"></div>
    </section>
    <section>
      <h2>Contas do site</h2>
      <p class="help">Cada conta tem 3 fichas de exemplo (completa, antiga e com construção enviada) e 2 NPCs ligados. Trocar o estado muda a ficha ativa e a <code>version</code>.</p>
      <div id="users" class="users"></div>
    </section>
    <section>
      <h2>NPCs</h2>
      <p class="help">NPC sem jogador e ativo aparece para staff como <code>staff_only</code>; vincular liga o NPC à pessoa. Liberar um NPC já vinculado deixa o vínculo <code>stale</code>.</p>
      <div class="log" style="max-height:none"><table id="npcs"></table></div>
    </section>
  </div>
  <div class="col">
    <section>
      <h2>Controles do site</h2>
      <div style="display:grid;gap:10px;margin-top:10px">
        <div class="row"><span class="help" style="min-width:92px">Disponível</span>
          <div class="seg" id="down"></div></div>
        <div class="row"><span class="help" style="min-width:92px">Latência</span>
          <input type="number" id="latency" min="0" max="30000" step="100"> <span class="help">ms (o mod desiste com 12000)</span>
          <button class="sm" data-lat="0">0</button><button class="sm" data-lat="1500">1,5 s</button><button class="sm" data-lat="4000">4 s</button><button class="sm" data-lat="13000">13 s</button></div>
        <div class="row"><span class="help" style="min-width:92px">Rolagem</span>
          <button id="busy">Forçar busy na próxima</button><span id="busyinfo" class="help"></span></div>
        <div class="row"><span class="help" style="min-width:92px">/vincular</span>
          <select id="linkmode"><option value="qualquer">qualquer código com 6+ caracteres</option><option value="fixo">só o código fixo</option></select>
          <input type="text" id="fixed" size="11"></div>
        <div class="row"><span class="help" style="min-width:92px">Vai para</span>
          <select id="linktarget"></select>
          <label class="help"><input type="checkbox" id="autolink"> vincular sozinho perfis novos</label></div>
        <div class="row"><span class="help" style="min-width:92px">Token</span>
          <input type="text" id="token" placeholder="vazio = aceita qualquer" size="20">
          <label class="help"><input type="checkbox" id="persist"> salvar em mock_state.json</label></div>
        <div class="row"><button class="gold" id="apply">Aplicar</button><button id="clearlog">Limpar log</button>
          <button class="danger" id="reset">Resetar tudo</button></div>
      </div>
    </section>
    <section>
      <h2>Rolagens</h2>
      <div id="rolls" class="log" style="max-height:520px"></div>
    </section>
    <section>
      <h2>Chamadas recebidas</h2>
      <div class="log"><table id="calls"></table></div>
    </section>
  </div>
</main>
<div class="toast" id="toast"></div>
<script>
"use strict";
let S=null, lastRev=-1, lastLog=-1, dirty=new Set(), dirtyAt=0;
const $=s=>document.querySelector(s);
const esc=v=>String(v??"").replace(/[&<>"']/g,c=>({"&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;","'":"&#39;"}[c]));
const hm=t=>new Date(t*1000).toLocaleTimeString("pt-BR");
function toast(m){const t=$("#toast");t.textContent=m;t.classList.add("show");clearTimeout(t._h);t._h=setTimeout(()=>t.classList.remove("show"),3200)}
async function act(name,data){
  try{const r=await fetch("/mock/api/"+name,{method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify(data||{})});
    const j=await r.json(); if(j.message)toast(j.message); dirty.clear(); apply(j.state,true);}catch(e){toast("Falhou: "+e)}
}
async function poll(){
  try{const r=await fetch("/mock/api/state",{cache:"no-store"}); apply(await r.json(),false);}catch(e){$("#pills").innerHTML='<span class="pill bad">mock parado?</span>'}
}
function apply(st,force){
  S=st; pills(); settings(force);
  if(dirty.size&&Date.now()-dirtyAt>20000)dirty.clear(); // edição abandonada não congela a tela
  const editing=document.activeElement&&document.activeElement.closest&&document.activeElement.closest("#profiles,#users,#npcs");
  if(force||(st.rev!==lastRev&&!editing&&dirty.size===0)){lastRev=st.rev;profiles();users();npcs();}
  if(force||st.logrev!==lastLog){lastLog=st.logrev;calls();rolls();}
}
function pills(){
  const s=S.settings, c=S.counters;
  const down={off:['ok','site no ar'],"503":['bad','fora do ar · 503'],transporte:['bad','fora do ar · sem resposta'],desativada:['warn','integração desativada']}[s.down];
  $("#pills").innerHTML=`<span class="pill ${down[0]}">${down[1]}</span>`+
   (s.latency_ms?`<span class="pill warn">lento · ${s.latency_ms} ms</span>`:"")+
   (S.busy_next?`<span class="pill warn">busy × ${S.busy_next}</span>`:"")+
   `<span class="pill">chamadas <b>${c.calls}</b></span><span class="pill">rolagens <b>${c.rolls}</b></span>`+
   `<span class="pill">eventos <b>${c.events}</b>${c.events_duplicate?` (+${c.events_duplicate} dup.)`:""}</span>`+
   `<span class="pill">diário <b>${S.diary_entries}</b></span>`+
   `<span class="pill">código fixo <b class="mono">${esc(s.fixed_code)}</b></span>`;
}
function settings(force){
  const s=S.settings;
  $("#down").innerHTML=[["off","No ar"],["503","503"],["transporte","Sem resposta"],["desativada","Desativada"]]
    .map(([v,l])=>`<button data-down="${v}" class="${s.down===v?"on":""}">${l}</button>`).join("");
  $("#busyinfo").textContent=S.busy_next?`${S.busy_next} na fila · `:"";
  if(S.busy_next&&!$("#busyclear")){$("#busyinfo").insertAdjacentHTML("afterend",'<button class="sm" id="busyclear">cancelar</button>')}
  if(!S.busy_next&&$("#busyclear"))$("#busyclear").remove();
  const ae=document.activeElement;
  const set=(id,v,prop="value")=>{const el=$(id); if(force||(ae!==el&&!dirty.has(id)))el[prop]=v};
  set("#latency",s.latency_ms); set("#linkmode",s.link_mode); set("#fixed",s.fixed_code); set("#token",s.token);
  set("#autolink",s.auto_link,"checked"); set("#persist",s.persist,"checked");
  const lt=$("#linktarget"); if(force||(ae!==lt&&!dirty.has("#linktarget"))){
    lt.innerHTML=`<option value="auto">automático (sua conta, depois conta nova)</option>`+S.users.map(u=>`<option value="${u.id}">${esc(u.name)}</option>`).join("");
    lt.value=s.link_target;}
}
function userName(id){const u=S.users.find(u=>u.id===id);return u?u.name:"#"+id}
const codeTag={ok:["ok","ficha ok"],needs_binding:["warn","needs_binding · alt sem ficha"],no_sheet:["warn","no_sheet · sem ficha ativa"]};
function profiles(){
  const el=$("#profiles");
  if(!S.profiles.length){el.innerHTML=`<div class="empty">Nenhum perfil ainda. Entre no mundo com o mod e abra a ficha (tecla <code>'</code> ou <code>/ficha</code>), ou use <code>/vincular</code>.</div>`;return}
  const open=new Set([...el.querySelectorAll("[data-p] details[open]")].map(d=>d.closest("[data-p]").dataset.p));
  el.innerHTML='<div class="grid">'+S.profiles.map(profileCard).join("")+"</div>";
  el.querySelectorAll("[data-p]").forEach(c=>{const d=c.querySelector("details");if(d&&open.has(c.dataset.p))d.open=true});
}
function profileCard(p){
  const nm=p.name||(p.main?"(conta principal)":"(sem nome)");
  let kind=p.main?'<span class="tag gold">principal</span>':`<span class="tag vio">alt de ${esc(p.account_name||p.account.slice(0,8))}</span>`;
  let link=p.linked_user?`<b>${esc(userName(p.linked_user))}</b>`:'<span class="c-mute">não vinculado</span>';
  const userOpts=`<option value="novo">conta nova</option>`+S.users.map(u=>`<option value="${u.id}" ${u.id===p.linked_user?"selected":""}>${esc(u.name)}</option>`).join("");
  let linkCtl=`<select data-f="user">${userOpts}</select><button class="sm" data-a="link">Vincular</button>`+(p.linked_user?`<button class="sm" data-a="unlink">Desvincular</button>`:"");
  if(!p.main)linkCtl+=`<button class="sm" data-a="foreign" title="Liga este alt a outra conta: o site deve responder foreign">alt → outra conta</button>`;
  let owner="", use="", bind="", res="";
  if(p.session_error){
    use=p.session_error==="foreign"?'<span class="tag bad">foreign · alt e conta em contas diferentes</span>':'<span class="tag bad">not_linked · conta sem /vincular</span>';
  }else{
    owner=`<span>Dono</span><span>${esc(p.owner_name)} ${p.staff?'<span class="tag gold">staff</span>':'<span class="tag">jogador</span>'} <button class="sm" data-a="staff" data-u="${p.owner}" data-v="${p.staff?0:1}">${p.staff?"tirar staff":"tornar staff"}</button></span>`;
    const ct=codeTag[p.code]||["bad",p.code];
    use=`<span class="tag ${ct[0]}">${esc(ct[1])}</span>`;
    if(p.sheet){const sh=p.sheet;use+=` <b>${esc(sh.name)}</b> <span class="tag">${sh.subject_type} #${sh.subject_id}</span> <span class="tag ${sh.source==="explicit"?"vio":""}">${sh.source==="explicit"?"explícita":"padrão"}</span>`+
      (sh.has_build?"":(sh.pending_review?' <span class="tag warn">aguardando aprovação</span>':' <span class="tag warn">sem construção</span>'))+
      ` <a href="/profile/rpg/ficha/${p.binding?p.binding.rpg_character_id:""}" target="_blank" class="mono" style="font-size:11px">v ${sh.version}</a>`;}
    if(p.binding&&p.binding.stale)use+=' <span class="tag bad">vínculo stale</span>';
    const opts=p.candidates.map(c=>`<option value="${c.subject_type}:${c.subject_id}" ${p.binding&&p.binding.source==="explicit"&&p.binding.subject_type===c.subject_type&&p.binding.subject_id===c.subject_id?"selected":""}>${c.subject_type==="sheet"?"Ficha":"NPC"} · ${esc(c.name)}${c.title?" — "+esc(c.title):""}${c.staff_only?" (livre, staff)":""}${c.bound_profiles.length?" · usada por "+esc(c.bound_profiles.join(", ")):""}</option>`).join("");
    bind=`<span>Vínculo</span><span class="row"><select data-f="cand" style="max-width:100%">${opts}</select><button class="sm" data-a="bind">Usar</button>${p.binding&&p.binding.source==="explicit"?'<button class="sm" data-a="unbind">Limpar</button>':""}</span>`;
    if(p.sheet)res=resourcesForm(p.sheet);
  }
  return `<div class="card ${p.main?"":"alt"}" data-p="${p.uuid}">
    <div class="top"><div class="head" style="background-image:url(https://mc-heads.net/avatar/${p.uuid}/40)"></div>
      <div style="min-width:0"><div class="name">${esc(nm)} ${kind}</div><div class="uuid">${p.uuid}</div></div></div>
    <div class="kv"><span>Conta do site</span><span>${link}</span><span></span><span class="row">${linkCtl}</span>${owner}
      <span>Ficha em uso</span><span>${use}</span>${bind}
      ${p.character?`<span>Personagem</span><span>${esc(p.character)}</span>`:""}
      <span>Última chamada</span><span class="c-mute">${esc(p.last_route||"")} ${p.last_seen?esc(p.last_seen.slice(11,19)):""}</span></div>
    ${res}</div>`;
}
function resourcesForm(sh){
  const r=sh.resources, d=sh.derived||{};
  const f=(k,l,mx)=>`<label>${l}<span><input type="number" data-r="${k}" value="${r[k]}" min="0">${mx!==undefined?`<span class="c-mute">/${mx}</span>`:""}</span></label>`;
  const conds=S.conditions.map(([id,l])=>`<label class="${r.conditions.includes(id)?"on":""}"><input type="checkbox" data-c="${id}" ${r.conditions.includes(id)?"checked":""}>${esc(l)}</label>`).join("");
  const pct=(a,b)=>b?Math.max(0,Math.min(100,a*100/b)):0;
  return `<details><summary>Recursos e condições (muda a version)</summary>
    <div style="display:grid;gap:8px;margin-top:8px" data-st="${sh.subject_type}" data-sid="${sh.subject_id}">
      <div class="bar" title="Vida"><i style="width:${pct(r.vida,d.vida_max)}%"></i></div>
      <div class="res">${f("vida","Vida",d.vida_max)}${f("stamina","Stamina",d.stamina_max)}${f("sanidade","Sanidade",d.sanidade_max)}${f("pe","P.E.",d.pe_max)}${f("temp_hp","Vida temp.")}${f("exhaustion","Exaustão",3)}</div>
      <div class="row"><label class="help">Estado <select data-r="vital">${S.vitals.map(v=>`<option ${v===r.vital?"selected":""}>${v}</option>`).join("")}</select></label>
        <label class="help"><input type="checkbox" data-r="initialized" ${r.initialized?"checked":""}> recursos inicializados</label></div>
      <div class="conds">${conds}</div>
      <div class="row"><button class="gold sm" data-a="resources">Salvar recursos</button><span class="help">Medo/Pânico/Exaustão dão desvantagem em todo teste; Envenenado nos físicos; Confusão nos mentais.</span></div>
    </div></details>`;
}
function users(){
  $("#users").innerHTML=S.users.map(u=>{
    const st=[["completa","Completa"],["antiga","Antiga"],["aguardando","Aguardando aprovação"],["sem","Sem ficha"]];
    return `<div class="card" data-u="${u.id}"><div class="row" style="justify-content:space-between">
      <div><span class="name">${esc(u.name)}</span> <span class="tag">${esc(u.tag)}</span> ${u.staff?'<span class="tag gold">staff</span>':'<span class="tag">jogador</span>'}</div>
      <button class="sm" data-a="staff" data-u="${u.id}" data-v="${u.staff?0:1}">${u.staff?"tirar staff":"tornar staff"}</button></div>
      <div class="row"><div class="seg">${st.map(([v,l])=>`<button data-a="sheet_state" data-v="${v}" class="${u.sheet_state===v?"on":""}">${l}</button>`).join("")}</div>
      <button class="gold sm" data-a="approve" ${u.pending?"":"disabled"}>Aprovar construção agora</button></div>
      <div class="sheets">${u.sheets.map(s=>`<span class="tag ${s.active?"ok":""}">#${s.id} ${esc(s.name)}${s.active?" · ativa":""}${s.has_build?"":s.pending?" · enviada":" · sem construção"}</span>`).join("")}</div>
      <div class="help">Perfis vinculados: ${u.profiles.length?u.profiles.map(x=>{const p=S.profiles.find(p=>p.uuid===x);return esc(p&&p.name||x.slice(0,8))}).join(", "):"nenhum"}</div></div>`}).join("");
}
function npcs(){
  const opts=id=>`<option value="">— sem jogador —</option>`+S.users.map(u=>`<option value="${u.id}" ${u.id===id?"selected":""}>${esc(u.name)}</option>`).join("");
  $("#npcs").innerHTML=`<tr><th>NPC</th><th>Status</th><th>Construção</th><th>Ligado a</th></tr>`+S.npcs.map(n=>`<tr data-n="${n.id}">
    <td><b>${esc(n.name)}</b><div class="c-mute">${esc(n.title)} · #${n.id}</div></td><td>${n.status==="ativo"?'<span class="c-ok">ativo</span>':'<span class="c-mute">'+esc(n.status)+"</span>"}</td>
    <td>${n.has_build?"aprovada":'<span class="c-warn">sem</span>'}</td><td><select data-a="npc_link">${opts(n.linked)}</select></td></tr>`).join("");
}
function codeClass(st,c){if(st===0||st>=500)return"c-bad";if(st>=400)return"c-bad";if(["ok","linked","results"].includes(c))return"c-ok";
  if(["needs_binding","no_sheet","busy","no_build","not_linked","gm_required"].includes(c))return"c-warn";return"c-info"}
function calls(){
  $("#calls").innerHTML=`<tr><th>Hora</th><th>Rota</th><th>HTTP</th><th>Código</th><th>ms</th><th>Perfil</th></tr>`+
   (S.log.length?S.log.map(l=>`<tr><td class="c-mute">${hm(l.t)}</td><td class="mono">${esc(l.route)}</td><td>${l.status||"—"}</td>
     <td class="${codeClass(l.status,l.code)}">${esc(l.code||"")}${l.gm===true?' <span class="tag gold">mestre ✔</span>':l.gm===false?' <span class="tag">sem mestre</span>':""}${l.note?`<div class="c-mute">${esc(l.note)}</div>`:""}</td>
     <td>${l.ms}</td><td>${esc(l.who)}</td></tr>`).join(""):`<tr><td colspan="6" class="c-mute">nada ainda</td></tr>`);
}
function rolls(){
  if(!S.rolls.length){$("#rolls").innerHTML='<div class="empty">Nenhuma rolagem ainda. No jogo: abra a ficha, clique numa perícia e role.</div>';return}
  $("#rolls").innerHTML=S.rolls.map(r=>{
    const cls=r.kind!=="free"&&r.natural===20?"crit":r.kind!=="free"&&r.natural===1?"fumble":"";
    const out=r.success===true?'<span class="c-ok">sucesso</span>':r.success===false?'<span class="c-bad">falha</span>':'<span class="c-mute">sem CD</span>';
    const flags=[r.mode!=="normal"?`<span class="tag warn">${r.mode}</span>`:"",r.critical?'<span class="tag gold">crítico</span>':"",
      r.kind!=="free"&&r.natural===1?'<span class="tag bad">falha crítica</span>':"",r.secret?'<span class="tag vio">secreta</span>':"",
      r.private?'<span class="tag">NPC (bônus privado)</span>':"",r.replayed?'<span class="tag bad">replayed</span>':"",
      r.gm?(r.gm_verified?'<span class="tag gold">mestre ✔</span>':'<span class="tag bad">mestre não verificado</span>'):""].join(" ");
    return `<div class="roll"><div class="d20 ${cls}">${r.kind==="free"?r.total:r.natural}</div><div style="min-width:0">
      <div><b>${esc(r.character)}</b> <span class="c-mute">(${esc(r.profile)}) · ${hm(r.t)}</span></div>
      <div>${esc(r.label)} → <b class="c-info">${r.total}</b> <span class="c-mute mono">${esc(r.formula)}</span>${r.target?` · CD ${r.target} · `+out:""}</div>
      <div class="dice">${r.dice.map(d=>`<span class="${d.dropped?"drop":""}" title="d${d.sides}">${d.value}</span>`).join("")}</div><div>${flags}</div></div></div>`}).join("");
}
// ── eventos ──
document.addEventListener("input",e=>{const c=e.target.closest("[data-p],#latency,#fixed,#token,#linkmode,#linktarget,#autolink,#persist");
  if(c){dirty.add(c.id?"#"+c.id:"p");dirtyAt=Date.now()} if(e.target.dataset.c!==undefined)e.target.parentElement.classList.toggle("on",e.target.checked)});
document.addEventListener("click",e=>{
  const b=e.target.closest("button"); if(!b)return;
  if(b.dataset.down){act("settings",{down:b.dataset.down});return}
  if(b.dataset.lat!==undefined){act("settings",{latency_ms:+b.dataset.lat});return}
  if(b.id==="busy"){act("busy",{n:1});return}
  if(b.id==="busyclear"){act("busy_clear");return}
  if(b.id==="clearlog"){act("clear_log");return}
  if(b.id==="reset"){if(confirm("Voltar tudo ao estado inicial? Vínculos, fichas e log somem."))act("reset");return}
  if(b.id==="apply"){act("settings",{latency_ms:+$("#latency").value,link_mode:$("#linkmode").value,fixed_code:$("#fixed").value,
    link_target:$("#linktarget").value,auto_link:$("#autolink").checked,token:$("#token").value,persist:$("#persist").checked});return}
  const a=b.dataset.a; if(!a)return;
  const card=b.closest("[data-p]"), ucard=b.closest("[data-u]");
  if(a==="staff"){act("staff",{user_id:+b.dataset.u,staff:b.dataset.v==="1"});return}
  if(a==="sheet_state"){act("sheet_state",{user_id:+ucard.dataset.u,state:b.dataset.v});return}
  if(a==="approve"){act("approve",{user_id:+ucard.dataset.u});return}
  const prof=card&&card.dataset.p;
  if(a==="link"){act("link",{profile:prof,user_id:card.querySelector('[data-f=user]').value});return}
  if(a==="unlink"||a==="foreign"||a==="unbind"){act(a,{profile:prof});return}
  if(a==="bind"){const [t,i]=card.querySelector('[data-f=cand]').value.split(":");act("bind",{profile:prof,subject_type:t,subject_id:+i});return}
  if(a==="resources"){const box=b.closest("[data-st]");const d={subject_type:box.dataset.st,subject_id:+box.dataset.sid,conditions:[]};
    box.querySelectorAll("[data-r]").forEach(i=>{d[i.dataset.r]=i.type==="checkbox"?i.checked:i.value});
    box.querySelectorAll("[data-c]").forEach(i=>{if(i.checked)d.conditions.push(i.dataset.c)});act("resources",d);return}
});
document.addEventListener("change",e=>{const s=e.target; if(s.dataset.a==="npc_link"){act("npc_link",{npc_id:+s.closest("[data-n]").dataset.n,user_id:s.value||null})}});
poll(); setInterval(poll,1500);
</script>
</body>
</html>
"""


def main():
    global STATE_FILE, PERSIST_ENABLED
    ap = argparse.ArgumentParser(description="Mock local do site Aurorion (ficha no jogo, /vincular, diário, eventos).")
    ap.add_argument("--host", default="127.0.0.1")
    ap.add_argument("--porta", type=int, default=8787)
    ap.add_argument("--abrir", action="store_true", help="abre o painel no navegador")
    ap.add_argument("--sem-salvar", action="store_true", help="não lê nem grava mock_state.json")
    ap.add_argument("--estado", default=STATE_FILE, help="arquivo do estado salvo")
    args = ap.parse_args()
    STATE_FILE = os.path.abspath(args.estado)
    PERSIST_ENABLED = not args.sem_salvar
    if PERSIST_ENABLED and os.path.exists(STATE_FILE):
        try:
            with open(STATE_FILE, encoding="utf-8") as f:
                loaded = STATE.load(json.load(f))
            print(f"[mock] estado carregado de {STATE_FILE}" if loaded else "[mock] mock_state.json de versão antiga: ignorado")
        except (OSError, ValueError) as e:
            print(f"[mock] não consegui ler {STATE_FILE}: {e}")
    threading.Thread(target=saver_loop, daemon=True).start()
    try:
        httpd = ThreadingHTTPServer((args.host, args.porta), Handler)
    except OSError as e:
        print(f"[mock] não consegui abrir {args.host}:{args.porta} ({e}). Já tem um mock rodando?")
        sys.exit(1)
    httpd.daemon_threads = True
    url = f"http://{args.host}:{args.porta}/"
    print("=" * 72)
    print(" Aurorion · mock do site")
    print(f"  painel ............ {url}")
    print(f"  url da integração . {url.rstrip('/')}{PREFIX}")
    print("  token ............. qualquer valor não vazio")
    print(f"  código fixo ....... {STATE.settings['fixed_code']}  (por padrão vale qualquer código com 6+ caracteres)")
    print("  Ctrl+C para parar")
    print("=" * 72, flush=True)
    if args.abrir:
        threading.Timer(0.6, lambda: webbrowser.open(url)).start()
    try:
        httpd.serve_forever()
    except KeyboardInterrupt:
        print("\n[mock] parando…")
    finally:
        httpd.server_close()
        with LOCK:
            if PERSIST_ENABLED and STATE.settings.get("persist"):
                try:
                    with open(STATE_FILE, "w", encoding="utf-8") as f:
                        json.dump(STATE.dump(), f, ensure_ascii=False, indent=1)
                except OSError:
                    pass


if __name__ == "__main__":
    main()
