# Aurorion Essentials

Comandos essenciais de servidor para o ecossistema Aurorion. Features:

- **fakename** — um nome falso que substitui o nome exibido do jogador em praticamente todo lugar
  do jogo.
- **cleanup periódico** — limpa itens dropados e orbs de XP de tempos em tempos, avisando pouco
  antes.
- **privacidade dos avisos** — quem vê entrada/saída, conquista e morte no chat.
- **`/ajuda`** — pedido de socorro do jogador, entregue a quem está moderando.

## Uso

```
/fakename set "My Name Is Tschipp"      -> aparece como "My Name Is Tschipp"
/fakename set "&6GoldenName"            -> aparece como "GoldenName" em dourado
/fakename clear                         -> limpa o proprio nome falso
/fakename clear <jogador>               -> limpa o de outro jogador (nivel 2+)
/realname <nomeFalso>                   -> revela o nome real por tras de um nome falso (nivel 2+)
/ajuda <o que aconteceu>                -> avisa todos os administradores online
```

Códigos de cor no estilo Bukkit (`&` + `0-9a-fk-or`) são traduzidos para formatação de verdade
antes de qualquer coisa ser exibida — não é o caractere de seção cru dentro do texto, é `Style`
por trecho, então funciona igual em chat, tab list e nametag.

`/fakename set` é auto-aplicável, sem permissão especial (é cosmético). Trocar o nome de **outro**
jogador (`/fakename clear <jogador>`) e descobrir quem está por trás de um nome falso (`/realname`)
exigem nível de operador 2+ — a segunda é deliberadamente uma ferramenta de moderação, não algo
disponível para jogadores comuns.

## Privacidade dos avisos do chat

Num servidor de 80 pessoas, os avisos automáticos do Minecraft são duas coisas ao mesmo tempo:
ruído e **meta-gaming**. Saber quem acabou de entrar, quem morreu e quem desbloqueou o quê é
informação que os personagens não deveriam ter.

Config em `config/aurorion/essentials-privacy.toml`. Três valores possíveis: `EVERYONE` (padrão do
Minecraft), `ADMINS` (só OP nível 2+) e `NOBODY`.

| Chave | Padrão | O que é |
|---|---|---|
| `joinLeaveMessages` | `NOBODY` | "Fulano entrou/saiu do jogo" |
| `advancementMessages` | `NOBODY` | "Fulano completou a conquista..." |
| `deathMessages` | `ADMINS` | "Fulano foi morto por..." |
| `restrictPrivateMessages` | `true` | `/msg`, `/tell` e `/w` só para operadores |

**A morte é `ADMINS`, e não `NOBODY`, de propósito.** Num servidor com sistema de vidas, o registro
de quem morreu é informação de moderação — o que não se quer é o chat de todo mundo acompanhando.

**O que não muda:**

- Quem morreu **continua vendo a causa da morte** na própria tela. Essa mensagem vai pelo
  `ClientboundPlayerCombatKillPacket`, direto para o jogador, e não passa por esta config.
- O **toast de conquista** (o avisinho no canto da tela) continua aparecendo para quem
  desbloqueou — ele nunca foi um broadcast.
- O **log do servidor** continua registrando tudo.

Os quatro pontos de interceptação são `@Redirect` em mixin, e não listeners, porque **o vanilla não
expõe nenhum evento cancelável para essas mensagens**. Os quatro chamam o mesmo
`PrivacyMessages.broadcast`: a regra de quem recebe mora num lugar só.

> **Atenção ao atualizar:** `hideJoinLeaveMessages` e `hideAdvancementMessages` eram booleanos e
> viraram os três estados acima. O NeoForge não migra chave que mudou de tipo — ele reescreve o
> arquivo com os padrões e registra a correção no log. Os padrões já são o comportamento desejado;
> só precisa reeditar quem tinha mudado os valores de propósito.

> **Fora do escopo desta config:** o `aurorion-vidas` faz os próprios anúncios de perda de vida e
> exílio, com as chaves `announceLifeLoss` e `announceExile` na config dele. São mensagens do
> sistema de vidas, não do vanilla, e se desligam por lá.

## `/ajuda`

```
/ajuda fiquei preso dentro de uma parede
```

Monta um aviso com **nome real, fakename, descrição e localização** e manda para todo operador
online. O fakename aparece ao lado do nome real justamente porque quem modera precisa dos dois.

