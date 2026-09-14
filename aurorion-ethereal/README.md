# Aurorion Ethereal

O mod central de Ethereal: **as cinco casas**, a **Cerimônia de Vinculação** e o **Projetor Aeônico**
num jar só.

Os três sistemas moram juntos porque são o mesmo dado visto de três ângulos: a casa de um jogador
decide o que a cerimônia grava; os pontos de cada jogador com casa somam no total da casa; o projetor
exibe essa soma. Ver [SDD §6.0](../SDD.md) para o porquê da fusão de `aurorion-ato2` com
`aurorion-placares`.

## As cinco casas

| Casa | O que representa em Ethereal | Cor |
|---|---|---|
| **Venthra** | O vento — movimento, adaptação, o que não fica parado | `#FFD23F` |
| **Sylvara** | A vida — compreensão, cuidado, crescer junto | `#3ECF6E` |
| **Nyx** | A mente — entendimento, estratégia, a pergunta certa | `#8A5CF0` |
| **Ignivar** | O fogo — força, domínio, coragem de segurar | `#FF5533` |
| **Aetheris** | O que existe além — o inexplicável, e a desconfiança de quem explica | `#B8C6E0` |

As casas **não estão em código**: cada uma é um JSON de datapack. Adicionar, remover, renomear,
recolorir, trocar o lema ou botar limite de membros é editar arquivo e dar `/reload`.

```json
// data/<namespace>/aurorion/houses/nyx.json
{
  "name": "Casa Nyx",
  "motto": "A magia obedece a quem a entende.",
  "description": "A mente de Ethereal.",
  "color": "#8A5CF0",
  "icon": "minecraft:amethyst_shard",
  "capacity": 0,
  "order": 3
}
```

| campo | obrigatório | o que faz |
|---|---|---|
| `name` | sim | texto (string simples ou componente JSON) |
| `motto` | não | o que a casa diz de si; aparece no card e, em destaque, na revelação |
| `description` | não | aparece no card, quebrada em linhas automaticamente |
| `color` | não | `"#RRGGBB"`; tinge o card, o nome no chat e a linha da casa no holograma. Padrão branco |
| `icon` | não | id de item desenhado no card. Item inexistente vira papel, não crash |
| `capacity` | não | `0` (padrão) = sem limite. Acima disso a casa lota |
| `order` | não | ordem na grade; empate desempata pelo id |

## A Cerimônia de Vinculação

A staff define a casa, e o mundo assiste:

```
/casa cerimonia <jogador> <casa>
   -> a casa é gravada na hora
       -> o Rito de Vinculação toca (13 s), para quem passa e para quem está por perto
           -> anúncio no chat no fim
```

### O Rito, em quatro tempos

| Fase | Duração | O que se vê |
|---|---|---|
| Convocação | 3 s | um anel de runas se fecha em volta da pessoa; nada ainda tem cor de casa |
| Reunião | 3 s | a luz converge de fora para dentro do peito e cresce |
| Revelação | 1 s | estouro nas cores da casa; o **símbolo** aparece girando acima da cabeça |
| Coroação | 6 s | a coluna de luz se sustenta sob o símbolo, e some junto com ele |

Quem está passando pelo rito fica parado e intocável durante a cena, e vê o **nome** e o **lema** da
casa surgirem por cima do HUD. Não há tela modal: a cena boa acontece no mundo, e uma tela cobriria
justamente o que há para ver.

O **símbolo** é o `icon` da casa — o mesmo item do card do altar — renderizado no mundo, com
brilho próprio, chegando com um estalo de escala.

### Detalhes que importam na operação

- **Luz e partículas são do servidor**, então todo mundo num raio de 32 blocos assiste. Um rito que
  só o dono enxergasse não seria um rito, seria uma tela.
- **Dois pacotes por rito**, um no início e um no fim. O cliente conta os próprios ticks; treze
  segundos de animação custam à rede o mesmo que dois cliques.
- **A casa é gravada antes da cena**, não no fim dela. Sair no meio, cair a conexão ou reiniciar o
  servidor não desfaz uma decisão da staff.
