"""Generate Pufferfish v3 data and the server catalog from the reviewed PDF catalog.

Run with Python 3; no Minecraft, Gradle, external libraries or original PDF needed.
Published IDs are stable. VX/AX distinguish travel from Tempo/Transmutacao.
"""
import itertools
import json
import math
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SOURCE = Path(__file__).with_name("catalog.json")
DATA = ROOT / "src/main/resources/data/aurorion_trama"
HOUSES = {"I": ("ignivar", "#ed7444", "minecraft:blaze_powder"),
          "S": ("sylvara", "#67bc86", "minecraft:oak_sapling"),
          "N": ("nyx", "#8a9cea", "minecraft:amethyst_shard"),
          "A": ("aetheris", "#ca82d6", "minecraft:ender_pearl"),
          "V": ("venthra", "#eed173", "minecraft:feather")}
# Ring order follows bridges, not the alphabetical order of Houses.
ORDER = "ISNAV"
CLUSTERS = {"I": "BEDO", "S": "RAGB", "N": "WCOV", "A": "TVLD", "V": "FTPA"}
TRAVEL = {c: c + ("X" if c in "AV" else "T") for c in ORDER}
BRIDGES = {"BIS": ("IT2", "ST1"), "BSN": ("ST4", "NT3"),
           "BNA": ("NT4", "AX1"), "BAV": ("AX3", "VX1"),
           "BVI": ("VX2", "IT5")}
PRINCIPLES = [["IE9", "IO9"], ["SA9", "SG9"], ["NC9", "NV9"],
              ["VP9", "VA9"], ["AT9", "AV9"], [f"C{i}" for i in range(16, 21)]]


