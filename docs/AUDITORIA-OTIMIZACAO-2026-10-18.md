# Auditoria de otimização — 18/10/2026

Revisão estática (somente leitura) dos 20 módulos na branch
`fix/correcoes-magias-simplymore-2026-10-17`. Nada foi compilado nem medido em jogo:
confirmar com `/spark profiler` no servidor antes e depois de cada mudança.

**Conclusão:** nenhum módulo derruba TPS no uso comum. Os ticks já são limitados, os mapas por
jogador são limpos no logout/stop, HTTP e disco pesados rodam em threads próprias e o
`SavedDataAccess` faz cache. Há **um ponto ALTO no servidor** e **um ALTO no cliente**.

**Aplicação (18/10, mesma branch, sem compilar):** só as otimizações que não mudam jogo nem
balanceamento. Cada item abaixo leva **Status**: *aplicado*, *aplicado em parte* ou *pulado (motivo)*.
Validar tudo na compilação e em jogo — ver "O que validar" no fim.

## ALTA

| Módulo | Onde | Problema | Correção sugerida |
|---|---|---|---|
| magia | `spell/Domains.java:81-115`, `config/MagiaConfig.java:184` | Pulso do Santuário Malevolente varre raio **200** (AABB 400³) a cada 10 ticks por 30 s, sem teto de alvos: dano, reflexão do `AreaApi`, pacote visual e som para cada mob/aldeão/animal | Raio padrão 48–64, `MAX_TARGETS` (mais próximos), jogadores via `level.players()`, um visual por pulso |
| | | **Status: aplicado em parte.** Raio, teto de alvos e "um visual por pulso" **pulados**: mudam balanceamento/visual e dependem do dono. Aplicado só o que não muda resultado: o `Holder<DamageType>` do Santuário é resolvido uma vez por pulso, não por alvo (`Domains.java:105`). O `Method` do `AreaApi` já era cacheado (`AreasMagic.method()`); o custo que sobra é o `invoke` por alvo, que depende da posição de cada um e não pode ser cacheado sem mudar o resultado. | |
| aeonita (cliente) | `client/DynamicLightHandler.java:79-180` | Luz dinâmica troca `minecraft:light` no mundo do cliente a cada mudança de bloco de cada portador, sem limite de distância: recálculo de luz + rebuild de seções | Limitar a 24–32 blocos, rodar a cada 2–4 ticks, histerese de 1–2 blocos, ou desligar por padrão |
| | | **Status: aplicado.** Alcance de 32 blocos da câmera (fora dele a luz apaga pelo `pruneGone`), passada a cada 2 ticks e histerese de 1 bloco por eixo (`DynamicLightHandler.java:76-80,112,123,135,180`). Recurso continua ligado por padrão. | |

## MÉDIA

