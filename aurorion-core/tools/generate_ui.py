"""Gera a arte da interface estilo "menu de MMO" do menu do personagem (aurorion-menu e quem desenha com o MenuUi do core).

Tudo e desenho vetorial nosso, feito com o Pillow em 4x e reduzido com Lanczos: as bordas saem lisas, sem
o serrilhado do Minecraft. Cada PNG ganha um .mcmeta com "blur": true, para o jogo amostrar com filtro
linear. As formas sao brancas: a cor vem do codigo (tinta), entao o tema muda sem regerar nada.

    python aurorion-core/tools/generate_ui.py
"""
import json
import math
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "src/main/resources/assets/aurorion_core/textures/gui/menu"
SS = 4  # supersample
WHITE = (255, 255, 255, 255)


def save(img, name, size):
    img = img.resize(size, Image.LANCZOS)
    OUT.mkdir(parents=True, exist_ok=True)
    img.save(OUT / f"{name}.png")
    (OUT / f"{name}.png.mcmeta").write_text(json.dumps({"texture": {"blur": True, "clamp": True}}) + "\n", encoding="utf-8")


def canvas(w, h):
    return Image.new("RGBA", (w * SS, h * SS), (0, 0, 0, 0))


def s(v):
    return v * SS


# --- formas de base -----------------------------------------------------------------------------

def circle():
    img = canvas(128, 128)
    ImageDraw.Draw(img).ellipse((s(2), s(2), s(126), s(126)), fill=WHITE)
    save(img, "circle", (128, 128))


def ring():
    img = canvas(128, 128)
    ImageDraw.Draw(img).ellipse((s(4), s(4), s(124), s(124)), outline=WHITE, width=s(7))
    save(img, "ring", (128, 128))


def glow():
    """Halo suave: anéis concêntricos de alfa decrescente (a seleção "respira" com ele)."""
    img = canvas(128, 128)
    d = ImageDraw.Draw(img)
    for i in range(40):
        a = int(150 * (1 - i / 40) ** 2)
        r = 34 + i * .7
        d.ellipse((s(64 - r), s(64 - r), s(64 + r), s(64 + r)), outline=(255, 255, 255, a), width=s(1))
    save(img, "glow", (128, 128))


def panel():
    """Cartão arredondado para 9-slice: cantos de 24 px em 64x64."""
    img = canvas(64, 64)
    ImageDraw.Draw(img).rounded_rectangle((0, 0, s(64) - 1, s(64) - 1), radius=s(24), fill=WHITE)
    save(img, "panel", (64, 64))


def panel_line():
    """Moldura fina do cartão, para a borda colorida."""
    img = canvas(64, 64)
    ImageDraw.Draw(img).rounded_rectangle((s(1), s(1), s(63), s(63)), radius=s(23), outline=WHITE, width=s(2))
    save(img, "panel_line", (64, 64))


def pointer():
    """O triângulo que liga a coluna de círculos à lista (aponta para a direita)."""
    img = canvas(32, 32)
    ImageDraw.Draw(img).polygon([(s(6), s(4)), (s(28), s(16)), (s(6), s(28))], fill=WHITE)
    save(img, "pointer", (32, 32))


def hp_bar():
    """A barra de vida: um paralelogramo com a ponta cortada (frente reta, fundo inclinado)."""
    w, h = 512, 48
    shape = [(s(0), s(8)), (s(w - 40), s(8)), (s(w), s(h - 8)), (s(24), s(h - 8))]
    img = canvas(w, h)
    ImageDraw.Draw(img).polygon(shape, fill=WHITE)
    save(img, "hp_fill", (w, h))
    img = canvas(w, h)
    d = ImageDraw.Draw(img)
    d.polygon(shape, outline=WHITE, width=s(3))
    save(img, "hp_frame", (w, h))