- **Jogador offline não perde o rito.** Definir com ele fora deixa o rito na fila; ele toca no
  próximo login.
- **O altar não decide mais nada.** Ele é onde se consulta a própria casa — e, com
  `ceremonyRequired=false`, onde o jogador escolhe sozinho, para quando não houver staff conduzindo.

### O que saiu daqui

Havia um questionário de oito perguntas em datapack, com contagem de pontos por casa, uma fila de
vereditos esperando decisão e quatro subcomandos de staff em volta disso. Tudo aquilo existia para
**sugerir** uma casa que, na prática, já era decidida fora do jogo — e cobrava oito telas de leitura
antes do único instante de que as pessoas lembram depois. As perguntas saíram; o instante ficou, e
agora dura treze segundos.

O teste [`ShippedDatapackTest`](src/test/java/com/aurorion/ethereal/ceremony/ShippedDatapackTest.java)
valida os JSON das casas no build: cor repetida, ordem duplicada e casa sem `icon` quebram a
compilação em vez de aparecerem no meio de um rito ao vivo.

### Que bloco é um altar

Qualquer bloco na tag `aurorion_ethereal:house_altars`. O jar já inclui o
`aurorion_aeonita:selection_altar` nela, com `"required": false` — se o mod de conteúdo não estiver
instalado, a tag fica vazia em vez de quebrar o datapack.

Isso é o acoplamento inteiro entre os dois mods: **uma tag, nenhum import**.

## O Projetor Aeônico

O bloco que projeta um painel holográfico com o ranking. Clique com botão direito (nível 2) abre a
tela: escolher a estatística e o tamanho do painel, e pronto — nenhum comando é necessário para
configurar um projetor.

O painel é o do mod de referência, trazido como estava: moldura pulsante, varredura subindo, duas
faíscas correndo o perímetro, runas flutuando em cinco colunas, cabeçalho com sombra e as cinco
posições. Ele **gira sozinho para encarar quem olha** — por isso o bloco não tem direção.

Sete estatísticas:

| meta | mostra |
|---|---|
| ★ Casas mais pontuadas | ranking de casas, cada linha na cor da casa |
| ↓ Casas menos pontuadas | o mesmo, invertido |
| ◆ Alunos com mais pontos | ranking de jogadores |
| ◇ Alunos com menos pontos | o mesmo, invertido |
| ✎ Maiores completadores de missões | quem mais concluiu missões |
| ☠ Alunos com mais mortes | contagem de mortes |
| ⚔ Maiores duelistas | jogador que matou jogador |

**O total de uma casa é o bônus dela mais a soma dos pontos dos membros.** É por isso que dar ponto a
um aluno mexe no painel da casa dele, e é por isso que este mod e o das casas são o mesmo mod.

Quem dá e tira ponto é o `/pontos`, como no mod de referência — o projetor só exibe.

### Sem GeckoLib

No original, o corpo do projetor era um modelo animado desenhado a cada frame por GeckoLib. Aqui ele
é o **mesmo modelo**, mas como JSON de bloco do vanilla — a geometria é o export do Blockbench que já
vinha no jar, com a textura original. O chunk desenha isso em batch junto com o resto do mundo, e o
renderizador por frame cuida só do holograma.

O que se perde: o cristal do centro não gira. O que se ganha: o mod não depende de mais nada além do
`aurorion-core`, e num modpack pesado uma dependência obrigatória a menos é uma peça a menos que pode
travar a atualização do pack inteiro.

## Uso

```
/casa                             -> mostra a sua casa (qualquer jogador)

/casa listar                      -> todas as casas e quantos membros cada uma tem   (nivel 2)
/casa ver <jogador>               -> a casa de alguem, mesmo offline                 (nivel 2)
/casa definir <jogador> <casa>    -> atribui, ignorando cerimonia e lotacao          (nivel 2)
/casa limpar <jogador>            -> tira da casa                                    (nivel 2)

/casa cerimonia <jogador> <casa>             -> define a casa e toca o Rito           (nivel 2)
/casa cerimonia cancelar <jogador>           -> corta o rito, ou tira ele da fila     (nivel 2)

/pontos ver                       -> pontuacao de todas as casas                     (nivel 2)
/pontos aluno <jogador> <qtd>     -> ponto do aluno (conta no total da casa dele)     (nivel 2)
/pontos casa <casa> <qtd>         -> bonus da casa inteira                            (nivel 2)
/pontos missao <jogador>          -> registra uma missao concluida                    (nivel 2)
```