def write(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def radial(angle, radius, tangent=0):
    return round(math.cos(angle) * radius - math.sin(angle) * tangent), round(math.sin(angle) * radius + math.cos(angle) * tangent)


def generate():
    nodes = json.loads(SOURCE.read_text(encoding="utf-8"))
    by_id = {n["id"]: n for n in nodes}
    assert len(nodes) == len(by_id) == 271
    for n in nodes:
        # The original extraction omitted the five central Fios. NO4 is conditional
        # on the correct tool and is handled by BreakSpeed, not a static attribute.
        if n["implementation"] == "A" and n["id"] != "NO4" and not n.get("ratings"):
            n["ratings"] = {key: int(value) for key, value in
                            re.findall(r"([A-Z]+)\s+([+-]\d+)", n["description"])}
            assert n["ratings"], n["id"]
    definitions, skills = {}, {}
    normal, directed = [], []
    angles = {c: -math.pi * .9 - ORDER.index(c) * 2 * math.pi / 5 for c in ORDER}
    positions = {"C00": (0, 0)}
    for c in ORDER:
        a = angles[c]
        positions[c + "00"] = radial(a, 800)
        for i in range(1, 6):
            positions[TRAVEL[c] + str(i)] = radial(a, 760 - i * 62)
        for k, cluster in enumerate(CLUSTERS[c]):
            ca = a + (k - 1.5) * .235
            # Recognizable split branches, convergence, and final specialization.
            pattern = [(0, 0), (40, -42), (82, -50), (120, -36),
                       (40, 42), (82, 50), (120, 36), (154, 0), (196, 0)]
            for i, (r, t) in enumerate(pattern, 1):
                positions[c + cluster + str(i)] = radial(ca, 820 + r, t)
    for prefix, (left, right) in BRIDGES.items():
        a, b = positions[left], positions[right]
        for i in range(1, 9):
            t = i / 9
            positions[prefix + str(i)] = (round(a[0] * (1 - t) + b[0] * t), round(a[1] * (1 - t) + b[1] * t))
    # Five central spokes. Each House has one late entrance; the Nexo connects them.
    for k, c in enumerate(ORDER, 1):
        for i, radius in [(k, 305), (k + 5, 240), (k + 10, 170)]:
            positions[f"C{i:02}"] = radial(angles[c], radius)
    central_parents = {"C16": "C11", "C17": "C13", "C18": "C14", "C19": "C15", "C20": "C12"}
    for ident, parent in central_parents.items():
        x, y = positions[parent]
        positions[ident] = (round(x * .53), round(y * .53))

    for n in nodes:
        ident, kind = n["id"], n["kind"]
        c = ident[1] if ident.startswith("B") else ident[0]
        color, item = HOUSES[c][1:] if c != "C" else ("#dfd5b0", "minecraft:echo_shard")
        origin = kind == "Início"
        requirement = n["required"]
        if ident == "C00":
            requirement = 24  # prevents a free early crossing of all five central spokes
        desc = n["description"]
        if origin:
            desc = "Concedida pelo servidor conforme sua Casa. As outras Casas continuam acessíveis pelas pontes."
        definitions[ident] = {
            "title": {"text": n["name"], "color": color},
            "description": {"text": desc},
            "extra_description": {"text": f"{ident} · {kind} · Formação: {requirement} pontos gastos. Bônus sujeitos aos limites da Trama."},
            "icon": {"type": "item", "data": {"item": item}},
            "frame": {"type": "advancement", "data": {"frame": "challenge" if kind == "Princípio" else "goal" if kind in ("Maestria", "Início", "Nexo") else "task"}},
            "size": 1.4 if kind in ("Princípio", "Início", "Nexo") else 1.15 if kind in ("Maestria", "Notável") else .8,
            "cost": 0 if origin or ident == "C00" else 1,
            "required_spent_points": requirement,
        }
        x, y = positions[ident]
        # No clickable roots: the house service force-unlocks exactly one origin.
        skills[ident] = {"x": x, "y": y, "definition": ident, "root": False}
        if not ident.startswith("B"):
            for parent in n["parents"]:
                assert parent in by_id and parent != ident, (ident, parent)
                # Do not let another house's travel path unlock its origin in reverse.
                (directed if parent.endswith("00") else normal).append([parent, ident])
    for prefix, (left, right) in BRIDGES.items():
        normal.append([left, prefix + "1"])
        normal.extend([[prefix + str(i), prefix + str(i + 1)] for i in range(1, 8)])
        normal.append([prefix + "8", right])
    for k, c in enumerate(ORDER, 1):
        normal.extend([[TRAVEL[c] + "5", f"C{k:02}"], [f"C{k:02}", "C00"]])
    exclusive = [list(pair) for group in PRINCIPLES for pair in itertools.combinations(group, 2)]
    connections = {"normal": {"bidirectional": normal, "unidirectional": directed},
                   "exclusive": {"bidirectional": exclusive}}
    origins = {c + "00" for c in ORDER}
    assert all(a not in origins and b not in origins for a, b in normal)
    assert all(b not in origins for a, b in directed)
    assert len(set(positions.values())) == len(nodes), "Overlapping node coordinates"
    for prefix in BRIDGES:
        bridge = {prefix + str(i) for i in range(1, 9)}
        edges = [(a, b) for a, b in normal if a in bridge or b in bridge]
        assert len(edges) == 9 and sum((a in bridge) != (b in bridge) for a, b in edges) == 2
    for a, b in normal + directed + exclusive:
        assert a in by_id and b in by_id and a != b, (a, b)
    category = {"title": "Trama do Eco", "description": "Sua Casa é o começo. O caminho pertence a você.",
                "extra_description": "Até 45 pontos por personagem. Descobertas e prática ativa: /trama progresso. /trama respec fora de combate.",
                "icon": {"type": "item", "data": {"item": "minecraft:echo_shard"}},
                "background": "minecraft:textures/block/black_concrete.png",
                "unlocked_by_default": False, "starting_points": 0,
                "spent_points_limit": 45, "exclusive_root": False, "erase_on_death": False}
    write(DATA / "puffish_skills/config.json", {"version": 3, "categories": ["trama_do_eco"]})
    for name, data in [("category", category), ("skills", skills), ("definitions", definitions), ("connections", connections)]:
        write(DATA / f"puffish_skills/categories/trama_do_eco/{name}.json", data)
    write(DATA / "aurorion/trama/catalog.json", {"nodes": nodes})
    print(f"Generated {len(skills)} nodes, {len(normal) + len(directed)} normal and {len(exclusive)} exclusive connections.")


if __name__ == "__main__":
    generate()
