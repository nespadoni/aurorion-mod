# Aurorion Intenção

O ritual da **batalha de intenção** dentro do jogo. A cena continua sendo jogada na mesa do Discord,
como uma sessão de RPG. O mod cuida do que acontece em volta dela: o Chamado, o Plano da Intenção e
o Veredito. Ninguém ganha atributo; a consequência mecânica fica com o mestre.

Instale no servidor **e** no cliente (há entidades e telas). Depende do `aurorion-core` 0.6.8 ou mais
novo.

## Duelo com a ficha (0.2.0)

Combate por turnos **feito pelo jogo**, no estilo de MMO, com os números da ficha do site (retrato do
`aurorion-ficha`). O mestre não precisa fazer nada; pode pausar, retomar ou parar.

```
/duelo desafiar <jogador> <limite>    limite: primeiro_sangue | 1-99 (% da vida) | morte
/duelo aceitar | recusar | desistir
/duelo pausar | retomar | parar <jogador>      (staff)
/intencao combate <participante> <limite>      (staff: combate com ficha dentro de uma batalha de intenção)
```

- **Desafio:** até `duelo.alcanceDesafio` (16) blocos. O desafiado recebe um cartão no meio da visão,
  responde apontando (○ aceita, × recusa) e tem `duelo.segundosDesafio` para isso. O chat só é usado para
  quem joga sem o mod. Os dois escolhem juntos **até que vida** vai o duelo.
- **Campo de não fuga:** raio `duelo.raioCampo` (12) em volta do ponto médio; quem passa da borda volta
  para dentro. Durante o duelo ninguém leva nem dá dano de verdade no Minecraft: a luta é na ficha.
- **A arena que a plateia vê:** quem está até `duelo.raioAviso` (48) blocos, e quem chegar depois, recebe
  um pacote e o cliente desenha:
  - uma parede de luz que sobe do chão, respira e tem faixas correndo em volta (violeta e dourado; vermelha
    no duelo até a morte);
  - o anel rente ao chão;
  - faíscas elétricas na quantidade que a config de partículas de cada um permite.
  - Raios só de efeito (sem fogo nem dano) caem nos duelistas na abertura, no alvo de cada crítico e em quem
    perde. O fim aparece à plateia no cartão de aviso, não no chat.
- **Abertura:** faixas de cinema, os nomes com o VS dourado e o limite. O número salta a cada segundo
  dentro de um anel que se fecha, com uma nota que sobe, e termina num "LUTEM!" com trovão.
- **Turnos:** contagem, depois a ordem por d20 + iniciativa. Na sua vez o menu abre em Ações (o gesto reabre;
  `duelo.segundosTurno` 30 s, passou se defende). Cada turno devolve 1 PE.
  - **Atacar** corpo a corpo (Corpo a corpo) ou à distância (Pontaria), contra a Defesa do alvo.
    - O dano segue o **RPG v0.3** (§04, §09, §10): `piso((soma dos d12 + MO) × E)`.
      - MO é o modificador de ataque, que entra uma vez no pacote.
      - **E = Vida de referência da alma ÷ 150**: alma 3 → 5/3, alma 4 → 320/150.
      - A alma vem da ficha. Ficha antiga sem alma usa a alma cuja Vida de referência fica mais perto da
        vida máxima.
    - Ataque básico: 3d12 + MO.
    - Crítico rola o dobro dos dados, soma o MO uma vez e só depois aplica E. Falha crítica deixa exposto
      (defesa −2).
    - Ordem: E e arredondamento → metade por Defender → RD → aparo → vida.
    - Na alma 4, o básico fica em média ~56: um duelo dura as 4–6 rodadas que o documento pede.
  - **Defender:** defesa +3 e metade do dano até a próxima vez, +1 PE.
  - **Técnicas** equipadas na ficha: custo = estrelas em PE; dano pelo pacote do nível da habilidade:
    H1 4d12, H2 5d12, H3 6d12, H4 8d12, H5 12d12 (+ MO, × E).
  - **Magias** do livro equipado que o Despertar Arcano permite: o mesmo teste da magia (CD da raridade,
    bônus e vantagem do especialista); custo = raridade + 1 PE; dano pelo pacote da raridade (comum = H1 …
    lendária = H5, + bônus da magia, × E). Falha crítica: exausto, perde a próxima vez.
  - **Desistir:** perde o duelo (nunca morre por desistir).