O jogador comum só tem `/casa` (consulta). Quem vincula é a staff.

A diferença entre `definir` e `cerimonia` é a cena: `definir` grava em silêncio, útil para corrigir
engano; `cerimonia` grava e apresenta.

Tudo aceita jogador offline, porque tudo é gravado por UUID. Definir a casa de quem não está online é
o caso normal num servidor de 80 pessoas, não a exceção — o rito espera o login.

## Configuração

`config/aurorion_ethereal-server.toml`:

```toml
[houses]
ceremonyRequired = true   # false = o altar volta a abrir a grade e o jogador escolhe sozinho
notifyStaff      = true   # avisa a staff online quando uma cerimonia termina
allowRechoose    = false  # true deixa o jogador refazer a vinculacao
announceInChat   = true   # anuncia no chat quando alguem e acolhido por uma casa
```

`ceremonyRequired = false` é a saída para quando não houver staff para conduzir as cerimônias: o
altar volta ao comportamento de escolha direta, em duas etapas.

O que **não** é config é tão proposital quanto o que é: nome, cor, lema e lotação de casa são
datapack, e as perguntas também. Config é para regra de servidor; conteúdo é para dado.

## Arquitetura

```
house/       House (record + Codec do JSON + StreamCodec da rede), HouseCatalog (reload listener),
             HouseData (SavedData por UUID), HouseManager (as regras + o unico ponto que grava casa),
             HouseOption, PendingSelections (quem tem grade aberta e em qual altar)
ceremony/    CeremonyQuestion (+ Option), CeremonyQuestionView (o que o cliente pode ver),
             CeremonyCatalog, ActiveCeremony (estado em memoria), CeremonyData (vereditos e
             revelacoes pendentes, em disco), CeremonyManager (o ritual)
ranking/     RankingData (SavedData), PlayerRanking, RankedEntry, BoardLine, BoardMode (as metas),
             BoardService
block/       AeonicProjectorBlock, entity/AeonicProjectorBlockEntity
registry/    EtherealBlocks, EtherealItems, EtherealBlockEntities, EtherealCreativeTab
network/     EtherealNetwork (registro + ponta servidora) e 9 payloads
client/      EtherealClientNetwork, EtherealClientEvents, AeonicProjectorRenderer,
             gui/ HouseSelectionScreen, HouseCardWidget, CeremonyScreen, HouseRevealScreen,
                  ProjectorConfigScreen
command/     HouseCommand (/casa), PointsCommand (/pontos)
event/       EtherealServerEvents
config/      EtherealConfig
EtherealTags a tag de altares
```

### Decisões

- **Zero trabalho por tick no servidor.** Nenhum ticker, timer ou varredura em lugar nenhum.
  Cerimônia e vinculação são eventos raros (uma vez por jogador na vida do personagem); o projetor
  recalcula no evento que mudou o dado, percorrendo O(projetores carregados daquela meta). O original
  tinha um `serverTick` por bloco comparando um contador de geração a cada segundo — um projetor que
  ninguém olhava custava tick igual ([SDD §10.1](../SDD.md)).
- **Toda gravação de casa passa por `HouseManager.bind`.** Não é organização: como o total de uma
  casa é a soma dos membros, **entrar ou sair de uma casa muda o painel** mesmo sem nenhum ponto ter
  mudado. Deixar esse refresh a cargo de cada chamador seria a armadilha do "quebra silenciosamente
  se quem usar esquecer da parte dele" ([SDD §3.1](../SDD.md)).
