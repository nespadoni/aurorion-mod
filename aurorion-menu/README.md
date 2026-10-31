# Aurorion Menu

O menu do personagem em estilo de MMO: um lugar só para a ficha, as interações e o resto do personagem,
liso e nas cores Aurorion (marfim, violeta profundo e dourado). O mod é o **host**. Quem diz o que aparece
são os outros mods, pelo registro `CharacterMenu` do `aurorion-core`. O menu não conhece ficha nem duelo.

Precisa estar no cliente e no servidor; o AutoModpack leva aos dois.

## Para o jogador

- **É um painel no mundo, não uma tela:** ele aparece flutuando na sua frente, um pouco abaixo da linha
  dos olhos, em primeira e em terceira pessoa. A câmera continua livre e dá para andar: o painel vai junto
  e, quando a cabeça vira além de 38°, gira junto.
- **Interagir:** aponte com a mira (o centro da visão), e um ponto de luz mostra onde ela toca.
  - Clique esquerdo escolhe. Clique direito volta um passo (fecha a janela, a última lista, o cartão e,
    no fim, o menu).
  - A rodinha rola o cartão. Enter confirma a janela ○/×. Esc fecha.
  - Fora do painel, os cliques voltam a ser do jogo. Abrir outra tela (inventário, chat) fecha o menu.
- **Abrir e fechar:**
  - pelo **gesto**: mão principal vazia, segure o botão direito e puxe a mira para baixo, rápido. A mira
    volta para onde estava;
  - por um **toque na tecla da ficha**, que abre direto no Perfil;
  - por uma tecla própria, que vem sem atribuição (Opções → Controles → Aurorion: Menu), porque Y e U já
    são de outros mods do pack.
  - Com o menu aberto, o gesto e a tecla fecham.
- **Navegar:** uma coluna de círculos desce, uma seção por círculo. Escolher uma seção abre a lista ao lado
  e, quando ela tem cartão, um cartão grande à esquerda. O Perfil abre com a **vitrine**: o seu personagem
  de corpo inteiro, virando o rosto para onde você aponta, com a armadura e as mãos em volta.
- **Seções de hoje:**

  | Seção | Mod | O que tem |
  |---|---|---|
  | Ações | intencao | só na sua vez no duelo |
  | Perfil | ficha | a ficha |
  | Interações | este mod | Meditar e os jogadores até 8 blocos, com o que os mods oferecem sobre cada um (hoje, desafiar para duelo) |
  | Duelo | intencao | estado do duelo ou desafiar alguém |

- **Janelas ○ / ×:** confirmações e convites (o convite de duelo, por exemplo) aparecem por cima do menu,
  com prazo quando têm.
- **Avisos na sua frente:** conquistas e os avisos da ficha aparecem num cartão branco, com o seu
  personagem ao lado. O cartão flutua um pouco acima da linha dos olhos e acompanha o olhar com atraso.
  Desafio concluído toca o som do vanilla. As conquistas deixam de usar o aviso do canto; os outros avisos
  do vanilla continuam lá.
- **Quem está perto vê:** o braço erguido descendo no gesto e um painel holográfico borrado na frente do
  peito, voltado para onde a pessoa olha, sem nada legível. Você vê o painel de verdade, não o borrado.
  Some ao fechar, a mais de 32 blocos, na boneca do inventário e no passe de sombra do Iris.
- **Meditar** (Interações):
  - Só no chão, fora da água, sem montaria, sem dano há `segundosForaDeCombate` (10 s) e sem hostil a
    `raioHostil` (8) blocos.
  - Toca o emote **Meditar** do Emotecraft quando o mod está no pack. Sem ele, uma pose sentada própria.
  - Recupera `cura` (1) de vida a cada `segundosCura` (3 s), com partículas.
  - Para ao andar, levar dano, atacar ou quebrar bloco, ou em **Parar de meditar**.

## Config do servidor (`config/aurorion/menu-server.toml`)

| Chave | Padrão | O que faz |
|---|---|---|
| `presenca.habilitada` | `true` | manda aos outros o estado "menu aberto" / "meditando" (braço, painel, pose) |
| `meditar.habilitado` | `true` | liga a meditação |
| `meditar.segundosCura` | `3` | intervalo entre curas |
| `meditar.cura` | `1.0` | vida por cura |
| `meditar.raioHostil` | `8` | hostil a esta distância impede começar |
| `meditar.segundosForaDeCombate` | `10` | tempo sem levar dano para poder começar |

## Config de cada um (`config/aurorion/menu-client.toml`)

| Chave | Padrão | O que faz |
|---|---|---|
| `painel.largura` | `2.25` | largura do painel no mundo, em blocos |
| `painel.distancia` | `1.35` | distância até os olhos |
| `painel.grausAteSeguir` | `38` | quanto a cabeça vira antes de o painel vir junto |
| `geral.gesto` | `true` | abrir pelo gesto (as duas mãos vazias, mira no ar, botão direito, puxar para baixo) |
| `geral.conquistasNoCartao` | `true` | conquistas no cartão; desligue se usar outro mod de avisos de conquista |

## Para quem escreve outro mod

Registre no cliente, na montagem (`FMLClientSetupEvent`):

```java
CharacterMenu.register(new MinhaSecao());              // id, título, ícone, ordem, visible(), entries(), panel()
CharacterMenu.registerInteraction(new CharacterMenu.Interaction("meu_id", 20, alvo -> Entry.action(...)));
```

- `CharacterMenu.open("perfil")` abre numa seção, `prompt(new Modal(...))` mostra a janela ○/×,
  `refresh()` redesenha quando os dados chegam, `close()` fecha e `notice(new Notice(...))` mostra o cartão
  de aviso.
- O menu não é uma `Screen`: `setScreen(null)` não o fecha, e o cartão nunca recebe `charTyped`.
- Sem este mod, `installed()` é `false` e os três devolvem `false` ou não fazem nada: o seu mod segue o
  caminho antigo.
- Desenhe cartões com o `MenuUi` do core, para tudo ficar com a mesma cara.

## Rede e custo

- **Pacotes:**
  - `Request(kind, value)`, do cliente: menu aberto ou fechado, começar ou parar de meditar;
  - `Presence(uuid, flags)`, do servidor: vai a quem rastreia a pessoa e a ela, e o `StartTracking`
    entrega a quem chega depois.
- **Limite:** abrir o menu vale no máximo 4 vezes por segundo por pessoa. Fechar nunca é descartado, e
  começar a meditar já apaga o menu aberto.
- **O que roda por tick:** a meditação percorre só a lista de quem está meditando, a cada 10 ticks. Nenhum
  outro sistema varre jogadores.
- **No cliente:** o painel borrado e a pose só existem para quem tem a flag. O painel próprio e o cartão
  de aviso são uma textura cada, pintada só enquanto estão à vista e desenhada como um quad no fim do nível.

## Arte

Formas e fonte vêm do core (`textures/gui/menu/`, gerado por `aurorion-core/tools/generate_ui.py`; fonte
Rajdhani, OFL). O emote `meditar.emotecraft` é do dono do servidor. Mods e repositórios de terceiros
serviram só de referência visual; nada foi copiado.
