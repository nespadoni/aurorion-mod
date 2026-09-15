# Aurorion Aeonita

Mod de **conteúdo** do ecossistema: itens e blocos. É o único lugar do monorepo que registra coisa
que entra no inventário do jogador — e é justamente por isso que ele não tem mecânica de ato nenhum.

A divisão é essa:

| | mod de conteúdo (`aurorion-aeonita`) | mod de ato (`aurorion-ato2`, e os próximos) |
|---|---|---|
| registra item/bloco | sim | **nunca** |
| tempo de vida | fica para sempre | sai do modpack quando o ato acaba |
| desligar custa | inventário e construções | só a mecânica |

Se o Altar de Seleção fosse registrado pelo mod do Ato 2, tirar o Ato 2 do modpack apagaria os
altares já construídos no mundo. Ele mora aqui; quem dá significado a ele é o mod do ato.

## Conteúdo

**Itens** — `aeonita_ingot_yellow`, `aeonita_ingot_blue`, `aeonita_ingot_red`. Comestíveis em
qualquer situação (`alwaysEdible`), 4 de nutrição, e sempre dão Brilho por 10 s ao serem comidos.

**Blocos** — `aeonita_block_<cor>` (armazenamento 9:1, emitem luz 10 de verdade) e `selection_altar`
(pedestal de três degraus, luz 7, forma própria).

Receita do altar: os três lingotes na linha de cima, `polished_deepslate` no resto.

**Capas de uniforme** — `uniform_cape_venthra`, `_sylvara`, `_nyx`, `_ignivar`, `_aetheris`. Peitoral
animado, uma cor por casa de Ethereal. Protege como couro (3 de armadura, 80 de durabilidade) e usa
o GeckoLib para animar gola, capa, mangas e panos de perna conforme quem veste está parado, andando,
correndo ou no ar. Não têm receita nem loot table: são uniforme, entregues por `/give` ou por kit.

Ver [Capa de uniforme](#capa-de-uniforme) para o que a conversão dos assets fez e o que ainda falta
conferir em jogo.

### Luz dinâmica

Qualquer item da tag `aurorion_aeonita:emits_light` acende onde estiver — na mão, no chão ou dentro
de um quadro. É **ilusão local**: troca o ar da posição pelo `minecraft:light` invisível na cópia do
mundo do próprio cliente. O servidor nunca fica sabendo e nada disso entra no save.

Como é tag e não lista em código, dá para fazer o lingote de outro mod acender sem recompilar nada:

```json
// data/<seu_datapack>/tags/item/... ou um datapack do servidor
{ "replace": false, "values": ["outromod:cristal_brilhante"] }
```

## Configuração

`config/aurorion_aeonita-client.toml` — só existe porque a luz dinâmica é a única parte deste mod
que custa frame time. Num modpack pesado é o primeiro item a sacrificar por FPS, e desligar ela não
tira nenhum item, bloco ou receita do jogo.

```toml
[dynamicLight]
enabled = true
lightLevel = 15
```

## Arquitetura

```
registry/    AeonitaBlocks, AeonitaItems, AeonitaCreativeTab, AeonitaTags, AeonitaArmorMaterials
block/       SelectionAltarBlock (so a VoxelShape do pedestal — sem estado, sem block entity, sem tick)
item/        UniformCapeItem (as cinco capas de uniforme)
client/      DynamicLightHandler, UniformCapeModel, UniformCapeRenderer
config/      AeonitaClientConfig
```

### Decisões

- **Ordem de registro é dependência real, não estilo.** `AeonitaItems` referencia os
  `DeferredBlock` para criar os `BlockItem`, então `AeonitaBlocks.BLOCKS.register(bus)` vem antes no
  construtor do mod. Inverter as duas linhas quebra o carregamento.
- **A luz dinâmica não aloca por tick.** Ela roda por tick de cliente, então cai na meta de "zero
  alocação por frame" do [SDD §2](../SDD.md): as duas coleções são campos reaproveitados (`fastutil`
  com chave `int`, sem boxing de `Integer` por entidade por tick), a posição da entidade viva é
  calculada num `MutableBlockPos` reaproveitado, e a varredura de quem sumiu — a única parte que
  aloca um iterador — só roda quando há de fato entidade a remover.
