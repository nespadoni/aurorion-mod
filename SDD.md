# Aurorion Mods — Documento de Design (SDD)

## 1. Contexto

O ecossistema Aurorion roda num **servidor único, com 80+ jogadores simultâneos**, dentro de um
**modpack pesado** — dezenas de outros mods já disputando tick do servidor, memória, largura de
banda e frame time do cliente. Isso não é um mod solo para um servidor de amigos: qualquer
ineficiência aqui compete por orçamento que já está apertado antes do Aurorion existir.

Consequência direta para todo o ecossistema: **performance é requisito de primeira classe**,
verificado a cada decisão de design, não um passe de otimização feito no fim.

## 2. Metas não-funcionais

Concretas, para poder julgar uma decisão de design contra elas:

| Meta | Por quê |
|---|---|
| Zero alocação por tick no caminho quente do servidor | Com 80 jogadores, algo que roda "por jogador por tick" roda 1600x/segundo |
| Zero alocação por frame no caminho quente do cliente | O cliente do jogador também roda o modpack inteiro — não é o único consumidor de frame time |
| Payload de rede O(1) fora do login | Login já é O(jogadores online); tudo depois disso não pode crescer com a população do servidor |
| Nunca interferir em mods de terceiros fora do escopo próprio | Modpack pesado = superfície de conflito grande; mixins e listeners devem ser cirúrgicos |
| Nunca quebrar a cadeia de assinatura do chat seguro | Um erro aqui derruba conexões em massa — catastrófico com 80 jogadores conectados |

## 3. Decisão de arquitetura: monorepo single-loader

- **NeoForge puro, sem Architectury/multiloader.** Este servidor não precisa rodar em Fabric nem
  Forge legado. Cada camada de abstração multiloader é overhead de manutenção sem benefício aqui
  — cortamos essa camada inteira.
- **Um subprojeto Gradle por mod**, versões de MC/NeoForge/Parchment centralizadas na raiz
  (`gradle.properties`). Cada mod é ativável/desativável sem afetar os outros — importante num
  modpack onde qualquer peça pode precisar ser desligada isoladamente para isolar um bug.
- Ver [README.md](README.md) para comandos e estrutura de pastas.

### 3.1 Uma exceção à independência: `aurorion-core`

A regra acima vale entre **mods**, não entre mod e biblioteca. `aurorion-core` é obrigatório para
todos os outros e **não** pode ser desligado.

A troca foi feita de olhos abertos, contra uma duplicação que já tinha custo real: os sete
`build.gradle` eram byte-idênticos (665 linhas copiadas), quatro mods repetiam o mesmo esqueleto de
`SavedData`, e — o pior caso — o mesmo algoritmo de "achar um lugar seguro para colocar o jogador"
existia escrito **duas vezes**, em `aurorion-portais` e `aurorion-vidas`. Duas cópias de uma
checagem de segurança são duas chances de consertar só uma quando o bug aparecer.

O critério para algo entrar no core é estreito de propósito: **já estava duplicado**. Utilidade que
só um mod usa fica no mod, senão a biblioteca vira depósito de código especulativo — que é o modo
clássico de uma camada compartilhada piorar a manutenção em vez de melhorar.

O que **não** entrou, e por quê: um helper para registrar pacotes de rede eliminaria ~14 linhas,
mas obrigaria a criar o lambda que referencia classe de cliente durante o registro, que roda também
no servidor dedicado. Hoje esse lambda só nasce depois da checagem de `Dist`. Trocar 14 linhas por
um risco de derrubar o servidor dedicado é péssimo negócio.

**Uma abstração que exige disciplina de quem chama não é uma abstração.** Duas classes do core
nasceram violando isso e foram corrigidas:

- `SavedDataAccess` pedia que cada mod chamasse `invalidate()` num listener de `ServerStoppedEvent`.
  Dois dos quatro mods não tinham esse listener e passaram a precisar de um — o bug mudou de lugar
  em vez de sumir. Hoje são duas travas independentes de quem usa: o core zera todos os caches ao
  parar o servidor, **e** o cache guarda de qual `MinecraftServer` veio, então o dado nunca cruza de
  um mundo para outro nem que o evento falhe.
- `DerivedConfig` substituiu um cache que dependia de um listener de `ModConfigEvent` — uma classe
  inteira no `aurorion-portais`, apagada. O gatilho passou a ser o próprio dado: guarda-se o valor
  cru junto com o convertido e compara-se. Sem evento para esquecer, sem ordem de inicialização
  para acertar, e editar o arquivo com o servidor no ar aplica na leitura seguinte.

O teste ao adicionar algo ao core é esse: *o que acontece se quem usar esquecer da parte dele?* Se a
resposta for "quebra silenciosamente", o desenho está errado.

### 3.2 Convenção de build em um lugar só

Os `build.gradle` dos mods têm **uma linha**: `apply from: "$rootDir/gradle/aurorion-mod.gradle"`.
O problema que isso resolve não é o volume de texto — é que sete cópias significam sete lugares para
esquecer numa mudança de versão de Java, de Parchment ou de argumento de run, e o esquecimento é
silencioso. Adicionar um mod ao ecossistema agora é criar a pasta, escrever `gradle.properties` e
incluir uma linha no `settings.gradle`.

## 4. aurorion-talk — decisões de performance

Primeiro mod do ecossistema; as decisões abaixo são o padrão a repetir nos próximos.

### 4.1 Caminho do chat (servidor "vê" isso 1x por mensagem, não por tick)

- **Cancelamento em `ChatComponent.addMessage`, nunca em `ChatListener.showMessageToPlayer`.**
  Cancelar no listener faria o cliente devolver `markMessageAsProcessed(false)`, bagunçando a
  cadeia de assinaturas do chat seguro. Com 80 jogadores, esse tipo de erro não é cosmético — é o
  tipo de bug que desconecta gente em lote.
- Toda a lógica de decidir "esconde ou não" é local ao cliente que recebeu a mensagem — o servidor
  não participa dessa decisão, então não há trabalho extra no lado que já é o gargalo.

### 4.2 Renderização 3D (roda por jogador visível, por frame)

- **`Font#split` (quebra de linha) roda uma vez por mensagem, no momento em que ela chega — não a
  cada frame.** Esse era o gargalo do Talk Balloons original: com dezenas de jogadores falando,
  refazer a quebra de texto 60x/segundo por balão é desperdício puro.
- **Quads vão direto para o `MultiBufferSource` da cena** (`VertexConsumer.addVertex` batelado),
  sem instanciar `GuiGraphics` novo por frame nem usar `RenderSystem.polygonOffset` em modo
  imediato. Em batch, o custo por balão extra é quase só os vértices em si.
- **Uma passada por textura, nunca intercaladas** (`BalloonRenderer`: balão → enfeite → texto, cada
  uma varrendo todas as mensagens). `BufferSource#getBuffer` fecha o batch anterior sempre que outro
  `RenderType` pede o buffer compartilhado, então um `VertexConsumer` guardado por cima de um
  `getBuffer` alheio vira referência morta e estoura `IllegalStateException: Not building!`. Separar
  as passadas é o que torna verdadeiro o "custo por balão extra é quase só os vértices": todos os
  balões do jogador saem em um draw call por textura, em vez de um por mensagem.
- Mensagens vencidas são podadas no render (`aurorion_talk$pruneBalloons`), então um jogador fora
  da tela nunca custa nada — sem tick agendado, sem timer, sem trabalho até alguém olhar para ele.
- A geometria nine-slice do balão (`BalloonNineSlice`) é pura matemática, sem estado e sem
  alocação — compartilhada entre o render 3D e o preview 2D da GUI para as duas versões nunca
  divergirem visualmente.

### 4.3 Rede (o que trafega, e quando)

- **O estilo inteiro trafega — não um índice de catálogo —, mas só muda de mão quando o jogador
  realmente troca de estilo.** Sem polling, sem heartbeat.
- **Snapshot de login é O(jogadores online), não O(histórico do mundo).** Um mundo com milhares de
  jogadores que já passaram por ali não infla o pacote de ninguém — só quem está conectado agora
  entra no snapshot.
- Delta de mudança (`UpdateStylePayload`) é broadcast de uma entrada só, não o mundo inteiro de
  novo.
- **Validação de forma no servidor** (`BalloonStyle#isWellFormed`) antes de aceitar qualquer coisa
  vinda do cliente — não é otimização, é a contrapartida de deixar o cliente decidir o próprio
  cosmético: o servidor não pode confiar cegamente no que chega pela rede.

### 4.4 Descoberta de conteúdo (arte nova sem rebuild de infraestrutura)

- **Catálogo de texturas escaneado em runtime** (`BalloonCatalog`, via
  `SimplePreparableReloadListener`), não hardcoded. Adicionar arte é soltar um PNG na pasta —
  nenhum registro em código, nenhum banco de dados, nenhuma API externa. Reduz a distância entre
  "eu quero adicionar uma skin" e "ela existe no jogo" a zero infraestrutura nova.

### 4.5 O sussurro é decisão do servidor (a regra não pode morar no cliente)

- **Esconder a fala do HUD é do cliente; proibir `/w` é do servidor.** As duas metades parecem a
  mesma feature e não são: a primeira é preferência de quem instalou o mod, a segunda é regra do
  mundo. Deixar a segunda no cliente seria deixá-la valendo só para quem quisesse — e um jogador
  com o cliente limpo continuaria conversando fora de cena, que é exatamente o que o mod existe
  para impedir. Daí o segundo arquivo de config (`talk-server.toml`), e não um campo novo no do
  cliente.
- **Veto em `CommandEvent`, não mixin em `MsgCommand`.** O evento já existe, é o mesmo ponto que o
  `aurorion-personagem` e o `aurorion-limbo` usam para barrar comando, e não conflita com nenhum
  outro mod do pack que também mexa em chat. Um mixin no comando do vanilla seria superfície nova
  por nada.
- **A lista de comandos é config, e a comparação é pelo nome digitado.** `/tell` e `/w` são
  apelidos redirecionados de `/msg`, mas o que chega ao evento é a palavra que a pessoa escreveu;
  os três precisam estar na lista. Ser config é o que faz o veto alcançar o comando de sussurro de
  qualquer outro mod do modpack sem uma linha de código nova.
- **Recusar em silêncio é bug.** Cancelar um `CommandEvent` não devolve nada ao jogador: sem a
  mensagem explícita, o comando engolido é indistinguível de servidor travado.

## 5. aurorion-essentials — decisões (fakename)

Segundo mod do ecossistema; primeira feature é o **fakename** (nome exibido trocado em quase todo
lugar do jogo — chat, morte, conquista, join/leave, nametag, tab list).

### 5.1 Um único ponto de injeção, não uma caçada por mensagem

