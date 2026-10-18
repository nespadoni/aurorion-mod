# Auditoria de otimização — 18/10/2026

Revisão estática (somente leitura) dos 20 módulos na branch
`fix/correcoes-magias-simplymore-2026-10-17`. Nada foi compilado nem medido em jogo:
confirmar com `/spark profiler` no servidor antes e depois de cada mudança.

**Conclusão:** nenhum módulo derruba TPS no uso comum. Os ticks já são limitados, os mapas por
jogador são limpos no logout/stop, HTTP e disco pesados rodam em threads próprias e o
`SavedDataAccess` faz cache. Há **um ponto ALTO no servidor** e **um ALTO no cliente**.

## ALTA

| Módulo | Onde | Problema | Correção sugerida |
|---|---|---|---|
| magia | `spell/Domains.java:81-115`, `config/MagiaConfig.java:184` | Pulso do Santuário Malevolente varre raio **200** (AABB 400³) a cada 10 ticks por 30 s, sem teto de alvos: dano, reflexão do `AreaApi`, pacote visual e som para cada mob/aldeão/animal | Raio padrão 48–64, `MAX_TARGETS` (mais próximos), jogadores via `level.players()`, um visual por pulso |
| aeonita (cliente) | `client/DynamicLightHandler.java:79-180` | Luz dinâmica troca `minecraft:light` no mundo do cliente a cada mudança de bloco de cada portador, sem limite de distância: recálculo de luz + rebuild de seções | Limitar a 24–32 blocos, rodar a cada 2–4 ticks, histerese de 1–2 blocos, ou desligar por padrão |

## MÉDIA

| Módulo | Onde | Problema | Correção sugerida |
|---|---|---|---|
| economia + ethereal | `economia/.../EconomyProjectorNotifier.java:15`, `ethereal/.../BoardService.java:82`, `AeonicProjectorBlockEntity.java:91,105` | Cada mudança de saldo recalcula o ranking e reenvia **todos** os projetores, mesmo com linhas iguais | Flag "sujo" + refresh no máximo 1x/s; calcular cada modo uma vez; sair cedo se as linhas não mudaram |
| core + personagem | `core/.../SavedDataAccess.java:130`, `personagem/.../CreationManager.java:404`, `AltLogin.java:171` | `flushAll` serializa e faz fsync de todos os SavedData sujos na thread do servidor em criação/troca de personagem | Gravar só `CharacterData`/`AltData` da transação |
| limbo | `oracle/OracleRotation.java:42,137,166` | Com movimento pendente e Oráculo ausente, varre entidades de todas as dimensões e renova ticket de chunk a cada segundo, para sempre | Limite de tentativas/backoff; `level.getEntity(uuid)` antes da varredura |
| trama | `server/TramaEvents.java:86-95`, `TramaCombat.java:140,156,201` | `Build.effects()` recalculado (3 `EnumMap`s, boxing) em `BreakSpeed` e 2–4x por golpe; `ResourceLocation.parse` em todo `BreakSpeed` | Cache por tick em `TramaState`; constante `static final` |
| profissões | `server/ProfessionEvents.java:51` | `GetEnchantmentLevelEvent` (muito quente) lê NBT antes do teste barato `isTargetting(MENDING)` | Reordenar as condições |
| serviços (cliente) | `client/ServicosScreen.java:146,163,299,302` | Tema do celular por reflexão (14 `Method.invoke`) e relógio formatados a cada frame | Cachear e atualizar a cada 10–20 ticks |

## BAIXA (fazer quando mexer no arquivo)

- magia: Vazio Infinito reaplica efeito a cada 4 ticks (`Domains.java:100`); `Domains.zone()` resolve NBT/dimensão todo tick (`SpellZoneEntity.java:264`); Possessão teleporta todo tick mesmo parada (`Possession.java:99`); `Hits.pvpAllowed` faz reflexão antes das checagens baratas (chamado pelo novo `SpellTeamCheckMixin` em colisão de projéteis).
- trama: sync com Pufferfish a cada segundo sem mudança (`TramaRuntime.java:121`); `EntityTickEvent.Pre` para toda entidade só para pegar projéteis novos — usar `EntityJoinLevelEvent` (`TramaEvents.java:97`).
- mundos: inject cancelável em `ServerLevel#getSeed` aloca a cada chamada — trocar por `@ModifyReturnValue` (`ServerLevelSeedMixin.java:46`).
- profissões: `@Inject(RETURN, cancellable)` em `FoodDecayMixin`/`QualityUtilsMixin` → `@ModifyReturnValue`; `BodyPartMixin` usa reflexão e copia o NBT por parte do corpo.
- essentials: captura de morte serializa o inventário duas vezes (`DeathSnapshot.java:75`); reset de personagem pode esperar até 10 s pela fila do telefone na thread do servidor (`EssentialsCharacterReset.java:35`); reflexão sem cache em `PhoneConversations.java:96,248`.
- talk: balões desenhados também no passe de sombra do Iris e no boneco do inventário (`PlayerRendererMixin.java:24`); `font.width` por frame (`BalloonRenderer.java:202`).
- ethereal: texto dos projetores recortado a cada frame (`AeonicProjectorRenderer.java:320`).
- limbo: `AuditLog` grava em disco na thread do servidor (`AuditLog.java:64`).
- areas: `new ResolvedRules()` por mob hostil admitido (`AreaEvents.java:83`).
- serviços: conferir se `PhoneAppCatalogMixin` (linhas 31-41) duplica o app quando `getApps` devolve lista cacheada.

## Correção (não é desempenho)

- utils `freeze/FrozenEffect.java:53-56`: com duração infinita (-1), `-1 % 20 == 0` nunca é verdadeiro,
  então o `/freeze` sem prazo nunca refaz a fixação periódica. Usar `tickCount` quando a duração for negativa.

## Pack

- `citadel-1.21.1-2.7.6.jar` e `citadel-up-0.2.0.jar` declaram o mesmo modId; só um carrega.
  Remover o desnecessário.
- `aurorion-runs` é só o harness de desenvolvimento (sem jar); custo zero em jogo.