- `/ajuda` sozinho **responde com o modo de usar**, em vez do "Unknown or incomplete command" em
  vermelho do Brigadier — que é indistinguível de um comando que não existe.
- **Todo pedido vai para o log do servidor**, com nome, coordenada e quantos administradores foram
  avisados. Sem isso, um pedido feito de madrugada sumia sem deixar rastro, e a única forma de saber
  se o comando funcionou era alguém ter visto na hora.
- Sem operador online, a resposta ("peça no Discord") sai em dourado e não em vermelho de erro: o
  pedido foi aceito e registrado, só não há quem atenda agora.

O destinatário é quem tem **OP nível 2+** (`ops.json`). Um administrador que não esteja opado não
recebe o aviso — se ninguém receber, é o primeiro lugar para olhar.

## O que muda de nome (e o que não muda, de propósito)

O nome falso substitui o resultado de `Player#getName()` — que é a base de quase todo texto que
menciona um jogador em Minecraft vanilla:

- Chat (quem falou)
- Mensagens de morte ("X foi morto por Y")
- Broadcast de conquista
- Entrada/saída do servidor ("X entrou no jogo")
- Nametag acima da cabeça (com a cor de time por cima, como já acontece hoje)
- Tab list (via `getTabListDisplayName`, sincronizado explicitamente — ver arquitetura)
- Feedback de comando que menciona o jogador (`/tell`, `/tp`, etc.)

**O que continua com o nome real, de propósito:**

- `Player#getScoreboardName()` — usado por scoreboard e seletores de alvo (`@p`, `@a[name=...]`).
  Sem isso, comandos administrativos e integrações de scoreboard quebrariam.
- Autocompletar de jogador nos comandos (usa o profile real).
- Logs do servidor e o profile/skin do jogador — o fakename é só o texto exibido, não uma
  identidade nova.
- Cabeça de jogador (item) e qualquer coisa gravada com o nome no momento da criação (livros,
  placas) — são dados persistidos, não uma exibição ao vivo.

Essa distinção **é o motivo de existir o `/realname`**: a identidade técnica nunca muda, só a
exibida — então dá para sempre reverter a máscara quando necessário.

## Arquitetura

```
fakename/    FakeName (valor: raw + Component + texto puro), LegacyColorCodes (parser "&"),
             FakeNameRegistry (cache uuid -> nome falso, comum a cliente e servidor)
mixin/       PlayerNameMixin — unico ponto de injeção, em Player#getName()/getTabListDisplayName()
server/      FakeNameData (persistência), FakeNameManager (validação + broadcast + refresh da
             tab list), FakeNameEvents (join/leave)
network/     SyncFakeNamesPayload (snapshot no login), UpdateFakeNamePayload (delta na troca)
privacy/     Visibility (EVERYONE/ADMINS/NOBODY), PrivacyConfig, PrivacyMessages (regra de quem
             recebe, em um lugar só — os quatro mixins de privacidade chamam este)
help/        HelpRequestManager (monta e entrega o aviso de /ajuda)
command/     FakeNameCommand, RealNameCommand, AjudaCommand
```

**Por que um mixin em vez de um evento do NeoForge:** não existe um hook de "nome exibido" no
NeoForge — `getName()`/`getDisplayName()` são o próprio mecanismo vanilla que quase todo código de
mensagem usa. Sobrescrever aqui, uma vez, vale para o jogo inteiro, em vez de caçar e sobrescrever
cada mensagem individualmente (chat, morte, conquista, join/leave...). É exatamente o motivo pelo
qual mods de nickname (Bukkit, Forge) sempre mexem neste ponto.

**Por que o registry é comum (não `client/` nem `server/`):** a nametag acima da cabeça é
renderizada pelo cliente lendo `getDisplayName()` da sua própria cópia local da entidade — então o
cliente precisa saber o nome falso de qualquer jogador visível, não só do dono da tela. Daí o
snapshot no login e o delta a cada troca, do mesmo jeito que o `aurorion-talk` sincroniza estilos
de balão. Chat, morte, conquista e tab list, por outro lado, já são resolvidos **no servidor** —
o Component pronto é que trafega para o cliente, então esses não precisam de rede própria.

**Por que a tab list precisa de refresh explícito:** o nome exibido na tab list vai embutido no
`ClientboundPlayerInfoUpdatePacket` no momento em que ele é montado e enviado. Sobrescrever
`getTabListDisplayName()` no mixin não alcança quem já recebeu o pacote antigo — por isso
`FakeNameManager` reenvia esse pacote (só a ação `UPDATE_DISPLAY_NAME`, só para o jogador que
mudou) toda vez que o nome falso muda.

