# Aurorion Portais

**Toda dimensão exceto o overworld fica trancada.** O acesso só abre em janelas agendadas — os
"trens" — ou por decisão explícita da staff.

Não há integração com mod nenhum, e não existe lista de portais conhecidos para manter: a regra é
negar por padrão, então uma dimensão de mod que apareça no modpack amanhã **já nasce trancada**.

## Como funciona, do ponto de vista do jogador

1. Ao longo da semana, o chat avisa: *"O trem da linha Expresso Ígneo parte em 15min. Esteja na
   Estação Central para embarcar!"*
2. Na hora exata, a passagem abre: *"Expresso Ígneo — embarque liberado! A passagem fica aberta pelos
   próximos 10min."* Nesse intervalo os portais para aquela dimensão funcionam normalmente.
3. No último minuto, quem está **dentro** recebe contagem regressiva na actionbar.
4. A janela fecha. Quem ficou, ficou — até o próximo trem.

`/portais` mostra o quadro de horários a qualquer momento.

## A regra: entrar e sair custam a mesma janela

Sair de uma dimensão controlada exige a passagem aberta, igual a entrar. Isso não é detalhe de
implementação — é a mecânica inteira. Se só a entrada fosse controlada, voltar ao overworld seria
sempre permitido e a aventura não teria risco.

Disso decorre o resto:

- **Morrer não é bilhete de volta.** Quem morre dentro renasce dentro. Sem isso, quem perdeu o trem
  se jogaria na lava para voltar de graça ao spawn.
- **Não há exceção embutida — nem a do End.** Matar o dragão e entrar no portal de retorno mostra os
  créditos e devolve o jogador ao End, como qualquer outro respawn.
- **Cama ou âncora dentro da própria dimensão continuam valendo.** Se o vanilla já ia renascer ali
  dentro, não há nada a corrigir.

Quem escapa da regra: staff (nível 2 por padrão), criativo/espectador, e quem tiver um **passe**.

## Linhas (datapack)

As linhas **não estão em código**. Cada uma é um JSON; mudar o dia da partida no meio da temporada é
editar arquivo e dar `/reload`, sem rebuild e sem derrubar o servidor.

```json
// data/<namespace>/aurorion/linhas/nether.json
{
  "name": { "text": "Expresso Ígneo" },
  "description": { "text": "Linha semanal com destino ao Nether." },
  "color": "#FF5555",

  "dimensions": ["minecraft:the_nether"],

  "station": {
    "dimension": "minecraft:overworld",
    "pos": [0, 64, 0],
    "name": { "text": "Estação Central" }
  },
  "arrival": {
    "dimension": "minecraft:the_nether",
    "pos": [0, 64, 0],
    "name": { "text": "Plataforma Ígnea" }
  },

  "schedule": {
    "timezone": "America/Sao_Paulo",
    "weekly": [
      { "days": ["saturday"], "at": "20:00" }
    ],
    "openMinutes": 10,
    "warnMinutesBefore": [60, 15, 5, 1]
  },

  "order": 0
}
```

| Campo | Obrigatório | O que faz |
|---|---|---|
| `name` | sim | Nome da linha no chat e no `/portais`. É um `Component` (aceita cor, negrito, tradução) |
| `dimensions` | sim | Que dimensões esta linha destranca. Lista, para um mod com dimensões irmãs caber numa linha só — e num aviso só |
| `schedule` | sim | Ver abaixo |
| `description` | não | Texto de apoio |
| `color` | não | `#RRGGBB`, tinge o nome. Padrão `#FFAA00` |
| `station` | não | Plataforma de embarque, citada nos avisos |
| `arrival` | não | Plataforma de desembarque — **e ponto de respawn de quem morrer lá dentro** |
| `order` | não | Ordem no `/portais` |

O `id` da linha vem do caminho do arquivo, como receita ou loot table.

### `schedule`

| Campo | Padrão | O que faz |
|---|---|---|
| `timezone` | fuso da máquina | Id IANA, ex.: `America/Sao_Paulo`. **Declare sempre** |
| `weekly` | `[]` | Lista de `{ "days": [...], "at": "HH:mm" }`. `days` ausente = todo dia |
| `everyMinutes` | `0` | Intervalo fixo, ancorado na meia-noite local. `360` = 00:00, 06:00, 12:00, 18:00. `0` desliga |
| `openMinutes` | `10` | Quanto tempo a passagem fica aberta |
| `warnMinutesBefore` | `[60, 15, 5, 1]` | Antecedência de cada chamada, em minutos |

`weekly` e `everyMinutes` convivem: se os dois estiverem preenchidos, vale a partida que vier
primeiro. Dá para escrever "de 6 em 6 horas, e mais uma extra no sábado à noite" sem sintaxe nova.

**Declare `timezone`.** Sem ele o servidor usa o fuso da máquina onde está hospedado, que quase nunca
é o fuso dos jogadores. E é tempo real, não tempo de jogo: o dia do Minecraft dura 20 minutos e para
com `doDaylightCycle false`, então não serve de relógio.

**Declare `arrival`.** É a única opção de respawn que o servidor *sabe* que é boa, porque quem
construiu a estação garantiu. Sem ela, o mod procura um vão seguro na coluna onde a pessoa morreu, e
só cai no spawn da dimensão se não achar nada.

## Comandos

