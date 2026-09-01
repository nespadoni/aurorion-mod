# Aurorion Talk

Falas dos jogadores viram balões acima da cabeça e **saem do HUD do chat**.

Fork do [Talk Balloons](https://github.com/CERBON-MODS/Talk-Balloons) (LGPL-3.0) — veja
[CREDITS.md](../CREDITS.md).

## O que ele faz

- Mensagem de jogador → balão acima da cabeça, e some do chat
- Mensagens de sistema, comandos, morte e entrada/saída **continuam** no chat
- Cada jogador personaliza o **próprio** balão (skin, enfeite, cor do balão, cor do texto) numa
  tela in-game — a escolha é sincronizada, então todo mundo vê o mesmo balão
- Arte nova (skins e enfeites) entra só soltando um PNG na pasta certa e reexportando o jar,
  sem tocar em código

## Uso

Tecla **B** (reconfigurável), ou `Mods → Aurorion Talk → Config`, abre a
**Personalização do Balão**: dois previews ao vivo (fala curta e fala longa), paleta de cor do
balão, paleta de cor do texto, e um botão para escolher skin/enfeite. Fechar a tela salva e avisa
o servidor. Um botão "Preferências" leva às opções pessoais (duração, tamanho, altura — essas não
são sincronizadas, são só gosto de cada um).

## Arquitetura

```
style/           BalloonStyle (skin + enfeite + 2 cores) + paleta fixa + convenção de pastas
network/         SetStylePayload (cliente -> servidor), Sync/Update (servidor -> clientes)
server/          SavedData (persistência por jogador), validação, broadcast
client/          catálogo de texturas (scan em runtime), cache de estilos, renderer 3D, GUI
client/render/   geometria nine-slice compartilhada entre o balão 3D e o preview 2D da GUI
mixin/           4 mixins, todos client-side
```

**O estilo inteiro trafega na rede — não um id de catálogo.** O jogador monta o balão na GUI a
partir do que existe nas pastas de textura; o cliente manda pro servidor, que **valida a forma**
(`BalloonStyle#isWellFormed`) antes de persistir e propagar, porque um cliente modificado poderia
mandar qualquer caminho de textura ou qualquer cor.

### Notas de performance (servidor de 80 jogadores)

- Ocultar a fala do chat é O(1) e não toca na cadeia de assinaturas do chat seguro
- A quebra de linha do texto (`Font#split`) acontece **uma vez por mensagem**, não por frame
- Os quads vão para o `MultiBufferSource` da cena; nada de `GuiGraphics` novo a cada frame
- Mensagens vencidas são limpas no render, então jogador fora da tela não custa nada
- O snapshot de estilos enviado no login inclui só quem está **online**

## Adicionando arte nova

Solte o PNG na pasta certa e reexporte o jar — a GUI descobre sozinha (o catálogo recarrega
junto com o resto dos resource packs, então nem precisa reiniciar durante o desenvolvimento):

```
src/main/resources/assets/aurorion_talk/textures/gui/balloon/skin/   -> balão inteiro (nine-slice, folha 32x32)
src/main/resources/assets/aurorion_talk/textures/gui/balloon/deco/   -> enfeite (16x16, 32x32, o tamanho que for — é desenhado 1:1)
```

Layout nine-slice de uma skin (folha 32x32, veja `skin/balloon.png` como referência): cantos 5x5,
bordas esticadas de 1px, setinha 7x4 em (18,6).

## Status

Compilado e testado contra Minecraft 1.21.1 + NeoForge 21.1.248. O build do monorepo e o boot do
servidor dedicado carregando todos os mods fazem parte da verificação; a geometria nine-slice tem
testes unitários. Mudanças visuais ainda devem passar por um smoke test no cliente:

```bash
./gradlew :aurorion-runs:runClient
```
