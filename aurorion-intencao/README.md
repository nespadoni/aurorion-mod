# Aurorion Intenção

O ritual da **batalha de intenção** dentro do jogo. A cena continua sendo jogada na mesa do Discord,
como uma sessão de RPG. O mod cuida do que acontece em volta dela: o Chamado, o Plano da Intenção e
o Veredito. Ninguém ganha atributo; a consequência mecânica fica com o mestre.

Instale no servidor **e** no cliente (há entidades e telas). Depende do `aurorion-core` 0.6.6 ou mais
novo.

## Comandos (staff, `permissionLevel`)

| Comando | O que faz |
|---|---|
| `/intencao iniciar <tema> <jogadores> <rivais>` | Abre a batalha. Os dois grupos aceitam nome ou seletor (`@a[team=x]`) e são os **dois lados**. De 2 a 8 participantes. |
| `/intencao clima <participante> calmo\|tenso\|ruptura` | Muda o tom da cena: neblina, volume, batimento e tremor de câmera. Não mostra rolagem. |
| `/intencao encerrar <vencedor>` | Veredito. A sessão é a do vencedor, e **o lado dele inteiro vence**: em time contra time, basta nomear um. |
| `/intencao empate <participante>` | Veredito de empate. |
| `/intencao listar` | Sessões abertas, com tema, clima e tempo. |
| `/intencao recuperar <jogadores>` | Devolve quem ficou preso no plano (pela âncora, ou para o spawn). |

`encerrar`, `empate` e `clima` aceitam participante offline: o mestre pode dar o veredito mesmo que
alguém tenha caído da conexão.

## Como é para quem joga

1. **O Chamado (5 s).** A tela escurece, o coração acelera, os nomes aparecem frente a frente e as
   pálpebras se fecham. O corpo fica pesado. Quem está perto vê um selo no chão.
2. **O Plano.** No lugar de cada participante fica um **Eco**: a silhueta sentada, translúcida e com
   o nome "em transe". Ela não pode ser atacada. No plano:
   - cada metade da arena tem a cor da Casa de um participante, e fios de luz ligam os rivais;
   - os **fragmentos de memória** flutuam pela arena e, com clique direito, dizem uma frase do tema;
   - há rastro de luz nos passos, som ambiente em laço e sussurros espaçados;
   - no topo da tela fica a linha *"a cena acontece na mesa do Discord"*, com o relógio;
   - ninguém leva dano nem quebra bloco, e quem passa da borda volta ao seu lugar.
3. **O Veredito (3,5 s).**
   - O vencedor vê um clarão na cor da Casa e volta com uma aura de 60 s que todos veem.
   - O perdedor vê a tela rachar e volta agachado por 3 s, com a vista turva.
   - No empate, o plano vira cinza.
   - Quem está perto vê uma onda de choque no retorno.

## Temas (datapack)

`data/<namespace>/aurorion/intencao_tema/<id>.json`. Todos os campos são opcionais:

```json
{
  "name": "aurorion_intencao.tema.tribunal",
  "structure": "meu_pack:arenas/ruinas_da_memoria",
  "radius": 14,
  "spawn_radius": 6,
  "fog": "#23203A",
  "ambience": "minecraft:ambient.soul_sand_valley.loop",
  "whisper": "minecraft:ambient.soul_sand_valley.mood",
  "fragments": 7,
  "phrases": ["Uma frase", "minha_chave.de_traducao"],
  "call": "aurorion_intencao.chamado",
  "win": "aurorion_intencao.veredito.vitoria",
  "lose": "aurorion_intencao.veredito.derrota",
  "draw": "aurorion_intencao.veredito.empate"
}
```

- **`structure`**: um `.nbt` salvo com o Structure Block. Ele é colocado centralizado, e a camada
  y=0 do arquivo vira o chão. Sem este campo (ou se o arquivo não existir), o mod monta o
  **Tribunal de Vitrais** procedural: um disco de vitral sobre lanternas, com cada fatia no vitral de
  corante mais próximo da cor da Casa.
- **`radius`** é a borda jogável. **`spawn_radius`** é a distância do centro em que cada
  participante aparece, de frente para o centro.
- Os textos aceitam chave de tradução ou texto direto.
- `/intencao iniciar tribunal ...` resolve para `aurorion_intencao:tribunal`.

## Dados e segurança

- Sessões e âncoras de retorno ficam em `data/aurorion_intencao_sessoes.dat`, fora do NBT do jogador.
  Elas sobrevivem a morte, relog e restart.
- Quem estava offline no veredito volta ao entrar.
- A âncora sai antes da volta e é reposta se outro mod vetar o teleporte. Ela nunca se perde.
- Sair do plano por fora (`/spawn`, `/tp` da staff) desfaz o Eco. No próximo login, o participante
  de uma batalha ainda aberta é levado de volta.
- Teleportes passam pelo `TwilightTeleportCompat` do core.
- **Travas de dimensão.** O `aurorion-portais` tranca toda dimensão fora de `freeDimensions`, o plano
  inclusive. A ida e a volta do ritual não dependem dessa config: elas usam o mesmo desenho da Porta do
  Esquecido do Limbo (SDD §9.2).
  - A autorização vale para um jogador, um destino e um tick.
  - Um listener `LOWEST` com `receiveCanceled` desfaz o veto só dessa viagem.
  - Fora do ritual (portal, `/tp` de quem não é staff) o plano continua trancado.

  Isso também passa por cima de outras travas na hora do chamado, como as do exílio, da criação de
  personagem e das amarras do magia. A batalha é aberta pela staff, e a volta é sempre pela âncora.
- Quem é chamado no meio de um pulo ou de um voo de élitro deixa o Eco e a âncora **no chão abaixo**,
  e não no ar, para a volta não virar uma queda.
- As arenas usam sempre a menor célula livre a partir da origem do plano, e não um rodízio. Assim
  elas não saem da borda do mundo, e fora da borda o clique nos fragmentos não funciona.
- Dois rivais da mesma Casa recebem cores diferentes: o segundo pega uma da paleta.
- `timeoutMinutes` (padrão 180) desfaz em empate uma batalha esquecida.

## Custo

Tudo é por evento. O único laço é a borda da arena, a cada 10 ticks, e só sobre participantes de
sessões abertas; sem batalha, custa uma checagem de lista vazia. A arena é montada uma vez por
batalha, num volume de ~35×35×16 blocos com flag 2. Partículas são geradas no cliente com orçamento
fixo; nenhuma viaja pela rede.

## Fora do escopo (por enquanto)

- Rolagens ao vivo no jogo.
- Escolhas mecânicas.
- Bônus de espectador.
- Aviso automático no Discord. O backend recusa tipos de fato desconhecidos em
  `internal/timeline/facts.go`, então publicar `GameFacts` exige adicionar os tipos lá antes.