| Comando | Nível | O que faz |
|---|---|---|
| `/portais` | todos | Quadro de horários: o que está aberto, e quando cada linha parte |
| `/portais abrir <linha> [minutos]` | 2 | Abre a passagem na hora. Sem `minutos`, usa o `openMinutes` da linha |
| `/portais fechar <linha>` | 2 | Fecha antes da hora |
| `/portais passe dar <jogador> <dimensão> [usos] [minutos]` | 2 | Concede passe. Padrão: 1 uso, sem prazo |
| `/portais passe ver <jogador>` | 2 | Lista os passes válidos |
| `/portais passe tirar <jogador> [dimensão]` | 2 | Revoga. Sem dimensão, revoga todos |

O comando informa, nunca transporta — quem atravessa é o portal, no horário.

Fechar antes da hora **não faz a linha pular a partida agendada** que ainda vem: se você fechar às
19h uma janela manual e a partida de sábado 20:00 ainda não chegou, ela acontece normalmente.

### Passes

Um passe vale **nos dois sentidos**, de propósito: o que tira alguém do Nether é o mesmo que põe. É
o único jeito de funcionar como *resgate*, que é o caso de uso que importa para quem perdeu o trem.

`TransitPass` (dimensão + validade + usos) é gravado por UUID e já funciona sem nenhum item que o
conceda — é o encaixe pronto para o item de "abrir portal" planejado, que só vai precisar chamar
`PassData#grant`.

## Config

`config/aurorion_portais-server.toml`. Só regra de servidor; horário é datapack.

| Chave | Padrão | O que faz |
|---|---|---|
| `enforce` | `true` | Chave mestra. `false` libera tudo na hora, sem apagar linha nem passe |
| `freeDimensions` | `["minecraft:overworld"]` | As únicas nunca controladas |
| `lockUnscheduledDimensions` | `true` | Dimensão controlada sem linha fica trancada de vez |
| `bypassPermissionLevel` | `2` | Nível que ignora os portões. `5` = ninguém |
| `bypassCreative` | `true` | Criativo/espectador ignora |
| `blockPortalsEarly` | `true` | Ver abaixo |
| `keepPlayersOnDeath` | `true` | Morrer dentro renasce dentro |
| `announceInChat` | `true` | Chamadas no chat |
| `countdownSeconds` | `60` | Contagem na actionbar de quem está dentro. `0` desliga |
| `checkIntervalTicks` | `20` | 20 = 1x por segundo |
| `denyMessageCooldownSeconds` | `5` | Intervalo entre dois "passagem fechada" para a mesma pessoa |
| `logDenials` | `false` | Loga toda viagem barrada, com origem e destino |

### `enforce` é o botão de emergência

Num modpack pesado, o conflito aparece no pior horário. `enforce = false` devolve o servidor ao
comportamento vanilla imediatamente, sem precisar reeditar datapack às pressas — e nada é perdido:
linhas e passes continuam lá, o relógio continua correndo, e religar volta tudo ao lugar.

### `logDenials` é como você descobre dimensão de mod

Instalou um mod novo? Ligue `logDenials`, jogue um pouco e leia o log. Se um mod usa uma dimensão
própria para mecânica interna (uma mina, um minigame), ela vai aparecer barrada ali — e é o sinal de
que ela precisa entrar em `freeDimensions`.

### `blockPortalsEarly`

Barra o portal **na entrada**, antes de o servidor calcular o destino. Vale bastante: sem isso, cada
jogador que encosta num portal do Nether fechado faz o servidor varrer o destino atrás de um portal
existente e, não achando, **escavar um novo** — trabalho pesado e lixo permanente no mundo,
multiplicado pela população online.

A checagem só conhece a origem, então em um caso raro ela erra para o lado de barrar: um mod que use
portal para viagem *dentro da mesma dimensão*. Se isso acontecer, desligue — a viagem continua
bloqueada pela regra principal, só fica mais cara.

## Como testar

```bash
./gradlew :aurorion-runs:runServer     # 1. sobe o servidor
./gradlew :aurorion-runs:runClient     # 2. entra como Dev1
```

Como o dev entra com permissão de operador, ele **escapa dos portões** por padrão. Para testar como
jogador comum, suba `bypassPermissionLevel` para `5` ou entre em sobrevivência sem OP.

Roteiro rápido:

1. `/portais` — as duas linhas de exemplo aparecem fechadas, com a próxima partida.
2. `/portais abrir aurorion_portais:nether 2` — chamada no chat, e o portal do Nether passa a
   funcionar por 2 minutos.
3. Entre no Nether e espere a janela fechar: a contagem regressiva aparece na actionbar e você fica
   preso do lado de dentro.
4. Morra lá dentro: você renasce no Nether, não no spawn.
5. `/portais passe dar Dev1 minecraft:the_nether` — agora o portal de volta funciona, uma vez.

## Estrutura

```
line/       TransitLine, Schedule, Departure, Station, LineCatalog  — o datapack
runtime/    LineClock, TransitClock  — o relógio (long comparado por segundo)
            TransitGate              — quem pode atravessar o quê
            TransitAnnouncer, DenyNotifier, RespawnAnchor, TimeFormat
pass/       TransitPass, PassData    — passes por UUID
mixin/      PortalEntryMixin         — otimização, não regra (ver SDD §8.3)
```

Decisões de design e por quê: [SDD §8](../SDD.md).
