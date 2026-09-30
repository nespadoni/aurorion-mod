"""Gera o icone 16x16 do app "Servicos" do celular (aurorion-servicos).

Mesmo estilo dos icones do proprio telefone: quadrado colorido de cantos arredondados e o desenho em
branco — aqui uma maleta. Sem dependencia nenhuma (so zlib), para rodar em qualquer maquina:

    python tools/gerar-icone-servicos.py
"""
import struct
import zlib
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "aurorion-servicos/src/main/resources/assets/aurorion_servicos/textures/gui/phone/servicos.png"

LARANJA = (249, 115, 22, 255)
BRANCO = (255, 255, 255, 255)
VAZIO = (0, 0, 0, 0)

# '#' maleta, '-' faixa laranja cortando a maleta, 'o' fundo, ' ' canto transparente.
ART = [
    "  oooooooooooo  ",
    " oooooooooooooo ",
    "oooooooooooooooo",
    "oooooo####oooooo",
    "ooooo#oooo#ooooo",
    "oo############oo",
    "oo############oo",
    "oo############oo",
    "oo-----##-----oo",
    "oo############oo",
    "oo############oo",
    "oo############oo",
    "oo############oo",
    "oooooooooooooooo",
    " oooooooooooooo ",
    "  oooooooooooo  ",
]


def pixel(ch):
    return {"#": BRANCO, "-": LARANJA, "o": LARANJA}.get(ch, VAZIO)


def chunk(kind, data):
    body = kind + data
    return struct.pack(">I", len(data)) + body + struct.pack(">I", zlib.crc32(body) & 0xFFFFFFFF)


def main():
    assert len(ART) == 16 and all(len(row) == 16 for row in ART)
    raw = b"".join(b"\x00" + b"".join(bytes(pixel(ch)) for ch in row) for row in ART)
    png = b"\x89PNG\r\n\x1a\n"
    png += chunk(b"IHDR", struct.pack(">IIBBBBB", 16, 16, 8, 6, 0, 0, 0))
    png += chunk(b"IDAT", zlib.compress(raw, 9))
    png += chunk(b"IEND", b"")
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_bytes(png)
    print(OUT)


if __name__ == "__main__":
    main()
