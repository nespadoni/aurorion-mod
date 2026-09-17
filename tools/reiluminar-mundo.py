#!/usr/bin/env python3
"""
Marca todas as chunks de um mundo como "precisa reiluminar", para sair do ScalableLux/Starlight
sem deixar o mundo preto.

POR QUE ISTO EXISTE
-------------------
Ao salvar uma chunk, o Minecraft pega os arrays de luz do motor *do vanilla* e, se a chunk estiver
marcada como iluminada, grava `isLightOn = true` (ChunkSerializer, 1.21.1). Com o ScalableLux
instalado o motor do vanilla fica vazio -- a luz mora na estrutura do ScalableLux -- entao as chunks
vao para o disco com `isLightOn = true` e SEM os arrays `BlockLight`/`SkyLight`.

Ao carregar, o vanilla faz `setLightCorrect(isLightOn)`: ve "ja esta iluminada", confia, e nao
recalcula nada. Sem `SkyLight`, tudo le nivel 0 -- mundo preto, sol que nao bate no chao.

Este script zera o `isLightOn` de todas as chunks. Na proxima vez que o servidor carregar cada uma,
o vanilla reilumina do zero. Nada e perdido: luz e dado derivado dos blocos.

COMO USAR
---------
    python tools/reiluminar-mundo.py "/caminho/do/world"            # so relatorio, nao grava
    python tools/reiluminar-mundo.py "/caminho/do/world" --aplicar  # grava

O padrao e simulacao. Sem `--aplicar` nenhum byte e escrito.

ORDEM OBRIGATORIA (servidor parado nos dois passos):
    1. Backup completo do mundo. Sem backup, nao rode.
    2. Tire o jar do ScalableLux.
    3. Rode este script com --aplicar.
    4. Suba o servidor. Opcional: pre-aqueca com Chunky antes de abrir para os jogadores, para
       que o custo da reiluminacao nao caia no colo de quem entrar primeiro.

Tirar o jar sem o passo 3 e exatamente o que deixa o mundo preto.

COMO ELE MEXE NO ARQUIVO
------------------------
Nao reserializa NBT. Procura a sequencia exata de um TAG_Byte chamado "isLightOn" e zera o byte de
valor -- um byte por chunk, tamanho descomprimido identico. O arquivo .mca e reescrito inteiro com
os offsets recalculados, o que tambem desfragmenta a regiao. Timestamps sao preservados.
"""

import argparse
import gzip
import pathlib
import struct
import sys
import zlib

SETOR = 4096

# TAG_Byte (0x01) + nome de 9 caracteres + "isLightOn". O byte seguinte e o valor.
MARCA = b"\x01\x00\x09isLightOn"

GZIP, ZLIB, CRU, LZ4 = 1, 2, 3, 4
EXTERNO = 0x80


class ChunkIlegivel(Exception):
    pass


def descomprimir(tipo: int, dados: bytes) -> bytes:
    if tipo == ZLIB:
        return zlib.decompress(dados)
    if tipo == GZIP:
        return gzip.decompress(dados)
    if tipo == CRU:
        return dados
    if tipo == LZ4:
        raise ChunkIlegivel("compressao LZ4 (a stdlib do Python nao le)")
    raise ChunkIlegivel(f"compressao desconhecida: {tipo}")


def comprimir(tipo: int, dados: bytes) -> bytes:
    if tipo == ZLIB:
        return zlib.compress(dados, 6)
    if tipo == GZIP:
        return gzip.compress(dados)
    if tipo == CRU:
        return dados
    raise ChunkIlegivel(f"compressao nao regravavel: {tipo}")


def apagar_marca(nbt: bytes) -> tuple[bytes, int]:
    """Zera o valor de todo TAG_Byte "isLightOn". Devolve (bytes novos, quantas trocas)."""
    trocas = 0
    saida = bytearray(nbt)
    pos = saida.find(MARCA)
    while pos != -1:
        valor = pos + len(MARCA)
        if valor < len(saida) and saida[valor] != 0:
            saida[valor] = 0
            trocas += 1
        pos = saida.find(MARCA, valor)
    return bytes(saida), trocas


def processar_mcc(caminho: pathlib.Path, aplicar: bool) -> int:
    """Chunk grande demais para a regiao mora num .mcc ao lado, sempre em zlib."""
    bruto = caminho.read_bytes()
    try:
        nbt = zlib.decompress(bruto)
    except zlib.error as erro:
        raise ChunkIlegivel(f"{caminho.name}: {erro}") from erro
    nbt, trocas = apagar_marca(nbt)
    if trocas and aplicar:
        caminho.write_bytes(zlib.compress(nbt, 6))
    return trocas


