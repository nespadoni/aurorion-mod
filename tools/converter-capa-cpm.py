#!/usr/bin/env python3
"""
Converte o export Blockbench de um projeto do Customizable Player Models para os assets da capa
do uniforme (aurorion-aeonita).

POR QUE ISTO EXISTE
-------------------
A capa e modelada no CPM, nao no Blockbench. O CPM exporta para o formato Bedrock, mas o que sai
de la nao carrega no GeckoLib como esta -- sao quatro diferencas, e nenhuma delas quebra o build
nem levanta excecao no jogo. A capa so aparece torta, ou parada, ou invisivel, e so no cliente,
em cima de um jogador:

1. NOMES DOS BONES. O GeoArmorRenderer procura oito nomes fixos (armorHead, armorBody,
   armorLeftArm, ...) para copiar neles a pose do modelo humanoide vanilla. O CPM exporta os
   nomes dele (head, body, left_arm, ...). Quando um nome nao bate, getHeadBone() devolve null
   sem reclamar e o render segue com ele -- o erro vira NullPointerException no meio de um frame.
   As botas nao existem nesta peca, mas precisam existir no arquivo pelo mesmo motivo.

2. ESCALA DA UV. O CPM exporta a UV num espaco 16x maior que a textura (4480x4096 para uma
   textura de 280x256). Sem dividir, toda face amostra fora da imagem.

3. CUBOS MARCADORES. Cada 'group' do CPM vira um cubo que nao desenha nada, e a forma dele muda
   de export para export: ja saiu como [0, 0, 0], como a linha [1.5, 0, 0] e como a placa
   [1.5, 0, 0.5]. Pelo tamanho nao da para separar marcador de geometria -- a capa usa placas
   achatadas de verdade (a gola, a perna esquerda). O que separa e a UV: o marcador tem area zero
   em TODA face, entao nao pinta pixel nenhum, enquanto uma placa de verdade tem area. Cada
   marcador que passa custa seis quads por frame (SDD secao 2).

4. NOMES DAS ANIMACOES. O UniformCapeItem pede as animacoes por string, e nome que nao existe faz
   o GeckoLib simplesmente nao animar, em silencio. O nome que o CPM guarda sobrevive ao import do
   Blockbench, entao uma animacao batizada la ('walking', 'jumping') chega aqui inteira -- e esse
   e o caminho bom. As de estado que ficam SEM nome no CPM (v_global, v_walking, v_running) saem
   numeradas na ordem do projeto, ordem que MUDA a cada reexport: num export '2' era running, no
   seguinte virou idle, e as capas passaram a andar com a animacao de correr. Por isso o nome vem
   primeiro e a duracao -- que vem do tipo no CPM e nao muda -- so entra como rede de seguranca.

   Batize as animacoes no CPM. E a unica coisa que torna o export nao-ambiguo.

COMO USAR
---------
    python tools/converter-capa-cpm.py <modelo.geo.json> <animacoes.animation.json>
    python tools/converter-capa-cpm.py <animacoes.animation.json>   # so as animacoes

Sobrescreve uniform_cape.geo.json e uniform_cape.animation.json em aurorion-aeonita. Depois rode
`./gradlew :aurorion-aeonita:test` -- o UniformCapeAssetsTest assa o modelo com o proprio Gson do
GeckoLib e confere bone a bone, que e a unica validacao do repo que chega perto de assets/.
"""
import collections
import json
import os
import re
import sys

RAIZ = os.path.join(os.path.dirname(os.path.abspath(__file__)), os.pardir)
ASSETS = os.path.join(RAIZ, "aurorion-aeonita", "src", "main", "resources",
                      "assets", "aurorion_aeonita")
DST_GEO = os.path.join(ASSETS, "geo", "armor", "uniform_cape.geo.json")
DST_ANIM = os.path.join(ASSETS, "animations", "armor", "uniform_cape.animation.json")

UV_SCALE = 16
TEX_W, TEX_H = 280, 256

# O CPM exporta o grupo do torso ja chamado "armorBody"; o nome tem de sobrar para o bone raiz do
# corpo, entao o de dentro vira "torso" ANTES do rename de "body".
RENAMES = [
    ("armorBody", "torso"),
    ("head", "armorHead"),
    ("body", "armorBody"),
    ("left_arm", "armorLeftArm"),
    ("right_arm", "armorRightArm"),
    ("left_leg", "armorLeftLeg"),
    ("right_leg", "armorRightLeg"),
]

BOTAS = [
    {"name": "armorRightBoot", "pivot": [-1.9, 12, 0]},
    {"name": "armorLeftBoot", "pivot": [1.9, 12, 0]},
]

