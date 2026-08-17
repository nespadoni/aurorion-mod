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

## 8. Fora de escopo (deliberadamente)

- Suporte a múltiplos servidores públicos / milhares de instalações — este é software para um
  servidor específico, não um mod para a CurseForge competir por downloads.
- Multiloader (Fabric/Forge legado) — ver seção 3.
- Escala além de ~100 jogadores simultâneos — não foi um requisito colocado, e desenhar para isso
  agora seria complexidade especulativa sem uso.