def processar_regiao(caminho: pathlib.Path, aplicar: bool) -> tuple[int, int, list[str]]:
    """Devolve (chunks vistas, chunks alteradas, avisos)."""
    bruto = caminho.read_bytes()
    if not bruto:
        # Regiao alocada e nunca preenchida. Normal, nao e defeito.
        return 0, 0, []
    if len(bruto) < SETOR * 2:
        return 0, 0, [f"{caminho.name}: cabecalho incompleto ({len(bruto)} bytes), pulado"]

    locais = bruto[:SETOR]
    tempos = bruto[SETOR:SETOR * 2]

    vistas = alteradas = 0
    avisos: list[str] = []
    # (indice, tipo de compressao, nbt ja corrigido) -- ou None para slot vazio.
    corpo: list[tuple[int, int, bytes] | None] = [None] * 1024

    for i in range(1024):
        b0, b1, b2, setores = locais[i * 4:i * 4 + 4]
        offset = (b0 << 16) | (b1 << 8) | b2
        if offset == 0 or setores == 0:
            continue

        inicio = offset * SETOR
        if inicio + 5 > len(bruto):
            avisos.append(f"{caminho.name}: chunk {i} aponta para fora do arquivo, mantida como esta")
            continue

        tamanho = struct.unpack(">I", bruto[inicio:inicio + 4])[0]
        tipo = bruto[inicio + 4]
        vistas += 1

        if tipo & EXTERNO:
            # O conteudo esta num .mcc; a entrada da regiao fica intacta.
            corpo[i] = (i, tipo, bruto[inicio + 5:inicio + 4 + max(tamanho, 1)])
            continue

        dados = bruto[inicio + 5:inicio + 4 + tamanho]
        try:
            nbt = descomprimir(tipo, dados)
        except (ChunkIlegivel, zlib.error, OSError) as erro:
            avisos.append(f"{caminho.name}: chunk {i} ilegivel ({erro}), mantida como esta")
            corpo[i] = (i, tipo, dados)
            continue

        nbt, trocas = apagar_marca(nbt)
        if trocas:
            alteradas += 1
            try:
                dados = comprimir(tipo, nbt)
            except ChunkIlegivel as erro:
                avisos.append(f"{caminho.name}: chunk {i} nao regravavel ({erro}), mantida como esta")
                alteradas -= 1
        corpo[i] = (i, tipo, dados)

    if not aplicar or alteradas == 0:
        return vistas, alteradas, avisos

    # Reescreve o .mca inteiro com offsets recalculados, empacotando sequencialmente.
    novos_locais = bytearray(SETOR)
    saida = bytearray()
    setor_atual = 2
    for i in range(1024):
        entrada = corpo[i]
        if entrada is None:
            continue
        _, tipo, dados = entrada
        bloco = struct.pack(">I", len(dados) + 1) + bytes([tipo]) + dados
        sobra = (-len(bloco)) % SETOR
        bloco += b"\x00" * sobra
        usados = len(bloco) // SETOR
        if usados > 255:
            raise ChunkIlegivel(f"{caminho.name}: chunk {i} ocuparia {usados} setores (maximo 255)")
        novos_locais[i * 4] = (setor_atual >> 16) & 0xFF
        novos_locais[i * 4 + 1] = (setor_atual >> 8) & 0xFF
        novos_locais[i * 4 + 2] = setor_atual & 0xFF
        novos_locais[i * 4 + 3] = usados
        saida += bloco
        setor_atual += usados

    temporario = caminho.with_suffix(caminho.suffix + ".novo")
    temporario.write_bytes(bytes(novos_locais) + tempos + bytes(saida))
    temporario.replace(caminho)
    return vistas, alteradas, avisos


def main() -> int:
    ap = argparse.ArgumentParser(
        description="Zera isLightOn para o vanilla reiluminar o mundo ao sair do ScalableLux.")
    ap.add_argument("mundo", help="pasta do mundo (a que contem level.dat)")
    ap.add_argument("--aplicar", action="store_true",
                    help="grava as alteracoes; sem isto o script so relata")
    args = ap.parse_args()

    raiz = pathlib.Path(args.mundo)
    if not raiz.is_dir():
        print(f"erro: {raiz} nao e uma pasta", file=sys.stderr)
        return 2
    if not (raiz / "level.dat").exists():
        print(f"erro: {raiz} nao tem level.dat -- aponte para a pasta do mundo", file=sys.stderr)
        return 2

    # Pega overworld, DIM-1, DIM1 e qualquer dimensao de mod, sem tocar em entities/ nem poi/.
    regioes = sorted(p for p in raiz.rglob("*.mca") if p.parent.name == "region")
    if not regioes:
        print("erro: nenhum arquivo region/*.mca encontrado", file=sys.stderr)
        return 2

    if not args.aplicar:
        print(">>> SIMULACAO -- nenhum byte sera escrito. Use --aplicar para valer.\n")
    else:
        print(">>> GRAVANDO. Espero que voce tenha o backup completo do mundo.\n")

    total_vistas = total_alteradas = 0
    todos_avisos: list[str] = []
    por_dimensao: dict[str, tuple[int, int]] = {}

    for regiao in regioes:
        relativo = regiao.parent.parent.relative_to(raiz)
        dimensao = "overworld" if relativo == pathlib.Path(".") else str(relativo)
        try:
            vistas, alteradas, avisos = processar_regiao(regiao, args.aplicar)
        except (ChunkIlegivel, OSError) as erro:
            todos_avisos.append(f"{regiao.name}: {erro}")
            continue
        total_vistas += vistas
        total_alteradas += alteradas
        todos_avisos += avisos
        v, a = por_dimensao.get(dimensao, (0, 0))
        por_dimensao[dimensao] = (v + vistas, a + alteradas)

    for mcc in sorted(p for p in raiz.rglob("*.mcc") if p.parent.name == "region"):
        try:
            if processar_mcc(mcc, args.aplicar):
                total_alteradas += 1
        except (ChunkIlegivel, OSError) as erro:
            todos_avisos.append(str(erro))

    for dimensao, (vistas, alteradas) in sorted(por_dimensao.items()):
        print(f"  {dimensao:<40} {vistas:>8} chunks   {alteradas:>8} a reiluminar")

    print(f"\n  {'TOTAL':<40} {total_vistas:>8} chunks   {total_alteradas:>8} a reiluminar")

    if todos_avisos:
        print(f"\n  avisos ({len(todos_avisos)}):")
        for aviso in todos_avisos[:20]:
            print(f"    - {aviso}")
        if len(todos_avisos) > 20:
            print(f"    ... e mais {len(todos_avisos) - 20}")

    if not args.aplicar and total_alteradas:
        print("\n  Nada foi gravado. Confira os numeros acima e rode de novo com --aplicar.")

    return 0


if __name__ == "__main__":
    sys.exit(main())