- **O custo que sobra é inerente ao truque**: cada mudança de posição é um `setBlock`, que refaz
  iluminação do chunk. Não dá para otimizar isso sem trocar de técnica; dá para desligar, e é o que
  a config existe para permitir.
- **Os bits do `setBlock` são o mínimo**: `UPDATE_CLIENTS | UPDATE_KNOWN_SHAPE`, e não
  `Block.UPDATE_ALL` (que é `UPDATE_NEIGHBORS | UPDATE_CLIENTS`). O bit de vizinhos não tem o que
  fazer num bloco que só existe na cópia local do mundo: ele mandava os blocos em volta reagirem, e a
  cascata de `updateShape` podia deixar vizinho desenhado errado até o chunk recarregar. A iluminação
  continua correta — quem a recalcula é o motor de luz, chamado pelo próprio `setBlockState` quando a
  emissão muda.
- **O que acende é dado, não código.** Tag de item em vez de `stack.is(A) || stack.is(B) ||
  stack.is(C)` — [SDD §7](../SDD.md), diretriz 4.
- **`SelectionAltarBlock` não sabe o que é uma casa.** Ele é um bloco decorativo com forma própria e
  mais nada. Quem escuta o clique e abre a escolha é o `aurorion-ato2`, casando por tag de bloco —
  sem import, sem dependência de compilação, e o Ato 3 pode reaproveitar o mesmo altar para outra
  coisa sem tocar aqui.

## Capa de uniforme

A arte chegou como projeto do **Customizable Player Models** (`.cpmproject`), mais um `.geo.json` e
um `.animation.json` exportados dele. O CPM exporta em formato Bedrock, que é o mesmo que o GeckoLib
lê — mas exporta com as convenções *dele*, e três delas não servem para armadura:

| O que o CPM exportou | O que o GeckoLib de armadura precisa | O que foi feito |
|---|---|---|
| Bones-raiz `head`, `body`, `left_arm`, ... | `armorHead`, `armorBody`, `armorLeftArm`, ... | Renomeados no `.geo.json` |
| UV num espaço de 4480×4096 para uma textura de 280×256 | UV no tamanho real da textura | Divididos por 16 (exato em binário: nenhum pixel se move) |
| Animações chamadas `""`, `"2"`, `"3"`, `"p:jumping"` | Nomes que o código possa pedir | `idle`, `walking`, `running`, `jumping` |

Os nomes das animações estavam perdidos: o CPM grava o nome que o autor digitou, e as de andar,
correr e parada estavam todas com nome vazio. Quem é quem foi recuperado cruzando as durações com o
`.cpmproject`, onde cada animação ainda tem o arquivo com o tipo no nome (`v_walking` dura 1,5 s,
`v_running` 1,2 s, `v_global` 2 s, `v_jumping` 1 s).

Duas coisas que **não** precisaram mudar: os pivôs (`[0,24,0]`, `[±5,22,0]`, `[±1.9,12,0]`) já eram
exatamente os que o `GeoArmorRenderer` espera, e nenhum bone animado precisou ser renomeado — só os
bones-raiz, que não têm keyframe nenhum.

Também saíram 9 cubos de tamanho zero. O CPM exporta cada *group* como um cubo vazio; eles não
desenham nada, mas o GeckoLib assa seis quads por cubo desses e paga os vértices por frame assim
mesmo ([SDD §2](../SDD.md): zero desperdício no caminho quente do cliente).

Sobraram no arquivo `jump_pre` e `jump_post` — as duas metades de um *gesture* do CPM, que aqui não
tem gatilho. Ficaram porque são trabalho do autor e não custam nada paradas; se um dia a capa ganhar
uma animação disparada por evento, elas já estão lá.

### Por que renomear no arquivo em vez de sobrescrever no renderer