- **O detalhe animado do holograma some a partir de 26 blocos.** Varredura, faíscas e runas são o que
  custa por frame, e de longe ninguém lê uma runa de nove milímetros — mas todo mundo paga por ela.
  Era o único corte que o original já fazia, e ele foi mantido.
- **A linha do ranking é `Component`, não texto pronto.** "pts", "missões" e "mortes" são palavras:
  montadas como string no servidor, sairiam no idioma do *servidor* para todo mundo. O original as
  tinha fixas em português dentro do código.
- **O vínculo opção→casa nunca sai do servidor.** `CeremonyQuestionView` existe só para isso.
- **As perguntas são fotografadas na abertura da cerimônia.** Um `/reload` no meio trocaria o
  conjunto sob os pés de quem está respondendo.
- **O pacote de abertura leva todas as perguntas.** Trocar de pergunta fica instantâneo em vez de um
  ida-e-volta pela rede; o servidor continua sendo a autoridade, guardando em que pergunta o jogador
  está e recusando resposta fora de ordem.
- **O empate na contagem é desfeito pelo id da casa.** A ordem de um `HashMap` não é estável entre
  execuções, e a sugestão não pode mudar sozinha entre o fim da cerimônia e a leitura da staff.
- **A revelação é encenada em tempo, não em quadros.** O original contava `tick++` dentro do
  `render`, amarrando a duração da cena ao frame rate — a 30 fps ela levava o dobro do tempo.
- **A cor da linha viaja pronta do servidor.** O cliente não tem os datapacks; para ele "casa" não
  existe como conceito, então ele nunca poderia derivar a cor sozinho. De brinde, o renderizador
  continua com zero trabalho por frame além de medir e desenhar.
- **Permissão e distância são reconferidas a cada pacote de edição**, não só na abertura da tela: uma
  tela aberta continua aberta depois de o jogador andar para longe ou perder o cargo.
- **Casa removida do datapack não apaga o registro do jogador.** O id continua gravado e cada leitura
  trata "casa desconhecida" explicitamente.
- **`@OnlyIn(Dist.CLIENT)` numa classe separada para a ponta cliente da rede.** `EtherealNetwork` só
  toca em `EtherealClientNetwork` depois do teste de dist, então o servidor dedicado nunca chega a
  resolver uma classe que importa `Minecraft`.

## Status

Compila contra NeoForge 21.1.248 / MC 1.21.1, com 16 testes passando. **Ainda não testado em jogo** —
o que conferir na primeira execução:

- O holograma inteiro: as cores e os tempos vieram do original, mas o `RenderType` é montado aqui e
  nunca foi visto desenhando.
- Se as runas (`ᚠ ᚢ ᚦ`) e os símbolos das metas (`★ ↓ ◆ ◇ ✎ ☠ ⚔`) saem na fonte do jogo ou viram
  caixas — o original usava as mesmas runas, mas com um resource pack por trás.
- O layout da tela do altar com cinco cards (a grade quebra em linhas, mas nunca foi vista com 5+).
- A altura da tela do projetor com sete metas, numa resolução pequena com GUI scale alto.
- A leitura de `data/<ns>/aurorion/ceremony_questions/*.json` pelo `SimpleJsonResourceReloadListener`
  com diretório composto (mesma dúvida que as casas já tinham).
- Se cancelar o `RightClickBlock` no altar e no projetor não conflita com nada do modpack que também
  escute esse evento.
- O ritmo da tela de revelação em um cliente com o modpack inteiro carregado.

### Migração de dados

Este mod trocou de `mod_id`. Um mundo que já rodou `aurorion_ato2` ou `aurorion_placares` **não
carrega os dados antigos**: os arquivos de `SavedData` mudaram de nome, e os placares já construídos
tinham o id `aurorion_placares:magical_scoreboard` — que agora é `aurorion_ethereal:aeonic_projector`.
Em desenvolvimento isso é irrelevante; num mundo com dado de verdade, a conversão precisaria ser feita
antes de subir esta versão.

```bash
./gradlew :aurorion-ethereal:runClient
./gradlew :aurorion-ethereal:test
./gradlew :aurorion-ethereal:build
```