# Nomes que o CPM guarda e que sobrevivem ao import do Blockbench. Os de gesto sempre tem nome;
# os de estado so tem se voce os batizou no CPM, e ai o nome manda -- e o mais confiavel que existe.
POR_NOME = {
    "jumping": "jumping",
    "p:jumping": "jump_pre",
    "p:jumping2": "jump_post",
    "idle": "idle",
    "walking": "walking",
    "running": "running",
}

# Rede de seguranca para os estados que sairam sem nome: o Blockbench os numera na ordem do
# projeto, que muda a cada reexport, mas a duracao vem do tipo no CPM e nao muda.
POR_DURACAO = {2.0: "idle", 1.5: "walking", 1.2: "running"}

# 'jumping Base' e o gatilho do CPM, nao uma animacao: uma keyframe so, e o Blockbench as vezes o
# exporta sem bone nenhum. Nao tem o que tocar.
DESCARTAR = re.compile(r"^jumping Base\d*$")

ORDEM = ["running", "idle", "jumping", "walking", "jump_pre", "jump_post"]


def num(valor):
    """Mantem inteiro como inteiro, para o JSON nao encher de '.0'."""
    return int(valor) if isinstance(valor, float) and valor.is_integer() else valor


def ler(caminho):
    with open(caminho, encoding="utf-8") as arquivo:
        return json.load(arquivo, object_pairs_hook=collections.OrderedDict)


def gravar(caminho, doc):
    with open(caminho, "w", encoding="utf-8", newline="\n") as arquivo:
        json.dump(doc, arquivo, indent=2)
        arquivo.write("\n")


def escalar_uv(uv):
    if isinstance(uv, list):
        return [num(c / UV_SCALE) for c in uv]
    saida = collections.OrderedDict()
    for face, dados in uv.items():
        convertida = collections.OrderedDict()
        for chave, valor in dados.items():
            convertida[chave] = ([num(c / UV_SCALE) for c in valor]
                                 if chave in ("uv", "uv_size") else valor)
        saida[face] = convertida
    return saida


def desenha_algo(cubo):
    """Falso para os cubos marcadores do CPM: sem volume, ou sem area de UV em face nenhuma."""
    if sum(1 for d in cubo["size"] if d) < 2:      # ponto ou linha: nao tem face com area
        return False
    uv = cubo.get("uv")
    if not isinstance(uv, dict):                   # UV de caixa: nao da para medir por face
        return True
    return any(face["uv_size"][0] and face["uv_size"][1] for face in uv.values())


def converter_geo(origem):
    doc = ler(origem)
    geo = doc["minecraft:geometry"][0]
    geo["description"]["identifier"] = "geometry.uniform_cape"
    geo["description"]["texture_width"] = TEX_W
    geo["description"]["texture_height"] = TEX_H

    nomes = {bone["name"] for bone in geo["bones"]}
    faltando = [antigo for antigo, _ in RENAMES if antigo not in nomes]
    if faltando:
        sys.exit("bone esperado nao existe no export: " + ", ".join(faltando))
    mapa = dict(RENAMES)

    descartados = 0
    for bone in geo["bones"]:
        bone["name"] = mapa.get(bone["name"], bone["name"])
        if "parent" in bone:
            bone["parent"] = mapa.get(bone["parent"], bone["parent"])

        cubos = [c for c in bone.get("cubes", []) if desenha_algo(c)]
        descartados += len(bone.get("cubes", [])) - len(cubos)
        for cubo in cubos:
            if "uv" in cubo:
                cubo["uv"] = escalar_uv(cubo["uv"])
        if cubos:
            bone["cubes"] = cubos
        else:
            bone.pop("cubes", None)

    geo["bones"].extend(collections.OrderedDict(bota) for bota in BOTAS)
    gravar(DST_GEO, doc)
    return len(geo["bones"]), descartados


def encurtar(graus):
    """Traz um angulo para (-180, 180], onde ele descreve o mesmo giro pelo caminho curto."""
    return (graus + 180) % 360 - 180


def vetores_do_canal(canais, nome):
    """
    Onde mora cada vetor de um canal, como pares (dono, chave) para ler e gravar no lugar.

    O Blockbench escreve o mesmo canal de tres jeitos, e os tres aparecem no mesmo arquivo:
    lista solta quando o valor e fixo, {"vector": [...]} quando e fixo mas veio do CPM, e um
    keyframe por tempo quando tem movimento.
    """
    canal = canais[nome]
    if isinstance(canal, list):                       # "rotation": [x, y, z]
        return [(canais, nome)]
    if "vector" in canal:                             # "rotation": {"vector": [x, y, z]}
        return [(canal, "vector")]

    saida = []                                        # "rotation": {"0.0": {...}, "0.5": {...}}
    for tempo in sorted(canal, key=float):
        quadro = canal[tempo]
        saida.append((quadro, "vector") if isinstance(quadro, dict) and "vector" in quadro
                     else (canal, tempo))
    return saida


