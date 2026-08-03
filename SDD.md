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