def holo():
    """O menu visto de fora: um painel com colunas e cartões reconhecíveis, mas borrado de propósito —
    quem está perto vê que a pessoa mexe num menu, não o que ela lê. Já colorido (violeta e marfim)."""
    w, h = 256, 168
    img = Image.new("RGBA", (w * SS, h * SS), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.rounded_rectangle((s(6), s(6), s(w - 6), s(h - 6)), radius=s(18), fill=(42, 22, 80, 150), outline=(197, 154, 255, 230), width=s(3))
    for i in range(4):
        cy = 34 + i * 34
        d.ellipse((s(150), s(cy - 11), s(172), s(cy + 11)), fill=(243, 234, 216, 235) if i else (255, 215, 106, 245))
    for i in range(3):
        y = 40 + i * 26
        d.rounded_rectangle((s(184), s(y), s(244), s(y + 16)), radius=s(7), fill=(243, 234, 216, 205))
    d.rounded_rectangle((s(18), s(20), s(130), s(150)), radius=s(12), fill=(243, 234, 216, 190))
    for i in range(5):
        d.rounded_rectangle((s(30), s(98 + i * 10), s(118 - i * 9), s(103 + i * 10)), radius=s(2), fill=(58, 31, 104, 200))
    d.ellipse((s(52), s(32), s(96), s(84)), fill=(58, 31, 104, 170))
    img = img.resize((w, h), Image.LANCZOS).filter(ImageFilter.GaussianBlur(3.2))
    OUT.mkdir(parents=True, exist_ok=True)
    img.save(OUT / "holo.png")
    (OUT / "holo.png.mcmeta").write_text(json.dumps({"texture": {"blur": True, "clamp": True}}) + "\n", encoding="utf-8")


# --- ícones (glifo branco em fundo transparente, 128 px) -----------------------------------------

def icon(name, draw):
    img = canvas(128, 128)
    draw(ImageDraw.Draw(img))
    save(img, f"icon_{name}", (128, 128))


def rot(points, angle, cx=64, cy=64):
    a = math.radians(angle)
    out = []
    for x, y in points:
        dx, dy = x - cx, y - cy
        out.append((s(cx + dx * math.cos(a) - dy * math.sin(a)), s(cy + dx * math.sin(a) + dy * math.cos(a))))
    return out


def sword_shape(d, angle):
    blade = [(60, 14), (68, 14), (68, 80), (64, 88), (60, 80)]
    guard = [(46, 80), (82, 80), (82, 88), (46, 88)]
    grip = [(60, 88), (68, 88), (68, 106), (60, 106)]
    pommel = [(57, 106), (71, 106), (71, 114), (57, 114)]
    for part in (blade, guard, grip, pommel):
        d.polygon(rot(part, angle), fill=WHITE)


def i_sword(d):
    sword_shape(d, 45)


def i_duel(d):
    sword_shape(d, 40)
    sword_shape(d, -40)


def i_bow(d):
    d.arc((s(30), s(18), s(86), s(110)), start=-80, end=80, fill=WHITE, width=s(8))
    d.line((s(64), s(20), s(64), s(108)), fill=WHITE, width=s(3))
    d.line((s(36), s(64), s(102), s(64)), fill=WHITE, width=s(5))
    d.polygon([(s(104), s(64)), (s(92), s(56)), (s(92), s(72))], fill=WHITE)


def i_shield(d):
    outer = [(64, 14), (102, 26), (98, 70), (64, 114), (30, 70), (26, 26)]
    d.polygon([(s(x), s(y)) for x, y in outer], fill=WHITE)
    inner = [(64, 28), (88, 36), (86, 66), (64, 96)]
    d.polygon([(s(x), s(y)) for x, y in inner], fill=(0, 0, 0, 0))


def star_points(cx, cy, outer, inner, n=5, phase=-90):
    pts = []
    for i in range(n * 2):
        r = outer if i % 2 == 0 else inner
        a = math.radians(phase + i * 180 / n)
        pts.append((s(cx + r * math.cos(a)), s(cy + r * math.sin(a))))
    return pts


def i_star(d):
    d.polygon(star_points(64, 68, 50, 21), fill=WHITE)


def i_magic(d):
    d.polygon(star_points(58, 66, 44, 11, n=4, phase=-90), fill=WHITE)
    d.polygon(star_points(96, 30, 16, 5, n=4, phase=-90), fill=WHITE)
    d.ellipse((s(92), s(88), s(104), s(100)), fill=WHITE)


def i_flag(d):
    d.rounded_rectangle((s(30), s(16), s(38), s(114)), radius=s(4), fill=WHITE)
    d.polygon([(s(38), s(20)), (s(100), s(30)), (s(84), s(48)), (s(100), s(66)), (s(38), s(62))], fill=WHITE)


def i_person(d):
    d.ellipse((s(44), s(16), s(84), s(56)), fill=WHITE)
    d.rounded_rectangle((s(24), s(64), s(104), s(118)), radius=s(30), fill=WHITE)


def i_people(d):
    d.ellipse((s(18), s(26), s(52), s(60)), fill=WHITE)
    d.rounded_rectangle((s(4), s(66), s(66), s(110)), radius=s(22), fill=WHITE)
    d.ellipse((s(72), s(18), s(110), s(56)), fill=WHITE)
    d.rounded_rectangle((s(58), s(62), s(124), s(112)), radius=s(24), fill=WHITE)


def i_yes(d):
    d.ellipse((s(26), s(26), s(102), s(102)), outline=WHITE, width=s(12))


def i_no(d):
    d.line((s(32), s(32), s(96), s(96)), fill=WHITE, width=s(13))
    d.line((s(96), s(32), s(32), s(96)), fill=WHITE, width=s(13))
    for x, y in ((32, 32), (96, 96), (96, 32), (32, 96)):
        d.ellipse((s(x - 6), s(y - 6), s(x + 6), s(y + 6)), fill=WHITE)


def i_back(d):
    d.line((s(80), s(26), s(42), s(64)), fill=WHITE, width=s(13))
    d.line((s(42), s(64), s(80), s(102)), fill=WHITE, width=s(13))
    for x, y in ((80, 26), (42, 64), (80, 102)):
        d.ellipse((s(x - 6), s(y - 6), s(x + 6), s(y + 6)), fill=WHITE)


def i_target(d):
    d.ellipse((s(18), s(18), s(110), s(110)), outline=WHITE, width=s(8))
    d.ellipse((s(44), s(44), s(84), s(84)), outline=WHITE, width=s(8))
    d.ellipse((s(58), s(58), s(70), s(70)), fill=WHITE)


def i_heart(d):
    d.ellipse((s(18), s(26), s(66), s(74)), fill=WHITE)
    d.ellipse((s(62), s(26), s(110), s(74)), fill=WHITE)
    d.polygon([(s(20), s(58)), (s(108), s(58)), (s(64), s(112))], fill=WHITE)


def i_close(d):
    i_no(d)


if __name__ == "__main__":
    circle(); ring(); glow(); panel(); panel_line(); pointer(); hp_bar(); holo()
    for name, fn in [("sword", i_sword), ("duel", i_duel), ("bow", i_bow), ("shield", i_shield), ("star", i_star),
                     ("magic", i_magic), ("flag", i_flag), ("person", i_person), ("people", i_people), ("yes", i_yes),
                     ("no", i_no), ("back", i_back), ("target", i_target), ("heart", i_heart)]:
        icon(name, fn)
    print("ok:", len(list(OUT.glob("*.png"))), "texturas em", OUT)
