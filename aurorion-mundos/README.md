# Aurorion Mundos

**Mais de um overworld.** Cada mundo usa o mesmo gerador e os mesmos mods de worldgen do overworld,
mas tem seed própria, barreira própria, e portais de obsidiana que levam ao mundo declarado no
datapack.

Desenhado para **terreno pré-gerado fora e importado**: por padrão, nenhuma travessia gera chunk com
o servidor no ar.

## O que ele faz, do ponto de vista do jogador

Constrói o portal de obsidiana, acende, atravessa. É a mecânica do Nether — e é de propósito que não
haja nada novo para aprender. O que muda é para onde o portal leva, que passa a depender de onde ele
foi construído.

Cada mundo tem sua própria barreira, no tamanho que a staff definiu para ele.

## As três coisas que o vanilla amarra ao overworld

O 1.21.1 não tem "outro overworld". Ele tem três amarras, e este mod desfaz as três:

| | Onde o vanilla amarra | O que aconteceria sem o mod |
|---|---|---|
| **Seed** | `ServerLevel#getSeed()` devolve o seed do mundo para toda dimensão | Dois overworlds com o mesmo gerador geram **o mesmo mapa** |
| **Barreira** | `MinecraftServer#createLevels` espelha a do overworld em toda dimensão; `PlayerList#sendLevelInfo` manda sempre a do overworld ao cliente | Uma barreira só, para o servidor inteiro |
| **Destino do portal** | `NetherPortalBlock#getPortalDestination` tem `NETHER ↔ OVERWORLD` fixo no código | Portal de obsidiana só sabe ir ao Nether |

**Dimensão sem seed declarado não é tocada por nada disso.** Ter seed na config é o que torna uma
dimensão nossa — então o Nether, o End e toda dimensão de mod do modpack continuam exatamente como
eram, sem lista de exceções para manter.

## Declarar um mundo

Duas coisas, em dois lugares, por um motivo.

**1. A dimensão é datapack** — `data/<namespace>/dimension/<nome>.json`:

```json
{
  "type": "aurorion_mundos:overworld_like",
  "generator": {
    "type": "minecraft:noise",
    "settings": "minecraft:overworld",
    "biome_source": { "type": "minecraft:multi_noise", "preset": "minecraft:overworld" }
  }
}
```

É apontar para o mesmo preset e as mesmas noise settings do overworld que faz os mods de worldgen
valerem aqui. Eles injetam no preset; o preset é o mesmo. **Não há uma linha de integração por mod,
e não há lista de mods para manter.**

> Ressalva honesta: mod que filtra por dimensão no próprio código (`if (level.dimension() ==
> OVERWORLD)`) não vai aplicar. Isso é verificação caso a caso no seu modpack, e a pré-geração expõe
> cedo — antes de qualquer jogador entrar.

**2. O seed é config** — `config/aurorion_mundos-server.toml`:

```toml
[mundos]
  seeds = ["aurorion_mundos:mundo_dois=284119730051"]
```

Por que não no datapack, junto com o resto: datapack recarrega com `/reload`, e **seed não pode
mudar com o servidor no ar** — o terreno já gerado não muda junto, e o mundo ficaria costurado com
dois mapas. Config só é lida no boot, que é a garantia que esse dado precisa. O segundo motivo é o
da seção abaixo: para pré-gerar em outra máquina, o que precisa viajar é um número num TOML, não um
save inteiro.

⚠️ **O id da dimensão vira o nome da pasta em `dimensions/`. Escolha o nome antes de pré-gerar** —
renomear depois orfana tudo que foi gerado.

## Pré-gerar em outra máquina e importar

Cada dimensão custom vive numa pasta autocontida (overworld, Nether e End são exceções históricas):

```
<mundo>/dimensions/aurorion_mundos/mundo_dois/
    region/     entities/     poi/     data/
```

Dá para gerar numa máquina forte, zipar e soltar no save de produção.

1. **Máquina de pregen**: mesmo modpack, mesmas versões, mesmos datapacks, e o mesmo
   `aurorion_mundos-server.toml`. Qualquer divergência vira uma parede de chunk na emenda.
