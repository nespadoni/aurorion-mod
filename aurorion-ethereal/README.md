# Aurorion Ethereal

O mod central de Ethereal: **as cinco casas**, a **Cerimônia de Vinculação** e o **Projetor Aeônico**
num jar só.

Os três sistemas moram juntos porque são o mesmo dado visto de três ângulos: a casa de um jogador
decide o que a cerimônia grava; os pontos de cada jogador com casa somam no total da casa; o projetor
exibe essa soma. Ver [SDD §6.0](../SDD.md) para o porquê da fusão de `aurorion-ato2` com
`aurorion-placares`.

## As cinco casas

| Casa | O que representa em Ethereal | Paleta da cerimônia |
|---|---|---|
| **Venthra** | O vento — movimento, adaptação, o que não fica parado | Amarelo `#FFD54A`, cinza `#8A919B` e amarelo claro |
| **Sylvara** | A vida — compreensão, cuidado, crescer junto | Verde `#58C87A`, branco gelo quente `#FFF7DC` e verde claro |
| **Nyx** | A mente — entendimento, estratégia, a pergunta certa | Azul `#439CFF`, branco `#F1F7FF` e azul claro |
| **Ignivar** | O fogo — força, domínio, coragem de segurar | Vermelho `#F04438`, preto `#130C10` e laranja `#FF952E` |
| **Aetheris** | O que existe além — o inexplicável, e a desconfiança de quem explica | Roxo `#A567F4`, preto `#100B19` e lilás |

As casas **não estão em código**: cada uma é um JSON de datapack. Adicionar, remover, renomear,
recolorir, trocar o lema ou botar limite de membros é editar arquivo e dar `/reload`.

```json
// data/<namespace>/aurorion/houses/nyx.json
{
  "name": "Casa Nyx",
  "motto": "A magia obedece a quem a entende.",
  "description": "A mente de Ethereal.",
  "color": "#439CFF",
  "ceremony": {
    "secondary": "#F1F7FF",
    "accent": "#A8DBFF",
    "music": "aurorion_ethereal:ceremony.memory_lane"
  },
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
| `ceremony` | não | `secondary` e `accent` completam a cor principal; `music` aponta para evento de som do resource pack |
| `icon` | não | id de item desenhado no card. Item inexistente vira papel, não crash |
| `capacity` | não | `0` (padrão) = sem limite. Acima disso a casa lota |
| `order` | não | ordem na grade; empate desempata pelo id |

## A Cerimônia de Vinculação

O resultado vem do site e a staff conduz a apresentação no jogo. Não há questionário nem conexão
HTTP com o site: o comando recebe a casa já decidida.

### Comandos de operação (staff nível 2)

```mcfunction
# Cadastrar antes da apresentação, inclusive para um jogador conhecido offline:
/casa definir NomeDoJogador aurorion_ethereal:nyx

# Na hora em que a pessoa chegar à frente, revelar a casa cadastrada:
/casa cerimonia NomeDoJogador

# Ou atribuir e iniciar a revelação em um único comando:
/casa cerimonia NomeDoJogador aurorion_ethereal:ignivar