- **Mixin em `Player#getName()`**, não em cada mensagem individual. Chat, morte, conquista e
  join/leave em Minecraft vanilla já convergem para `getDisplayName()` (que chama `getName()`)
  precisamente para permitir esse tipo de override — é o mesmo ponto que mods de nickname
  (Bukkit, Forge) sempre usaram. Caçar e sobrescrever cada `Component.translatable` individual
  seria mais mixins, mais superfície de conflito com outros mods do modpack, e ainda deixaria
  buracos em qualquer mensagem vanilla nova que apareça em atualizações futuras.
- **`getScoreboardName()` deliberadamente não é tocado.** Scoreboard e seletores de alvo (`@p`,
  `@a[name=...]`) dependem dele para continuar funcionando com o nome real. Essa fronteira entre
  "nome exibido" (pode mentir) e "identidade técnica" (nunca mente) é o que sustenta o `/realname`
  como ferramenta de moderação — reverter a máscara é sempre possível porque a identidade real
  nunca deixou de existir por baixo.

### 5.2 Rede: só o necessário para o que é renderizado no cliente

- Chat, morte, conquista e tab list já são resolvidos **no servidor** antes de qualquer coisa ser
  enviada — o `Component` pronto (já com o nome falso) é que trafega, então não precisam de canal
  próprio.
- A nametag acima da cabeça é a exceção: o cliente renderiza lendo `getDisplayName()` da própria
  cópia local da entidade de **cada jogador visível**, não só a do dono da tela — por isso existe
  um snapshot no login (O(jogadores online), como no `aurorion-talk`) e um delta a cada troca.
- **Tab list exige refresh explícito.** O nome exibido nela vai embutido no
  `ClientboundPlayerInfoUpdatePacket` no momento do envio — sobrescrever o método não alcança quem
  já recebeu o pacote antigo, então trocar/limpar um nome falso reenvia esse pacote (só a ação
  `UPDATE_DISPLAY_NAME`, só para quem mudou, nunca a tab list inteira).

### 5.3 Validação no servidor (mesmo sendo "só cosmético")

- Nome vazio, texto puro maior que 48 caracteres, ou igual (sem diferenciar maiúsculas) ao nome
  real ou ao nome falso ativo de outro jogador online — todos rejeitados antes de qualquer
  escrita. A regra de impersonation é a mesma categoria de decisão da diretriz 5 da seção 7: um
  nome falso idêntico ao de outro jogador é o vetor mais óbvio de golpe/confusão que dá para
  cortar sem nenhum custo de complexidade.

### 5.4 Cleanup periódico (itens dropados + orbs de XP)

- **O contador de tick nunca aloca e nunca toca em entidade fora da hora certa.** O
  `CleanupScheduler` roda a cada tick do servidor, mas o trabalho de "espera" é só um incremento de
  `long` e duas comparações — o mesmo tipo de garantia de "zero alocação por tick" da seção 2, só
  que aplicada a uma feature nova em vez do caminho de chat/render do `aurorion-talk`.
- **O aviso é deliberadamente subutil: actionbar, uma vez por ciclo, nunca chat.** Chat brasileiro
  de 80 jogadores já é barulhento; um aviso que empilha no histórico ou repete a cada tick de
  contagem regressiva seria pior que a própria limpeza. Actionbar some sozinho e não interrompe
  nada.
- **A limpeza em si roda no máximo 1x por hora, nunca por tick.** Mesmo que remova milhares de
  entidades de uma vez (pico depois de uma fazenda AFK rodando a hora toda), o custo é O(entidades
  removidas) concentrado num único tick — infinitamente mais barato que checar isso continuamente.
  `discard()` (não `remove()`) é o que torna seguro remover durante a própria iteração das
  entidades da level, sem `ConcurrentModificationException`.
- **Escopo deliberadamente restrito a itens dropados e orbs de XP** — nunca mobs, veículos, ou
  qualquer coisa que um jogador colocou de propósito. Igual à meta da seção 2 de nunca interferir
  fora do escopo próprio: um "cleanup" que remove mais do que isso é fácil de errar e caro de
  debugar num modpack pesado com dezenas de outros mods manipulando entidades.

## 6. Conteúdo x comportamento — `aurorion-aeonita` e `aurorion-ethereal`

Dois mods que existem em par: um registra conteúdo reutilizável, o outro dá comportamento a esse
conteúdo.

### 6.0 Por que casas, cerimônia e placares são um mod só

`aurorion-ethereal` nasceu da fusão de dois mods que existiam separados: `aurorion-ato2` (casas e
altar) e `aurorion-placares` (placares holográficos). A separação original tinha um argumento
razoável — "a casa é mecânica removível do ato; o placar é infraestrutura permanente" — e ela estava
**errada na prática**, por um motivo que só apareceu quando os dois ficaram prontos:

> **O total de uma casa é a soma dos pontos dos membros dela.**

Isso torna casa e placar o mesmo dado visto de dois ângulos. Enquanto estavam separados, o placar
não tinha como saber a casa de ninguém, e o que sobrou foi um remendo: "casa", no placar, era um
**texto livre digitado na GUI**. As consequências eram todas silenciosas — dar ponto a um aluno não
mexia no placar da casa dele; renomear a casa no datapack deixava a linha do placar órfã; um erro de
digitação criava uma segunda "Casa Nyx" com pontuação própria.

As alternativas eram publicar uma API entre os dois mods, ou duplicar o registro de casas dos dois
lados. Fundir foi mais barato que qualquer uma das duas, e junto veio o efeito de a cerimônia poder
existir: ela precisa das casas *e* precisa registrar pontuação.

O que **não** mudou: a regra da seção 3 continua valendo entre mods. `aurorion-ethereal` não depende
de nenhum outro mod Aurorion além do `aurorion-core`, e continua ligável/desligável sozinho.

A promoção de "mod de ato" para infraestrutura permanente é o que autoriza este mod a registrar um
bloco (o placar), o que o `aurorion-ato2` tinha proibido a si mesmo — e pelo motivo certo: um mod
feito para sair do modpack não pode ser dono de nada que fique no mundo.

### 6.1 A fronteira: quem registra item/bloco, e quem só tem comportamento

- **O Altar de Seleção continua sendo do `aurorion-aeonita`, não deste mod.** Mesmo agora que
  `aurorion-ethereal` é permanente e registra o próprio bloco de placar, mover o altar para cá seria
  trocar o id dele e **orfanar todos os altares já construídos** no mundo. Um bloco muda de dono uma
  vez: quando é criado.
- **O acoplamento entre os dois é uma tag de bloco, nunca um import.** O `aurorion-ethereal` pergunta
  "o bloco clicado está em `aurorion_ethereal:house_altars`?", e a entrada do altar nessa tag é
  `"required": false`. Consequência direta da seção 3 (cada mod ativável/desativável isoladamente):
  desligar um dos dois não impede o outro de carregar. Consequência de brinde: qualquer bloco do
  modpack vira altar por datapack, e um ato futuro reaproveita o mesmo altar sem tocar em código.

### 6.2 Luz dinâmica (`aurorion-aeonita`) — a única parte que custa frame time

- **Roda por tick de cliente, então segue a regra de zero alocação da seção 2.** Colecões são campos
  reaproveitados (`fastutil` com chave `int` — sem boxing de `Integer` por entidade por tick), a
  posição é calculada num `MutableBlockPos` reaproveitado, e a varredura de entidades que sumiram —
  a única parte que aloca um iterador — só roda quando existe de fato entrada a remover, detectado
  por uma comparação de tamanhos que é válida porque o conjunto "visto" é sempre subconjunto do
  "ativo".
- **O custo que sobra é inerente à técnica e não foi escondido:** cada mudança de posição é um
  `setBlock` no nível do cliente, que refaz iluminação do chunk. Não dá para otimizar sem trocar de
  técnica. Por isso a feature tem chave de desligar própria (`aurorion_aeonita-client.toml`) — num
  modpack pesado, é o primeiro item a sacrificar por FPS, e desligá-la não tira nenhum item nem
  bloco do jogo.
- **O que dava para tirar do `setBlock`, foi tirado: os bits de atualização.** Era
  `Block.UPDATE_ALL`, que é `UPDATE_NEIGHBORS | UPDATE_CLIENTS`; hoje é
  `UPDATE_CLIENTS | UPDATE_KNOWN_SHAPE`. A luz troca de lugar várias vezes por segundo debaixo de
  quem anda com um item aceso, e cada troca mandava os blocos em volta reagirem a um bloco que só
  existe na cópia local do mundo — trabalho jogado fora e, na cascata de `updateShape`, uma chance
  real de deixar vizinho desenhado errado até o chunk recarregar. A iluminação continua correta: quem
  a recalcula é o motor de luz, chamado pelo próprio `setBlockState` quando a emissão muda, sem
  depender desses bits.
- **Quais itens acendem é dado (tag), não código.** Diretriz 4 da seção 7 aplicada: incluir o
  cristal de outro mod do modpack na luz dinâmica é editar um JSON, não recompilar.

### 6.3 Vinculação a uma casa (`aurorion-ethereal`)

- **Custo total da feature: um evento de clique, um pacote de ida, um de volta.** Não há ticker,
  timer nem varredura de jogadores em lugar nenhum — a vinculação é um evento raro (uma vez por
  jogador, na vida do personagem). Até o prazo da tela aberta é um `long` comparado no momento em
  que a escolha chega, não uma contagem regressiva: uma pendência vencida não custa nada até alguém
  tentar usá-la.
- **Nada trafega no login — nem snapshot.** O catálogo inteiro de casas vai junto com o pacote que
  abre a tela: O(casas), e só quando alguém clica num altar. Isso é mais barato que o padrão de
  snapshot-no-login do `aurorion-talk` e do `aurorion-essentials`, e é o que permite as casas serem
  datapack — o cliente não precisa ter os JSONs, não dá `/reload` junto e não tem como ficar
  dessincronizado do servidor.
- **Casa é conteúdo, então é datapack, não enum Java nem config.** Nome, cor, ícone, descrição e
  lotação vivem em `data/<ns>/aurorion/houses/*.json`. Trocar o nome de uma casa no meio do ato é
  editar arquivo e dar `/reload`, sem rebuild de jar e sem derrubar o servidor. Diretriz 4 de novo.
  Config (`allowRechoose`, `announceInChat`) é para **regra de servidor**, que é comportamento.
- **O id que vem do cliente passa por três validações antes de virar escrita.** Existe no catálogo?
  O jogador estava mesmo diante de um altar — mesma dimensão, menos de 8 blocos, dentro do prazo, e
  o bloco ainda está na tag? A casa ainda cabe mais gente? A permissão é gasta *antes* das outras
  checagens: uma tela aberta vale exatamente uma tentativa, certa ou errada, senão daria para varrer
  ids até achar casa com vaga. Diretriz 5 da seção 7, aplicada a algo que hoje parece só narrativo.
- **A lotação é reconferida na confirmação, não só na abertura da tela.** Com 80 jogadores entrando
  no ato ao mesmo tempo, duas pessoas disputando a última vaga é cenário real, não hipótese. É por
  isso que existe um pacote de veredito: fechar a tela no clique e torcer deixaria quem perdeu a
  corrida achando que entrou.