def normalizar_rotacoes(animacao):
    """
    Reescreve as rotacoes perto de 360 como o negativo equivalente.

    O CPM exporta angulo negativo como 360 menos o valor: a mesma pose, mas escrita do outro lado
    da volta. Como pose nao muda nada. O problema e a TRANSICAO: o GeckoLib mistura duas animacoes
    interpolando os graus em linha reta, entao sair de 'running' com group2 em 0 e entrar em
    'jumping' com group2 em 329 nao gira 31 graus para tras -- gira 329 para a frente, a volta
    inteira, na frente do jogador.

    O passo entre keyframes e preservado: so o ponto de partida e trazido para perto de zero, e
    dali cada keyframe anda o mesmo delta de antes (tambem pelo caminho curto). Uma animacao que
    de proposito desse uma volta completa continuaria dando, desde que nenhum passo sozinho chegue
    a 180 graus -- ai nao ha como saber para que lado ela ia, e nem o GeckoLib saberia.
    """
    corrigidos = 0
    for canais in animacao.get("bones", {}).values():
        if "rotation" not in canais:
            continue

        antes, depois = None, None
        for dono, chave in vetores_do_canal(canais, "rotation"):
            bruto = list(dono[chave])
            if depois is None:
                novo = [encurtar(v) for v in bruto]
            else:
                novo = [d + encurtar(v - a) for d, v, a in zip(depois, bruto, antes)]
            corrigidos += sum(1 for x, y in zip(bruto, novo) if x != y)
            dono[chave] = [num(round(v, 6)) for v in novo]
            antes, depois = bruto, novo
    return corrigidos


def converter_anim(origem):
    doc = ler(origem)
    exportadas = doc["animations"]

    renomeadas = {}
    por_duracao = collections.defaultdict(list)
    for chave, animacao in exportadas.items():
        if DESCARTAR.match(chave) or not animacao.get("bones"):
            continue
        if chave in POR_NOME:
            renomeadas[POR_NOME[chave]] = animacao
        else:
            por_duracao[float(animacao.get("animation_length", 0))].append((chave, animacao))

    for duracao, achadas in sorted(por_duracao.items()):
        if duracao not in POR_DURACAO:
            sys.exit("animacao sem nome de %ss no export, e nenhum estado do CPM tem essa duracao "
                     "(esperadas: %s)" % (duracao, ", ".join("%ss" % d for d in POR_DURACAO)))
        if len(achadas) > 1:
            # Ja aconteceu: o CPM guardava 'walking' em duas camadas aditivas e o Blockbench as
            # separou em duas animacoes. O GeckoLib toca uma por controller, entao nao da para
            # adivinhar qual vale -- junte as camadas no CPM e reexporte.
            sys.exit("o export tem %d animacoes sem nome de %ss (%s); o CPM deve ter uma camada "
                     "so por estado" % (len(achadas), duracao,
                                        ", ".join(repr(c) for c, _ in achadas)))
        alvo = POR_DURACAO[duracao]
        if alvo in renomeadas:
            sys.exit("o export tem uma animacao chamada '%s' e outra sem nome de %ss, que cairia "
                     "no mesmo lugar (%r); apague a que sobra no CPM"
                     % (alvo, duracao, achadas[0][0]))
        renomeadas[alvo] = achadas[0][1]

    faltando = [nome for nome in ORDEM if nome not in renomeadas]
    if faltando:
        sys.exit("faltou converter: " + ", ".join(faltando))

    corrigidos = sum(normalizar_rotacoes(animacao) for animacao in renomeadas.values())

    doc["animations"] = collections.OrderedDict((nome, renomeadas[nome]) for nome in ORDEM)
    gravar(DST_ANIM, doc)
    return list(doc["animations"]), corrigidos


def main(argv):
    # Reexportar so as animacoes e comum: o modelo fica igual e o que muda e a pose. Nesse caso o
    # geo do jar continua valendo e nao ha o que converter nele.
    if len(argv) == 2:
        print("geo : nao informado, mantido o que ja esta no jar")
        relatar_anim(converter_anim(argv[1]))
        return
    if len(argv) != 3:
        sys.exit("uso: python tools/converter-capa-cpm.py "
                 "[<modelo.geo.json>] <animacoes.animation.json>")
    bones, descartados = converter_geo(argv[1])
    print("geo : %d bones, %d cubos marcadores do CPM descartados" % (bones, descartados))
    relatar_anim(converter_anim(argv[2]))


def relatar_anim(resultado):
    nomes, corrigidos = resultado
    print("anim: " + ", ".join(nomes))
    print("rot : %d valores trazidos de perto de 360 para o negativo equivalente" % corrigidos)


if __name__ == "__main__":
    main(sys.argv)