- **Reação cronometrada:** quando um golpe acerta, quem apanha vê um anel fechando até o instante do
  impacto. **Clique esquerdo apara, direito esquiva.**
  - **O tempo só dá bônus; quem decide é o dado.** A reação é um teste: d20 + Corpo a corpo (aparo) ou
    Acrobacia (esquiva) contra a **CD = o total do ataque** (d20 + bônus de quem atacou), que aparece no
    anel.
    - No instante perfeito: +`duelo.bonusTempoPerfeito` (5).
    - Dentro da janela: +`duelo.bonusTempo` (2).
    - Fora da janela, ou sem clicar, não há teste: o golpe entra inteiro.
  - **Aparo:** passou, metade do dano. Passou por 10 ou mais no instante perfeito, nada de dano e
    contra-ataque (2d12 + Força, × E de quem aparou, − RD). Falhou, o golpe inteiro.
  - **Esquiva:** passou, nada de dano. Falhou, o golpe inteiro (arrisca mais que o aparo).
  - 1 natural sempre falha e 20 natural sempre passa.
  - **Guarda cansada:** cada defesa bem-sucedida seguida tira `duelo.guardaCansada` (2) da próxima.
    Volta a zero quando um golpe entra ou quando a pessoa usa **Defender** no próprio turno. Aparar o
    tempo todo não segura o duelo para sempre.
  - O registro do duelo mostra a conta ("d20 13 → 20 contra 17, tempo +5, guarda cansada −2").
  - A janela começa em `duelo.reacaoMs` (180 ms) e cresce `reacaoMsPorPonto` (15) por ponto de
    Acrobacia ou Corpo a corpo, até `reacaoMsMaximo` (450).
- **Fim:** perde quem chegar ao limite combinado (a marca branca na barra de vida). No **duelo até a morte**
  (exige `duelo.permiteMorte` e o `aurorion-limbo`), quem zera a vida da ficha **perde o personagem**: a
  cena de fim do Limbo, direto, sem passar pelo Limbo (`CharacterFinale` do core). Sair do jogo no meio
  conta como derrota — e, com `duelo.fugaMata`, como morte.
- **Regras de proteção:**
  - **Um desafio por vez:** um convite aberto não pode ser trocado por outro (nada de trocar "primeiro
    sangue" por "até a morte" no instante do aceite). Entre dois desafios do mesmo jogador há
    `duelo.segundosEntreDesafios` (10).
  - **Desistir nunca mata**, nem no duelo até a morte.
  - Sair do jogo só mata com `duelo.fugaMata`, que agora vem **desligado**. Reinício do servidor encerra os
    duelos sem vencedor antes de qualquer logout contar como fuga.
  - **Duelo não é imortalidade:** só o dano entre os duelistas (e deles para fora) é cancelado. Mob, lava,
    queda e terceiros continuam valendo, e o duelo acaba empatado em `duelo.rodadasMaximas` (30).
  - **XP só de duelo lutado:** alguém causou dano, ninguém desistiu nem fugiu, e a mesma dupla só ganha de
    novo depois de `duelo.minutosXpMesmaDupla` (30).
  - **Tempo da reação conferido no servidor:** o instante que o cliente diz ter clicado precisa bater com a
    hora em que o pacote chegou (latência de até 600 ms). Um cliente que sempre diz "perfeito" cai fora
    da janela.
  - Montado não escapa do campo (desmonta ao voltar para dentro).
  - Os raios são desenhados no cliente de quem vê a arena: sem trovão para a dimensão, sem mexer em
    cobre ou para-raios.
  - O cliente precisa do `aurorion-menu` (convite, escolha da vez, resultado); o mods.toml exige.
- **Resultado:** cada duelista recebe um cartão no meio da visão (VITÓRIA, DERROTA ou EMPATE) com o seu
  personagem e o placar: dano causado e recebido, golpes que entraram, defesas, críticos e XP ganho.
  - O XP é a experiência do vanilla: `duelo.xpVitoria` (50) e `duelo.xpDerrota` (15); quem desiste ou sai
    do jogo não ganha.
  - Nada vai para o chat (só para quem joga sem o mod).
- Dentro de uma batalha de intenção, `/intencao combate` usa os lados da sessão e o veredito do Plano no fim
  (até a morte só no `/duelo`).
- **No menu do personagem** (`aurorion-menu`; o intencao só registra no `CharacterMenu` do core):
  - **Ações**: aparece só na sua vez, e o menu abre sozinho nela. Atacar › corpo a corpo / à distância,
    Defender, Técnicas ›, Magias ›, Desistir (com confirmação); com mais de um inimigo, a lista de alvos.
  - **Duelo**: em duelo, a rodada, o limite, a vida de cada um e Desistir; fora dele, quem está até 8
    blocos → o limite → enviar (até a morte pede confirmação ○/×).
  - Em **Interações**, sobre um jogador perto: "Desafiar para duelo" com a lista de limites.
  - O convite chega como janela com ○ (aceitar) e × (recusar), além da linha no chat.
  - Sem o `aurorion-menu`, tudo continua pelo `/duelo` e pelas linhas clicáveis do chat; o HUD avisa a vez.
- **HUD do duelo** com o desenho liso do core (`MenuUi`): barra de vida em paralelogramo com a marca do
  limite, PE, de quem é a vez e o anel da reação.
- **Custo:** duelos vivem em memória; cada um só age quando o relógio dele vence ou chega um pacote.
  - O campo confere a borda a cada 10 ticks.
  - O servidor não manda partícula nenhuma por tick: a plateia recebe **um** pacote `Arena` ao chegar e um
    no fim, e o cliente desenha o resto.
  - A cada 2 s o servidor procura quem chegou perto (só os jogadores da dimensão, só com duelo aberto).
  - Pacotes de estado só para os duelistas, a cada mudança.
  - No cliente, a parede é um quad por segmento (64), e só para arenas a até 96 blocos.

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