- **Casa removida do datapack não apaga o registro do jogador.** O id continua gravado e cada
  leitura trata "casa desconhecida" explicitamente. Alguém editando JSON com o servidor no ar não
  pode custar o histórico de ninguém — mesma fronteira do `getScoreboardName()` na seção 5.1: o dado
  de identidade nunca mente, mesmo quando a apresentação não consegue ser resolvida.
- **A tela quebra texto e monta os `ItemStack` dos ícones uma vez, na abertura.** Mesma lição da
  seção 4.2 — refazer `Font#split` a 60 fps é o gargalo clássico deste tipo de GUI.
- **Toda gravação de casa passa por um método só (`HouseManager.bind`).** Não é organização: é que o
  total de uma casa é a soma dos membros, então **entrar ou sair de uma casa muda o placar** mesmo
  sem nenhum ponto ter mudado. Deixar esse refresh a cargo de cada chamador seria a mesma armadilha
  que a seção 3.1 já tinha corrigido no `SavedDataAccess` — "quebra silenciosamente se quem usar
  esquecer da parte dele".

### 6.4 A Cerimônia de Vinculação

O site decide a casa; o servidor persiste o resultado recebido por comando da staff.
Cadastro e apresentação são ações independentes: `/casa definir` prepara, e
`/casa cerimonia <jogador> [casa]` dispara no palco. Não existe chamada HTTP ao site.
Uma casa cadastrada offline não produz animação ao entrar: o momento pertence à narração da staff.

O vínculo é gravado antes da cena e passa pelo mesmo `HouseManager` que atualiza os placares.
Interromper a apresentação nunca desfaz a atribuição. Há somente um rito ativo de cada vez.
A nova apresentação e seu orçamento de render/rede estão descritos na seção 12.11.

## 7. Diretrizes para os próximos mods do ecossistema

Ao adicionar um mod novo neste monorepo, os mesmos princípios se aplicam:

1. **Todo custo "por jogador" precisa justificar por que não é "por evento".** Se um mod precisa
   saber algo sobre todo mundo a cada tick, questione o design antes de escrever a otimização.
2. **Nunca cancele em pontos de injeção que participam de protocolo com estado** (assinatura de
   chat, handshake de configuração, etc.) — cancele o mais tarde possível, depois que o protocolo
   já terminou seu trabalho.
3. **Rede: pense em O(jogadores online), nunca em O(histórico) ou O(mundo inteiro).** Snapshot no
   login, delta depois.
4. **Prefira descoberta em runtime a registro estático quando o conteúdo é dado (arte, texto,
   config), não comportamento.** Comportamento é código; conteúdo não deveria exigir recompilar.
5. **Toda entrada vinda da rede é hostil até validada no servidor**, mesmo em decisões que hoje
   parecem "só estéticas" — cosmético hoje pode virar vetor de abuso amanhã (nomes ofensivos via
   textura, por exemplo, se algum dia a arte for enviada pelo jogador em vez de ficar no jar).
6. **Meça contra 80 jogadores, não contra 1.** Um teste local com um único jogador não expõe
   nenhum dos problemas que essas regras existem para evitar.

## 8. aurorion-portais — controle de acesso às dimensões

Primeiro mod escrito já sob as diretrizes da §7, e o primeiro cuja mecânica principal é uma
**restrição** e não uma adição. A regra do servidor: **toda dimensão exceto o overworld fica
trancada**, e só abre em janelas agendadas — os "trens" — ou por decisão explícita da staff.

### 8.1 Negar por padrão, com lista de exceções — nunca lista de alvos

- A config declara `freeDimensions` (só o overworld) e trata **todo o resto** como controlado. O
  inverso — listar as dimensões a trancar — parece equivalente e não é: num modpack pesado, cada mod
  novo instalado traria uma dimensão que **nasceria aberta sem ninguém notar**. Aqui ela nasce
  trancada, e o custo do engano é alguém reclamar que não consegue entrar, não um buraco silencioso.
- Consequência direta: "funciona com dimensão de mod" não exigiu uma linha de código por mod, nem
  uma lista de portais conhecidos para manter. Não há integração com mod nenhum.
- O preço honesto dessa escolha é que um mod que use dimensão própria para mecânica interna (uma
  mina, um minigame) precisa entrar em `freeDimensions` na mão. É para isso que existe `logDenials`:
  ligar, tentar, ler o log, whitelistar.

### 8.2 Um funil só para "trocar de dimensão"

- **Toda** a regra vive em `EntityTravelToDimensionEvent`, que o NeoForge dispara no topo de
  `ServerPlayer#changeDimension`. Portal do Nether, portal do End, `/tp` entre dimensões e teleporte
  de mod que use a maquinaria vanilla passam todos por ali. Caçar cada bloco de portal seria mais
  superfície de conflito e deixaria buraco em toda dimensão nova do modpack — mesmo raciocínio da
  §5.1 (um ponto de injeção, não uma caçada).
- Só jogador é barrado. Mob, item e projétil continuam viajando: ampliar isso mexeria em mecânica de
  mod de terceiro sem pedido (§2, nunca interferir fora do escopo próprio).

### 8.3 O mixin de portal é otimização, não regra

- `Entity#canUsePortal` é interceptado para barrar a travessia **antes** de o vanilla calcular o
  destino. O motivo é concreto: `NetherPortalBlock#getPortalDestination` varre o destino atrás de um
  portal existente e, não achando, **escava um portal novo** — tudo isso antes do
  `changeDimension` que o evento cancelaria. Sem o mixin, cada jogador encostando num portal fechado
  custaria uma busca pesada e deixaria um portal órfão no Nether, multiplicado pela população online.
- Ele é otimização e não regra porque **errar nele nunca libera nada**: `canUsePortal` só conhece a
  origem, então responde a uma pergunta mais fraca ("existe alguma viagem possível agora?"), e o
  evento da §8.2 continua sendo a palavra final. Por isso tem chave própria (`blockPortalsEarly`) —
  desligá-la deixa o servidor mais caro, nunca mais permissivo.
- Devolver `false` ali é um caminho que o vanilla já sabe tratar (é o mesmo de um jogador morto ou de
  carona): o contador do portal só decai, sem estado inconsistente. Diretriz 2 da §7 aplicada.

### 8.4 A simetria entrada/saída *é* a mecânica

- Sair de uma dimensão controlada exige a mesma janela que entrar nela. Não é um detalhe de
  implementação: se só a entrada fosse controlada, voltar ao overworld (que é livre) seria sempre
  permitido e a aventura não teria risco nenhum. É essa simetria que faz quem perdeu a hora do trem
  ficar do lado de dentro.
- **Morrer não é bilhete de volta.** Sem isso a mecânica teria uma porta dos fundos óbvia: quem
  perdeu o trem se joga na lava e reaparece no spawn do mundo de graça. `PlayerRespawnPositionEvent`
  redireciona o respawn para dentro da própria dimensão.
- **Nenhuma exceção embutida** — nem a do End. O portal de retorno depois do dragão passa pelo mesmo
  evento, com `isFromEndFight()`, e é deliberadamente tratado como qualquer outro respawn: o jogador
  vê os créditos e acorda no End. Um mod cuja regra é "eu decido quem sai" não pode ter uma rota de
  saída que o próprio mod concede sozinho.
- O respawn tem ordem de preferência declarada: estação de desembarque do datapack → vão seguro na
  coluna onde morreu → spawn da dimensão. A primeira é a única que o servidor *sabe* que é boa,
  porque quem construiu a estação garantiu — por isso vale sempre declará-la.

### 8.5 Relógio: `long` comparado por segundo, calendário só nas transições

- Um tick de espera — o estado de 99,99% do tempo — custa **duas comparações de `long`** por linha e
  mais nada. Toda a aritmética de calendário (`ZonedDateTime`, que aloca) acontece só quando uma
  janela fecha e a próxima precisa ser descoberta: algumas vezes por semana. Mesma garantia do
  `CleanupScheduler` da §5.4, aplicada a um agendador bem mais complexo.
- O cursor de avisos só anda para frente. É o que garante que um servidor que travou um minuto volte
  **pulando** os avisos vencidos, em vez de despejá-los em rajada no chat de 90 pessoas.
- **Tempo real com fuso explícito, não tempo de jogo.** "Sábado às 20:00" precisa cair no sábado às
  20:00 da comunidade; o dia do Minecraft dura 20 minutos e para com `doDaylightCycle false`. O fuso
  fica no datapack porque o padrão da JVM é o do provedor de hospedagem, que quase nunca é o dos
  jogadores.
- `anyOpen` é um `boolean` mantido nas transições, não uma varredura por consulta: é a pergunta que a
  checagem de portal faz no caminho quente.

### 8.6 Horário é conteúdo; regra de servidor é config

- Nome da linha, dimensões, dias, horários, duração da janela e estações vivem em
  `data/<ns>/aurorion/linhas/*.json`. Mudar o dia da partida no meio da temporada é editar arquivo e
  dar `/reload`, sem rebuild de jar e sem derrubar o servidor — diretriz 4 da §7, igual às casas do
  Ato 2.
- A config guarda o que é **regra**: quem escapa, o que é livre, como o servidor avisa. `enforce` é o
  botão de emergência — libera tudo na hora, sem apagar linha nem passe, para quando um conflito de
  modpack aparecer no meio do horário de pico.

### 8.7 O passe é o encaixe do item que ainda não existe

- `TransitPass` (dimensão + validade + usos) já existe e já é gravado por UUID, mesmo sem nenhum item
  que o conceda. O item de "abrir portal" planejado só vai precisar chamar `PassData#grant` — sem
  saber nada sobre horário, linha ou relógio.
- Vale nos dois sentidos de propósito: o passe que tira alguém do Nether é o mesmo que põe. É o único
  jeito de ele funcionar como **resgate**, que é o caso de uso que importa para quem perdeu o trem.
- **Autorizar não é consumir.** `EntityTravelToDimensionEvent` ainda pode ser cancelado por outro mod
  depois que o passe foi validado; por isso a autorização dura só até o fim do tick e o uso é debitado
  apenas em `PlayerChangedDimensionEvent`, quando a troca correspondente já aconteceu. Viagem
  cancelada nunca gasta passe nem deixa estado pendente para uma viagem futura.

## 9. aurorion-vidas — vidas limitadas e exílio

Cada jogador tem um número fixo de vidas; cada morte gasta uma; zerar leva ao **exílio** numa
dimensão da qual não se sai sozinho. O exílio só tem peso porque o `aurorion-portais` mantém o
Nether trancado — e é justamente aí que está a decisão de design que importa.

### 9.1 Dois mods que dependem um do outro sem se conhecerem

Não há import, nem dependência declarada, entre `aurorion-vidas` e `aurorion-portais`. Eles se
coordenam por **eventos do vanilla**, cada um agindo pelo seu próprio motivo:

