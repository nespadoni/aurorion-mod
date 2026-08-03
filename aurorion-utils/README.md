# Aurorion Utils

Utilitários diversos do ecossistema Aurorion que não se encaixam nem no `aurorion-essentials`
(comandos administrativos "puros") nem no `aurorion-talk` (balões de fala). Duas features:
**abdução** — puxa um jogador até outro lugar com uma animação de feixe de luz — e **freeze** —
imobiliza jogadores de verdade, pra uso geral (ex: pausar todo mundo num evento).

## Uso

```
/abduzir <jogador>            -> abduz <jogador> até a posição de quem executou o comando
/abduzir <jogador> <destino>  -> abduz <jogador> até a posição de <destino> (outro jogador online)
/abduzir voltar <jogador>     -> leva <jogador> de volta pra onde foi abduzido da última vez

/freeze <jogadores>            -> imobiliza (aceita nome, @a, @e[team=...], etc.)
/unfreeze <jogadores>          -> libera
```

Nível de operador 2 em toda a árvore — são ferramentas de staff, não algo auto-aplicável.

### Abdução: as 3 fases

Um feixe de luz (entidade `AbductionBeamEntity`, 3×3, reaproveitando o visual do beam de beacon)
aparece **de uma vez**, já na altura cheia (do chão ao "infinito do céu"), na posição atual do
alvo — com som. A partir daí:

1. **HOLD** — o alvo fica montado no próprio feixe (imóvel, só pode olhar ao redor — ver
   "Imobilidade real" abaixo) por um tempo curto antes de começar a subir. Pausa dramática.
2. **ASCEND** — o feixe (e o alvo montado nele) sobe até a altura configurada. Durante HOLD +
   ASCEND o alvo fica cego (`MobEffects.BLINDNESS`). Ao terminar de subir, o teleporte de verdade
   acontece: o alvo desmonta e é teleportado pro destino real.
3. **RETRACT** — sem mais ninguém montado, o feixe (sozinho) "recolhe" de baixo pra cima até sumir
   e se descartar. Puramente cosmético — o jogador já foi embora nesse ponto.

Jogadores que não são o alvo são empurrados pra fora se chegarem perto do feixe durante toda a
vida dele; ele nunca tem colisão sólida (ver "Decisões de arquitetura"). Depois do teleporte real,
o jogador fica invisível por um tempo curto (configurável).

`/abduzir voltar` refaz a mesma máquina de 3 fases, partindo de onde o jogador está agora e indo
para a última posição salva de onde ele foi abduzido — a origem é lembrada entre reinícios do
servidor (`AbductionOriginData`) e só é apagada quando o teleporte de retorno é concluído (fim da
fase ASCEND, não precisa esperar o RETRACT cosmético terminar).

### Imobilidade real (abdução e /freeze)

Nem a abdução (fases HOLD/ASCEND) nem o `/freeze` usam `MobEffect` pra imobilizar. Os dois montam
o jogador numa entidade invisível parada (`FreezeAnchorEntity`, ou a própria `AbductionBeamEntity`
no caso da abdução) — montaria é mecanismo nativo do jogo: não anda, não pula, mas continua
olhando livremente ao redor, e nada como um balde de leite tem qualquer efeito sobre isso, porque
não é um efeito. As duas features são propositalmente independentes (entidades e classes
diferentes) — `/unfreeze` nunca consegue desmontar alguém no meio de uma abdução por engano, e
vice-versa.

## Configuração

`config/aurorion_utils-server.toml`, lido no momento em que cada abdução começa (editar o arquivo
nunca afeta uma abdução já em andamento):

```toml
[abduction]
holdDurationTicks = 40
ascentHeightBlocks = 6
ascentDurationTicks = 60
retractDurationTicks = 20
beamRadius = 1.5
beamColorRgb = "9B30FF"
invisibilityDurationTicks = 60
```

`/freeze` não tem config própria — não há nada nele que faça sentido customizar por servidor.

## Som

O `.ogg` do feixe **não vem com o código**. `assets/aurorion_utils/sounds.json` já aponta para
`aurorion_utils:abduction_beam`; solte o arquivo em
`assets/aurorion_utils/sounds/abduction_beam.ogg` (Ogg Vorbis) e funciona sem recompilar nada.

## Arquitetura

```
entity/      AbductionBeamEntity (ancora visual + montaria + repulsao),
             FreezeAnchorEntity (ancora invisivel generica, so montaria), ModEntities (registro)
client/      AbductionBeamRenderer (reaproveita BeaconRenderer.renderBeaconBeam),
             FreezeAnchorRenderer (no-op — nunca aparece), UtilsClientEvents (registro)
sound/       ModSounds (registro do SoundEvent)
abduction/   AbductionManager (maquina de estados HOLD/ASCEND/RETRACT), AbductionTicker,
             ActiveAbduction (estado em memoria de uma abducao rolando),
             AbductionOriginData (persistencia da origem, pro /abduzir voltar),
             TeleportSpot (posicao + dimensao + olhar)
freeze/      FreezeManager (freeze/unfreeze via montaria)
command/     AbductionCommand, FreezeCommand
config/      AbductionConfig
```

