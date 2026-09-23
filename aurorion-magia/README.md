# Aurorion Magia

Addon do [Iron's Spells 'n Spellbooks](https://github.com/iron431/irons-spells-n-spellbooks) para o
Aurorion. **Nenhuma magia vem liberada**: a staff ensina magias soltas ou escolas inteiras por
personagem, em aula, com `/aurorion spells`. O estado é espelhado no Iron's Restrictions.

Traz duas magias autorais: **Dolor Cruciatus** e **Imperium Mentis**.

Versões de referência: Iron's Spells `1.21.1-3.16.3`, Iron's Restrictions `1.21.1-5.2.0` (jars em
`mod-servidor-referencia/`).

## Liberação de magias

```
/aurorion spells unlock spell  <magia>  <alvos>
/aurorion spells unlock school <escola> <alvos>
/aurorion spells lock   spell  <magia>  <alvos>
/aurorion spells lock   school <escola> <alvos>
/aurorion spells list <jogador>
```

- `<alvos>` é seletor vanilla: `@a`, `@a[team=sonserina]`, `@p`, nome. Só pega quem está online:
  libera quem estava na aula.
- O Tab sugere as magias e escolas registradas no Iron's, incluindo as de outros addons.
- Regra: pode conjurar se a **magia** estiver liberada **ou** a **escola dela** estiver liberada.
- Os ids das magias deste mod são `aurorion_magia:dolor_cruciatus` e `aurorion_magia:imperium_mentis`.
  O namespace segue o `mod_id`, como em todo mod do ecossistema.
- O jogador recebe o aviso "Você aprendeu: …" na hora.
- As liberações são do **personagem**: morte definitiva zera tudo (`CharacterResetEvent`).
- Staff (permissão 2+) conjura tudo enquanto `staffBypass = true`.

Exemplos:

```
/aurorion spells unlock spell aurorion_magia:dolor_cruciatus @a[team=sonserina]
/aurorion spells unlock school irons_spellbooks:blood @a
```

### Onde a liberação é conferida

| Ponto | Mecanismo | Lado |
|---|---|---|
| Conjurar (livro, scroll, arma imbuída) | `SpellPreCastEvent` cancelado | servidor |
| Gravar no livro ("equipar") | `InscribeSpellEvent` cancelado | servidor |
| Livro de pesquisa, scroll, cliente | `learnedSpells` do Iron's, lido pelo Restrictions | ambos |

### Ponte com o Iron's Restrictions

O Restrictions não tem lista própria. Ele força `requiresLearning() = true` em todas as magias, e
assim o Iron's só aceita as que estão em `SyncedSpellData.learnedSpells`. A ponte é **manter esse
conjunto igual à lista oficial** (`SpellAccess.reconcile`). Isso acontece no login (prioridade
`LOWEST`, depois do handler do Restrictions), a cada comando e no reset de personagem. Nunca por tick.

Não há `import` de classe do Restrictions: a dependência é opcional de verdade. Sem ele, o gate do
`SpellPreCastEvent` continua bloqueando.

O gate próprio existe porque o Restrictions tem duas brechas: arma ou livro **imbuído** conjura sem
aprender (quando `ImbuedItemsRequireLearning = false`), e **manuscritos** ensinam a qualquer momento.

Config recomendada do Restrictions no servidor:

| Chave | Valor | Por quê |
|---|---|---|
| `DefaultLearntSpells` | `[]` | Com `authoritative`, o que ele ensinar é esquecido no login |
| `ImbuedItemsRequireLearning` | `true` | O gate já bloqueia, mas assim a UI também mostra |
| `StartingRarity` | `LEGENDARY` se a raridade não for progressão no RP | O Restrictions tem **um segundo gate, de raridade**, que este mod não controla |

O gate de raridade do Restrictions continua valendo depois do nosso. Se a staff liberar uma magia
épica para alguém com raridade `COMMON`, o Iron's ainda recusa. Isso vale para staff também.

### `authoritative` (padrão `true`)

- `true`: a lista do Aurorion é a única fonte. O que o jogador aprendeu por fora (manuscrito, magias
  padrão, eldritch pesquisado) é esquecido no próximo login/comando e não conjura.
- `false`: o aprendido por fora vale junto com as liberações.

Custo do reconcile: uma passada pelo registro de magias (~150 entradas) e no máximo dois pacotes de
sincronização, só quando algo muda. Login sem mudança não manda nada.

## Magias

### Dolor Cruciatus (Sangue, contínua, até nível 5)

Mira com o raycast do Iron's: um raio com hitbox inflada, alcance 20. Segurando o botão por até 4 s,
um **pulso** a cada 10 ticks:

1. confere alvo vivo, no alcance (+25% de folga) e à vista (1 raycast de linha de visão por pulso);
2. dano do pulso (`poder × 0,5`) sem empurrão;
3. renova `aurorion_magia:cruciatus` por 15 ticks. O efeito zera velocidade e pulo
   (`ADD_MULTIPLIED_TOTAL -1`) e cai sozinho logo que a canalização para;
4. reenvia o feixe visual com o mesmo ttl.

No alvo jogador, o cliente vê o efeito e treme a câmera (roll/yaw/pitch com senoides). O tremor
respeita `cameraShake` e a opção vanilla "Efeitos de distorção".

### Imperium Mentis (Eldritch, longa 1,5 s, até nível 3)

- **Mob**: recebe `aurorion_magia:dominado` (8 s, +4 s por nível) e ataca o vivo mais próximo que não
  seja o mestre, aliado de time, pet do mestre nem outro dominado do mesmo mestre. Enquanto dominado,
  a IA vanilla não troca de alvo por conta própria. Dano do dominado no mestre é cancelado. Ao
  expirar, ou com leite ou `/effect clear`, o mob esquece o mestre e larga o alvo.
- **Jogador**: recebe `aurorion_magia:desorientado` (4 s, +1 s por nível). No servidor: conjuração
  em andamento cancelada, nova conjuração e uso de item bloqueados. No cliente dele: movimento
  invertido e tela escurecida.
- **Imunes**: tag `data/aurorion_magia/tags/entity_type/imune_dominacao.json` (chefes via `c:bosses`,
  warden, guardião ancião e mobs de IA por *brain*: piglin, hoglin, zoglin). Editável por datapack.

**Limite honesto da inversão de controles:** no Minecraft, quem lê o teclado e move o corpo é o
cliente. O servidor decide quando o efeito começa e acaba, e bloqueia magia e item. A inversão em si
roda no cliente, e um cliente modificado pode ignorá-la. Revalidar movimento no servidor custaria um
check por jogador por tick, que é o que este mod evita.

### Trocar escola e outros números sem recompilar

O Iron's 3.16 gera um arquivo por magia em
`config/irons_spellbooks_spell_config/aurorion_magia/<magia>.json`, e aceita override por datapack
em `data/aurorion_magia/irons_spellbooks_spell_config/<magia>.json`. Escola, nível máximo, raridade
mínima e cooldown saem daí. `/ironsSpellbooks config list` lista as chaves.

## Performance (100+ jogadores)

Nada neste mod assina `ServerTickEvent` ou `PlayerTickEvent`.

| O que | Quando roda | Custo |
|---|---|---|
| Gate de conjuração | a cada tentativa de conjurar | 2 buscas em `HashSet` |
| Reconcile com o Iron's | login, comando, reset | ~150 entradas, sem pacote se nada mudou |
| Pulso do Cruciatus | a cada 10 ticks, só durante a canalização | 1 distância + 1 raycast + 1 payload |
| Paralisia | ao entrar/sair do efeito | modificador de atributo (vanilla) |
| Dominação: escolher alvo | no feitiço e 1×/s **só no mob dominado**, se o alvo sumiu | `getEntitiesOfClass` no raio (12 blocos por padrão) |
| Dominação: proteger o mestre | quando um mob troca de alvo ou alguém toma dano | 1 consulta de efeito |
| Desorientação: bloquear item | quando o jogador tenta usar item | 1 consulta de efeito |

### Como os efeitos visuais são feitos sem pesar no TPS

A regra é: **o servidor manda intenção, o cliente desenha**.

1. **Nenhuma partícula sai do servidor.** `ServerLevel.sendParticles` manda um pacote por chamada a
   cada jogador perto. Aqui, o servidor manda um `SpellVisualPayload` (tipo, id do conjurador, id do
   alvo, ttl: 6 a 10 bytes). Vai só para quem rastreia o alvo
   (`PacketDistributor.sendToPlayersTrackingEntityAndSelf`).
2. **O cliente acompanha as entidades sozinho.** `ClientSpellVisuals` guarda o visual e, a cada
   `ClientTickEvent`, acha as entidades por id (O(1)) e gera as partículas nas posições atuais. Não
   trafega posição.
3. **Sem pacote de "parar".** O feixe do Cruciatus é reenviado a cada pulso com ttl curto e some
   sozinho quando a canalização acaba.
4. **Estado de efeito não precisa de pacote nosso.** O vanilla já sincroniza os efeitos do jogador com
   o cliente dele. Tremor, controles invertidos e escurecimento só olham `player.hasEffect(...)`.
   Os efeitos são aplicados com `visible = false` para não gerar as bolhas de poção vanilla, que
   viajam como dado de entidade para todos por perto.
5. **Orçamento no cliente.** No máximo 48 visuais simultâneos. Feixe com até 36 pontos por tick,
   proporcional ao comprimento. A opção vanilla "Partículas" divide tudo (Todas 1×, Reduzidas ½,
   Mínimas ¼). Nada é gerado além de `visualDistance`.

Para um visual novo:

```java
// 1. Novo tipo em SpellVisualPayload.Kind (sempre no fim da lista: o ordinal vai na rede).
enum Kind { CRUCIATUS_BEAM, IMPERIUM_AURA, MEU_VISUAL }

// 2. No servidor, dentro do onCast da magia: um envio, nunca por tick.
MagiaNetwork.sendVisual(caster, target, SpellVisualPayload.Kind.MEU_VISUAL, duracaoEmTicks);

// 3. No cliente, um case novo em ClientSpellVisuals.tick(), respeitando o stride:
case MEU_VISUAL -> { if (time % stride == 0) meuDesenho(level, living, stride); }
```

Se o formato do payload mudar, suba `PROTOCOL_VERSION` em `MagiaNetwork`.

Para efeito que só o afetado vê (tela, câmera, controles), não use pacote: crie um `MobEffect` e
leia no cliente, como `DisorientationLayer` e `MagiaClientEvents`.

## Configuração

`config/aurorion/magia-server.toml`: `staffBypass`, `authoritative`, `dominationRadius`.

`config/aurorion/magia-client.toml`: `visualDistance` (até 32, o limite do vanilla para partículas),
`cameraShake` (0 desliga).

## Build

Compila contra o jar local do Iron's (`compileOnly`), por padrão
`mod-servidor-referencia/irons_spellbooks-1.21.1-3.16.3.jar`. Outro jar:
`-PironsSpellbooksJar=C:/caminho/irons_spellbooks.jar`.

O mod fica fora do `aurorion-runs` (`aurorion_runs_skip=true`), porque o run do ecossistema não
carrega o Iron's e a árvore dele (Curios, GeckoLib, PlayerAnimator, Caelus). A validação é no pack do
servidor.

O mod é obrigatório no cliente: as magias e os efeitos entram em registros sincronizados.

## Validar no pack

- [ ] `./gradlew :aurorion-magia:build` compila contra o jar 3.16.3 (inclui `SpellGrantsTest`).
- [ ] Servidor sobe com Iron's + Restrictions + este mod; cliente conecta.
- [ ] `/aurorion spells unlock …`: Tab sugere magias e escolas; `@a[team=…]` funciona; id inexistente é recusado.
- [ ] Jogador sem liberação: scroll, livro e arma imbuída não conjuram; inscrição é recusada.
- [ ] Liberação por escola vale para todas as magias da escola; `lock` volta a bloquear e cancela canalização em andamento.
- [ ] Com o Restrictions: livro de pesquisa mostra a magia liberada como aprendida; após relogar, o aprendido por manuscrito some (`authoritative = true`).
- [ ] Gate de raridade do Restrictions com `StartingRarity` padrão (esperado: ainda barra raridade alta).
- [ ] Cruciatus: feixe visível para terceiros, alvo paralisado (também com Velocidade II), tremor só no alvo, paralisia some ~0,25 s após soltar.
- [ ] Cruciatus: mago do Iron's (mob) conjurando funciona sem crash.
- [ ] Imperium em zumbi/esqueleto: ataca outros mobs, nunca o mestre nem colega de time; volta ao normal ao expirar, com leite e após recarregar o chunk.
- [ ] Imperium em jogador: controles invertidos, tela escura, sem magia, sem comer/beber/arco/escudo; portas continuam abrindo.
- [ ] Imperium em chefe/warden: só o lampejo, sem domínio.
- [ ] Reset de personagem zera as liberações.
- [ ] Ícones das magias e dos efeitos aparecem (arte provisória; troque por resource pack).
