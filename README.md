# Aurorion Mods

Monorepo Gradle dos mods do Aurorion para **Minecraft 1.21.1 / NeoForge 21.1**.

Cada mod é um subprojeto independente com seu próprio `mod_id` e seu próprio `.jar`, mas todos
compartilham a mesma versão de Minecraft, NeoForge e mappings (`gradle.properties` da raiz). A ideia
é poder ligar/desligar peça por peça no servidor sem uma quebrar a outra.

| Mod | Pasta | O que faz |
|---|---|---|
| Aurorion Talk | [aurorion-talk/](aurorion-talk/) | Balões de fala acima dos jogadores; tira as falas do HUD do chat |

## Requisitos

- **JDK 21** (`java -version` deve mostrar 21.x)
- Nada mais — o Gradle vem pelo wrapper (`./gradlew`)

## Comandos

```bash
./gradlew :aurorion-talk:runClient    # abre o Minecraft com o mod
./gradlew :aurorion-talk:runServer    # sobe um servidor dedicado de teste
./gradlew :aurorion-talk:build        # gera o .jar em aurorion-talk/build/libs/
./gradlew buildAll                    # gera o .jar de todos os mods
```

Os mundos/logs de teste ficam em `aurorion-talk/run/client` e `aurorion-talk/run/server`
(ignorados pelo git).

## Adicionando um mod novo ao ecossistema

1. Crie a pasta `aurorion-<nome>/` copiando `build.gradle` e `gradle.properties` do `aurorion-talk`
2. Ajuste `mod_id`, `mod_name`, `mod_version`, `mod_description`
3. Adicione `include 'aurorion-<nome>'` no [settings.gradle](settings.gradle)

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
