# Aurorion Mods

Monorepo Gradle dos mods do Aurorion para **Minecraft 1.21.1 / NeoForge 21.1**.

Cada mod é um subprojeto independente com seu próprio `mod_id` e seu próprio `.jar`, mas todos
compartilham a mesma versão de Minecraft, NeoForge e mappings (`gradle.properties` da raiz). A ideia
é poder ligar/desligar peça por peça no servidor sem uma quebrar a outra.

| Mod | Pasta | O que faz |
|---|---|---|
| Aurorion Core | [aurorion-core/](aurorion-core/) | **Biblioteca**: não adiciona nada ao jogo, mas todos os outros dependem dela |
| Aurorion Talk | [aurorion-talk/](aurorion-talk/) | Balões de fala acima dos jogadores; tira as falas do HUD do chat |
| Aurorion Essentials | [aurorion-essentials/](aurorion-essentials/) | Comandos essenciais de servidor: `/fakename` (troca o nome exibido em todo o jogo) e cleanup periódico de itens/XP no chão |
| Aurorion Utils | [aurorion-utils/](aurorion-utils/) | Utilitários diversos: `/abduzir` (puxa um jogador com uma animação de feixe de luz, com opção de trazer de volta) |
| Aurorion Aeonita | [aurorion-aeonita/](aurorion-aeonita/) | **Conteúdo**: itens e blocos de Aeonita, luz dinâmica e o Altar de Seleção |
| Aurorion Ato 2 | [aurorion-ato2/](aurorion-ato2/) | **Mecânica do Ato 2**: escolha de casa no altar, com as casas definidas por datapack |
| Aurorion Portais | [aurorion-portais/](aurorion-portais/) | Tranca todas as dimensões menos o overworld; o acesso abre em janelas agendadas por datapack (os "trens"), com avisos automáticos |
| Aurorion Vidas | [aurorion-vidas/](aurorion-vidas/) | Vidas limitadas por jogador, contador no HUD acima da fome, e exílio no Nether para quem zerar |

### Conteúdo x mecânica de ato

A separação entre os dois últimos é deliberada e vale para todo ato futuro:

- **Mod de conteúdo** (`aurorion-aeonita`) é o único que registra item e bloco. Fica no modpack para
  sempre, porque desligá-lo apagaria coisa do inventário dos jogadores e do mundo.
- **Mod de ato** (`aurorion-ato2`, e os próximos) só tem comportamento. Sai do modpack quando o ato
  acaba, sem levar nada junto.

O acoplamento entre os dois é uma **tag**, nunca um import: o Ato 2 pergunta "esse bloco está em
`aurorion_ato2:house_altars`?", não "esse bloco é o `SelectionAltarBlock`?". Por isso os dois podem
ser ligados e desligados independentemente, e um ato futuro pode reaproveitar o mesmo altar.

## Requisitos

- **JDK 21** (`java -version` deve mostrar 21.x)
- Nada mais — o Gradle vem pelo wrapper (`./gradlew`)

## Comandos

```bash
./gradlew :aurorion-runs:runClient    # abre o Minecraft com TODOS os mods de uma vez
./gradlew :aurorion-runs:runClient2   # segundo cliente, para teste de 2 jogadores
./gradlew :aurorion-runs:runServer    # servidor dedicado com todos os mods

./gradlew :aurorion-talk:runClient    # abre o Minecraft com um mod so
./gradlew :aurorion-talk:runServer    # sobe um servidor dedicado de teste
./gradlew :aurorion-talk:build        # gera o .jar em aurorion-talk/build/libs/
./gradlew buildAll                    # gera o .jar de todos os mods
```

Os mundos/logs de teste ficam em `<subprojeto>/run/client` e `<subprojeto>/run/server`
(ignorados pelo git) — inclusive `aurorion-runs/run/`, que é o mundo de teste do ecossistema
inteiro.

### Testando o ecossistema inteiro

[aurorion-runs/](aurorion-runs/) não é um mod: é um subprojeto que só existe para rodar o jogo. Ele
declara um mod por subprojeto apontando para o `sourceSet` do dono, então `:aurorion-runs:runClient`
sobe um cliente com todos os mods carregados direto das classes compiladas — sem gerar `.jar` e sem
copiar nada para uma pasta `mods/`. O Gradle recompila o que mudou antes de abrir o jogo, e no
desligamento os `run/` de cada mod continuam separados do `run/` do agregador.

