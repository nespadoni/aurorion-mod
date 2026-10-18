# Guia de desenvolvimento — mods Aurorion

Guia para quem mexe no código do monorepo `aurorion-mod`: arquitetura, módulos, ambiente, build,
testes, padrões e fluxo de release. Para operar o servidor, veja o [Guia da staff](GUIA-STAFF.md).
O porquê de cada decisão está na [SDD](../SDD.md). Os detalhes de cada mod estão no `README.md` da
pasta dele.

> **Regra desta máquina: não compilar.** O [AGENTS.md](../AGENTS.md) proíbe rodar `./gradlew`
> (`compileJava`, `build`, `test`, `runClient`, `runServer`, `runGameTestServer`) e qualquer tarefa que
> baixe ou decompile o Minecraft/NeoForge nesta máquina. Aqui só se edita código e documentação.
> Compilação, testes e ensaios são feitos em outra máquina. Toda entrega preparada aqui termina com a
> lista do que precisa ser validado lá (ver [Fluxo de release](#7-fluxo-de-release)).

---

## 1. Visão geral

| Item | Valor |
|---|---|
| Minecraft | 1.21.1 (`minecraft_version_range=[1.21.1,1.22)`) |
| NeoForge | 21.1.248 (`neo_version`; aceita `[21.1.0,)`) |
| Java | 21 (toolchain e `options.release = 21`) |
| Plugin | `net.neoforged.moddev` 2.0.143 (ModDevGradle) |
| Mappings | Mojang + Parchment `2024.11.17` |
| Grupo / autor / licença | `com.aurorion` / Spadoni / LGPL-3.0-or-later |
| Subprojetos | 19 mods `aurorion-*` + `aurorion-runs` (só para rodar o jogo, não gera jar) |

São **mods NeoForge puros**, sem Architectury nem multiloader. Cada mod é um subprojeto Gradle com
`mod_id` e `.jar` próprios, para ser ligado e desligado sozinho no servidor. Versões de
MC/NeoForge/Parchment/GeckoLib ficam num lugar só: o [`gradle.properties`](../gradle.properties) da
raiz.

### 1.1 Dependências entre módulos

```
aurorion-core  (biblioteca, obrigatória para TODOS)
 ├─ talk, essentials, utils, aeonita, areas, portais, mundos, vidas, personagem,
 │  economia, magia, integracao, trama, ethereal ...  (dependem só do core)
 ├─ vidas ──▶ limbo            (limbo exige vidas; vidas não conhece o limbo)
 ├─ economia ──▶ profissoes ──▶ servicos
 └─ integracao ──▶ diario

Dependências opcionais entre mods Aurorion (declaradas como optional no neoforge.mods.toml):
  ethereal  → aeonita (altar/capas), economia (cofre/salário das casas);
              vidas por reflexão, sem declarar no toml
  limbo     → economia (loja do Oráculo)
  magia     → vidas ≥ 0.2.5 (Ceifar/Entregar Vida; lado SERVER), utils (efeito congelado)
  trama     → ethereal ≥ 0.3.3 (casa define a raiz da árvore)
```

Regras de acoplamento (SDD §3, §6.1, §9.1):

- **Core é a única exceção à independência.** Ele não registra item, bloco nem comando. Contém o
  que já estava duplicado (`SavedDataAccess`, `PlayerMapNbt`, `SafeSpot`, `TimeFormat`,
  `AurorionConfigs`, `DerivedConfig`, `ActionCooldown`) e os **contratos entre mods**
  (`CharacterGate`, `HouseGate`, `LivesGate`, `GameFacts`, eventos `CharacterResetEvent`,
  `CharacterNamedEvent`, `HouseChangedEvent`). Um mod pergunta, outro responde, sem import entre
  eles. Se ninguém responde, `installed()` devolve `false` e quem pergunta trata isso como "sistema
  ausente".
- **Dependência dura entre mods de conteúdo só quando é real:** `limbo → vidas`,
  `profissoes → economia`, `servicos → profissoes`, `diario → integracao`.
- **Mods de terceiros nunca são dependência dura**, salvo onde o mod não faz sentido sem eles:
  GeckoLib (aeonita), Iron's Spells (magia), Pufferfish's Skills (trama), TesseraUI (telas de
  profissoes e diario, só no cliente). O resto entra por `@Pseudo` mixin, `ModList.isLoaded` e
  reflexão, e o mod funciona sem eles.
- **Acoplamento por tag ou datapack, não por classe:** o Ethereal reconhece altar pela tag
  `aurorion_ethereal:house_altars`, não pela classe do bloco do Aeonita.

### 1.2 Lado (cliente, servidor ou ambos)

O lado vem de dois pontos do código: `displayTest = "IGNORE_ALL_VERSION"` no `neoforge.mods.toml`
(o cliente pode entrar sem o jar) e `event.registrar(...).optional()` na rede (o canal é opcional).
Um mod que registra item, bloco, entidade, efeito ou magia entra em registro sincronizado e é
**obrigatório no cliente**.

| Classe | Mods |
|---|---|
| **Só servidor** | `aurorion_integracao` (sem rede, sem tela). Exceção: o `aurorion_diario` declara `aurorion_integracao` obrigatório nos dois lados, então quem tem o Diário no cliente precisa dos dois jars |
| **Servidor, com cliente opcional** (sem o jar, o jogador entra e perde só a parte visual) | `core`, `talk` (balões), `essentials` (fakename na nametag, telefone, streamer), `areas` (névoa/vinheta; contorno vira partículas), `personagem` (tela de nome; sem ela, `/personagem criar` no chat), `vidas` (HUD), `portais`, `mundos`. Vidas, portais e mundos mandam `Component.translatable` do servidor: um cliente sem o jar vê a **chave crua** em vez do texto (dedução a partir do código, não testada) |
| **Obrigatório nos dois lados** | `aeonita` (itens/blocos, GeckoLib), `ethereal` (blocos Projetor Aeônico e Mural), `utils` (entidades do feixe/congelamento, efeito `congelado`, tipo de argumento `beam_color`), `limbo` (itens, entidades e sons registrados, apesar do `displayTest` IGNORE), `magia` (protocolo 9, registros de magias/efeitos/entidades), `economia` (canal "2" não opcional), `profissoes` (canal "3" não opcional, TesseraUI no cliente), `servicos` (exige profissoes), `trama` (Pufferfish's Skills dos dois lados), `diario` (TesseraUI no cliente) |

Na prática o pack do AutoModpack leva **todos** os jars Aurorion para os clientes. A tabela acima
importa para diagnosticar quem consegue entrar com pack desatualizado. As proteções de crash do
`utils` (Simply More/Mimi-Cry) rodam no mundo local de **cada** cliente, então precisam chegar a
todos.

---

## 2. Tabela de módulos

Versões conforme o `gradle.properties` de cada módulo na branch atual
(`fix/correcoes-magias-simplymore-2026-10-17`). As versões marcadas com * já foram incrementadas
para a correção de 17/10/2026 e **ainda não foram compiladas**. A release compilada mais recente é a
[08/10/2026](../releases/2026-10-08/README.md).

| Pasta | `mod_id` | Versão | Lado | O que faz | Terceiros opcionais (integração) |
|---|---|---|---|---|---|
| [aurorion-core](../aurorion-core/) | `aurorion_core` | 0.6.5 | ambos (biblioteca) | SavedData com cache, configs em `aurorion/`, contratos entre mods, lugar seguro, itens `kept_on_death`, diagnóstico | Iris/Oculus (reflexão, névoa com shader) |
| [aurorion-talk](../aurorion-talk/) | `aurorion_talk` | 0.1.3 | ambos (cliente opcional) | Balões de fala; tira as falas do HUD do chat; sussurro decidido no servidor | — |
| [aurorion-essentials](../aurorion-essentials/) | `aurorion_essentials` | 0.8.0 | ambos (cliente opcional) | Fakename, telefone por personagem (grupos no Mensagens, Gram salvo no servidor), modo streamer, avisos de morte e histórico recuperável, `/gritar`, `/ajuda`, limpeza, lista do tab | Simple Voice Chat (plugin + `@Pseudo`), Mattupolis Phone (`@Pseudo` + reflexão), Curios e Accessories (histórico de mortes) |
| [aurorion-utils](../aurorion-utils/) | `aurorion_utils` | 0.2.4* | ambos (obrigatório) | `/abduzir`, `/freeze`; correções de crash no cliente | Simply More (`@Pseudo`: stun/cooldown, Mimi-Cry) |
| [aurorion-aeonita](../aurorion-aeonita/) | `aurorion_aeonita` | 0.3.3 | ambos (obrigatório) | Itens/blocos de Aeonita, luz dinâmica, Altar de Seleção, capas de uniforme | GeckoLib (**obrigatório**) |
| [aurorion-ethereal](../aurorion-ethereal/) | `aurorion_ethereal` | 0.3.5 | ambos (obrigatório: blocos) | Cinco casas por datapack, Cerimônia de Vinculação, Projetor Aeônico (rankings), pontos | — |
| [aurorion-areas](../aurorion-areas/) | `aurorion_areas` | 0.6.4 | servidor (cliente opcional: névoa/vinheta) | Áreas de staff com formas livres, regras, exceções, zonas seguras, barreira de casa, ambientes | Legendary Survival Overhaul (`@Pseudo` + reflexão), Iron's Spells (reflexão: voo/magia) |
| [aurorion-portais](../aurorion-portais/) | `aurorion_portais` | 0.1.3 | servidor | Tranca dimensões fora da lista livre; janelas agendadas ("trens") por datapack; passes | — |
| [aurorion-mundos](../aurorion-mundos/) | `aurorion_mundos` | 0.1.3 | servidor | Overworlds extras com seed e barreira próprias; portais de obsidiana com destino por datapack | — |
| [aurorion-vidas](../aurorion-vidas/) | `aurorion_vidas` | 0.2.5* | ambos (HUD opcional) | Vidas limitadas, HUD acima da fome, exílio ao zerar, transferência de vida | — |
| [aurorion-limbo](../aurorion-limbo/) | `aurorion_limbo` | 0.4.7 | ambos (itens e entidades registrados) | Dimensão de exílio, prazo, Oráculo, Porta do Esquecido, morte definitiva, auditoria | Immersive Messages, ADM (diálogos), Cinematic Respawn, PlayerRevive, Twilight Teleport (pontes por reflexão/mixin) |
| [aurorion-personagem](../aurorion-personagem/) | `aurorion_personagem` | 0.3.2 | ambos (tela opcional) | Nome/sobrenome no primeiro login, troca de personagem após morte definitiva, alts da staff | — |
| [aurorion-economia](../aurorion-economia/) | `aurorion_economia` | 0.3.2 | ambos (obrigatório) | Óbolo/Fragmento por personagem, `/pagar`, cofre e salário de casa, terrenos, app de banco do celular | Mattupolis Phone (opcional) |
| [aurorion-magia](../aurorion-magia/) | `aurorion_magia` | 0.6.1* | ambos (obrigatório, protocolo 9) | Addon do Iron's: liberação por staff, 55 magias autorais (12 proibidas), passivas, domínios | Iron's Spells (**obrigatório**), Iron's Restrictions, Simple Voice Chat, Emotecraft; por reflexão: Areas, LSO, Profissões, Twilight Teleport |
| [aurorion-profissoes](../aurorion-profissoes/) | `aurorion_profissoes` | 0.5.2* | ambos (obrigatório) | Médico, ferreiro, cozinheiro, arcanista, corretor; atendimento; NPCs de ofício com loja | TesseraUI (**obrigatório no cliente**); LSO, Quality Food, FoodSpoil, ADM (mixins `@Pseudo` em configs condicionais) |
| [aurorion-servicos](../aurorion-servicos/) | `aurorion_servicos` | 0.1.3 | ambos | App "Serviços" do celular: anúncios, pedidos, vagas, botão "Trabalhando" | Mattupolis Phone (7 mixins `@Pseudo` + reflexão) |
| [aurorion-integracao](../aurorion-integracao/) | `aurorion_integracao` | 0.2.2 | servidor | Envia ao site os marcos do mundo (mortes, Limbo, portais, casas); fila persistente; `/vincular` | — |
| [aurorion-diario](../aurorion-diario/) | `aurorion_diario` | 0.1.3 | ambos | Diário do personagem em tela TesseraUI, sincronizado com o site | TesseraUI (**obrigatório no cliente**) |
| [aurorion-trama](../aurorion-trama/) | `aurorion_trama` | 0.1.2 | ambos (obrigatório) | Trama do Eco: árvore de 271 nós por casa, 45 pontos, progressão por descoberta e prática | Pufferfish's Skills 0.19.x (**obrigatório**) |
| [aurorion-runs](../aurorion-runs/) | — | — | dev | Sobe cliente(s)/servidor com todos os mods; não gera jar | — |

---

## 3. Ambiente

### 3.1 Requisitos

- **JDK 21** (`java -version` → 21.x). O `settings.gradle` usa o Foojay resolver e baixa um JDK 21
  se faltar. O Gradle 8.14 **não roda em JDK 25**: se um `org.gradle.java.home` global apontar para
  JDK 25, use `./gradlew -Dorg.gradle.java.home="C:/Program Files/Java/jdk-21" ...`.
- Gradle pelo wrapper (`./gradlew` / `gradlew.bat`). `org.gradle.jvmargs=-Xmx3G`, `parallel` e
  `caching` ligados.
- Problemas de rede com `libraries.minecraft.net` (TLS 1.3 ou antivírus bloqueando `.jar`): ver
  "Problemas conhecidos de ambiente" no [README](../README.md#problemas-conhecidos-de-ambiente).

### 3.2 Jars de referência (`mod-servidor-referencia/`)

Os mods que compilam contra jars de terceiros que não estão em Maven leem esses jars da pasta
`mod-servidor-referencia/`, versionada no git. Cada caminho pode ser trocado por uma propriedade
Gradle (`-P<propriedade>=C:/caminho.jar` ou `gradle.properties` local):

| Propriedade | Jar padrão em `mod-servidor-referencia/` | Usado por | Como entra |
|---|---|---|---|
| `ironsSpellbooksJar` | `irons_spellbooks-1.21.1-3.16.3.jar` | magia | `modLibraries` (compila e roda) |
| `ironsLibJar` | `irons_lib-1.21.1-2.1.0.jar` | magia | `modLibraries` |
| `curiosJar` | `curios-neoforge-9.5.1+1.21.1.jar` | magia | `modLibraries` |
| `playerAnimatorJar` | `player-animation-lib-forge-2.0.4+1.21.1.jar` | magia | `modLibraries` |
| `ironsRestrictionsJar` | `irons_restrictions-1.21.1-5.2.0.jar` | magia | `modLibraries` |
| `voicechatJar` | `voicechat-neoforge-1.21.1-2.6.24.jar` | magia, essentials | `modLibraries` |
| `emotecraftJar` | `emotecraft-for-MC1.21.1-2.4.12-neoforge.jar` | magia | `modLibraries` |
| `puffishSkillsJar` | `puffish_skills-0.19.0-1.21-neoforge.jar` | trama | `modLibraries` |
| `tesseraUiJar` | `tesseraui-1.1.jar` | profissoes, diario | `clientModLibraries` (só cliente; copiado para `run/client/mods`) |
| `phoneJar` | `mattupolisphone112.jar` | aurorion-runs | só runtime do run (ninguém compila contra o telefone) |
| `lsoJar`, `qualityFoodJar`, `foodSpoilJar` | — (opcionais) | profissoes | `runtimeOnly` se informados |
| `admJar`, `cinematicRespawnJar`, `playerReviveJar`, `creativeCoreJar` | — (opcionais) | limbo | `runtimeOnly` se informados |

GeckoLib (`geckolib_version=4.9.2`) vem do Maven da Cloudsmith, não da pasta.

Outros jars na pasta (`legendarysurvivaloverhaul-…`, `immersivemessages-…`, `webgui-…`) servem
de referência para inspecionar APIs e para testes de contrato.

### 3.3 Configurations especiais (`gradle/aurorion-mod.gradle`)

- `modLibraries`: mod de terceiro necessário em runtime. Entra em `implementation` e o
  `aurorion-runs` varre essa configuration de todos os subprojetos.
- `clientModLibraries`: mod de tela que compila (`compileOnly`) mas nunca entra no servidor
  dedicado. Uma tarefa `prepareClientMods` copia esses jars para `run/client/mods` antes do
  `runClient`.
- O core entra como `sourceSets.main.output` (diretório), não como jar. Com o jar, o FML acharia o
  mod duas vezes. O mesmo vale para dependências entre mods (`limbo → vidas`, `servicos →
  profissoes`, etc.), que usam `evaluationDependsOn` + `sourceSets.main.output`.

---

## 4. Compilar (na máquina de build)

Todos os comandos abaixo rodam **só na máquina de build**, nunca nesta.

```bash
./gradlew buildAll                         # todos os mods (exceto aurorion-runs)
./gradlew :aurorion-magia:build            # um mod (inclui test e check)
./gradlew :aurorion-vidas:build :aurorion-utils:build :aurorion-magia:build :aurorion-profissoes:build
```

- **Onde saem os jars:** `build/jars-servidor/<mod_id>-neoforge-1.21.1-<versão>.jar`, inclusive no
  build de um mod só. Os `-sources.jar` ficam em `<módulo>/build/libs/`.
- **Jars de terceiros remendados:** `copiarJarsDeTerceiros` roda depois de todo `jar` e de
  `buildAll` e copia o C2ME sem ScalableLux de `build/vendor-backups/` para `build/jars-servidor/`
  (`c2me-neoforge-mc1.21.1-0.4.0-alpha.0.120.jar`). O `build/` está no `.gitignore`, então esse
  backup existe só na máquina de build.
- O `processResources` expande os `${...}` do `META-INF/neoforge.mods.toml` com os valores do
  `gradle.properties` (versão, nome, descrição, faixas de MC/NeoForge).

### 4.1 Tarefas úteis

| Tarefa | Para quê |
|---|---|
| `buildAll` | Builda os 19 mods e copia o C2ME remendado |
| `:aurorion-runs:runClient` / `runClient2` / `runServer` | Ecossistema inteiro, direto das classes compiladas. Clientes `Dev1` e `Dev2`, servidor offline-mode em `localhost` |
| `:<mod>:runClient` / `:<mod>:runServer` | Um mod só (mais o core e as dependências declaradas no `build.gradle` dele) |
| `:<mod>:test` | Testes unitários JUnit 5 |
| `:<mod>:runGameTestServer` | GameTests (ethereal, limbo, profissoes, trama). O mundo `run/gameTestServer/world` é apagado antes de cada execução |
| `:aurorion-essentials:phoneResetTest` | Regressão do reset de dados do telefone. Faz parte do `check` do essentials |
| `/diario teste` (no runClient) | Abre o diário com 14 entradas de exemplo, sem site |

Os mundos e logs de teste ficam em `<módulo>/run/client` e `<módulo>/run/server` (ignorados pelo
git). Para limitar os mods de um run do agregador, fixe `loadedMods` em
[aurorion-runs/build.gradle](../aurorion-runs/build.gradle). Um subprojeto com
`aurorion_runs_skip=true` no `gradle.properties` fica fora do agregador.

---

## 5. Testes

### 5.1 Unitários (`src/test/java`, JUnit 5)

| Módulo | Classes de teste | Observação |
|---|---|---|
| core | 6 | inclui `AurorionConfigs.fileName` |
| essentials | 29 | contratos do telefone e `phoneResetTest` |
| profissoes | 9 | contratos Quality Food / FoodSpoil |
| areas | 8 | contratos LSO |
| limbo | 8 | |
| economia | 5 | contrato do banco do telefone |
| ethereal | 5 | |
| magia | 3 | assets/lang, `SpellGrants`, `VoiceMute` |
| integracao, mundos, talk, trama | 3 cada | |
| diario, portais, servicos | 2 cada | |
| aeonita | 1 | |
| personagem, utils, vidas | 0 | |

**Testes de contrato** conferem métodos e campos de mods de terceiros no jar real. Ficam ignorados
se a variável de ambiente não estiver definida:

| Variável | Jar |
|---|---|
| `AURORION_PHONE_JAR` | `mattupolisphone112.jar` (essentials, economia, servicos) |
| `AURORION_LSO_JAR` | Legendary Survival Overhaul (areas) |
| `AURORION_QUALITY_FOOD_JAR` | Quality Food (profissoes) |
| `AURORION_FOODSPOIL_JAR` | FoodSpoil (profissoes) |

```powershell
$env:AURORION_PHONE_JAR = (Resolve-Path 'mod-servidor-referencia/mattupolisphone112.jar').Path
./gradlew :aurorion-core:build :aurorion-essentials:build
```

### 5.2 GameTests (`src/gameTest`)

Existem em **ethereal, limbo, profissoes e trama**. Cada um declara um mod de teste próprio
(`aurorion_<mod>_tests`) e o run `gameTestServer` com
`neoforge.enabledGameTestNamespaces=aurorion_<mod>_tests`. As classes de teste não entram no jar.

- **limbo:** usa um preset flat. `-PlimboRealWorldgen` mantém a geração real da dimensão.
  Carrega também `aurorion-portais`.
- **trama:** carrega o ethereal. `-PtramaTestMagicPack` adiciona o Iron's (modLibraries da magia).
- **profissoes:** com `-PlsoJar=...` roda contra o LSO real.

A release de 08/10 registrou 462 testes unitários (0 falhas, 35 ignorados) e 12 GameTests de
Profissões com LSO 2.4.7.2.

---

## 6. Padrões do código

### 6.1 Novo mod

1. Pasta `aurorion-<nome>/` com `build.gradle` de uma linha:
   `apply from: "$rootDir/gradle/aurorion-mod.gradle"`.
2. `gradle.properties` com `mod_id`, `mod_name`, `mod_version`, `mod_description`.
3. `include 'aurorion-<nome>'` no [settings.gradle](../settings.gradle). O `aurorion-runs` e o
   `buildAll` pegam o mod sozinhos.
4. `META-INF/neoforge.mods.toml` com dependência `aurorion_core` e, se o cliente puder entrar sem o
   jar, `displayTest = "IGNORE_ALL_VERSION"`.

### 6.2 Persistência: `SavedDataAccess`

Todo dado por jogador ou personagem fica num `SavedData` do **overworld**, acessado por um campo
estático:

```java
private static final SavedDataAccess<PassData> ACCESS =
        new SavedDataAccess<>("aurorion_portais_passes", PassData::new, PassData::load);

public static PassData get(MinecraftServer server) { return ACCESS.get(server); }
```

- A fábrica é criada uma vez. O cache é zerado pelo core no `ServerStoppedEvent` e também é
  descartado se vier de outro `MinecraftServer`. Quem usa **não** precisa invalidar nada.
- Só na thread do servidor.
- `PlayerMapNbt` lê e grava mapas por UUID. `DurableSavedData` existe para dados que não podem se
  perder. `SavedDataAccess.flushAll` força a gravação (usado na troca de personagem).
- Dado de **personagem** escuta o `CharacterResetEvent` do core e se apaga na morte definitiva ou
  na troca (vidas, casa, saldo, profissão, magias, passivas, trama, telefone).

### 6.3 Configs

- Sempre por `AurorionConfigs.register(container, tipo, SPEC[, variante])`. O arquivo vira
  `config/aurorion/<mod sem "aurorion_">[-variante]-<tipo>.toml`, por exemplo
  `config/aurorion/limbo-server.toml` e `config/aurorion/essentials-privacy-server.toml`.
  - No NeoForge 21.1 as configs **SERVER** também ficam em `config/`, não em
    `<mundo>/serverconfig/`. Os runs de dev confirmam isso
    (`aurorion-runs/run/server/config/aurorion/*.toml`). São sincronizadas com o cliente.
  - **STARTUP** não sincroniza com o cliente e só vale após reiniciar. É usada para segredos: o token
    da integração e o webhook do Limbo.
  - O nome antigo `config/aurorion_<mod>-<tipo>.toml` está obsoleto. O core avisa no log de
    inicialização quando ainda encontra esses arquivos.
- Valor derivado de config (lista convertida, mapa) usa `DerivedConfig`: guarda o valor cru com o
  convertido e reconverte quando muda. Editar o arquivo com o servidor no ar aplica na leitura
  seguinte, sem listener de `ModConfigEvent`.
- Conteúdo (casas, horários de portal, mundos, NPCs, árvore da Trama) é **datapack ou JSON**, não
  config (SDD §7.4).

### 6.4 Rede

- Cada mod registra os próprios payloads em `RegisterPayloadHandlersEvent` com
  `event.registrar("<versão>")`. Use `.optional()` sempre que o cliente puder viver sem o mod.
  Suba a versão do protocolo quando mudar formato de payload. A Magia está no protocolo **9**: cliente
  com versão diferente não entra.
- O handler que toca classe de cliente só é criado depois da checagem de `Dist`. Por isso não existe
  helper de rede no core (SDD §3.1, §13.5).
- Entrada vinda do cliente é hostil: validar no servidor (SDD §7.5). Snapshot no login, delta
  depois. Custo O(jogadores online), nunca O(mundo) (SDD §7.3).

### 6.5 Mixins e compatibilidade

- Cada mod tem `<mod_id>.mixins.json` (e variantes condicionais, como
  `aurorion_profissoes.lso.mixins.json`, `.quality.`, `.spoil.`, `aurorion_servicos.phone.mixins.json`)
  declaradas em `[[mixins]]` no `neoforge.mods.toml`.
- Mixin em classe de mod de terceiro usa **`@Pseudo`** e `require = 0` / `defaultRequire 0`, para o
  mod carregar sem o alvo. Exemplos: telefone Mattupolis (essentials, economia, servicos), LSO
  (areas, profissoes), Quality Food e FoodSpoil (profissoes), Simply More (utils).
- O resto da compatibilidade é por `ModList.get().isLoaded("<modid>")` + reflexão (classes `compat/`).
  Nenhum import direto de mod opcional.
- Mixin em classe do próprio Minecraft ou de dependência obrigatória (ex.: Iron's na Magia) não usa
  `@Pseudo`.
- Prefira `@ModifyReturnValue`/`@ModifyExpressionValue` (MixinExtras) a `@Inject(cancellable)` que
  aloca `CallbackInfoReturnable` em caminho quente (ver
  [auditoria de 18/10](AUDITORIA-OTIMIZACAO-2026-10-18.md)).

### 6.6 Textos e lang

- `assets/<mod_id>/lang/pt_br.json` e `en_us.json` com as mesmas chaves. O `MagiaAssetsTest` confere
  isso na Magia. Texto ao jogador em português do Brasil.
- Diario, integracao, servicos e trama **não têm** arquivos de lang: o texto é
  `Component.literal` em português.

### 6.7 Desempenho

Diretrizes da SDD §7: custo por evento e não por tick; nunca cancelar dentro de protocolo com
estado; HTTP e disco pesados em thread própria (como a integração e o diário fazem); medir pensando
em 80 jogadores. Antes e depois de mudança de desempenho, medir com `/spark profiler` no servidor.

---

## 7. Fluxo de release

1. **Código** numa branch (`fix/...`, `release/...`). Nesta máquina só se edita.
2. **Versão:** subir `mod_version` no `<módulo>/gradle.properties` de cada mod alterado. Não há
   versão global de mod. Se outro mod depender da API nova, ajuste o `versionRange` no
   `neoforge.mods.toml` dele (ex.: magia exige `aurorion_vidas [0.2.5,)`). Se mudar payload, suba o
   protocolo de rede.
3. **Documentação:**
   - [CHANGELOG.md](../CHANGELOG.md): entrada no topo, escrita para os jogadores (comunicado da UF)
     ou como "Correções preparadas".
   - `docs/<ASSUNTO>-<AAAA-MM-DD>.md`: documento técnico da correção, com causa, alteração,
     módulos e versões a atualizar juntos, e o **roteiro de validação** na máquina de build e em
     jogo. Exemplo: [CORRECOES-MAGIAS-SIMPLYMORE-2026-10-17.md](CORRECOES-MAGIAS-SIMPLYMORE-2026-10-17.md).
   - README do módulo, quando muda comando, config ou comportamento.
4. **Build e testes (outra máquina):** `buildAll` ou o build dos módulos, testes de contrato com as
   variáveis de ambiente, GameTests dos módulos afetados, servidor dedicado e ensaio com dois
   clientes quando a mecânica envolve outro jogador.
5. **Pasta `releases/<AAAA-MM-DD>/`:**
   - `README.md`: o que mudou, validação feita e pendente, atenção na instalação.
   - `versions.json`: versões de todos os mods, MC, NeoForge e Java.
   - `SHA256SUMS.txt`: hashes dos jars.
   - `verification.json` (opcional): tamanho e hash por artefato.
   - Os jars substituídos vão para `build/jars-arquivados/release-<data>/`, para que
     `build/jars-servidor` fique com **uma versão por mod**.
6. **Distribuição:** os jars de `build/jars-servidor/` vão para a pasta `mods/` do servidor e para a
   **origem do AutoModpack** no servidor. O AutoModpack sincroniza os clientes no próximo login.
   Publicar fora da origem do AutoModpack faz a troca ser revertida na sincronização seguinte.
   Remover as versões antigas: o pack sincronizado já chegou a listar duas versões de 15 módulos ao
   mesmo tempo. Mods que mudam registro ou protocolo (Magia, Aeonita, Economia, Profissões, Trama)
   precisam chegar ao servidor **e** a todos os clientes juntos. Passo a passo da staff no
   [Guia da staff](GUIA-STAFF.md#3-instalar-uma-atualização-no-servidor).

### 7.1 Estado atual (18/10/2026)

- Última release compilada: [08/10/2026](../releases/2026-10-08/README.md) (Magia 0.6.0,
  Profissões 0.5.1, Utils 0.2.3, Vidas 0.2.4). Nenhum jar dela foi instalado em produção quando o
  README foi escrito.
- Preparadas nesta branch e **não compiladas**: Magia 0.6.1, Vidas 0.2.5, Utils 0.2.4 e Profissões
  0.5.2. Instalar juntas. O roteiro de validação está em
  [CORRECOES-MAGIAS-SIMPLYMORE-2026-10-17.md](CORRECOES-MAGIAS-SIMPLYMORE-2026-10-17.md).
- Pontos de desempenho abertos: [AUDITORIA-OTIMIZACAO-2026-10-18.md](AUDITORIA-OTIMIZACAO-2026-10-18.md).

### 7.2 Checklist ao terminar uma alteração nesta máquina

- [ ] `mod_version` subiu nos módulos alterados, e dependências e protocolo foram ajustados
- [ ] Chaves novas de lang em `pt_br.json` e `en_us.json`
- [ ] CHANGELOG e `docs/<assunto>-<data>.md` com a lista do que validar na compilação e em jogo
- [ ] README do módulo atualizado se mudou comando ou config
- [ ] Nada de `./gradlew` rodado aqui
