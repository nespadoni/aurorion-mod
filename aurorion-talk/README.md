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
**Personalização do Balão**: dois previews ao vivo (fala curta e fala longa), uma grade de 30 cores,
um campo de **hexadecimal** para a cor exata, e um botão para escolher skin/enfeite. Fechar a tela
salva e avisa o servidor. Um botão "Preferências" leva às opções pessoais (duração, tamanho, altura
— essas não são sincronizadas, são só gosto de cada um).

A grade e o campo pintam **uma coisa de cada vez**: dois botões no topo escolhem se você está mexendo
na cor do balão ou na do texto. Antes eram duas paletas empilhadas, porque as listas eram diferentes;
agora a lista é a mesma, então duas grades seriam a mesma imagem ocupando o dobro da tela.

### Qualquer cor, sempre legível

O mod aceitava só cores de duas listas fechadas — dezesseis pastéis de balão, dezesseis escuras de
texto. A razão era boa: ninguém pode escolher balão preto com texto preto, porque **todo mundo
precisa conseguir ler a fala dos outros**. O preço era não dar para usar a cor da sua casa, do seu
clã, nem nada vivo.

A proibição virou correção. Qualquer cor entra, e o **texto** é empurrado para perto do preto ou do
branco até passar do contraste mínimo — preservando o matiz o quanto der, e parando na primeira cor
que já dá para ler. A conta é a luminância relativa da WCAG, a mesma que qualquer verificador de
contraste usa, com limite 3,0 (a fala do balão é curta e grande; exigir os 4,5 de texto corrido
achataria escolha demais).

Quem decide é o **servidor**, em `BalloonStyle#normalized()`: a garantia não pode depender de uma GUI
que é do cliente e pode ser trocada. A tela avisa antes, para a correção não parecer um bug.

## Arquitetura

```
style/           BalloonStyle (skin + enfeite + 2 cores) + grade/contraste + convenção de pastas
network/         SetStylePayload (cliente -> servidor), Sync/Update (servidor -> clientes)
server/          SavedData (persistência por jogador), validação, broadcast
client/          catálogo de texturas (scan em runtime), cache de estilos, renderer 3D, GUI
client/render/   geometria nine-slice compartilhada entre o balão 3D e o preview 2D da GUI
mixin/           4 mixins, todos client-side
```

**O estilo inteiro trafega na rede — não um id de catálogo.** O jogador monta o balão na GUI a
partir do que existe nas pastas de textura; o cliente manda pro servidor, que **valida a forma**
(`BalloonStyle#isWellFormed`) antes de persistir e propagar, porque um cliente modificado poderia
mandar qualquer caminho de textura. A cor não é recusada — é normalizada, como acima.

### Notas de performance (servidor de 80 jogadores)

- Ocultar a fala do chat é O(1) e não toca na cadeia de assinaturas do chat seguro
- A quebra de linha do texto (`Font#split`) acontece **uma vez por mensagem**, não por frame
- Os quads vão para o `MultiBufferSource` da cena; nada de `GuiGraphics` novo a cada frame
- **Config lida uma vez por tick**, não uma vez por jogador por frame: cinco leituras × 80 jogadores
  × 60 quadros eram 24 mil chamadas por segundo para buscar cinco números que não mudam dentro do
  mesmo tick
- **O yaw da câmera é memoizado por quadro.** O render é chamado uma vez por jogador, mas o ângulo é
  o mesmo para todos; comparar quatro floats é mais barato que dois `atan2`, e a câmera parada nunca
  recalcula
- **Nenhum iterador por quadro.** O laço das linhas de texto passou a ser por índice — era a última
  alocação por balão por frame que sobrava
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
