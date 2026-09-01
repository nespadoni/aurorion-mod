---
name: minecraft-modding
description: "Desenvolvimento dos mods Aurorion — NeoForge 21.1 puro, single-loader, Minecraft 1.21.1, Java 21, monorepo Gradle. Use para qualquer trabalho de código nos subprojetos aurorion-* (registries, mixins, networking, GUI, datagen, config). Este servidor tem 80+ jogadores e um modpack pesado: toda decisão de design passa pelo orçamento de performance em SDD.md antes do código."
---

# Minecraft Modding — Aurorion (escopo deste repo)

Versão enxuta da skill global `minecraft-modding`, cortada para o que este repo realmente usa.
Se você chegou aqui, **não precisa consultar Fabric, Forge 1.20.1 nem Architectury** — este
monorepo é NeoForge single-loader, decisão final registrada em [SDD.md §3](../../../SDD.md).

## Antes de escrever qualquer código

1. Leia [SDD.md](../../../SDD.md) — metas de performance (80 jogadores, modpack pesado) e as
   decisões já tomadas em `aurorion-talk`. Todo mod novo segue os mesmos princípios (§5).
2. Confirme a versão em `gradle.properties` da raiz: `minecraft_version=1.21.1`,
   `neo_version=21.1.248`. Não use exemplos de outra minor version sem checar a API real.
3. Se a API que você vai usar não é óbvia, prefira **baixar o jar real do NeoForge e inspecionar
   com `javap`** a confiar em memória — o Maven da NeoForged (`maven.neoforged.net`) costuma estar
   acessível mesmo quando o CDN da Mojang (`libraries.minecraft.net`) não está neste ambiente de
   dev. Veja `aurorion-talk/README.md` (seção "Status") para o precedente disso no projeto.

## Convenções deste repo

- Um subprojeto Gradle por mod (`aurorion-talk/`, próximos a seguir o mesmo padrão), incluído em
  `settings.gradle` da raiz.
- `mod_id` em snake_case com prefixo `aurorion_` (ex.: `aurorion_talk`), pasta em kebab-case
  correspondente (`aurorion-talk`).
- Pacote base `com.aurorion.<nome-sem-prefixo>` (ex.: `com.aurorion.talk`).
- Config: **`ModConfigSpec` nativo do NeoForge, nunca Cloth Config nem arquivo editado à mão.**
  Tudo ajustável pelo jogador tem que ter GUI in-game ou ser decidido pelo próprio jogo (ver
  `aurorion-talk` para o padrão de tela custom em `client/gui/`).
- Cosmético/conteúdo variável (texturas, estilos) é **descoberto em runtime a partir de uma
  pasta de assets**, nunca hardcoded num registry Java nem dependente de banco externo — ver
  `BalloonCatalog` em `aurorion-talk`.

## Comandos deste monorepo

```bash
./gradlew :aurorion-runs:runClient     # cliente com TODOS os mods de uma vez
./gradlew :aurorion-runs:runClient2    # segundo cliente (teste de 2 jogadores)
./gradlew :aurorion-talk:runClient     # cliente com um mod so
./gradlew :aurorion-talk:runServer     # servidor dedicado de teste
./gradlew :aurorion-talk:build         # jar em aurorion-talk/build/libs/
./gradlew buildAll                     # todos os mods do ecossistema
```

`aurorion-runs` nao e um mod: e um subprojeto so de execucao que declara um mod por subprojeto
apontando para o `sourceSet` do dono. Serve para testar a interacao entre os mods (ex.: altar do
`aurorion-aeonita` + escolha de casa do `aurorion-ato2`) sem buildar jar nenhum.

Mecanica que envolve dois jogadores (balao de fala, `/abduzir`, `/fakename`, sussurro, escolha de
casa) se testa com `runServer` + `runClient` (Dev1) + `runClient2` (Dev2), os tres conectando em
`localhost`. Os `--username` tem que ser diferentes: em offline-mode o UUID vem do nome, entao
nome igual = mesmo jogador e o segundo cliente chuta o primeiro.

## Referências (da skill global — só o que se aplica aqui)

- Padrões de registro/evento NeoForge: `~/.Codex/skills/minecraft-modding/references/neoforge-api.md`
- Templates de JSON (blockstate, model, loot table, recipe, tags) — só relevante se um mod futuro
  registrar blocks/items de verdade: `~/.Codex/skills/minecraft-modding/references/common-patterns.md`
- **Não leia** `forge-1.20.1-api.md` nem `fabric-api.md` da skill global — este repo não usa
  nenhum dos dois; carregá-los só gasta contexto com padrões de outro loader.

## Checklist rápido ao registrar algo novo (block/item/entity)

Nenhum mod do ecossistema registra isso ainda (`aurorion-talk` é client-rendering + rede, sem
registries de conteúdo). Se um mod futuro precisar:

- [ ] `DeferredRegister` no pacote `<mod>/registry/` (não no pacote raiz)
- [ ] Assets em `assets/<mod_id>/...`, dados em `data/<mod_id>/...` (singular: `loot_table/`,
      `tags/block/` — convenção 1.21.x, não confundir com o plural do Forge 1.20.1 legado)
- [ ] Antes de registrar algo que roda "por jogador por tick", ver SDD.md §5.1 — questione se não
      dá para ser "por evento"
