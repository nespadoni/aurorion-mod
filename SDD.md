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

## 6. Conteúdo x mecânica de ato — `aurorion-aeonita` e `aurorion-ato2`

Os dois primeiros mods do ecossistema que existem em par: um registra conteúdo, o outro dá
comportamento a esse conteúdo durante um ato específico da história.

### 6.1 A fronteira: quem registra item/bloco, e quem só tem comportamento

- **Só o mod de conteúdo (`aurorion-aeonita`) registra item e bloco. Mod de ato nunca.** O motivo é
  operacional, não estético: um mod de ato é feito para ser **removido** quando o ato acaba. Se o
  Altar de Seleção fosse registrado pelo `aurorion-ato2`, tirar o Ato 2 do modpack apagaria todos os
  altares já construídos no mundo e todo item dele no inventário de 80 jogadores. Registrando no mod
  de conteúdo, remover o ato custa só a mecânica.
- **O acoplamento entre os dois é uma tag de bloco, nunca um import.** O `aurorion-ato2` pergunta "o
  bloco clicado está em `aurorion_ato2:house_altars`?", e a entrada do altar nessa tag é
  `"required": false`. Consequência direta da seção 3 (cada mod ativável/desativável isoladamente):
  desligar um dos dois não impede o outro de carregar. Consequência de brinde: qualquer bloco do
  modpack vira altar por datapack, e o Ato 3 reaproveita o mesmo altar sem tocar em código do Ato 2.

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
- **Quais itens acendem é dado (tag), não código.** Diretriz 4 da seção 7 aplicada: incluir o
  cristal de outro mod do modpack na luz dinâmica é editar um JSON, não recompilar.

### 6.3 Escolha de casa (`aurorion-ato2`)

- **Custo total da feature: um evento de clique, um pacote de ida, um de volta.** Não há ticker,
  timer nem varredura de jogadores em lugar nenhum — a escolha é um evento raro (uma vez por
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

## 10. Fora de escopo (deliberadamente)

- Suporte a múltiplos servidores públicos / milhares de instalações — este é software para um
  servidor específico, não um mod para a CurseForge competir por downloads.
- Multiloader (Fabric/Forge legado) — ver seção 3.
- Escala além de ~100 jogadores simultâneos — não foi um requisito colocado, e desenhar para isso
  agora seria complexidade especulativa sem uso.