- **A ida para o exílio** usa `PlayerRespawnPositionEvent`, que não passa por `changeDimension`. O
  portão do outro mod nem chega a ser consultado — a ida simplesmente não é uma "viagem". Isso
  evitou ter que abrir no `aurorion-portais` uma exceção de "teleporte de sistema", que seria
  exatamente o tipo de buraco que a §8.4 diz que ele não pode ter.
- **A volta** é vetada por um listener de `EntityTravelToDimensionEvent` do próprio
  `aurorion-vidas`. Os dois mods cancelam o mesmo evento sem saber um do outro: o `portais` porque a
  janela está fechada, o `vidas` porque a pessoa está exilada.

Consequência prática: cada um funciona sozinho. Sem o `portais`, o exílio continua acontecendo — só
fica mais fraco, porque o exilado sai pelo primeiro portal que achar.

### 9.2 Prioridade de evento como árbitro, não uma checagem cruzada

Os dois mods têm listener em `PlayerRespawnPositionEvent`: o `portais` para prender quem morreu
dentro de dimensão trancada, o `vidas` para exilar quem zerou. Quando os dois se aplicam (morrer no
End sem vidas), quem decide é a **prioridade**: o `vidas` roda em `LOW` e sobrescreve.

A alternativa seria um dos dois perguntar ao outro o que fazer — que é o acoplamento que a §6.1 já
tinha rejeitado no par aeonita/ato2. Prioridade de evento é um mecanismo que o próprio loader
oferece para exatamente isto: a regra mais específica fala por último.

O listener de morte também é `LOW`, por outro motivo: qualquer mod que **cancele** a morte
(ressurreição, totem custom) age primeiro e o nosso nem é chamado — `@SubscribeEvent` não entrega
evento já cancelado. Cobrar vida de uma morte que não aconteceu seria o bug mais caro possível num
sistema de vidas limitadas.

### 9.3 Quem nunca morreu não ocupa espaço

Jogador sem entrada no mapa **é** um jogador com vidas cheias — a leitura devolve o máximo da
config. Num mundo com anos de histórico, o arquivo cresce com o número de pessoas que perderam vida,
não com o total de visitantes. Mesma família de decisão da §4.3 (snapshot é O(jogadores online),
nunca O(histórico do mundo)).

O **ponto** de exílio, por outro lado, mora no save e não na config: é uma coordenada de mundo, tem
que acompanhar o que foi construído e sumir junto quando o mundo for trocado. Config que guarda
coordenada envelhece mal. Quando ninguém marcou um ponto, o mod procura um lugar seguro uma vez,
**grava** o resultado e avisa no log — assim todos os exilados chegam no mesmo lugar, o que também é
melhor de jogo: a chegada vira um lugar reconhecível em vez de gente espalhada pelo Nether.

### 9.4 HUD: ancorado no vanilla, não em pixels

A fileira de vidas é uma camada registrada **acima de `FOOD_LEVEL`**, e usa o mesmo par de
referências que o vanilla usa para empilhar o canto direito (`guiWidth()/2 + 91` e `Gui#rightHeight`),
incrementando `rightHeight` ao terminar. É isso que faz a fileira acompanhar a barra de fome quando
algo entra ou sai da pilha — montar num cavalo, bolhas de ar — em vez de sobrepor.

Entrar no grupo `playerHealthComponents` (onde o `FOOD_LEVEL` vive) dá de graça dois comportamentos
certos, sem uma linha de código: some em criativo/espectador junto com vida e fome, e some com o HUD
escondido no F1.

Custo por frame: nenhuma alocação e no máximo `maxLives` blits de 9×9 — e **nada** enquanto o
servidor não tiver informado as vidas (§2, zero alocação no caminho quente do cliente).

### 9.5 Rede: o único mod do ecossistema sem snapshot de login

`aurorion-talk` e `aurorion-essentials` precisam de snapshot no login porque o cliente renderiza
coisas de **outros** jogadores (balão, nametag). Aqui não: ninguém desenha a vida de outra pessoa.
O payload é dois inteiros, só para o dono da tela — O(1) de verdade, que nem cresce com a população.

O máximo viaja junto com o valor em vez de ser lido da config no cliente: config de servidor não
existe no cliente, e um cliente que adivinhasse o máximo desenharia a quantidade errada de ícones. E
`max = 0` é o estado "o servidor ainda não falou" — o HUD não desenha nada em vez de chutar.

### 9.6 A arte é dado, não código

Os ícones são sprites de GUI (`textures/gui/sprites/hud/`). Trocar a arte é substituir dois PNG —
dá para fazer por resource pack, sem tocar no jar e sem recompilar. Diretriz 4 da §7 aplicada ao
HUD: comportamento é código, aparência não deveria exigir build.

## 10. O Projetor Aeônico (`aurorion-ethereal`)

O projetor não é dono de nenhum número: ele é a **vitrine** dos dados que as casas e a cerimônia já
mantêm. Também não entrou no `aurorion-vidas`, porque aquele mod decide quantas vidas restam — não é
dono de estatísticas nem de apresentação pública. Por que ele vive no mesmo jar que as casas está na
seção 6.0.

### 10.0 O corpo é modelo de bloco; só o painel é renderizador

No mod de referência o projetor inteiro era um modelo animado desenhado por frame (GeckoLib). Aqui a
peça foi partida em duas:

- **O corpo** é o mesmo modelo — o export do Blockbench que já vinha no jar — mas como modelo JSON de
  bloco. O chunk o desenha em batch junto com o resto do mundo e ele não custa nada por frame.
- **O painel** continua no `BlockEntityRenderer`, porque ele é vértice puro e não existe outro jeito.

O que se perdeu foi a rotação do cristal. O que se ganhou foi o mod não passar a exigir uma
**dependência obrigatória** nova de todo cliente e todo servidor do pack — e num modpack pesado cada
dependência obrigatória é mais uma peça que pode travar a atualização do conjunto.

### 10.1 Atualização por evento, nunca por tick

- Mortes e vitórias PvP são atualizadas somente em `LivingDeathEvent`. “Duelista” começa com um
  critério verificável e sem heurística: um jogador matou outro jogador. Arenas futuras podem trocar
  a fonte sem mudar o bloco ou o renderer.
- Pontos de casas e jogadores mudam por ação explícita da staff (`/pontos`), e a contagem de missões
  idem. Não há polling, ticker de bloco nem varredura de jogadores.
- Só block entities em chunks carregados ficam no conjunto de atualização. Um evento raro percorre
  O(projetores carregados), não O(mundo), e somente projetores da meta afetada recebem novo snapshot.
- **Isto é o que o mod de referência não fazia.** Lá, cada projetor tinha um `serverTick` comparando
  um contador de geração uma vez por segundo: um projetor que ninguém estava olhando custava tick
  igual, e o custo crescia com a quantidade de projetores construídos no mundo.

### 10.2 Snapshot pronto para renderizar

Ordenação e formatação rodam no servidor quando o dado muda. O bloco sincroniza no máximo as cinco
linhas visíveis já formatadas — **texto e cor**; o cliente não recebe histórico nem tabela completa
para desenhar o holograma. A cor viaja junto porque o cliente não tem os datapacks: para ele, "casa"
não existe como conceito, então ele nunca poderia derivar a cor de uma linha sozinho.

A linha é um `Component` e não texto pronto: "pts", "missões" e "mortes" são palavras, e montadas como
string no servidor sairiam no idioma do *servidor* para todo mundo. O original as tinha fixas em
português dentro do código.

No caminho por frame, o renderer mede e emite o snapshot, e desenha a moldura viva. **Esse desenho é
o único custo por frame deliberado do ecossistema** — é o que o bloco existe para fazer. Ele é
limitado por dois cortes: o corpo do projetor saiu do renderer (seção 10.0), e a parte animada do
painel (varredura, faíscas, runas) desliga a partir de 26 blocos, onde ninguém consegue lê-la mas
todo mundo pagaria por ela.

Largura e altura são escalas independentes da matriz, editadas pela mesma tela que escolhe a meta.

### 10.3 Duas moedas, não uma

`/pontos aluno` mexe no ponto do jogador, que conta duas vezes (ranking de alunos **e** total da casa
dele). `/pontos casa` mexe num **bônus** da casa, que existe para premiar ou punir a casa inteira sem
mentir sobre o que cada aluno fez. Um número só não daria para fazer as duas coisas: somar ao aluno
para representar um mérito coletivo inventaria histórico individual que não aconteceu.

### 10.4 Rede hostil e autoridade da staff

Todo pacote de configuração reconfirma três condições no servidor: permissão nível 2, distância
máxima de oito blocos e existência de uma block entity do tipo correto naquela posição — **a cada
pacote**, e não só na abertura da tela: uma tela aberta continua aberta depois de o jogador andar
para longe, ou depois de perder o cargo. Meta e dimensões do painel são limitadas novamente no
servidor; a tela nunca é autoridade.

Dar e tirar ponto ficou fora do bloco, no `/pontos`, como no mod de referência. Isso também apagou o
único pacote do mod que crescia com a população do servidor: a tela de ajuste em massa mandava uma
linha por jogador já visto.

## 11. aurorion-mundos — mais de um overworld

Mod de **dimensão**: vários overworlds, cada um com o mesmo gerador e os mesmos mods de worldgen, mas
com seed, barreira e destino de portal próprios. É o primeiro mod do ecossistema cujo requisito
central não é gameplay nem restrição, e sim **operação**: o terreno é pré-gerado fora e importado,
porque worldgen ao vivo com 90 pessoas é justamente o que não pode acontecer.

As dimensões não estão em código — são JSON apontando para o preset `minecraft:overworld` e para as
noise settings `minecraft:overworld`. Isso é o que faz os mods de worldgen do modpack valerem nelas
**sem uma linha de integração por mod**: eles injetam no preset, e o preset é o mesmo. Mesmo
raciocínio da §8.1 — não há lista de mods a manter, porque não há lista.

O preço honesto: mod que filtra por dimensão no próprio código (`if (level.dimension() == OVERWORLD)`)
não aplica. Não há desenho que resolva isso do lado de fora, e a pré-geração expõe cedo.

### 11.1 Seed é config, contra a diretriz 4 da §7 e por causa dela

A §7.4 manda preferir datapack a código quando o dado é conteúdo. O seed **parece** conteúdo e não é,
por um motivo que só aparece quando se pergunta o que acontece num `/reload`: o terreno já gerado não
recarrega junto. Um seed que mudasse com o servidor no ar deixaria o mundo costurado com dois mapas,
e a emenda seria permanente.

Config tem exatamente a propriedade que falta ao datapack aqui — **é lida uma vez, no boot**. E a
janela serve: `DedicatedServer#initServer` carrega a config SERVER (linha 191) antes do `loadLevel()`
(linha 193) que chama `createLevels`. O dado está pronto quando a primeira dimensão é construída.

O segundo motivo é operacional e decidiu o desenho: **o seed não é derivado do seed do mundo.** A
alternativa óbvia (`worldSeed ^ hash(id)`) obrigaria a máquina de pré-geração a ser uma cópia do save
de produção. Com um número declarado, o que viaja entre as máquinas é uma linha de TOML.

