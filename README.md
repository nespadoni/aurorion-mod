# Aurorion Mods

Monorepo Gradle dos mods do Aurorion para **Minecraft 1.21.1 / NeoForge 21.1**.

Cada mod é um subprojeto independente com seu próprio `mod_id` e seu próprio `.jar`, mas todos
compartilham a mesma versão de Minecraft, NeoForge e mappings (`gradle.properties` da raiz). A ideia
é poder ligar/desligar peça por peça no servidor sem uma quebrar a outra.

| Mod | Pasta | O que faz |
|---|---|---|
| Aurorion Core | [aurorion-core/](aurorion-core/) | **Biblioteca**: não adiciona nada ao jogo, mas todos os outros dependem dela |
| Aurorion Talk | [aurorion-talk/](aurorion-talk/) | Balões de fala acima dos jogadores; tira as falas do HUD do chat e fecha o sussurro |
| Aurorion Essentials | [aurorion-essentials/](aurorion-essentials/) | Fakename, limpeza, privacidade e avisos de morte para a staff com TP e histórico recuperável |
| Aurorion Utils | [aurorion-utils/](aurorion-utils/) | Utilitários diversos: `/abduzir` (puxa um jogador com uma animação de feixe de luz, com opção de trazer de volta) e `/freeze` (congela de verdade, com seletores e tempo opcional) |
| Aurorion Aeonita | [aurorion-aeonita/](aurorion-aeonita/) | **Conteúdo**: itens e blocos de Aeonita, luz dinâmica, o Altar de Seleção e as capas de uniforme animadas (uma por casa) |
| Aurorion Ethereal | [aurorion-ethereal/](aurorion-ethereal/) | **O mod central**: as cinco casas por datapack, a Cerimônia de Vinculação no altar com revelação animada, e o Projetor Aeônico com os rankings |
| Aurorion Areas | [aurorion-areas/](aurorion-areas/) | Areas de staff com formas livres, regras sobrepostas, excecoes por personagem, zonas seguras e ambientes narrativos |
| Aurorion Portais | [aurorion-portais/](aurorion-portais/) | Tranca todas as dimensões menos o overworld; o acesso abre em janelas agendadas por datapack (os "trens"), com avisos automáticos |
| Aurorion Mundos | [aurorion-mundos/](aurorion-mundos/) | Mais de um overworld: mesmo gerador e mesmos mods de worldgen, mas com seed própria, barreira própria e portais de obsidiana com destino declarado em datapack |
| Aurorion Vidas | [aurorion-vidas/](aurorion-vidas/) | Vidas limitadas por jogador, contador no HUD acima da fome, e exílio no Nether para quem zerar |
| Aurorion Limbo | [aurorion-limbo/](aurorion-limbo/) | A dimensão de exílio e o **prazo** que corre nela; a Porta do Esquecido para quem ninguém foi buscar, a **morte definitiva** com epílogo, e a auditoria que a staff lê por RCON ou webhook |
| Aurorion Personagem | [aurorion-personagem/](aurorion-personagem/) | Nome e sobrenome numa tela no primeiro login, e a troca de personagem depois da morte definitiva: identidade nova, progressão zerada, conta preservada |
| Aurorion Profissões | [aurorion-profissoes/](aurorion-profissoes/) | Médico, ferreiro, cozinheiro e arcanista: uma profissão por personagem, atendimento entre jogadores por interface e integrações com LSO, Quality Food e FoodSpoil |
| Aurorion Magia | [aurorion-magia/](aurorion-magia/) | Addon do Iron's Spells: magia só com liberação da staff (`/aurorion spells`, por magia ou escola, espelhado no Iron's Restrictions) e dezessete magias autorais (três proibidas, só por concessão da staff), com selos mágicos e telas de possessão desenhados no cliente |

### Conteúdo x comportamento

`aurorion-aeonita` é **conteúdo**: registra item e bloco, e por isso fica no modpack enquanto esses
objetos existirem no mundo. O Altar de Seleção mora nele mesmo sendo usado só pelo
`aurorion-ethereal` — um bloco muda de dono uma vez, quando é criado; movê-lo depois orfanaria todos
os altares já construídos.

O acoplamento entre os dois é uma **tag**, nunca um import: o Ethereal pergunta "esse bloco está em
`aurorion_ethereal:house_altars`?", não "esse bloco é o `SelectionAltarBlock`?". Por isso os dois
podem ser ligados e desligados independentemente, e qualquer bloco do modpack vira altar por
datapack.

> **Histórico**: `aurorion-ethereal` é a fusão de `aurorion-ato2` (casas) com `aurorion-placares`
> (placares). Os dois eram o mesmo dado visto de dois ângulos — o total de uma casa é a soma dos
> pontos dos membros dela — e separados o placar tratava "casa" como texto livre digitado na GUI. O
> Projetor Aeônico que ficou no lugar dele veio do mod original do servidor. Ver [SDD §6.0](SDD.md).