| Módulo | Onde | Problema | Correção sugerida |
|---|---|---|---|
| economia + ethereal | `economia/.../EconomyProjectorNotifier.java:15`, `ethereal/.../BoardService.java:82`, `AeonicProjectorBlockEntity.java:91,105` | Cada mudança de saldo recalcula o ranking e reenvia **todos** os projetores, mesmo com linhas iguais | Flag "sujo" + refresh no máximo 1x/s; calcular cada modo uma vez; sair cedo se as linhas não mudaram |
| | | **Status: aplicado.** `BoardService.refresh(server, modos...)` só marca os modos num `EnumSet` sujo; `BoardService.tick` (chamado no `ServerTickEvent.Post` de `EtherealServerEvents.java:174`) recalcula no máximo 1x/s, cada modo uma vez, e entrega a mesma lista a todos os projetores do modo (`BoardService.java:44,75,101,108`). `setRenderedLines` sai cedo com linhas iguais (`AeonicProjectorBlockEntity.java:94`). O `EconomyProjectorNotifier` continua igual (agora só marca). Projetor que carrega ou é configurado continua atualizando na hora. Vale para todos os modos (pontos, casas, vidas, duelos), não só economia: atraso máximo de 1 s. | |
| core + personagem | `core/.../SavedDataAccess.java:130`, `personagem/.../CreationManager.java:404`, `AltLogin.java:171` | `flushAll` serializa e faz fsync de todos os SavedData sujos na thread do servidor em criação/troca de personagem | Gravar só `CharacterData`/`AltData` da transação |
| | | **Status: aplicado em parte.** Nova API `SavedDataAccess#flush` (mesmo temp+fsync+move atômico, `SavedDataAccess.java:131`), com `CharacterData.flush`/`AltData.flush`. Usada ao nomear um vivo e ao reservar a identidade nova (`CreationManager.java:240,262`) e na troca de alt (`AltLogin.java:171`) — só esses arquivos mudam ali. **Pulado na publicação** (`CreationManager.publish` → `journal`, `CreationManager.java:425`): o `CharacterResetEvent` faz cada mod zerar o próprio SavedData, e a garantia do código é a identidade nova chegar ao disco junto com esses zeros; gravar só o `CharacterData` permitiria voltar "com metade da vida anterior" após uma queda. Ali o `flushAll` fica. | |
| limbo | `oracle/OracleRotation.java:42,137,166` | Com movimento pendente e Oráculo ausente, varre entidades de todas as dimensões e renova ticket de chunk a cada segundo, para sempre | Limite de tentativas/backoff; `level.getEntity(uuid)` antes da varredura |
| | | **Status: aplicado.** `level.getEntity(uuid)` na dimensão gravada antes da varredura por tipo (`OracleRotation.java:199`); backoff 2, 4, 8… até 60 s entre tentativas, aviso único no log após 10 falhas, zerado ao concluir, ao agendar movimento novo e no `ServerStoppedEvent` (`OracleRotation.java:56,136`). Sem desistência definitiva: o movimento persistido continua sendo concluído quando o Oráculo aparecer. | |
| trama | `server/TramaEvents.java:86-95`, `TramaCombat.java:140,156,201` | `Build.effects()` recalculado (3 `EnumMap`s, boxing) em `BreakSpeed` e 2–4x por golpe; `ResourceLocation.parse` em todo `BreakSpeed` | Cache por tick em `TramaState`; constante `static final` |
| | | **Status: aplicado.** `TramaState.effects(context)` guarda o último resultado e só recalcula quando o `Build` ou o `Context` (record, igualdade por valor) mudam — mais exato que "por tick", porque `effects` é função pura dos dois (`TramaState.java:30`). Todos os chamadores usam o cache. `BBR_MODIFIER` constante (`TramaEvents.java:25`). | |
| profissões | `server/ProfessionEvents.java:51` | `GetEnchantmentLevelEvent` (muito quente) lê NBT antes do teste barato `isTargetting(MENDING)` | Reordenar as condições |
| | | **Status: aplicado** (`ProfessionEvents.java:58`). Condições sem efeito colateral; mesmo resultado. | |
| serviços (cliente) | `client/ServicosScreen.java:146,163,299,302` | Tema do celular por reflexão (14 `Method.invoke`) e relógio formatados a cada frame | Cachear e atualizar a cada 10–20 ticks |
| | | **Status: aplicado.** Tema, brilho, modo avião e relógio lidos no `tick()` a cada 10 ticks (`ServicosScreen.java:110,150`). | |

## BAIXA (fazer quando mexer no arquivo)

- magia: Vazio Infinito reaplica efeito a cada 4 ticks (`Domains.java:100`); `Domains.zone()` resolve NBT/dimensão todo tick (`SpellZoneEntity.java:264`); Possessão teleporta todo tick mesmo parada (`Possession.java:99`); `Hits.pvpAllowed` faz reflexão antes das checagens baratas (chamado pelo novo `SpellTeamCheckMixin` em colisão de projéteis).
  - **Vazio Infinito — aplicado:** duração passa de 8 para 12 ticks (3 pulsos) e só renova com ≤ 1 pulso restante, padrão `DreadAura.renew`; continua contínuo e o resto do pulso (desmontar, cancelar conjuração, voz) segue a cada 4 ticks (`Domains.java:38,143`). Efeito colateral: depois que o alvo sai da esfera/o domínio acaba, a paralisia pode durar até 12 ticks em vez de 8 (+0,2 s).
  - **`Domains.zone()` — aplicado:** `Domains.owns(caster, zona)` compara o UUID gravado com a própria zona, sem resolver dimensão nem buscar entidade (`Domains.java:71`, `SpellZoneEntity.java:264,265,275`).
  - **Possessão — aplicado:** só teleporta quando posição (> 1 mm) ou rotação mudaram (`Possession.java:100`).
  - **`InfiniteVoidEffect` — aplicado:** sem `String`/`Vec3` por tick: dimensão comparada sem montar `"ns:path"` e âncora lida em primitivos (`InfiniteVoidEffect.java:50,52,76`).
  - **`Hits.pvpAllowed` — pulado (achado não confirmado):** as checagens baratas (`instanceof Player`/`ServerPlayer`, `isPvpAllowed()`) já vêm antes da reflexão, o `Method` já é cacheado em `AreasMagic`, e no `FriendlyFire.ignoresTeamsBetween` a config e o `caster != victim` também vêm antes. Não há reordenação que mantenha o resultado.