**Ter seed declarado é o que torna uma dimensão nossa** — não há segunda lista de "dimensões
gerenciadas", porque duas listas que precisam concordar são duas chances de discordarem. A
consequência que importa num modpack pesado é a inversa: dimensão sem seed declarado não é tocada
por nada deste mod, nem no terreno nem na barreira (§2).

### 11.2 Um ponto de injeção para o terreno, dois para o cliente concordar

`ServerLevel#getSeed()` é o funil: `ChunkMap` é o único consumidor dele para as três coisas que
definem o terreno — `RandomState` (relevo e biomas), `ChunkGeneratorStructureState` (estruturas) e a
semente de decoração por chunk. Um `@Inject` ali resolve o mundo inteiro.

O segundo ponto existe porque **o `BiomeManager` tem seed próprio**, e os dois lados o obtêm por
caminhos diferentes: o servidor recebe o dele por parâmetro de construtor, derivado do seed do
*mundo*; o cliente monta o seu com `BiomeManager.obfuscateSeed(level.getSeed())`. Corrigir só o
primeiro faria a cor de grama do cliente deixar de bater com o bioma que o servidor calcula. A troca
acontece no `RETURN` do construtor, e não no parâmetro: injetar em argumento de construtor é bem mais
frágil, e no `RETURN` o objeto já está inteiro e a substituição é uma atribuição de campo.

Custo: frio. Uma consulta por nível criado, uma por chunk gerado, uma por troca de dimensão.

### 11.3 A barreira: remover o delegado é trava, não disciplina

`createLevels` pendura um `DelegateBorderChangeListener` da barreira do overworld em toda dimensão, e
`PlayerList#sendLevelInfo` manda `server.overworld().getWorldBorder()` **ignorando o `level` que
recebe como parâmetro**. São duas amarras, uma no servidor e uma na rede.

A solução barata seria "não mexa na barreira do overworld". Ela é disciplina de quem usa, e
disciplina falha em silêncio (§3.1). Duas coisas furam a promessa sem ninguém notar: o `applySettings`
que o próprio `createLevels` chama **depois** de registrar os delegados, e qualquer `/worldborder` da
staff. Remover o delegado no boot torna o vazamento impossível em vez de improvável — custa dois
mixins de acessor, que não acrescentam bytecode a método nenhum.

Na rede, a correção é por evento e não por mixin: um `ClientboundInitializeBorderPacket` depois do
vanilla, em três eventos raros. O pacote é absoluto, então o último a chegar vence, e o NeoForge
dispara os três depois do `sendLevelInfo` — `placeNewPlayer` (233 antes de 275), `respawn` (497 antes
de 505) e `changeDimension` (927 antes do fire). Um mixin no `PlayerList` seria um ponto de conflito
a mais num modpack pesado por nenhum ganho.

Persistir é obrigatório porque **o vanilla só salva uma barreira**, a do overworld no `level.dat`.
E quem grava é o próprio listener, não quem chama: assim qualquer caminho que mude a barreira fica
salvo, sem ninguém precisar lembrar de gravar.

### 11.4 O destino é datapack; o círculo é o que dá topologia

Um portal do Nether carrega **um destino por dimensão de origem**: a pergunta que o vanilla faz é "de
onde você veio", nunca "por qual porta". Com três mundos isso daria uma corrente — do primeiro ao
terceiro, passando pelo segundo.

A saída considerada e descartada foi bloco de portal próprio: exigiria registro de conteúdo (que pela
§6.1 nem moraria neste mod), acendedor próprio, textura, e uma mecânica nova para o jogador aprender.
A `area` resolve sem nada disso — a ligação vale para portais dentro de um círculo declarado, e um
mundo passa a ter várias saídas. Custo: varrer uma lista de uma ou duas entradas e uma distância ao
quadrado, só no instante da travessia.

Círculo ganha de ligação sem área **independente do `order`**. Deixar a ordem decidir faria a
topologia dos mundos depender de um campo que existe para ordenar listagem de comando.

Sem ligação declarada, o mixin devolve o controle ao vanilla no primeiro `return`. Nether e End
continuam exatamente como eram — o que importa porque este é o método por onde passa o portal de todo
jogador do servidor.

### 11.5 Raio 16 em vez de 128

A única otimização deste mod que dá para medir antes de produção, e ela é grande.

`PortalForcer#findClosestPortalPosition` usa raio **128** sempre que o destino não é o Nether. Dentro
de `PoiManager#ensureLoadedAndValid` isso vira `SectionPos.aroundChunk(chunk, 8, …)`: **17×17 chunks
e cerca de 6.900 seções de POI, por travessia**.

O raio 128 existe porque o Nether comprime coordenadas 8:1 e a chegada cai longe do esperado. Entre
dois mundos de `coordinate_scale 1.0`, `getTeleportationScale` devolve 1 e o destino cai **na mesma
coordenada** — 128 não compra nada e custa tudo. Com 16 são 9 chunks e 216 seções: **~32× menos chunk
tocado**, multiplicado pela população online num horário de trem.

Fica configurável por ligação, para o dia em que algum mundo usar escala diferente de 1.

### 11.6 A trava de geração, e a trava que realmente segura

Não achando portal do outro lado, o vanilla **cava um** — `createPortal` varre um espiral de 16
blocos perguntando a altura do terreno, e perguntar a altura de um chunk que não existe **gera esse
chunk**, na thread do servidor.

Por isso `allowRuntimeGeneration` é vazia por padrão, na mesma lógica de negar-por-padrão da §8.1:
uma dimensão nova nasce sem permissão de gerar. Travessia sem portal de chegada é erro de operação —
um portal que a staff esqueceu de construir — e a resposta certa a erro de operação é uma mensagem,
não um pico de worldgen no horário de pico.

**Mas a trava forte é a barreira.** Borda dentro da área pré-gerada significa que não há para onde
andar, logo não há chunk novo para gerar. As duas features pedidas acabaram se sustentando uma na
outra: a barreira por dimensão é o que transforma a pré-geração de intenção em garantia.

Isso só é operável porque cada dimensão custom vive numa pasta autocontida —
`dimensions/<namespace>/<path>/`, com `region`, `entities`, `poi` e `data`. Gerar fora, zipar e
soltar no save de produção é copiar uma pasta.

### 11.7 Outra dupla que não se conhece

`aurorion-mundos` decide **para onde** um portal leva; `aurorion-portais` decide **quando** se pode
atravessar. Não há import nem dependência declarada entre eles — o mesmo arranjo da §9.1.

E aqui ele sai literalmente de graça: a regra de negar-por-padrão da §8.1 já trata toda dimensão fora
de `freeDimensions` como trancada, então **um mundo novo nasce trancado sem uma linha de código**. A
ordem ainda ajuda no caminho quente: o `PortalEntryMixin` barra em `Entity#canUsePortal`, que o
vanilla consulta **antes** de `getPortalDestination` — fora do horário, a travessia custa duas
comparações de `long` e a busca de portal da §11.5 nem chega a rodar.

## 12. aurorion-limbo — prazo, saída solitária e auditoria

O escopo aprovado para esta etapa é a Porta do Esquecido: nas últimas cinco horas, quem não teve
tentativa de resgate pode sair após caminhar uma distância sorteada entre 1000 e 2000 blocos. A
saída devolve uma vida e fica registrada para a staff. Ritual, Oráculo e consequências de prazo
vencido continuam fora desta implementação.

### 12.1 Relógio persistente, sem trabalho por jogador por tick

- O listener de tick só incrementa um contador. Uma vez por segundo percorre o mapa dos exilados;
  com o mapa vazio não cria listas nem percorre jogadores online.
- O restante do prazo é salvo em NBT. A diferença vem de `System.nanoTime`, não do relógio civil,
  e a referência pertence apenas à sessão atual: reiniciar não cobra manutenção. Cada passo
  desconta no máximo cinco segundos para limitar o efeito de travamentos longos.
- Consumo do prazo chama `setDirty`: isto agenda persistência no autosave, sem escrever o arquivo
  a cada segundo. Sem essa marcação, reiniciar restaurava um prazo antigo.
- Vencimento possui confirmação persistida, inclusive quando `/limbo prazo` define zero. Avisos
  avançam o cursor inteiro e enviam apenas uma mensagem, mesmo atravessando várias faixas.
- Caminhada lê as estatísticas vanilla (andar, correr e agachar). Sorteio e ponto inicial são
  persistidos; relogar não troca o alvo.

### 12.2 Travessia só é saída depois de acontecer

O Limbo depende de Vidas, em uma direção apenas: `limbo -> vidas`. Portais continua independente,
coordenado pelo evento de viagem. A autorização do retorno vale somente para o jogador, origem,
destino e tick da chamada; o `finally` a limpa mesmo sem evento.

Resgates e travessias são recolhidos durante a varredura e aplicados depois da iteração. Para a
Porta, uma vida é concedida provisoriamente e revertida se `changeDimension` falhar. Registro,
contador de saídas e auditoria só mudam após confirmar a chegada. Sem local seguro no overworld,
a viagem é adiada; não se usa um spawn potencialmente letal como fallback.

`/vidas dar` passa a concluir o retorno ao overworld. Se o jogador estiver offline, o registro
permanece pendente, sem consumir prazo, até o login. Não se carrega mundo para procurar jogador
offline. Tentativas caras de gerar a Porta ou retornar têm intervalo mínimo de dez segundos
quando falham.

### 12.3 Moldura temporária e orçamento de chunks

A busca da Porta tem raio seis, intervalo vertical limitado e só consulta chunks já carregados.
Toda a área 3×4 precisa estar livre e dentro da borda. A moldura guarda posição, orientação,
material e dimensão; a remoção alcança somente suas dez bordas, preservando blocos de outro tipo.
Não se limpa uma região por comparação com a config atual.

Molduras removidas por resgate, vencimento, tentativa ou mudança de prazo usam o mesmo caminho.
Se um chunk estiver descarregado, uma pendência persistida espera por ele; a verificação roda uma
vez por minuto e nunca força carga. Saves antigos sem orientação/material mantêm a decoração,
pois não há informação suficiente para apagá-la sem risco a construções vizinhas.

### 12.4 Controle da staff

O formato RCON `v1` preserva as chaves existentes. `retorno_pendente` identifica resgate aguardando
login/teleporte. Nomes de quem saiu sozinho são guardados junto ao histórico para não depender da
permanência do perfil no cache. Ajustes de prazo e tentativas também entram na auditoria.

A consulta de auditoria lê o arquivo do fim para o começo em blocos de 8 KiB, limitada à cauda
recente, sem carregar o histórico da temporada. Gravação JSONL ocorre apenas em eventos. HTTP
roda em uma thread própria, com fila de 64, timeout e cliente pertencente à sessão; desligar o
servidor interrompe a fila e fecha o cliente. O timestamp pertence ao evento, não à entrega.

