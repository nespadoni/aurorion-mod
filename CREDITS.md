# Créditos e obrigações de licença

## Talk Balloons

O `aurorion-talk` é um fork do [Talk Balloons](https://github.com/CERBON-MODS/Talk-Balloons), de
**CerbonXD / CERBON-MODS**, licenciado sob **GNU LGPL-3.0**.

Herdamos deste projeto:

- A ideia e o layout do balão nine-slice, incluindo a textura
  `assets/aurorion_talk/textures/gui/balloon/balloon.png` (arquivo original, inalterado)
- A matemática de posicionamento e empilhamento dos balões (`BalloonRenderer`)
- Os dois pontos de injeção em `ChatListener` que capturam a fala — chat assinado e o caminho de
  mensagem de sistema usado por plugins de chat e pelo No Chat Reports (`ChatListenerMixin`)

O que **não** veio do original e foi escrito para o Aurorion:

- Ocultação das falas no HUD do chat (`ChatComponentMixin`, `ChatSuppressor`, `IncomingChat`)
- Estilos de balão por jogador definidos pelo servidor: registry, comandos, persistência e
  sincronização (`style/`, `server/`, `network/`)
- Renderização em batch pelo `MultiBufferSource` no lugar de `GuiGraphics` em modo imediato
- Config nativa do NeoForge e a tela de configuração in-game
- Estrutura de projeto single-loader NeoForge (o original é multiloader via Architectury)

### O que a LGPL-3.0 exige de nós

- Este repositório **precisa** continuar LGPL-3.0 (ou GPL-3.0). Não dá para fechar o código nem
  relicenciar como MIT.
- Distribuir o `.jar` (CurseForge, Modrinth, ou só passar para os jogadores) obriga a
  disponibilizar o código-fonte correspondente e manter os avisos de copyright.
- Mudanças relevantes devem ficar registradas — é para isso que serve este arquivo.
