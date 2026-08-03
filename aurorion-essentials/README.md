# Aurorion Essentials

Comandos essenciais de servidor para o ecossistema Aurorion. Features:

- **fakename** — um nome falso que substitui o nome exibido do jogador em praticamente todo lugar
  do jogo.
- **cleanup periódico** — limpa itens dropados e orbs de XP de tempos em tempos, avisando pouco
  antes.

## Uso

```
/fakename set "My Name Is Tschipp"      -> aparece como "My Name Is Tschipp"
/fakename set "&6GoldenName"            -> aparece como "GoldenName" em dourado
/fakename clear                         -> limpa o proprio nome falso
/fakename clear <jogador>               -> limpa o de outro jogador (nivel 2+)
/realname <nomeFalso>                   -> revela o nome real por tras de um nome falso (nivel 2+)
```

Códigos de cor no estilo Bukkit (`&` + `0-9a-fk-or`) são traduzidos para formatação de verdade
antes de qualquer coisa ser exibida — não é o caractere de seção cru dentro do texto, é `Style`
por trecho, então funciona igual em chat, tab list e nametag.

`/fakename set` é auto-aplicável, sem permissão especial (é cosmético). Trocar o nome de **outro**
jogador (`/fakename clear <jogador>`) e descobrir quem está por trás de um nome falso (`/realname`)
exigem nível de operador 2+ — a segunda é deliberadamente uma ferramenta de moderação, não algo
disponível para jogadores comuns.

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
command/     FakeNameCommand, RealNameCommand
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
- `discard()` (não `remove()`) marca a entidade para remoção; a remoção de fato do armazenamento
  da `ServerLevel` acontece depois, no tick da própria level — por isso é seguro chamar durante a
  iteração de `level.getEntities().getAll()`, sem `ConcurrentModificationException`.

## Status

Escrito contra NeoForge 21.1 seguindo os mesmos padrões de API já usados e conferidos no
`aurorion-talk` deste monorepo (`DeferredRegister`, `PayloadRegistrar`, `PacketDistributor`,
`SavedData`, mixins). **Ainda não compilado** — mesma limitação de rede documentada no
`aurorion-talk/README.md`. Pontos de maior risco para conferir na primeira máquina com rede
liberada:

- Assinatura exata de `Player#getTabListDisplayName()` (existe, mas não foi conferida contra
  bytecode real desta versão).
- Construtor de `ClientboundPlayerInfoUpdatePacket(EnumSet<Action>, Collection<ServerPlayer>)`
  usado em `FakeNameManager#refreshTabList`.
- Confirmar que `multiplayer.player.joined`/`.left`, mensagens de morte e broadcast de conquista
  realmente usam `getDisplayName()` nesta versão (é o padrão documentado de versões recentes, mas
  vale ver no jogo).
- Nome exato do evento de tick do servidor (`net.neoforged.neoforge.event.tick.ServerTickEvent.Post`)
  usado pelo `CleanupScheduler` — a API de tick foi reformulada numa versão recente do NeoForge e
  pode ter mudado de nome entre patches do 21.1.
- `Level#getEntities()` retornando um `LevelEntityGetter` com `getAll()` — confirmar que o método
  se chama exatamente assim nesta versão.

```bash
./gradlew :aurorion-essentials:runClient
./gradlew :aurorion-essentials:runServer
```