`/limbo retornar <jogador>` recupera somente jogadores online que estejam fisicamente no Limbo sem
estar exilados. É a saída operacional para alguém levado por uma passagem de versão antiga ou por
outro teleporte indevido. O comando usa a mesma busca de chegada segura dos resgates, não altera
vidas, recusa exilados e registra `RETORNO_ADMIN`; portanto não fabrica um resgate no histórico.

### 12.5 Validação

Testes JUnit cobrem prazo, autosave, migração de NBT, avisos, caminhada, identidade da moldura,
leitura de auditoria e a regra de reenvio do painel — inclusive o caso negativo, que é o que protege
a garantia de não haver pacote por segundo (§12.6) de uma regressão silenciosa.
`:aurorion-limbo:runGameTestServer` exercita o ciclo com dois jogadores
simulados e os mods Vidas e Portais carregados: travessia simultânea, cancelamento, tentativa de
resgate, vencimento único, relatório RCON e retorno pela staff (inclusive após reconectar).
O preset de teste usa um tipo e uma camada plana exclusivos do namespace de testes. Assim o ciclo de
exílio continua determinístico e barato no CI, enquanto a validação do worldgen de produção fica a
cargo do carregamento de datapack e de uma inspeção no cliente. Classes, tipo e estruturas de teste
não entram no jar de distribuição. Uma execução explícita com `-PlimboRealWorldgen` conserva o
gerador de produção no preset e cobre a criação de chunks quando o perfil de terreno muda.

### 12.6 Apresentação: três camadas, e nenhuma delas obrigatória

O Limbo é a primeira mecânica do ecossistema em que **não ver um aviso custa o personagem**. Isso
muda o que a camada de apresentação precisa garantir: não é estética, é a diferença entre uma pessoa
saber que tem seis horas e não saber.

- **Chat é o fundo do poço, não o normal.** Com 80 jogadores o chat é mangueira de incêndio, e o
  `aurorion-talk` existe justamente para tirar fala do HUD de chat (§4.1). Um aviso de morte
  definitiva não pode disputar espaço com "vendo diamante".
- **Três camadas, escolhidas uma vez no boot**: `ImmersiveNarrator` (se o mod estiver no pack) →
  `NativeNarrator` (o padrão, desenhado pelo próprio mod) → `ChatNarrator`. Cada uma **herda** da de
  baixo, então uma entrega que falhe cai para a seguinte pela chamada ao `super` — silêncio é a pior
  falha possível aqui.