# Interromper música e efeitos; a casa continua atribuída:
/casa cerimonia cancelar NomeDoJogador
```

IDs: `aurorion_ethereal:ignivar`, `aurorion_ethereal:aetheris`,
`aurorion_ethereal:sylvara`, `aurorion_ethereal:venthra` e `aurorion_ethereal:nyx`.
O autocomplete oferece os IDs do datapack carregado.

**Uma pessoa por vez.** O comando de cerimônia aceita somente um jogador online e vivo.
Atribuir uma casa offline nunca agenda uma revelação no login. Uma fila legada também não toca
automaticamente: a staff dispara o momento. A casa é gravada antes da animação; desconectar,
cancelar ou reiniciar o servidor não desfaz o vínculo.

### A cena (18 segundos a 20 ticks/s)

| Fase | Tempo | Apresentação |
|---|---|---|
| Entrada | 0–2 s | Música entra com fade; escurecimento suave somente para o escolhido |
| Convocação | 2–5 s | Círculo duplo luminoso se forma nos pés; runas aparecem |
| Reunião | 5–8 s | O selo pulsa, as lanças de luz sobem e a coluna se fecha sobre a pessoa |
| Revelação | 8–9 s | Onda de luz e partículas nas cores da casa; nome grande surge acima da cabeça e no HUD; sobe a primeira salva de fogos |
| Coroação | 9–18 s | Nome, círculo e runas permanecem; fogos seguem abrindo; música e efeitos somem nos últimos 2 s |

**A cena é parada, de propósito.** O selo não gira e as runas não orbitam: com trinta pessoas
espalhadas em volta, qualquer giro faz cada uma ver um desenho diferente no mesmo instante, e o
nome parecia escorregar para fora do círculo. O selo fica cravado nos pés e o que dá vida a ele é
um pulso de brilho, igual de qualquer ângulo. As runas ficam paradas em volta do corpo, viradas
para fora do círculo.

O nome flutua aproximadamente **4,4 blocos acima dos pés** e acompanha quem olha **só no eixo Y**:
ele fica sempre em pé e ancorado acima da cabeça, em vez de tombar junto com a mira de cada
espectador — que era o que o descolava do círculo para quem estava perto ou olhando de baixo.
Círculo, runas e título são desenhados no mundo até **96 blocos**, sem depender do alcance de
renderização do corpo. Paredes continuam ocultando a cena. Os detalhes das runas param a
64 blocos e as partículas pequenas a 48; o círculo, a luz e o nome permanecem à distância.

### Luz e fogos

A luz da casa é uma segunda camada de geometria, **aditiva** (`SRC_ALPHA, ONE`): ela soma no que já
está na tela em vez de pintar por cima, e não escreve profundidade — então nunca tapa os traços do
selo nem as letras do nome. São quatro peças, todas na cor principal e no acento da casa: o chão
aceso sob os pés, dezesseis lanças verticais em volta do círculo, a coluna de luz que desce sobre a
pessoa e a onda que abre na revelação. Atrás das letras do nome fica um clarão na cor da casa.

Os **fogos de artifício** abrem de 7,5 a 12 blocos em volta e de 7,5 a 11,5 blocos acima do palco,
a partir da revelação: seis salvas, a primeira com três bombas e as demais com duas. A faísca de
foguete do vanilla não aceita cor por `addParticle` — quem tinge a do foguete comum é a entidade,
lendo o item. Criando a partícula pelo motor recebe-se a instância de volta, e a cor da casa vira
uma chamada direta, sem entidade nem item no meio (é o mesmo caminho que o próprio Minecraft usa).

Cada bomba é sorteada a partir do id do escolhido e do seu número de ordem, nunca de um estado que
ande junto com o tick: os trinta clientes abrem a mesma bomba no mesmo ponto do céu, e quem chega no
meio da cena cai no mesmo espetáculo em vez de num paralelo. Em "partículas: mínimas" os fogos não
saem; em "reduzidas" cada bomba cai de 76 para 36 faíscas.

O escolhido recebe nome e lema sobre o HUD, com um clarão suave na revelação. O palco continua
visível e não se abre tela modal. O rito acompanha pequenos deslocamentos; sair mais de quatro
blocos do ponto inicial, morrer, trocar de dimensão ou desconectar encerra a apresentação.
Não altera invulnerabilidade nem efeitos de poção. Uma alteração administrativa da casa também
encerra a cena antiga.

### Música

Os dois OGG fornecidos estão em `assets/aurorion_ethereal/sounds/ceremony/`:

- `going_north.ogg` — Going North, Jon Björk; padrão de Ignivar e Venthra.
- `memory_lane.ogg` — Memory Lane, Jon Björk; padrão de Aetheris, Sylvara e Nyx.

A trilha é transmitida do arquivo local por streaming, com volume uniforme para a plateia e
controle pelo volume **Música** do Minecraft. Um resource pack pode substituir os OGG ou declarar
outro evento em `sounds.json`; `ceremony.music` no JSON da casa seleciona esse evento.
[Formato de áudio do NeoForge 1.21.1](https://docs.neoforged.net/docs/1.21.1/resources/client/sounds/).

### Rede e ciclo de vida

O servidor envia um início e um fim para cada espectador na mesma dimensão e no raio da cena.
Não envia partículas por tick: cada cliente reconstrói a animação localmente. Quem passa a rastrear
o participante durante o rito recebe a fase atual; nesse caso a música não reinicia do zero.
Quem se afasta ou muda de dimensão limpa a cena local. Cancelamento é entregue também à plateia
original que já se afastou.

O anúncio no chat acontece **na revelação**, respeitando `announceInChat`. Ambos os lados precisam
usar esta versão do mod (protocolo 2) para a apresentação completa.
O altar continua servindo para consulta; `ceremonyRequired=false` mantém a escolha direta opcional.

### Validação

```powershell
.\gradlew.bat :aurorion-ethereal:build :aurorion-ethereal:runGameTestServer
```

Foram escritos testes para os dados de casas e caminhos dos OGG. O GameTest usa um servidor dedicado descartável
para conferir cadastro, revelação por comando, audiência a 80 blocos, exclusão fora do raio,
cancelamento, desconexão, persistência do vínculo e transmissão da paleta.
O build e os GameTests desta alteração ainda precisam ser executados em um ambiente de validação.

Para conferir aparência e áudio no modpack, usar dois clientes com nomes diferentes, participante
e espectador, conectados ao mesmo servidor. Conferir as cinco casas, distâncias de 10/48/80 blocos,
primeira e terceira pessoa, qualidade de partículas reduzida, shaders e cancelamento no meio.
As classes e os recursos dos testes não entram no jar distribuído.

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

/casa cerimonia <jogador> [casa]             -> revela a casa cadastrada (ou atribui a informada)           (nivel 2)
/casa cerimonia cancelar <jogador>           -> corta o rito, ou tira ele da fila     (nivel 2)

/pontos ver                       -> pontuacao de todas as casas                     (nivel 2)
/pontos aluno <jogador> <qtd>     -> ponto do aluno (conta no total da casa dele)     (nivel 2)
/pontos casa <casa> <qtd>         -> bonus da casa inteira                            (nivel 2)
/pontos missao <jogador>          -> registra uma missao concluida                    (nivel 2)
```