O `GeoArmorRenderer` tem `getHeadBone`, `getBodyBone` e companhia justamente para isso. Só que eles
devolvem `null` quando o bone não existe, e o render segue adiante com o `null` — um nome errado não
vira erro de carregamento, vira `NullPointerException` no meio de um frame, no cliente de quem
estiver perto de alguém de capa. Com os oito nomes escritos no `.geo.json` (inclusive as duas botas,
vazias, que esta peça não tem), não há nome para errar em lugar nenhum.

### O que o teste cobre

`UniformCapeAssetsTest` lê os dois arquivos com o **próprio Gson do GeckoLib** e monta o modelo
assado — o mesmo caminho que o jogo percorre ao carregar o asset. Ele existe porque erro nesses
arquivos é silencioso: não quebra build, não levanta exceção, a capa só aparece torta, parada ou
invisível. Os três casos são:

- a geometria assa e tem os oito bones de armadura;
- nenhum cubo de tamanho zero sobrou da conversão;
- as quatro animações que o `UniformCapeItem` pede pelo nome existem no arquivo — as constantes
  `RawAnimation` são lidas da própria classe, então não há como o teste e o código divergirem.

### GeckoLib é dependência obrigatória deste mod

E isso tem uma consequência que vale registrar: o Altar de Seleção mora aqui, então um pack sem
GeckoLib perde também o altar — e com ele a porta de entrada da escolha de casa do
`aurorion-ethereal`. Não é quebra do isolamento entre mods (o Ethereal continua carregando e falando
com o altar por tag, nunca por import), mas é um mod a mais na lista de "tem que estar lá".

## Arte

As texturas de bloco (`aeonita_block_*`, `selection_altar_*`) são **placeholders gerados** a partir
da paleta dos lingotes originais — gema lapidada nas três cores e pedra escura com runa dourada no
altar. São 16×16 comuns: para trocar por arte de verdade, basta substituir o PNG.

Os ícones de inventário das capas (`uniform_cape_*.png`) também são gerados: não veio arte 2D
nenhuma, só a textura do modelo. Cada ícone é recortado das três faces de trás da capa — as que têm
o brasão da casa — encolhido para 16×16 e mascarado na silhueta, com o contraste levantado porque a
textura é quase preta de propósito e some no fundo do slot. Mesma regra: nenhum código sabe como o
PNG foi feito, então trocar por arte desenhada à mão é só substituir o arquivo.

## Status

Compila contra NeoForge 21.1.248 / MC 1.21.1. **Ainda não testado em jogo** — o que conferir na
primeira execução:

- Se os três elementos do modelo do altar batem com a `VoxelShape` de `SelectionAltarBlock`
  (silhueta e colisão desenhadas separadamente, é o típico de sair desalinhado).
- **Se a luz dinâmica ainda acende com os bits novos do `setBlock`.** O protótipo original usava
  `Block.UPDATE_ALL` e era assim que tinha sido testado; hoje são `UPDATE_CLIENTS |
  UPDATE_KNOWN_SHAPE`. A leitura do `Level#setBlock` do 1.21.1 diz que a iluminação não depende
  desses bits, mas isso **não foi confirmado em jogo** — se a luz parar de aparecer, é aqui.
- **Como a capa cai no corpo do jogador.** O teste garante que a geometria assa e que os bones certos
  existem; ele não tem como saber se a capa fica no lugar. Olhar: se as mangas acompanham os braços,
  se os panos de perna não atravessam a perna, e se a capa não corta dentro do peitoral de outro mod.
- **Se as animações de andar e correr trocam na hora certa.** A escolha lê `onGround`, `isSprinting`
  e `isMoving` da cópia local da entidade; num modpack com mods de movimento isso pode se comportar
  diferente do vanilla.
- **Nyx e Aetheris.** As texturas que chegaram têm a Nyx em ciano e a Aetheris em roxo, enquanto o
  datapack de casas do `aurorion-ethereal` diz que a Nyx é roxa (`#8A5CF0`) e a Aetheris é azul-claro
  (`#B8C6E0`). Os arquivos foram mantidos com os nomes que vieram — se estiverem trocados, é trocar
  os dois PNG em `textures/entity/armor/uniform_cape/` e os dois em `textures/item/`.

```bash
./gradlew :aurorion-aeonita:runClient
./gradlew :aurorion-aeonita:build
```