- **Nenhum import de mod de terceiro.** O Immersive Messages entra por reflexão resolvida uma vez na
  carga da classe. A alternativa seria o jar dele num `libs/` — binário no git e build que quebra na
  máquina de quem não copiou o arquivo, por um mod que o design trata como opcional. É a §9.1 ("dois
  mods que dependem um do outro sem se conhecerem") aplicada para fora do ecossistema.

**O painel não custa um pacote por segundo.** O relógio do exilado não trafega: o servidor envia
snapshot só quando a *etapa* muda — caiu, armou, a Porta apareceu, o prazo venceu — e o cliente conta
os segundos sozinho, com um reenvio por minuto para corrigir deriva. Um pacote por segundo por
exilado seria o único ponto do mod com tráfego proporcional a **tempo** em vez de a **evento**, que é
exatamente o que a diretriz 3 da §7 proíbe.

Quem decide reenviar compara a etapa já enviada com a atual, em vez de chamar o envio em cada
transição. A diferença não é de estilo: **uma transição nova, acrescentada depois, não nasce
esquecida**. Login, respawn e troca de dimensão invalidam o que a tela sabia — e a troca de dimensão
é a única que cobre quem saiu do mapa de exilados (resgatado ou atravessado), porque a partir daí
nenhuma volta da varredura passa por essa pessoa de novo.

Partículas, névoa e interpolação do relógio são locais ao cliente: não geram tráfego, respeitam a
configuração de partículas do jogador e só desenham perto da Porta do próprio dono.

**A fonte é dado.** A Cinzel entra por provider `ttf` do vanilla, sem depender de Caxton nem de mod
de tipografia, e é seguida pelas referências vanilla — são elas que resolvem acento e CJK que a
Cinzel não tem, e sem elas um nome fora do alfabeto latino viraria caixinha no pior momento
possível. Trocar a fonte é substituir o arquivo, inclusive por resource pack (§9.6). A OFL vale para
o arquivo, não para o código que o referencia; ver `CREDITS.md`.

### 12.7 Morte atômica, Cinematic Respawn e PlayerRevive

Uma exceção dentro do listener de `LivingDeathEvent` é especialmente cara: a vida já pode estar em
zero, mas o vanilla ainda não terminou `die()`. O resultado é uma entidade viva com zero de saúde,
HUD tremendo e estado persistido ao reconectar. Por isso a abertura do registro do Limbo contém
falhas de apresentação, auditoria e compatibilidade; a morte sempre continua, e login/respawn
reconstroem qualquer registro que tenha ficado incompleto.

O Cinematic Respawn controla câmera, esconde a tela de morte e solicita o respawn alguns segundos
depois. Na última vida isso disputa exatamente o trecho em que Vidas troca o destino para outra
dimensão. Um mixin de cliente `@Pseudo` o desliga apenas enquanto `ClientLives == 0` e o jogador está
morto: a tela vanilla conclui o respawn, e a apresentação do Limbo começa na chegada. Mortes comuns
continuam cinematográficas; sem o mod de terceiro, o mixin não tem alvo e desaparece.

O PlayerRevive cancela a morte em prioridade `HIGHEST`; Vidas cobra em `LOW`. Essa ordem define a
regra sem chamada entre os dois: ser reanimado não gasta vida, enquanto desistir ou sangrar até o fim
gera uma morte não cancelada e gasta uma. Depois de chegar a zero, um mixin `@Pseudo` faz
`isReviveActive` devolver falso para o exilado, impedindo uma segunda camada de quase-morte dentro do
Limbo. Login e respawn reconciliam por reflexão qualquer estado de sangramento que tenha sobrevivido
a uma queda interrompida. As reflexões são resolvidas uma vez e só rodam nesses eventos raros.

### 12.8 Ambiente do Limbo: floresta, escuridão e custo de geração

O vazio plano dá lugar a relevo, cavernas, aquíferos e minérios do Overworld, cobertos por um bioma
próprio de floresta escura. A dimensão continua sendo conteúdo de datapack: o gerador de ruído copia
o perfil vanilla de 1.21.1, troca somente a regra de superfície por podzol e terra infértil e fixa o
bioma `aurorion_limbo:limbo_forest`. A vegetação parte da seleção da floresta escura, com 24 tentativas
por chunk em vez de 16. Ruínas construídas pela staff não recebem manutenção posterior; features só
rodam quando um chunk nasce.

O céu usa os efeitos do End, o tempo fica travado em 18000, não existe luz do céu e a luz ambiente é
zero. O bioma conserva fauna, monstros, cavernas e recursos vanilla para que seja possível sobreviver,
mas não recebe estruturas naturais: as ruínas são pontos de narrativa feitos no próprio mundo.

**Custo:** ruído, cavernas e árvores tornam chunk novo muito mais caro que o flat anterior. O servidor
de produção deve pré-gerar a área de resgate e a faixa provável da Porta com Chunky antes de abrir o
Limbo. Não existe varredura de blocos nem modificação recorrente do terreno. Chunks antigos continuam
planos até a dimensão ser recriada ou a exploração alcançar terreno novo.

Darkness I é parte da regra da dimensão. Ela é aplicada na entrada, reconciliada na varredura de um
segundo já existente apenas sobre a lista de jogadores da dimensão e sua remoção ou expiração é
cancelada enquanto o jogador está no Limbo; leite,
comando e outro efeito não abrem uma janela clara. A marca do efeito fica no NBT do jogador para que
a saída remova apenas a escuridão criada pelo ambiente, inclusive depois de reiniciar o servidor.

O terror sonoro combina o `mood_sound` vanilla do bioma com uma ocorrência espacial de monstro a cada
18–42 segundos por jogador presente. O agendamento mantém somente um `long` por UUID e é limpo na
saída; não há busca de blocos, spawn artificial nem pacote por tick. Monstros reais continuam vindo
da tabela de spawn normal do bioma e do limite global do servidor.

### 12.9 Morte definitiva: conta, personagem e apresentação

O prazo vencido passa a encerrar o personagem. O Core mantém um UUID de personagem distinto do
UUID autenticado da conta e uma marca terminal de morte. Vidas consulta essa identidade, sem
depender de Limbo: conceder vidas depois do vencimento não reabre o personagem. Não existe banimento.

O estado terminal é gravado antes da apresentação. O epílogo pendente tem o ID do personagem,
uma cópia limitada do conteúdo e o progresso de visualização. Login retoma esse progresso;
terminar remove o epílogo pendente, conservando a identidade morta. Registros vencidos antigos
são convertidos na varredura ou no login. Resgate e prazo não podem reabrir a identidade.

O servidor mantém apenas sessões de visualização online na nova varredura de um segundo.
Mortos concluídos saem do mapa de exilados e não entram no trabalho periódico. A consulta de
bloqueio nos pacotes de movimento/inventário é O(1), na thread do servidor, depois do despacho
vanilla; o estado persistente nunca é acessado na thread de rede.

A música começa junto com o fechamento dos olhos, depois da morte confirmada pelo servidor.
Um relógio local chegando a zero não inicia a cena; resgate ainda é permitido até o vencimento
autoritativo. A cena envia um
snapshot, com correção esparsa por minuto. Rolagem, câmera e áudio são locais; quebra de linhas
ocorre na abertura ou resize. A câmera sobe sem teleportar a entidade ou gerar chunks no servidor.
O OGG usa streaming, repetição e fade; texto e som podem mudar sem reconstruir a apresentação.

A morte cinematográfica não chama o respawn vanilla. Espectador, veto a comandos/dano/gamemode/
viagem e bloqueio de pacotes de movimento/inventário mantêm a regra mesmo se a tela for fechada.
Após os créditos, o servidor conta 60 segundos e desconecta, sem aguardar confirmação do cliente.

Criar outro personagem é a §12.10. Remover uma flag ou apenas devolver vidas não substitui essa
operação. Detalhes e configuração:
[aurorion-limbo/MORTE-DEFINITIVA.md](aurorion-limbo/MORTE-DEFINITIVA.md).

### 12.10 Criação de personagem: nome, portão e reset transacional

O nome exibido passa a ser escolhido pelo jogador numa tela de login, e é a mesma identidade que o
Core guarda. Nome e sobrenome são validados no servidor e indexados sem acento e sem caixa; a chave
continua reservada depois da morte, então nome de personagem morto não volta a circular.

O portão é do servidor, não da tela: quem deve um personagem fica em espectador, com chat, comandos,
viagem e troca de modo vetados. A única brecha é a raiz de comando do criador, que é o que permite a
um cliente sem o mod responder. A tela do cliente é cortesia — ela reabre sozinha, mas apagá-la não
devolve o jogo. A varredura do portão roda uma vez por segundo sobre a lista de jogadores, sem
alocar, e é ela que pega quem passou a dever um personagem no meio da sessão.

Morrer não dá direito a recomeçar: a reserva exige uma autorização de staff, gravada no diário e
consumida ao publicar a identidade. Sem isso a morte definitiva seria um contratempo de dois minutos.

Apagar a história anterior é apagar `playerdata`, `stats` e `advancements` da conta, e não zerar
campo a campo. Zerar o que se conhece é, por construção, deixar intacto o que se desconhece — e num
modpack pesado a maior parte da progressão está em NBT persistente e data attachments dentro do
próprio arquivo do jogador. Apagar inverte o padrão para "não sobrevive".

Isso exige a conta **offline**: com o dono online o arquivo em disco é cópia velha que o logout
reescreve. Então a troca é uma transação com diário em quatro tempos — reserva com `fsync`,
desconexão, apagamento mais evento de reset, publicação com segundo `fsync` — e quem a conduz é a
varredura de um segundo, não o clique. Queda em qualquer ponto deixa reserva no disco e conta morta,
que é o estado de retomada, com todos os passos escritos para serem idempotentes.

O criador não conhece nenhum outro mod do ecossistema: ele dispara um evento e cada mod apaga o que é
seu, ao lado dos dados que escreveu. É a mesma regra do §9.2 aplicada a dado em vez de evento — quem
é dono decide. O evento roda com o dono offline. Progressão que um mod guarde **fora** do arquivo do
jogador continua precisando de listener próprio.

A política de quem é barrado mora no criador, e não no Core: ela depende de config de servidor que o
Core não lê. O Core guarda só o que os outros mods precisam perguntar — se existe criador instalado,
qual raiz de comando deixar passar para um morto, e quais contas estão no meio de outra cena e não
podem ser interrompidas por uma pergunta de nome.
Detalhes: [aurorion-personagem/README.md](aurorion-personagem/README.md).

### 12.11 Rito de Vinculação: revelação pública comandada

Uma cena de 18 segundos: música entra em dois segundos, o círculo se forma por seis,
a casa aparece aos oito e a coroação permanece até dezoito, com dois segundos de fade final.
As cinco paletas e o evento de música são conteúdo de datapack, em `House.ceremony`.
Os OGG são recursos locais em streaming, substituíveis por resource pack.

**Uma cena ativa, sem entidades adicionais e sem partículas enviadas pelo servidor.**
O início contém participante, dimensão, âncora, idade da cena, nome, lema e paleta.
O fim é enviado à plateia que recebeu o início, inclusive quem se afastou. Login/tracking podem
enviar a fase atual a um novo espectador. Não existe varredura da plateia por tick.
O servidor faz apenas validações do participante e incrementa um contador enquanto existe rito.

**Círculo e título não dependem do render do corpo.** Um estágio do mundo desenha a geometria
pré-calculada até 96 blocos, com corte por frustum e profundidade. Isso permite que a plateia
veja a casa mesmo quando o Minecraft deixa de desenhar o jogador distante. Faixas de cor secundária
mantêm o preto/cinza das casas e o núcleo colorido mantém os traços legíveis. Runas só são
desenhadas até 64 blocos.

**A cena é parada, e isso é decisão de design, não economia.** Os anéis giravam em sentidos opostos
e as runas orbitavam o corpo; o nome usava a rotação inteira da câmera. Com trinta pessoas em volta
em posições diferentes — o caso real do evento, não o teste com um cliente da diretriz §7.6 — cada
espectador via um desenho diferente no mesmo instante, e o nome tombava junto com a mira de quem
olhava, escorregando para fora do círculo de perto ou de baixo. Hoje o selo fica cravado nos pés,
as runas ficam paradas viradas para fora, e o único elemento que acompanha o espectador é o nome,
**só no eixo Y**: em pé, sempre ancorado acima da cabeça. O pulso de brilho substituiu o giro como
fonte de movimento porque se lê igual de qualquer ângulo.

A luz da casa é uma segunda camada, aditiva (`SRC_ALPHA, ONE`) e sem escrita de profundidade — soma
sobre o mundo como luz em vez de cobri-lo como tinta, e por isso nunca tapa os traços do selo nem as
letras do nome. São ~134 quads (chão aceso, dezesseis lanças, coluna e a onda da revelação) sobre os
~2.200 que o selo já custava: **+6% numa cena que existe uma de cada vez**.

O caminho de render reutiliza segmentos e texto medido no início. PoseStack e buffers do motor
são reaproveitados, e cada `RenderType` fecha o próprio batch antes que outro peça o buffer
compartilhado — a armadilha descrita na §4.2. Nenhum dos métodos novos aloca por frame: as colunas
do clarão atrás do nome saem por aritmética, não por tabela. O custo visual é limitado a uma única
cena: três passadas do mesmo material para a geometria de cada anel e uma chamada de texto.

Partículas vanilla, até 48 blocos, têm limite de seis a cada dois ticks após a revelação, com uma
rajada de 48 no instante dela. Os fogos de artifício são a exceção de alcance: abrem alto e longe,
antes do corte de 48 blocos, porque quem assiste do fundo pode perder as faíscas dos pés mas não o
céu. São seis salvas de duas a três bombas, sorteadas a partir do id do escolhido e do número de
ordem — nunca de um estado que ande junto com o tick, o que faria cada cliente ver um céu diferente
e quem chega atrasado cair num espetáculo paralelo. Essas partículas alocam apenas no cliente
durante o efeito e respeitam as opções do Minecraft: "mínimas" desliga os fogos, "reduzidas" corta
cada bomba de 76 para 36 faíscas.

Não há teleporte, invulnerabilidade ou alteração de poções para imobilizar a pessoa. A apresentação
acompanha pequenos deslocamentos e encerra se ela sair quatro blocos da âncora, trocar de dimensão,
morrer ou desconectar. A casa permanece salva. Cadastro administrativo diferente também encerra o
rito anterior. Cancelamento limpa áudio e render; mudança de mundo limpa o cliente por dimensão.

A trilha usa o volume Música, começa somente junto com um início de idade zero e termina com a
cena. Um espectador que entra atrasado vê a fase atual sem ouvir um reinício fora de sincronia.
O anúncio público acontece junto da revelação; o texto pessoal é HUD, sem tela modal.


### 12.12 NPCs narrativos: conteúdo no ADM, identidade visual compartilhada

O ADM continua dono da conversa escrita e das escolhas do NPC. O Oráculo usa um diálogo de datapack
com ID namespaced e registra apenas as condições que dependem das regras do Limbo. Quando a escolha
precisa mostrar dados vivos — nomes, prazo, presença e custo — ela abre uma tela Aurorion alimentada
por um snapshot do servidor. A autorização da passagem continua no servidor.

Moldura, botão, cabeçalho, linha, etiqueta e barra de rolagem passam a ser componentes de cliente do
`aurorion-core`. Cada mod fornece somente tema, texto e conteúdo. Isso deixa os próximos NPCs com a
mesma gramática visual sem criar dependência do Core no Limbo ou copiar uma tela inteira.

A paleta do diálogo ADM repete, por dados, a paleta da tela dinâmica. O ADM 0.7.3 expõe cores, mas não
expõe fonte por diálogo; por isso não há mixin global em uma tela privada de terceiro. Os componentes
nativos referenciam a fonte Aurorion por `ResourceLocation`. Caxton pode renderizá-la quando estiver
instalado e o provider TTF do Minecraft permanece a base. Immersive Messages continua reservado aos
avisos e cenas: abrir uma segunda sobreposição durante a conversa esconderia as escolhas do ADM.

**Custo:** tema e layout são resolvidos em `init`/resize; durante a tela aberta existem apenas
retângulos e texto no frame normal da GUI. A integração não acrescenta tick de servidor, consulta de
mundo, pacote periódico ou reflexão por quadro. ADM e Immersive Messages continuam opcionais e suas
pontes são resolvidas uma vez.

### 12.13 Passagem individual e entrega do kit

A passagem guarda o UUID de quem pagou e só reage à presença dessa pessoa. Para qualquer outro
jogador ela é cenário sem colisão nem destino. Depois que o dono atravessa com sucesso, a entidade é
descartada no mesmo tick; assim ninguém o segue por acidente e ninguém fica no Limbo sem Vínculo ou
sem registro de exílio. Passagens antigas sem UUID de dono são inválidas e somem ao carregar.

O Vínculo de Alma só é entregue depois de o servidor confirmar que o resgatador chegou ao Limbo.
Cada item tenta entrar no inventário e cai aos pés do jogador se não houver espaço. Depois da entrega,
o menu de inventário envia uma única atualização completa ao cliente. A sincronização explícita é
necessária porque a alteração acontece logo após `changeDimension`, fora do fluxo comum de coleta:
sem ela, o servidor pode possuir o item enquanto o cliente continua mostrando o inventário anterior.

O custo é uma consulta direta ao jogador dono e um broadcast de inventário por travessia de resgate,
um evento raro; não há varredura da lista de jogadores, consulta ou pacote periódico. O GameTest
valida que terceiros não ativam a passagem, que o dono chega à dimensão com a quantidade exata de
Vínculos e que a passagem fecha imediatamente.

## 13. As capas de uniforme (`aurorion-aeonita`)

Primeira peça de arte animada do ecossistema, e a primeira dependência de biblioteca de terceiros.

### 13.1 Por que cinco itens, e não um item com a casa num componente

A §7, diretriz 4, manda preferir descoberta em runtime a registro estático quando o conteúdo é
*dado*. A capa parece caso disso — cinco texturas de uma peça só — e não é: o que varia aqui não é
a aparência de um objeto, são cinco objetos diferentes. Uma capa é entregue pela staff, guardada num
baú, dada de presente. Com id próprio, cada uma aparece em `/give`, em receita, em loot table e em
advancement sem nenhum predicado de componente no meio; com um componente, tudo isso viraria um
predicado de componente escrito à mão em cada lugar, e um `/clear` de "capa" teria que saber a
diferença.

O preço é assumido e está registrado: **uma casa nova exige recompilar**, diferente das casas em si,
que são datapack no `aurorion-ethereal`. Aceitável porque a lista de casas é o dado mais estável do
servidor — o que muda nelas é nome, lema e cor, e nada disso passa pela capa.

### 13.2 A capa não sabe em que casa o jogador está

As cinco texturas têm o nome das cinco casas, e ainda assim este mod não lê o datapack de casas nem
pergunta nada ao `aurorion-ethereal`. Quem veste o quê é decisão de quem entrega a capa.

É a mesma fronteira da §6.1: o Altar de Seleção mora no mod de conteúdo, e o comportamento mora no
mod de ato. Travar a capa na casa vinculada exigiria uma ponte entre os dois mods para uma regra que
um comando de staff já resolve — e transformaria uma peça de roupa numa mecânica, com validação de
servidor, pacote de sincronia e um caso novo a tratar toda vez que alguém troca de casa.

### 13.3 Protege como couro, de propósito

3 de armadura, durabilidade de couro, sem tenacidade. Um peitoral novo com proteção competitiva
mudaria o balanceamento de PvP e de mob de dezenas de outros mods sem ninguém ter pedido — que é
exatamente a meta de "nunca interferir fora do escopo próprio" da §2. Proteção de couro faz vestir
a capa custar alguma coisa sem que ela vire a melhor peça do modpack.

O material declara **lista de camadas vazia**. Quem desenha a capa é o `GeoArmorRenderer`, que
cancela o caminho vanilla de render de armadura antes de ele resolver textura nenhuma — declarar
camadas seria apontar para um PNG que nunca é lido. Com a lista vazia, o pior caso (GeckoLib
ausente ou quebrado) é a capa ficar invisível, e não virar textura faltando em cima do jogador.

### 13.4 O custo por frame foi pago na conversão, não no código

A escolha de animação roda **por capa visível, por frame**. Duas consequências diretas da §2:

- **Os quatro `RawAnimation` são constantes.** `RawAnimation.begin().thenLoop(...)` aloca uma lista
  e um record por chamada; construí-los dentro do handler seria lixo novo 60×/s por jogador na tela.
- **Os 9 cubos de tamanho zero do export do CPM foram removidos do `.geo.json`.** Eles não desenham
  nada, mas o GeckoLib assa seis quads por cubo desses e paga os vértices assim mesmo — 120 vértices
  jogados fora por capa, por frame, multiplicados por quantas capas estiverem na tela.

A decisão de animação não gera pacote nenhum: ela lê `onGround`, `isSprinting` e `isMoving` da cópia
local da entidade, que o cliente já tem de qualquer jeito. O servidor não participa.

### 13.5 A referência a classe de cliente nasce depois da checagem de `Dist`

`UniformCapeItem` implementa `GeoItem`, que é comum, e expõe o renderer numa classe anônima dentro
de `createGeoRenderer`. O GeckoLib só executa esse método depois de checar `isPhysicalClient()`, e a
checagem mora dentro de um `Supplier` preguiçoso — então a classe anônima, que referencia
`GeoArmorRenderer` e `HumanoidModel`, só é carregada pela JVM quando o corpo executa, ou seja, nunca
no servidor dedicado.

É literalmente a disciplina descrita na §3.1 sobre os lambdas de registro de rede, e o motivo de ela
ter sido verificada no bytecode do GeckoLib em vez de assumida: o modo de falhar é derrubar o
servidor dedicado no boot, e num servidor de 80 jogadores isso não se descobre em produção.

### 13.6 Um teste para arte, porque nenhum outro comando chega perto dela

A geometria e as animações não foram escritas à mão: vieram de um export do Customizable Player
Models convertido por script — bones renomeados, UV reescalada de um espaço 16× maior, cubos
degenerados removidos, animações renomeadas. Um erro nessa conversão **não quebra build e não
levanta exceção**: a capa só aparece torta, parada ou invisível, e só em cliente, em cima de um
jogador.

Nenhum dos três comandos de validação do repo (build, teste, gameTest) chega perto de `assets/` —
gameTest roda no servidor, que nem carrega essa pasta. Por isso `UniformCapeAssetsTest` lê os dois
arquivos com o **próprio Gson do GeckoLib** e monta o modelo assado, que é o mesmo caminho que o
jogo percorre. E as constantes `RawAnimation` são lidas da própria classe do item, não copiadas no
teste: um nome de animação escrito errado não tem como passar pelos dois lados.

### 13.7 A dependência de terceiro fica numa configuration própria

O GeckoLib é declarado em `modLibraries`, e não em `implementation`. O motivo é o `aurorion-runs`:
ele monta o classpath a partir do **output** de cada subprojeto (classes + resources), que não
carrega dependência junto. Sem isso, um mod com biblioteca externa compilaria e passaria nos testes,
e só quebraria ao abrir o `runClient` do ecossistema — o mesmo esquecimento silencioso que a §3.2
existe para evitar. Como o `aurorion-runs` varre todos os subprojetos, declarar em `modLibraries` é
o bastante e o próximo mod com biblioteca externa não precisa lembrar de nada.

**A consequência que fica registrada:** o GeckoLib é obrigatório para o `aurorion-aeonita` carregar,
e o Altar de Seleção mora nele. Um pack sem GeckoLib perde o altar, e com ele a porta de entrada da
escolha de casa do `aurorion-ethereal`. Não é quebra do isolamento da §3 — o Ethereal continua
carregando sozinho e falando com o altar por tag, nunca por import — mas é um mod a mais na lista de
"tem que estar lá", e essa lista é o que a §3 tenta manter curta.

## 14. Fora de escopo (deliberadamente)

- Suporte a múltiplos servidores públicos / milhares de instalações — este é software para um
  servidor específico, não um mod para a CurseForge competir por downloads.
- Multiloader (Fabric/Forge legado) — ver seção 3.
- Escala além de ~100 jogadores simultâneos — não foi um requisito colocado, e desenhar para isso
  agora seria complexidade especulativa sem uso.


## 15. aurorion-areas — regras e ambientes por territorio

- Areas sao geometria persistida no mundo e editada apenas pela staff. Circulos exatos e
  poligonos concavos formam unioes com recortes e alturas independentes. Nenhum chunk e carregado
  para criar, consultar ou visualizar limites.
- A prioridade resolve cada regra separadamente; herdar nao e permitir. Uma sala pode liberar
  magia mantendo voo e spawn de monstros bloqueados pela escola. Empate usa id alfabetico.
- Um BVH por dimensao e reconstruido somente quando o cadastro muda; nao se indexa cada chunk de
  areas gigantes. Consultas de movimento reutilizam o resultado por jogador, com revisao e
  coordenadas exatas. Parado nao recalcula geometria; o caminho por tick nao monta colecoes.
- Som, darkness e dano ambiental usam prazos independentes somente para jogadores em perfis
  ativos. Som e individual, sem entidades falsas e sem broadcast para a plateia. Neblina chega
  por delta na troca de perfil e a visao fechada por pulsos com prazo local.
- Spawn e dano usam eventos do servidor. Escala de vida e dano de monstros nasce no spawn e
  persiste, sem multiplicar de novo ao carregar chunk. Zona segura bloqueia spawn novo e dano
  hostil, sem apagar mobs salvos, NPCs ou animais.
- Voo/magia afetam jogadores. Excecoes por UUID, regra e area autorizam personagens especificos;
  reset de personagem remove essas excecoes. NPCs continuam usando suas proprias mecanicas.
- Elytra e habilidades vanilla sao fiscalizadas no servidor. Iron's Spells usa evento de
  pre-cast via ponte opcional; outros meios de voo usam tags e a API publica conforme o modpack.
  Permitir voo nunca concede uma habilidade que o jogador nao tinha.
- Edicao e visualizacao exigem permissao nivel 2. Previews duram 30 segundos, so para o ADM,
  com no maximo 96 amostras por emissao. Nao ha ferramenta de claim para jogadores.
- Limites de vertices, partes, areas e regras sao validados antes de publicar o novo indice.
  Profiles narrativos sao datapack; /reload atualiza quem esta dentro sem resetar prazos por passo.
- Poligonos guardam coordenadas e tolerancias de borda em arrays calculados na criacao; consultas
  nao recalculam comprimentos de arestas. Dano e alvo reutilizam as regras do jogador, com a mesma
  invalidacao por posicao/revisao; os padroes imutaveis da dimensao tambem sao reaproveitados.
- Falha de leitura das definicoes preserva o NBT original inteiro e bloqueia todas as edicoes,
  inclusive a limpeza de excecoes no reset transacional. Nenhuma area parcialmente lida entra em
  vigor. A copia defensiva existe apenas no caminho de erro, sem custo adicional por tick.

## 16. aurorion-profissoes — especialidades e atendimento

- Uma profissao por personagem, atribuida pela staff e apagada no CharacterResetEvent. O modulo
  depende somente do Core; LSO, Quality Food e FoodSpoil sao pontes opcionais, verificadas contra
  os jars 2.4.7.2, 2.3.6 e 1.1.7 fornecidos pelo dono. Binarios externos nao entram na distribuicao.
- Primeiros socorros continuam disponiveis. Lesoes graves do LSO ficam marcadas no proprio membro
  e primeiros socorros estabilizam ate metade da saude; tratamento medico remove essa limitacao.
  A marca acompanha o NBT do LSO. Nao existe outro tick nem varredura de jogadores para ferimentos.
- Bigorna exige ferreiro. Reparo por combinacao na grade/rebolo e desativado; desencantar um unico
  item continua permitido. Mending comum nao repara; apenas equipamento autorizado pela staff.
  Encantamentos comuns param no nivel configurado; aplicar livros avancados e tarefa do arcanista,
  perto da mesa. Pocoes basicas continuam livres; aprimoramentos usam atendimento junto ao suporte.
- Qualidade e conservacao pertencem ao alimento produzido, nao a quem o carrega. Transferir comida
  nunca rebaixa o trabalho do cozinheiro nem renova a validade. Automacao sem autor produz comida
  comum. FoodSpoil calcula a validade; a ponte multiplica a taxa ja existente, sem tick adicional.
- Shift + interacao com outro jogador abre os servicos dele; uma tecla abre o proprio oficio.
  Pedidos entre pessoas exigem aceite do profissional. O servidor valida alcance, dimensao,
  profissao, bancada, inventario, prazo e token descartavel novamente no aceite. Sem custodia de
  itens ou moeda nesta fase. Nao ha comando de atendimento para jogadores.
- Sessoes sao limitadas a uma por jogador, expiram e somem no logout/parada/reset. Rede e snapshot
  limitado apenas ao abrir/agir. UI usa NpcPanelScreen do Core. Eventos de autorizacao/conclusao
  oferecem pontos para a futura economia; autorizacao nao deve cobrar antes do resultado.

## 17. aurorion-economia — livro-caixa e ciclo semanal

- Toda alteracao de saldo passa por uma operacao atomica e idempotente do servidor, classificada
  como transferencia, emissao ou queima. Valores sao inteiros em fragmentos e nunca negativos.
- Saldos ficam em contas tipadas de personagem, Academia, Greymor, Casas e custodia. Um unico
  SavedData mantem saldos, oferta, fechamento e janela recente; o log completo e somente auditoria.
- O fechamento semanal e dirigido por eventos de atividade e por indices persistidos. Nao existe
  varredura economica por tick, por chunk ou pelos blocos de uma propriedade.
- Servicos de profissao reservam o pagamento no aceite e so liberam na conclusao; cancelamento ou
  expiracao devolve a reserva. UI e celular sao o fluxo comum; comandos ficam administrativos.
- O contrato detalhado, formulas, fases e integracoes estao em `aurorion-economia/ECONOMIA.md`.