### A segunda exceção à independência: `aurorion-limbo` → `aurorion-vidas`

Além do core, existe **uma** dependência declarada entre dois mods de conteúdo, e ela é assumida: o
`aurorion-limbo` é a segunda metade do exílio. Um "Limbo" sem sistema de vidas não tem quem colocar
dentro.

A direção é única e continua assim: o `aurorion-vidas` não sabe que o Limbo existe e continua
funcionando sem ele — nesse caso o exílio volta a ser o Nether sem prazo, que é o comportamento que
ele já tinha.

Isso **não** é o acoplamento que a [SDD §9.1](SDD.md) rejeita no par `vidas`/`portais`. Lá são dois
sistemas independentes que por acaso se cruzam, e por isso se coordenam por evento. Aqui a
dependência é real, e declará-la é mais honesto que simulá-la com eventos.

Mods de terceiros continuam sem nenhuma dependência dura: o Limbo fala com o Immersive Messages por
uma ponte que some sozinha quando ele não está no pack.

## Requisitos

- **JDK 21** (`java -version` deve mostrar 21.x)
- Nada mais — o Gradle vem pelo wrapper (`./gradlew`)

O `aurorion-aeonita` é o único mod com biblioteca de terceiros: o **GeckoLib**, que anima as capas de
uniforme. Ela é baixada sozinha pelo Gradle e entra no `runClient` do ecossistema sem nenhum passo
manual; no servidor de verdade, o jar precisa estar instalado ao lado dos mods Aurorion.

## Comandos

```bash
./gradlew :aurorion-runs:runClient    # abre o Minecraft com TODOS os mods de uma vez
./gradlew :aurorion-runs:runClient2   # segundo cliente, para teste de 2 jogadores
./gradlew :aurorion-runs:runServer    # servidor dedicado com todos os mods

./gradlew :aurorion-talk:runClient    # abre o Minecraft com um mod so
./gradlew :aurorion-talk:runServer    # sobe um servidor dedicado de teste
./gradlew :aurorion-talk:build        # gera o .jar em build/jars-servidor/
./gradlew buildAll                    # gera todos os jars em build/jars-servidor/
```

Os JARs instaláveis de todos os mods saem diretamente em `build/jars-servidor/`, inclusive ao
buildar um mod individual. Os arquivos `-sources.jar` continuam em `<subprojeto>/build/libs/`.

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

Balão de fala, `/abduzir`, `/fakename`, sussurro e a Cerimônia de Vinculação no altar só dá para
testar de verdade com dois jogadores — a cerimônia porque o veredito é lido por outra pessoa. Em três
terminais:

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
antivírus interceptando TLS) derrubam a conexão contra o CDN da Mojang. O sintoma é o Gradle falhar
em `Could not resolve com.mojang:brigadier` e afins.

Vale diagnosticar antes de escolher o contorno, porque são duas falhas diferentes com a mesma
mensagem:

```bash
curl -I https://libraries.minecraft.net/com/mojang/logging/1.2.7/logging-1.2.7.pom               # TLS padrão
curl -I --tlsv1.2 --tls-max 1.2 https://libraries.minecraft.net/com/mojang/logging/1.2.7/logging-1.2.7.pom
curl -I --tlsv1.2 --tls-max 1.2 https://libraries.minecraft.net/com/mojang/logging/1.2.7/logging-1.2.7.jar
```

- **Só o primeiro falha** → é o handshake **TLS 1.3**. Contorno, só na máquina afetada:

  ```properties
  # gradle.properties (local, não commitar)
  org.gradle.jvmargs=-Xmx3G -Djdk.tls.client.protocols=TLSv1.2
  ```

- **`.pom` passa e `.jar` é derrubado** → não é TLS: é antivírus/proxy bloqueando **download de
  arquivo compactado**. Nenhuma config do Gradle resolve; a saída é liberar `libraries.minecraft.net`
  na política do antivírus/rede, ou buildar noutra rede uma vez para encher o cache em
  `~/.gradle/caches` (ele é reaproveitado offline depois).

**`Unsupported class file major version 69` antes de qualquer tarefa** — o Gradle 8.14 não roda em
JDK 25. Acontece quando um `org.gradle.java.home` apontando para JDK 25 está no
`~/.gradle/gradle.properties` **global** por causa de outro projeto: ele vale para todos. Contorno
por invocação, sem mexer no arquivo global:

```bash
./gradlew -Dorg.gradle.java.home="C:/Program Files/Java/jdk-21" build
```

## Licença

`aurorion-talk` deriva do [Talk Balloons](https://github.com/CERBON-MODS/Talk-Balloons)
(CERBON-MODS), licenciado sob **LGPL-3.0**. Por isso este repositório também é LGPL-3.0 — veja
[LICENSE](LICENSE) e [CREDITS.md](CREDITS.md).

Histórico administrativo de mortes: [guia](aurorion-essentials/DEATH-HISTORY.md).
Áudio de imersão do Limbo e magias: [guia](docs/IMMERSION-AUDIO.md).