### Validação (servidor nunca confia no cliente)

- Vazio ou maior que `FakeName.MAX_LENGTH` (48 caracteres de texto puro, sem contar código de cor)
  é rejeitado.
- Não pode coincidir (sem diferenciar maiúsculas) com o nome real de outro jogador online, nem com
  o nome falso ativo de outro jogador online — evita a forma mais óbvia de um jogador se passar
  pelo outro.
- Códigos `&` não reconhecidos ficam como texto literal (mesma convenção do Bukkit) — não é erro.

### Persistência

`FakeNameData` (SavedData, overworld) guarda só o texto cru digitado (com os `&`), por UUID. O
`Component` colorido nunca é serializado — é sempre reconstruído a partir do texto cru, tanto ao
carregar do disco quanto ao chegar por rede.

## Cleanup periódico

Remove **itens dropados** (`ItemEntity`) e **orbs de XP** (`ExperienceOrb`) de todas as dimensões
carregadas, de hora em hora por padrão. Nunca mexe em mobs, veículos, molduras ou blocos — só nas
duas entidades que realmente se acumulam sozinhas com o servidor rodando por horas (fazendas AFK,
mobs mortos em massa) sem ninguém para catar.

Um minuto antes (configurável), todo mundo recebe um aviso **na actionbar** — texto pequeno acima
da hotbar, cinza e itálico, que some sozinho. De propósito não é chat nem boss bar: é sutil o
bastante para não interromper ninguém, mas dá tempo de quem quiser catar algo do chão antes da
limpeza.

Configurável em `config/aurorion_essentials-server.toml` (lido ao vivo, sem precisar reiniciar):

```toml
[cleanup]
enabled = true
intervalMinutes = 60
warningSecondsBefore = 60
cleanItems = true
cleanExperienceOrbs = true
```

`cleanItems`/`cleanExperienceOrbs` deixam desligar cada tipo de entidade individualmente (por
exemplo, um servidor com fazenda de XP grande pode querer limpar só os itens e deixar os orbs).

### Por que isso não custa nada nos outros 3599 ticks

- `CleanupScheduler` só incrementa um contador `long` e faz duas comparações a cada tick — zero
  alocação, zero trabalho em entidade nenhuma fora do momento exato do aviso/limpeza.
- O aviso dispara **uma única vez** por ciclo (flag booleana), nunca repete.
- A limpeza em si (`EntityCleanup#run`) é O(entidades removidas), e só roda no tick em que o
  intervalo fecha — mesmo num pico de milhares de itens dropados (fazenda grande rodando a hora
  toda), remover cada um é só marcar `discard()`, sem lógica pesada por entidade.
- A limpeza coleta os alvos e só chama `discard()` depois da iteração. Isso evita invalidar o
  iterador do armazenamento interno de entidades, algo que ocorre com otimizações de
  armazenamento do NeoForge/C2ME e pode derrubar o servidor durante um ciclo de limpeza.

## Status

Versão 0.3.0. Compila e os testes do módulo passam; as versões anteriores já rodam na VPS, então o
fakename, o cleanup e o esconderijo de entrada/saída e conquista estão confirmados em jogo.

O que entrou nesta versão e ainda precisa de uma passada no servidor:

- **Mensagem de morte para admin.** O `@Redirect` mira o único
  `PlayerList.broadcastSystemMessage(Component;Z)` presente no bytecode de `ServerPlayer#die`
  (conferido com `javap` no NeoForge 21.1.248). Conferir que quem morreu continua vendo a causa na
  tela de morte e que um OP recebe a linha no chat.
- **Migração da config de privacidade.** Na primeira subida, conferir no log que o
  `essentials-privacy.toml` foi reescrito com as três chaves novas — as antigas eram booleanas.
- **Times de scoreboard.** Se o servidor passar a usar times com `deathMessageVisibility` diferente
  de `ALWAYS`, as duas rotas de time do `die` saem por fora desta config.
- **`/ajuda`.** Conferir com um jogador não-OP e com nenhum OP online, e checar a linha
  correspondente no log do servidor nos dois casos.

```bash
./gradlew :aurorion-essentials:build
./gradlew :aurorion-essentials:runClient
./gradlew :aurorion-essentials:runServer
```