- trama: sync com Pufferfish a cada segundo sem mudança (`TramaRuntime.java:121`); `EntityTickEvent.Pre` para toda entidade só para pegar projéteis novos — usar `EntityJoinLevelEvent` (`TramaEvents.java:97`).
  - **Sync — aplicado (comparação barata):** o Pufferfish só tem eventos de lock/unlock de skill (já ligados ao `invalidate`), nada para pontos/categoria. `TramaRuntime.quietSync` pula o sync do segundo quando entrada, personagem, origem e pontos são os mesmos do último sync completo e não houve lock/unlock; um sync completo ainda roda a cada 10 s para reparar edição feita direto no Pufferfish (`TramaRuntime.java:22,93,159`). Mudança perceptível só nesse caso de admin: o reparo leva até 10 s em vez de 1 s.
  - **Projéteis — aplicado:** `EntityJoinLevelEvent` (`TramaEvents.java:104`). Equivalente para a tag (flecha e flecha espectral do vanilla): dono e velocidade já vêm definidos no `addFreshEntity`, e o bônus sai no próprio pacote de spawn. Projétil que receba velocidade só depois de entrar no mundo deixa de ganhar o bônus (o guarda de velocidade zero já existia).
- mundos: inject cancelável em `ServerLevel#getSeed` aloca a cada chamada — trocar por `@ModifyReturnValue` (`ServerLevelSeedMixin.java:46`).
  - **Status: aplicado** (`ServerLevelSeedMixin.java:49`). Mesma lógica: seed declarado vence, senão o original.
- profissões: `@Inject(RETURN, cancellable)` em `FoodDecayMixin`/`QualityUtilsMixin` → `@ModifyReturnValue`; `BodyPartMixin` usa reflexão e copia o NBT por parte do corpo.
  - **`FoodDecayMixin` — aplicado** (`FoodDecayMixin.java:13`); assinatura conferida no jar do pack (FoodSpoil 1.1.7: `static float getDecayRateMultiplier(ItemStack)`).
  - **`QualityUtilsMixin` — pulado:** o retorno de `getQuality` é `Quality`, classe do Quality Food que não está no classpath de compilação; o `@ModifyReturnValue` exige o tipo exato no handler e não dá para validar coerção para `Object` sem compilar. O `applyQuality` é `void` e não cancelável (fora do escopo).
  - **`BodyPartMixin` — aplicado:** nome do membro e chaves de NBT em campos `@Unique` (reflexão uma vez por parte), e o NBT só é copiado quando algum valor precisa de correção (`BodyPartMixin.java:24,92`).
- essentials: captura de morte serializa o inventário duas vezes (`DeathSnapshot.java:75`); reset de personagem pode esperar até 10 s pela fila do telefone na thread do servidor (`EssentialsCharacterReset.java:35`); reflexão sem cache em `PhoneConversations.java:96,248`.
  - **`DeathSnapshot` — aplicado:** a lista `Items` de inventário e ender chest reaproveita o NBT que o `PlayerData` já serializou (cópia sem o `Slot`), conferindo id e quantidade com o item vivo; layout fora do vanilla ou divergência caem no encode antigo (`DeathSnapshot.java:85,163,180`).
  - **`CharacterTarget` — aplicado (não estava na auditoria):** índice de nomes sem cor (Tab e busca) refeito só quando `FakeNameData.version()` muda num `setRaw` (`CharacterTarget.java:45,54,95`, `FakeNameData.java:72,82`).
  - **`EssentialsCharacterReset`/`PhoneNumberSaveQueue.awaitWrites` e `PhoneConversations` — pulados:** área do telefone, em edição por outro agente.
