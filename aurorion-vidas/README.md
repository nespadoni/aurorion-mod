# Aurorion Vidas

Cada jogador tem um número limitado de vidas. Cada morte gasta uma. **Ao zerar, a pessoa é exilada
para o Nether e não sai de lá por conta própria** — nem pegando o trem.

As vidas aparecem no HUD, numa fileira logo acima da barra de fome.

## O ciclo

1. Você morre. O chat te avisa em particular: *"Você perdeu uma vida. Restam 4 de 5."* O HUD apaga um
   ícone.
2. Na última morte, o servidor inteiro fica sabendo: *"Fulano perdeu a última vida e foi exilado."*
3. Você renasce no ponto de exílio, no Nether. Morrer de novo te devolve ao mesmo ponto — o exílio
   não afunda mais.
4. A única saída é alguém te devolver uma vida: `/vidas dar <você> 1`, ou o item de resgate.

## A dupla com o aurorion-portais

O exílio só tem peso porque o [aurorion-portais](../aurorion-portais/) mantém o Nether trancado.
Mas **os dois mods não se conhecem**: não há import nem dependência declarada entre eles. A
coordenação é por eventos do vanilla.

| | Como funciona | Por quê |
|---|---|---|
| **Ida** | `PlayerRespawnPositionEvent` | Respawn não passa por `changeDimension`, então o portão do outro mod nem é consultado — não foi preciso abrir nele uma exceção de "teleporte de sistema" |
| **Volta** | `EntityTravelToDimensionEvent` | Os dois mods cancelam o mesmo evento, cada um pelo seu motivo: o `portais` porque a janela está fechada, o `vidas` porque a pessoa está exilada |
| **Empate** | Prioridade `LOW` | Morrer no End sem vidas aciona os dois; o `vidas` roda por último e o exílio vence |

Cada um funciona sozinho. Sem o `portais`, o exílio continua acontecendo — só fica mais fraco,
porque o exilado sai pelo primeiro portal que encontrar.

### Por que o exilado não pega o trem

`blockExileExit` (ligado por padrão) impede o exilado de sair da dimensão de exílio **mesmo quando o
trem abre a passagem para todo mundo**. É o que dá sentido à taxa de resgate: se bastasse esperar
sábado à noite, ninguém pagaria nada.

## O ponto de exílio

**Construa a chegada e marque com `/vidas exilio aqui`.** Todos os exilados aparecem no mesmo lugar,
então vale a pena ser um lugar de verdade — uma recepção, uma cela, uma plataforma.

Se ninguém marcar nada, no primeiro exílio o mod procura um vão seguro perto do spawn da dimensão,
**grava** o resultado e escreve um aviso no log. Funciona, mas é sorteio: a busca só garante que você
não nasce dentro de pedra nem de lava.

O ponto fica no save do mundo, não na config — é uma coordenada, tem que acompanhar o que foi
construído e sumir junto quando o mundo for trocado.

## Comandos

| Comando | Nível | O que faz |
|---|---|---|
| `/vidas` | todos | Quantas vidas você ainda tem |
| `/vidas ver <jogador>` | 2 | Vidas de outra pessoa (funciona offline) |
| `/vidas definir <jogador> <n>` | 2 | Define o valor exato |
| `/vidas dar <jogador> [n]` | 2 | Soma. **É assim que se tira alguém do exílio** |
| `/vidas tirar <jogador> [n]` | 2 | Subtrai |
| `/vidas exilio aqui` | 2 | Marca o ponto de chegada na sua posição |
| `/vidas exilio ver` | 2 | Mostra o ponto atual |

`dar` é o caminho de saída do exílio, e é o mesmo que o item de resgate vai usar quando existir —
ele só precisa chamar `LivesManager.addLives(...)`.

## Config

`config/aurorion_vidas-server.toml`:

| Chave | Padrão | O que faz |
|---|---|---|
| `maxLives` | `5` | Vidas por jogador, e quantos ícones aparecem no HUD |
| `ignoreCreative` | `true` | Morte em criativo/espectador não gasta vida |
| `exileDimension` | `"minecraft:the_nether"` | Para onde vai quem zerou |
| `blockExileExit` | `true` | Exilado não sai nem com o trem aberto |
| `announceExileInChat` | `false` | Anuncia o exílio no chat de todo mundo (com som) |
| `announceLifeLoss` | `false` | Anuncia **cada** vida perdida. Desligado porque com 90 jogadores morte é rotina e o vanilla já anuncia a morte |

`config/aurorion_vidas-client.toml`:

| Chave | Padrão | O que faz |
|---|---|---|
| `showHud` | `true` | Mostra os ícones acima da barra de fome |

Desligar o HUD não muda regra nenhuma — as vidas continuam sendo gastas.

## Trocando o ícone

Os ícones são **sprites de GUI**, não código. Substitua os dois PNG:

```
assets/aurorion_vidas/textures/gui/sprites/hud/life_full.png    (vida cheia)
assets/aurorion_vidas/textures/gui/sprites/hud/life_empty.png   (vida gasta)
```

Os placeholders são 9×9 — a mesma medida dos corações e das coxinhas do vanilla, que é o que faz a
fileira alinhar com o resto do HUD. Se você usar outro tamanho, o jogo estica para 9×9 na tela, então
o ideal é manter 9×9 ou um múltiplo (18×18, 36×36) para a arte não borrar.

Dá para trocar **por resource pack**, sem tocar no jar e sem recompilar nada.

A cor do placeholder é âmbar de propósito, e não vermelha: o vermelho já é a vida do vanilla, do
outro lado da tela.

## Como testar

```bash
./gradlew :aurorion-runs:runServer
./gradlew :aurorion-runs:runClient
```

Em criativo você não perde vidas (`ignoreCreative`), então entre em sobrevivência para testar:

1. `/gamemode survival` — os 5 ícones aparecem acima da fome.
2. `/kill` — some um ícone e chega a mensagem em particular.
3. Repita até zerar: o servidor anuncia o exílio e você acorda no Nether.
4. Tente voltar pelo portal: barrado, mesmo se o trem do `aurorion-portais` estiver aberto.
5. `/vidas dar Dev1 1` — o HUD volta a mostrar uma vida e a saída destrava.

## Estrutura

```
lives/     LivesData (SavedData por UUID), LivesManager (regras), ExileSpot (ponto de chegada)
event/     VidasServerEvents  — morte, respawn, veto de saída
network/   SyncLivesPayload   — dois inteiros, só para o dono da tela
client/    ClientLives, LivesHudLayer, VidasClientEvents
command/   VidasCommand
```

Decisões de design e por quê: [SDD §9](../SDD.md).
