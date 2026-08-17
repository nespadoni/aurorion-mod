# Aurorion Aeonita

Mod de **conteúdo** do ecossistema: itens e blocos. É o único lugar do monorepo que registra coisa
que entra no inventário do jogador — e é justamente por isso que ele não tem mecânica de ato nenhum.

A divisão é essa:

| | mod de conteúdo (`aurorion-aeonita`) | mod de ato (`aurorion-ato2`, e os próximos) |
|---|---|---|
| registra item/bloco | sim | **nunca** |
| tempo de vida | fica para sempre | sai do modpack quando o ato acaba |
| desligar custa | inventário e construções | só a mecânica |

Se o Altar de Seleção fosse registrado pelo mod do Ato 2, tirar o Ato 2 do modpack apagaria os
altares já construídos no mundo. Ele mora aqui; quem dá significado a ele é o mod do ato.

## Conteúdo

**Itens** — `aeonita_ingot_yellow`, `aeonita_ingot_blue`, `aeonita_ingot_red`. Comestíveis em
qualquer situação (`alwaysEdible`), 4 de nutrição, e sempre dão Brilho por 10 s ao serem comidos.

**Blocos** — `aeonita_block_<cor>` (armazenamento 9:1, emitem luz 10 de verdade) e `selection_altar`
(pedestal de três degraus, luz 7, forma própria).

Receita do altar: os três lingotes na linha de cima, `polished_deepslate` no resto.

### Luz dinâmica

Qualquer item da tag `aurorion_aeonita:emits_light` acende onde estiver — na mão, no chão ou dentro
de um quadro. É **ilusão local**: troca o ar da posição pelo `minecraft:light` invisível na cópia do
mundo do próprio cliente. O servidor nunca fica sabendo e nada disso entra no save.

Como é tag e não lista em código, dá para fazer o lingote de outro mod acender sem recompilar nada:

```json
// data/<seu_datapack>/tags/item/... ou um datapack do servidor
{ "replace": false, "values": ["outromod:cristal_brilhante"] }
```

## Configuração

`config/aurorion_aeonita-client.toml` — só existe porque a luz dinâmica é a única parte deste mod
que custa frame time. Num modpack pesado é o primeiro item a sacrificar por FPS, e desligar ela não
tira nenhum item, bloco ou receita do jogo.

```toml
[dynamicLight]
enabled = true
lightLevel = 15
```

## Arquitetura

```
registry/    AeonitaBlocks, AeonitaItems, AeonitaCreativeTab, AeonitaTags
block/       SelectionAltarBlock (so a VoxelShape do pedestal — sem estado, sem block entity, sem tick)
client/      DynamicLightHandler
config/      AeonitaClientConfig
```

### Decisões

- **Ordem de registro é dependência real, não estilo.** `AeonitaItems` referencia os
  `DeferredBlock` para criar os `BlockItem`, então `AeonitaBlocks.BLOCKS.register(bus)` vem antes no
  construtor do mod. Inverter as duas linhas quebra o carregamento.
- **A luz dinâmica não aloca por tick.** Ela roda por tick de cliente, então cai na meta de "zero
  alocação por frame" do [SDD §2](../SDD.md): as duas coleções são campos reaproveitados (`fastutil`
  com chave `int`, sem boxing de `Integer` por entidade por tick), a posição da entidade viva é
  calculada num `MutableBlockPos` reaproveitado, e a varredura de quem sumiu — a única parte que
  aloca um iterador — só roda quando há de fato entidade a remover.
- **O custo que sobra é inerente ao truque**: cada mudança de posição é um `setBlock`, que refaz
  iluminação do chunk. Não dá para otimizar isso sem trocar de técnica; dá para desligar, e é o que
  a config existe para permitir.
- **O que acende é dado, não código.** Tag de item em vez de `stack.is(A) || stack.is(B) ||
  stack.is(C)` — [SDD §7](../SDD.md), diretriz 4.
- **`SelectionAltarBlock` não sabe o que é uma casa.** Ele é um bloco decorativo com forma própria e
  mais nada. Quem escuta o clique e abre a escolha é o `aurorion-ato2`, casando por tag de bloco —
  sem import, sem dependência de compilação, e o Ato 3 pode reaproveitar o mesmo altar para outra
  coisa sem tocar aqui.

## Arte

As texturas de bloco (`aeonita_block_*`, `selection_altar_*`) são **placeholders gerados** a partir
da paleta dos lingotes originais — gema lapidada nas três cores e pedra escura com runa dourada no
altar. São 16×16 comuns: para trocar por arte de verdade, basta substituir o PNG.

## Status

Compila contra NeoForge 21.1.248 / MC 1.21.1. **Ainda não testado em jogo** — o que conferir na
primeira execução:

- Se os três elementos do modelo do altar batem com a `VoxelShape` de `SelectionAltarBlock`
  (silhueta e colisão desenhadas separadamente, é o típico de sair desalinhado).
- Se a luz dinâmica ainda funciona com `Block.UPDATE_ALL` no `setBlock` do cliente — comportamento
  herdado do protótipo original, mantido de propósito por já ter sido testado assim.

```bash
./gradlew :aurorion-aeonita:runClient
./gradlew :aurorion-aeonita:build
```