- talk: balões desenhados também no passe de sombra do Iris e no boneco do inventário (`PlayerRendererMixin.java:24`); `font.width` por frame (`BalloonRenderer.java:202`).
  - **Status: aplicado.** Sem balão quando o dispatcher está sem sombra (só o boneco do inventário desliga) — novo acessor `EntityRenderDispatcherAccessor` — e no passe de sombra do Iris (`IrisShadowPass`, reflexão cacheada, só consultada com shader pack ligado via `ShaderPacks.inUse()`) (`PlayerRendererMixin.java:42`). Larguras medidas uma vez em `BalloonMessage.lineWidths` (`IncomingChat.java:67`, `BalloonRenderer.java:203`).
- ethereal: texto dos projetores recortado a cada frame (`AeonicProjectorRenderer.java:320`).
  - **Status: aplicado.** Linhas recortadas e centralizadas em cache por projetor (`WeakHashMap`), refeito quando a lista de linhas, a largura ou o idioma/recursos mudam (`AeonicProjectorRenderer.java:112,327,375`).
- limbo: `AuditLog` grava em disco na thread do servidor (`AuditLog.java:64`).
  - **Status: aplicado.** Executor de uma thread (ordem preservada), criado só no primeiro uso; JSON e caminho montados na thread do servidor; `drain()` no `ServerStoppedEvent` e antes do `tail` do `/limbo auditoria` (`AuditLog.java:61,85,121,138`, `LimboServerEvents.java:360`). O comentário que justificava a escrita síncrona foi reescrito.
- areas: `new ResolvedRules()` por mob hostil admitido (`AreaEvents.java:83`).
  - **Status: aplicado** (`AreaEvents.java:37,90-92`): instância única, só na thread do servidor; `resolve` sempre faz `reset`.
- serviços: conferir se `PhoneAppCatalogMixin` (linhas 31-41) duplica o app quando `getApps` devolve lista cacheada.
  - **Status: conferido e protegido.** No jar atual (`mattupolisphone112.jar`) `getApps` cria um `ArrayList` novo a cada chamada — não duplica hoje. Acrescentado `contains` antes do `add` (`PhoneAppCatalogMixin.java:41`). `PhoneBridge` passou a guardar os alvos usados por quadro em campos (`Target`) e os acessores de registro por classe/nome (`PhoneBridge.java:39,234`).

## Correção (não é desempenho)

- utils `freeze/FrozenEffect.java:53-56`: com duração infinita (-1), `-1 % 20 == 0` nunca é verdadeiro,
  então o `/freeze` sem prazo nunca refaz a fixação periódica. Usar `tickCount` quando a duração for negativa.
  - **Status: pulado (achado não confirmado).** No 1.21.1, `MobEffectInstance.tick` já passa
    `entity.tickCount` (e não `-1`) para `shouldApplyEffectTickThisTick` quando a duração é infinita
    (conferido no bytecode do NeoForge 21.1). O `/freeze` sem prazo já refaz a fixação a cada 20 ticks.

## Pack

- `citadel-1.21.1-2.7.6.jar` e `citadel-up-0.2.0.jar` declaram o mesmo modId; só um carrega.
  Remover o desnecessário.
- `aurorion-runs` é só o harness de desenvolvimento (sem jar); custo zero em jogo.
- **Status: fora do escopo do código** (decisão do pack).

## O que validar

- Compilar todos os módulos (nada foi compilado). Pontos de atenção: `@ModifyReturnValue` em
  `ServerLevelSeedMixin` e `FoodDecayMixin` (MixinExtras do NeoForge); novo acessor
  `EntityRenderDispatcherAccessor` no `aurorion_talk.mixins.json`; record `BalloonMessage` com
  `int[] lineWidths`; `SavedDataAccess#flush` (core novo é exigido pelo personagem novo).
- Testes/gameTests existentes: `AuditLogTest`, `BuildTest`, `ProfessionGameTests` (FoodSpoil), `PhoneAppContractTest`.
- Em jogo: Vazio Infinito e Santuário (paralisia contínua, dano igual); Possessão parada e andando;
  luz dinâmica (some além de 32 blocos, acompanha andando); projetores atualizando em até 1 s;
  nomear/reservar personagem e trocar de alt; Oráculo com movimento pendente; `/limbo auditoria`
  logo depois de um evento; arco com nó PSPD da Trama; mundos com seed próprio; balões com Iris
  (sem sombra de balão) e no inventário; app Serviços (tema, brilho, relógio); `/deathhistory`
  (capturar, comparar e restaurar); Tab do argumento "pessoa".
