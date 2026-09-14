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

O ritual tem duas metades separadas no tempo, e é isso que o diferencia de um questionário: **as
respostas** acontecem no altar e terminam sem revelar nada; **a decisão** é da staff, por comando. A
contagem das respostas é uma *sugestão* que chega pronta para ela.

- **O vínculo opção→casa nunca sai do servidor.** O cliente recebe só os textos das alternativas. Se
  o mapeamento viajasse no pacote, qualquer jogador leria o tráfego e saberia a resposta "certa" para
  cair na casa que quisesse — e a cerimônia viraria um menu. Diretriz 5 da seção 7 aplicada na
  direção contrária à usual: aqui o risco não é o cliente *mandar* algo falso, é ele *saber* demais.
- **As perguntas são datapack, como as casas.** Reescrever uma pergunta, mudar a ordem ou trocar o
  conjunto inteiro entre um ato e outro é editar arquivo e dar `/reload`. Foi a decisão que mais se
  afastou do mod de referência, onde as cinco casas e as oito perguntas eram `enum` e array em Java:
  corrigir uma palavra do lema da Nyx era recompilar e reiniciar um servidor de 80 jogadores.
- **As perguntas são fotografadas na abertura da cerimônia.** Um `/reload` no meio trocaria o
  conjunto sob os pés de quem está respondendo, e a resposta 3 cairia numa pergunta que ele nunca leu.
- **O veredito vai para o disco, não para o chat de quem estiver online.** Um fluxo mediado por admin
  que só funcionasse com staff acordada seria inútil num servidor de 80 pessoas: alguém termina a
  cerimônia às três da manhã e a decisão sai no dia seguinte. Pelo mesmo motivo, uma casa confirmada
  com o jogador offline deixa a **revelação na fila** e ela toca no próximo login dele — a cerimônia
  dele não pode terminar num anúncio de chat que ele não estava lá para ver.
- **Fechar a tela no meio não tranca ninguém.** Clicar no altar de novo devolve o jogador à pergunta
  em que o *servidor* diz que ele parou. Sem isso, um Esc acidental deixaria a cerimônia em
  andamento para sempre, com o altar respondendo "você já está respondendo".
- **O empate na contagem é desfeito pelo id da casa.** A ordem de um `HashMap` não é estável entre
  execuções; sem o desempate, a "casa sugerida" poderia mudar sozinha entre o fim da cerimônia e a
  leitura da staff no dia seguinte.
- **A revelação é encenada em tempo, não em quadros.** O original contava `tick++` dentro do
  `render`, o que amarrava a duração da cena ao frame rate: num cliente rodando o modpack inteiro a
  30 fps, a revelação levava o dobro do tempo que no cliente do dono do servidor.

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

### 12.5 Validação

Testes JUnit cobrem prazo, autosave, migração de NBT, avisos, caminhada, identidade da moldura,
leitura de auditoria e a regra de reenvio do painel — inclusive o caso negativo, que é o que protege
a garantia de não haver pacote por segundo (§12.6) de uma regressão silenciosa.
`:aurorion-limbo:runGameTestServer` exercita o ciclo com dois jogadores
simulados e os mods Vidas e Portais carregados: travessia simultânea, cancelamento, tentativa de
resgate, vencimento único, relatório RCON e retorno pela staff (inclusive após reconectar).
O preset de teste incorpora o tipo real da dimensão, mas substitui o gerador por uma camada plana
descartável. Assim o ciclo de exílio continua determinístico e barato no CI, enquanto a validação
do worldgen de produção fica a cargo do carregamento de datapack e de uma inspeção no cliente.
Classes e estruturas de teste não entram no jar de distribuição.

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
segundo já existente e sua remoção ou expiração é cancelada enquanto o jogador está no Limbo; leite,
comando e outro efeito não abrem uma janela clara. A marca do efeito fica no NBT do jogador para que
a saída remova apenas a escuridão criada pelo ambiente, inclusive depois de reiniciar o servidor.

O terror sonoro combina o `mood_sound` vanilla do bioma com uma ocorrência espacial de monstro a cada
18–42 segundos por jogador presente. O agendamento mantém somente um `long` por UUID e é limpo na
saída; não há busca de blocos, spawn artificial nem pacote por tick. Monstros reais continuam vindo
da tabela de spawn normal do bioma e do limite global do servidor.

## 13. Fora de escopo (deliberadamente)

- Suporte a múltiplos servidores públicos / milhares de instalações — este é software para um
  servidor específico, não um mod para a CurseForge competir por downloads.
- Multiloader (Fabric/Forge legado) — ver seção 3.
- Escala além de ~100 jogadores simultâneos — não foi um requisito colocado, e desenhar para isso
  agora seria complexidade especulativa sem uso.