O jogador comum só tem `/casa` (consulta). Quem vincula é a staff.

`definir` cadastra ou corrige a casa. `cerimonia` revela a casa cadastrada; com o argumento de casa,
atribui e apresenta em uma única ação.

Cadastro e consulta aceitam perfis offline conhecidos. A cerimônia exige um participante online
e somente começa quando a staff executa o comando.

## Configuração

`config/aurorion_ethereal-server.toml`:

```toml
[houses]
ceremonyRequired = true   # false = o altar volta a abrir a grade e o jogador escolhe sozinho
allowRechoose    = false  # true deixa o jogador refazer a vinculacao
announceInChat   = true   # anuncia no chat quando alguem e acolhido por uma casa
```

`ceremonyRequired = false` é a saída para quando não houver staff para conduzir as cerimônias: o
altar volta ao comportamento de escolha direta, em duas etapas.

O que **não** é config é tão proposital quanto o que é: nome, cor, lema e lotação de casa são
datapack, junto com as cores e a trilha da cerimônia. Config é para regra de servidor; conteúdo é para dado.

## Arquitetura

```
house/       House (record + Codec do JSON + StreamCodec da rede), HouseCatalog (reload listener),
             HouseData (SavedData por UUID), HouseManager (as regras + o unico ponto que grava casa),
             HouseOption, PendingSelections (quem tem grade aberta e em qual altar)
ceremony/    BindingRite (cena e plateia), CeremonyManager (atribuicao e apresentacao),
             CeremonyData (leitura/limpeza de filas legadas)
ranking/     RankingData (SavedData), PlayerRanking, RankedEntry, BoardLine, BoardMode (as metas),
             BoardService
block/       AeonicProjectorBlock, entity/AeonicProjectorBlockEntity
registry/    EtherealBlocks, EtherealItems, EtherealBlockEntities, EtherealCreativeTab
network/     EtherealNetwork (registro + ponta servidora), RitePayload e payloads de casas/projetor
client/      EtherealClientNetwork, EtherealClientEvents, AeonicProjectorRenderer,
             RiteClient, RiteRenderer, RiteOverlay, RiteParticles, RiteMusic,
             gui/ HouseSelectionScreen, HouseCardWidget, ProjectorConfigScreen
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