Os mods entram no classpath do run como `sourceSets.main.output` (diretórios), e não como
`project(':aurorion-x')` (jar): com o jar, o FML acharia cada mod duas vezes — uma pelo jar no
classpath e outra pelo diretório anunciado em `-Dfml.modFolders`.

Para rodar só um subconjunto, use o `runClient` do próprio mod, ou fixe a lista no
`aurorion-runs/build.gradle` com `loadedMods = [mods.aurorion_talk, mods.aurorion_aeonita]`.

### Testando mecânica de dois jogadores

Balão de fala, `/abduzir`, `/fakename`, sussurro e escolha de casa no altar só dá para testar de
verdade com dois jogadores. Em três terminais:

```bash
./gradlew :aurorion-runs:runServer     # 1. sobe o servidor e deixa rodando
./gradlew :aurorion-runs:runClient     # 2. entra como Dev1
./gradlew :aurorion-runs:runClient2    # 3. entra como Dev2
```

Nos dois clientes: **Multiplayer → Direct Connection → `localhost`**. O servidor de dev já sobe em
`online-mode=false`, então não precisa de conta Microsoft.

Os dois clientes precisam de `--username` diferente, e isso **não é cosmético**: em offline-mode o
servidor deriva o UUID a partir do nome, então dois clientes com o mesmo nome são o mesmo jogador —
o segundo a conectar chuta o primeiro. Por isso `client` é `Dev1` e `client2` é `Dev2`, cada um com
seu `run/`.

Rodar dois `gradlew` ao mesmo tempo no monorepo funciona: o segundo abre outro daemon e não fica
esperando lock do primeiro.

Para adicionar um terceiro jogador, copie o bloco `client2` em
[aurorion-runs/build.gradle](aurorion-runs/build.gradle) trocando o nome do run e o `--username`.

## A biblioteca compartilhada

[aurorion-core/](aurorion-core/) não adiciona nada ao jogo: não registra item, bloco, comando nem
evento. Ela existe porque vários mods estavam repetindo o mesmo código — acesso a `SavedData`,
leitura/escrita de mapas por UUID, busca de lugar seguro para teleporte e formatação de tempo.

**Ela precisa estar sempre no pack.** É a contrapartida assumida: os mods continuam podendo ser
ligados e desligados um a um, o core não. Ver [SDD §3.1](SDD.md) para o porquê da troca.

O critério para algo entrar no core é estreito: **já estava duplicado**. Utilidade que só um mod usa
fica no mod — senão a biblioteca vira depósito de código especulativo, que é como uma camada
compartilhada piora a manutenção em vez de melhorar.

## Adicionando um mod novo ao ecossistema

1. Crie a pasta `aurorion-<nome>/` com um `build.gradle` de **uma linha**:
   ```gradle
   apply from: "$rootDir/gradle/aurorion-mod.gradle"
   ```
2. Crie o `gradle.properties` com `mod_id`, `mod_name`, `mod_version`, `mod_description`
3. Adicione `include 'aurorion-<nome>'` no [settings.gradle](settings.gradle)

Toda a configuração de build (toolchain, runs, Parchment, manifest, dependência do core) vem de
[gradle/aurorion-mod.gradle](gradle/aurorion-mod.gradle). Um lugar só para mudar — antes eram sete
arquivos byte-idênticos, e sete chances de esquecer um numa atualização.

O `aurorion-runs` e o `buildAll` pegam o mod novo sozinhos — os dois derivam a lista de
`subprojects`, não têm nome de mod escrito à mão.

Versões de Minecraft/NeoForge/Parchment são atualizadas em um lugar só: o
[gradle.properties](gradle.properties) da raiz.

## Problemas conhecidos de ambiente

**`Connection reset` baixando `libraries.minecraft.net`** — algumas redes (corporativas, com DPI ou
antivírus interceptando TLS) derrubam handshakes **TLS 1.3** contra o CDN da Mojang. O sintoma é o
Gradle falhar em `Could not resolve com.mojang:brigadier` e afins. Contorno, só na máquina afetada:

```properties
# gradle.properties (local, não commitar)
org.gradle.jvmargs=-Xmx3G -Djdk.tls.client.protocols=TLSv1.2
```

## Licença

`aurorion-talk` deriva do [Talk Balloons](https://github.com/CERBON-MODS/Talk-Balloons)
(CERBON-MODS), licenciado sob **LGPL-3.0**. Por isso este repositório também é LGPL-3.0 — veja
[LICENSE](LICENSE) e [CREDITS.md](CREDITS.md).