2. **Pré-gere com o [Chunky](https://modrinth.com/mod/chunky)** a área que pretende abrir, com folga.
   Este mod não reimplementa isso — o Chunky já faz melhor, e com controle de taxa.
3. **Servidor de produção parado.** Copie a pasta inteira, com as quatro subpastas. `region` sem
   `poi` deixa o portal sem achar o destino.
4. Suba e ajuste a barreira **para dentro** da área pré-gerada: `/mundos borda <nome> set <valor>`.
5. Construa os portais de chegada na mão e abra a dimensão quando quiser.

**Confira que os seeds bateram.** Toda vez que o servidor sobe, o log traz uma linha por mundo:

```
aurorion_mundos:mundo_dois: seed 284119730051, barreira 10000 blocos, geracao em runtime desligada.
```

O número vem de `level.getSeed()`, o mesmo que o gerador consulta. Se ele não bater entre as duas
máquinas, você descobre agora — não depois de uma pré-geração inteira perdida.

## A trava: nada gera com o servidor cheio

Não achando portal do outro lado, o vanilla **cava um**. Cavar significa `PortalForcer#createPortal`
varrendo um espiral de 16 blocos e perguntando a altura do terreno em cada coluna — e perguntar a
altura de um chunk que não existe **gera esse chunk**, na thread do servidor.

Por isso `allowRuntimeGeneration` é vazia por padrão: sem a dimensão nessa lista, a travessia sem
portal de chegada é negada com mensagem. Um portal que a staff esqueceu de construir é um erro de
operação, e a resposta certa a isso é um aviso — não um pico de worldgen às 20h de sábado.

**A barreira é a segunda trava, e é a mais forte.** Borda dentro da área pré-gerada significa que não
há para onde andar, logo não há chunk novo para gerar.

## Ligações de portal (datapack)

`data/<namespace>/aurorion/ligacoes/<nome>.json`:

```json
{
  "from": "minecraft:overworld",
  "to": "aurorion_mundos:mundo_dois",
  "area": { "center": [2000, 0], "radius": 128 },
  "order": 0
}
```

| Campo | Obrigatório | O que faz |
|---|---|---|
| `from` | sim | Dimensão onde o portal foi aceso |
| `to` | sim | Para onde ele leva |
| `area` | não | Círculo no plano horizontal. Sem ele, a ligação vale para toda a dimensão |
| `searchRadius` | não | Raio da busca por portal existente do outro lado. Padrão: o da config (16) |
| `order` | não | Só a ordem na listagem de `/mundos ligacoes` |

**`area` é o que permite mais de duas portas.** Um portal do Nether carrega um destino só por
dimensão de origem — a pergunta que o vanilla faz é "de onde você veio", nunca "por qual porta". Com
três mundos isso daria uma corrente: para ir do primeiro ao terceiro, passar pelo segundo. Com
`area`, um mundo tem várias saídas, cada uma num lugar declarado.

Círculo declarado ganha de ligação sem área, **independente do `order`** — a ligação sem área é por
definição a genérica, e deixar a ordem decidir isso faria a topologia depender de um campo que existe
para ordenar listagem. Quem está fora de todos os círculos e não tem ligação genérica cai no vanilla:
portal do Nether continua indo ao Nether.

### Por que o raio de busca é 16, e não os 128 do vanilla

O vanilla procura num raio de **128** sempre que o destino não é o Nether. Dentro de
`PoiManager#ensureLoadedAndValid` isso vira **17×17 chunks e ~6.900 seções de POI, por travessia**.

Esse raio existe porque o Nether comprime coordenadas 8:1 e a chegada cai longe do esperado. Entre
dois mundos de `coordinate_scale 1.0` o destino cai **na mesma coordenada** — 128 não compra nada.
Com 16 são 9 chunks e 216 seções: **cerca de 32× menos chunk tocado**.

## A dupla com o aurorion-portais

Este mod decide **para onde** um portal leva. O [aurorion-portais](../aurorion-portais/) decide
**quando** se pode atravessar. **Os dois não se conhecem** — não há import nem dependência declarada
entre eles, igual ao par `aurorion-vidas` × `aurorion-portais` ([SDD §9.1](../SDD.md)).

Isso sai de graça: a regra de negar-por-padrão do outro mod já trata toda dimensão fora de
`freeDimensions` como trancada, então **um mundo daqui nasce trancado sem uma linha de código**. Dar
horário a ele é escrever uma linha de trem com `"dimensions": ["aurorion_mundos:mundo_dois"]`.

A ordem ajuda: o `PortalEntryMixin` do outro mod barra em `Entity#canUsePortal`, que o vanilla
consulta **antes** de `getPortalDestination`. Fora do horário, a travessia custa duas comparações de
`long` — a busca de portal daqui nem chega a rodar.

## Comandos

```
/mundos                                      -> os mundos, com seed, barreira e geração em runtime
/mundos ligacoes                             -> as ligações de portal carregadas
/mundos borda <dimensão> ver
/mundos borda <dimensão> set <tamanho> [segundos]
/mundos borda <dimensão> centro <x> <z>
/mundos borda <dimensão> aviso distancia <blocos> | tempo <segundos>
/mundos borda <dimensão> dano porbloco <valor> | zona <blocos>
```

Nível 2 em toda a árvore — é ferramenta de staff.

**Por que não basta o `/worldborder`:** ele é meio de cada. `get` e `add` leem `source.getLevel()`,
mas `set`, `center`, `damage` e `warning` escrevem em `server.overworld()`. Num servidor de uma
barreira só isso nunca aparece; com uma barreira por mundo, significa que metade do comando mente
sobre onde está mexendo. Aqui a dimensão é sempre argumento explícito, e o comando **recusa**
dimensão que este mod não gerencia — mexer no overworld continua sendo trabalho do `/worldborder`.

## Custo

Nada roda por tick. Foi verificado no código, não estimado:

| Caminho | Quando | Custo |
|---|---|---|
| Seed da dimensão | Criação do nível, geração de chunk, troca de dimensão | Uma consulta a `Map` |
| Destino do portal | Travessia de portal | Uma consulta a `Map` + uma distância ao quadrado |
| Busca do portal de chegada | Travessia de portal | 9 chunks (vanilla: 289) |
| Barreira | Mudança de barreira, entrar/trocar de dimensão | Um pacote por jogador **daquela** dimensão |

O `tick()` de cada barreira o vanilla já fazia: `ServerLevel` sempre ticou a sua própria, mesmo
quando ela era só um espelho.

**O que não foi medido:** TPS com 90 pessoas. Os números acima vêm da leitura do código do vanilla,
não de um profile em produção. Em produção, o que vale olhar é o `spark` em `PortalForcer` e em
`PoiManager` durante um horário de trem.

## Mixins

Seis, todos cirúrgicos. Quatro são acessores ou invocadores, que não acrescentam bytecode a método
nenhum:

| Mixin | Alvo | Para quê |
|---|---|---|
| `ServerLevelSeedMixin` | `ServerLevel#getSeed`, construtor | Seed e mistura de biomas por dimensão |
| `LevelBiomeManagerAccessor` | `Level.biomeManager` | Escrita no campo, para o acima |
| `NetherPortalDestinationMixin` | `NetherPortalBlock#getPortalDestination` | Destino por datapack |
| `NetherPortalExitInvoker` | `NetherPortalBlock#getDimensionTransitionFromExit` | Reaproveitar a física de saída do vanilla |
| `WorldBorderListenersAccessor` | `WorldBorder.listeners` | Achar o delegado a remover |
| `DelegateBorderTargetAccessor` | `DelegateBorderChangeListener.worldBorder` | Saber qual delegado é de qual mundo |

O mixin de seed é o ponto mais arriscado do mod: um mod que guarde seed em estático na inicialização
pode discordar. A saída é tirar o seed da config daquela dimensão — degrada para "mundo igual ao
overworld", nunca para mundo corrompido.