### Decisões de arquitetura

- **Entidade, nunca blocos manipulados manualmente.** A ideia original da abdução (tentada antes
  no MCreator) usava blocos posicionados com base na posição do jogador — dava bugs de alinhamento
  e o efeito às vezes não desaparecia. Uma entidade tem ciclo de vida próprio (spawna, é dona da
  sua fase atual, se descarta sozinha): não há como "esquecer" de limpar.
- **Montaria, não `MobEffect`, pra imobilidade.** Ver "Imobilidade real" acima — decisão explícita
  do usuário pra não depender de algo que um balde de leite desfaz.
- **`FreezeAnchorEntity` e `AbductionBeamEntity` são classes irmãs, não uma herda da outra.** As
  duas usam a mesma ideia (montar numa âncora parada), mas têm ciclos de vida opostos quando
  ficam sem passageiro: a âncora de freeze se descarta sozinha nesse momento (`tick()` verifica);
  o feixe de abdução é **esperado** ficar sem passageiro ao entrar em RETRACT (o alvo já foi
  teleportado embora) e precisa sobreviver até o fim da animação de recolhimento. Compartilhar uma
  classe-mãe juntaria essas duas regras opostas — mais simples manter separado.
- **Sem colisão física de verdade.** `AbductionBeamEntity` não sobrescreve nada que a tornaria
  sólida — a barreira "não dá pra atravessar e tirar a pessoa de lá" (pra quem NÃO é o alvo) é
  feita empurrando quem chega perto, a cada tick em que o feixe existe, nunca uma `VoxelShape`
  customizada.
- **Custo por tick proporcional a abduções/freezes ativos, nunca à população do servidor.** O
  `AbductionTicker` só itera o mapa de abduções em andamento (tipicamente vazio); a busca por
  "quem está perto pra empurrar" é limitada a uma caixa pequena ao redor do próprio feixe, não à
  lista de jogadores online. Mesmo princípio do `CleanupScheduler` do `aurorion-essentials`.
- **Config congelada no spawn.** Durações, altura, raio e cor são lidos da config uma vez, no
  início da abdução, e guardados na entidade (campos sincronizados) e em `ActiveAbduction` — editar
  o arquivo nunca afeta algo já em andamento, só as próximas abduções.
- **Estado em memória, não persistido, exceto a origem da abdução.** Se o servidor cair no meio de
  uma abdução ou de um freeze, o jogador fica montado onde estava — sem dado corrompido, sem
  travar (na próxima entrada, pode ser destravado manualmente com `/unfreeze` se sobrar alguma
  âncora órfã). Só a origem da abdução (pra onde `voltar`) é salva em disco.
- **Sem mixins.** Nenhum ponto de injeção em código vanilla é necessário em nenhuma das duas
  features — tudo é `DeferredRegister`, comando e evento de tick, mecanismos já expostos pelo
  NeoForge.

## Status

Escrito contra NeoForge 21.1, mesmos padrões já usados no `aurorion-talk`/`aurorion-essentials`
deste monorepo. **Ainda não compilado** — mesma limitação de rede documentada no
`README.md` raiz. Pontos de maior risco pra conferir na primeira máquina com rede liberada:

- Assinatura exata de `BeaconRenderer.renderBeaconBeam` nesta versão (nome dos parâmetros de raio
  do feixe e do brilho, e o parâmetro `yOffset` usado pra animar a retração em
  `AbductionBeamRenderer`).
- `EntityType.Builder#build(String)` — a assinatura pode ter mudado de `build(String)` pra
  `build(ResourceKey<EntityType<?>>)` em versões recentes do NeoForge.
- `Entity#startRiding(Entity, boolean)` / `Entity#stopRiding()` / `Entity#getPassengersRidingOffset()`
  — a base de toda a imobilidade (abdução e `/freeze`); confirmar que o passageiro segue a âncora
  suavemente quando ela se move via `setPos` a cada tick (`AbductionManager#tickAscend`).
- `ServerPlayer#teleportTo(ServerLevel, double, double, double, Set<RelativeMovement>, float, float, boolean)`
  usado em `AbductionManager#completeTeleport` pro teleporte final (o único que pode cruzar
  dimensão) — chamado só depois de `stopRiding()`, pra não ser sobrescrito pela sincronização de
  posição da montaria.
- Nome exato do evento de tick do servidor (`ServerTickEvent.Post`) — mesmo ponto de risco já
  anotado no `aurorion-essentials/README.md`.

```bash
./gradlew :aurorion-utils:runClient
./gradlew :aurorion-utils:runServer
```
