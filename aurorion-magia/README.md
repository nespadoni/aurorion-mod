# Aurorion Magia

Addon do [Iron's Spells 'n Spellbooks](https://github.com/iron431/irons-spells-n-spellbooks) para o
Aurorion. **Nenhuma magia vem liberada**: a staff ensina magias soltas ou escolas inteiras por
personagem, em aula, com `/aurorion spells`. O estado é espelhado no Iron's Restrictions.

Vinte e três magias autorais (três delas **proibidas**), cada uma com um **nome conhecido** e uma
**invocação** (o id), mais duas **passivas**: marcas que ficam no personagem em vez de serem
conjuradas. As magias servem para duelo, captura, interrogatório, perseguição, fuga, invasão
e RP do dia a dia. Quatro delas mudam de forma quando conjuradas **agachado** (ver "Agachado, em
área"), e três se desfazem conjurando de novo no mesmo alvo (Prostração, Voz Interdita, Mundo Vazio).

Versões de referência (jars em `mod-servidor-referencia/`): Iron's Spells `1.21.1-3.16.3`, Iron's
Restrictions `1.21.1-5.2.0`, Emotecraft `2.4.12`, Simple Voice Chat `1.21.1-2.6.24`.

## Liberação de magias

```
/aurorion spells unlock spell  <magia>  <alvos>
/aurorion spells unlock school <escola> <alvos>
/aurorion spells lock   spell  <magia>  <alvos>
/aurorion spells lock   school <escola> <alvos>
/aurorion spells list <jogador>
```

- `<alvos>` é seletor vanilla: `@a`, `@a[team=sonserina]`, `@p`, nome. Só pega quem está online:
  libera quem estava na aula.
- O Tab sugere as magias e escolas registradas no Iron's, incluindo as de outros addons.
- Regra: pode conjurar se a **magia** estiver liberada **ou** a **escola dela** estiver liberada.
- Os ids das magias deste mod são `aurorion_magia:dolor_cruciatus` e `aurorion_magia:imperium_mentis`.
  O namespace segue o `mod_id`, como em todo mod do ecossistema.
- O jogador recebe o aviso "Você aprendeu: …" na hora.
- As liberações são do **personagem**: morte definitiva zera tudo (`CharacterResetEvent`).
- Staff (permissão 2+) conjura tudo enquanto `staffBypass = true`.

Exemplos:

```
/aurorion spells unlock spell aurorion_magia:dolor_cruciatus @a[team=sonserina]
/aurorion spells unlock school irons_spellbooks:blood @a
```

### Onde a liberação é conferida

| Ponto | Mecanismo | Lado |
|---|---|---|
| Conjurar (livro, scroll, arma imbuída) | `SpellPreCastEvent` cancelado | servidor |
| Gravar no livro ("equipar") | `InscribeSpellEvent` cancelado | servidor |
| Livro de pesquisa, scroll, cliente | `learnedSpells` do Iron's, lido pelo Restrictions | ambos |

### Ponte com o Iron's Restrictions

O Restrictions não tem lista própria. Ele força `requiresLearning() = true` em todas as magias, e
assim o Iron's só aceita as que estão em `SyncedSpellData.learnedSpells`. A ponte é **manter esse
conjunto igual à lista oficial** (`SpellAccess.reconcile`). Isso acontece no login (prioridade
`LOWEST`, depois do handler do Restrictions), a cada comando e no reset de personagem. Nunca por tick.

Não há `import` de classe do Restrictions: a dependência é opcional de verdade. Sem ele, o gate do
`SpellPreCastEvent` continua bloqueando.

O gate próprio existe porque o Restrictions tem duas brechas: arma ou livro **imbuído** conjura sem
aprender (quando `ImbuedItemsRequireLearning = false`), e **manuscritos** ensinam a qualquer momento.

Config recomendada do Restrictions no servidor:

| Chave | Valor | Por quê |
|---|---|---|
| `DefaultLearntSpells` | `[]` | Com `authoritative`, o que ele ensinar é esquecido no login |
| `ImbuedItemsRequireLearning` | `true` | O gate já bloqueia, mas assim a UI também mostra |
| `StartingRarity` | `LEGENDARY` se a raridade não for progressão no RP | O Restrictions tem **um segundo gate, de raridade**, que este mod não controla |

O gate de raridade do Restrictions continua valendo depois do nosso. Se a staff liberar uma magia
épica para alguém com raridade `COMMON`, o Iron's ainda recusa. Isso vale para staff também.

### `authoritative` (padrão `true`)

- `true`: a lista do Aurorion é a única fonte. O que o jogador aprendeu por fora (manuscrito, magias
  padrão, eldritch pesquisado) é esquecido no próximo login/comando e não conjura.
- `false`: o aprendido por fora vale junto com as liberações.

Custo do reconcile: uma passada pelo registro de magias (~150 entradas) e no máximo dois pacotes de
sincronização, só quando algo muda. Login sem mudança não manda nada.

## Magias

| Nome conhecido | Invocação (id) | Escola | Tipo | Para quê |
|---|---|---|---|---|
| Dolor Cruciatus | `dolor_cruciatus` | Sangue | contínua 4 s | dor que paralisa |
| Imperium Mentis | `imperium_mentis` | Eldritch | longa 1,5 s | dominar mob, desorientar jogador |
| Vínculo do Carrasco | `vinculum_carnificis` | Sangue | instantânea | prender alguém a uma área |
| Transposição | `transpositio` | Ender | instantânea | trocar de lugar com o alvo |
| Mão do Algoz | `manus_carnificis` | Evocação | contínua 5 s | segurar e arremessar |
| Mão Vazia | `manus_vacua` | Evocação | instantânea | desarmar |
| Prostração | `genua_flecte` | Eldritch | instantânea | forçar a ajoelhar, de mãos atadas |
| Olhar Cativo | `aspectus_captus` | Eldritch | instantânea | prender o olhar de um, ou da praça |
| Voz Interdita | `vox_interdicta` | Eldritch | instantânea | tirar e devolver a voz |
| Mundo Vazio | `mundus_vacuus` | Eldritch | instantânea | apagar o mundo dos olhos de alguém |
| Lacre Profano | `sigillum_clausum` | Ender | instantânea | trancar porta/baú |
| Queda Forçada | `deiectio_corporis` | Ender | instantânea | derrubar quem voa, ou tudo em volta |
| Devorar Luz | `lux_vorata` | Eldritch | longa 1 s | apagar luzes e cegar quem está dentro |
| Ferro Vinculado | `ferrum_ligatum` | Sangue | instantânea | travar a armadura |
| Afogamento | `submersio` | Gelo | instantânea | afogar alguém em terra firme |
| Maremoto | `unda_magna` | Gelo | instantânea | abrir espaço, empurrar todo mundo |
| Cárcere de Água | `carcer_aquae` | Gelo | longa 1 s | prender um alvo numa bolha |
| Vento Guardião | `ventus_custos` | Evocação | instantânea | barreira que empurra para fora |
| Coluna de Vento | `columna_venti` | Evocação | instantânea | subir, e descer de pena |
| Turbilhão | `turbo_ventorum` | Evocação | longa 1 s | furacão que anda puxando gente |
| Tempo Suspenso ⛔ | `tempus_sistere` | Ender | longa 1 s | congelar tudo em volta |
| Sentença Final ⛔ | `mortem_dico` | Eldritch | instantânea | morte instantânea, de um ou de todos |
| Tormento Coletivo ⛔ | `dolor_universus` | Sangue | contínua 3–7 s | Cruciatus em área, no ar |

Os ids completos são `aurorion_magia:<invocação>`. Escola, nível máximo, raridade e cooldown são
só o padrão; ver "Trocar escola" abaixo.

### Agachado, em área

Quatro magias têm duas formas, e o gatilho é o mesmo em todas: **conjurar agachado**.

| Magia | Em pé | Agachado |
|---|---|---|
| Queda Forçada | um alvo mirado | onda em volta de você (6 a 14 blocos) |
| Olhar Cativo | um alvo mirado | todos em volta (14 a 30 blocos) |
| Sentença Final ⛔ | um alvo mirado | todos em volta (nível 2+: 8 ou 14 blocos) |
| Mão Vazia | mão principal | mão secundária |

Ninguém em volta para atingir: a magia é recusada antes de gastar mana ou entrar em recarga. Quem
conjura nunca está na própria área, e chefes (`imune_deslocamento`), espectadores e staff em criativo
ficam de fora das versões em área — mirados um a um, continuam valendo alvo.

### Dolor Cruciatus
Mira do Iron's, alcance 20. Enquanto o botão fica apertado (até 4 s), dá um pulso a cada 10 ticks.
O pulso confere se o alvo está vivo, no alcance e à vista, causa dano sem empurrão, renova o efeito
`cruciatus` (velocidade e pulo zerados) e reenvia o feixe. O alvo jogador sente um tremor de câmera.
**Sob tortura não se conjura nem se usa nada**: enquanto o `cruciatus` durar, o alvo não solta magia e
nenhum item responde na mão dele — nem comida, nem arco, nem escudo, nem totem. Vale igual para o
Tormento Coletivo, que usa o mesmo efeito.
**Visual:** feixe em hélice vermelho e negro da mão ao peito, e um selo de espinhos sob o alvo.

### Imperium Mentis
- **Mob:** fica `dominado` (8 s + 4 s/nível) e ataca o vivo mais próximo que não seja o mestre, um
  aliado de time, um pet do mestre ou outro dominado do mesmo mestre.
- **Jogador:** fica `desorientado` (6 s + 2 s/nível, 10 s no 3). Os controles invertem, a tela
  escurece e ele não usa magia nem item. Era 4 s, e nos testes a cena não tinha tempo de
  acontecer: a pessoa perdia o controle, olhava em volta sem entender e já estava livre.
- **Imunes:** tag `aurorion_magia:imune_dominacao`.
- **Visual:** espiral jade/prata descendo pelo corpo, coroa rúnica girando na cabeça e um selo que
  se abre no chão.

### Vínculo do Carrasco — *Vinculum Carnificis*
O ponto onde o alvo foi atingido vira a âncora de uma corrente de 6 blocos, por 10 s + 2 s/nível.
- O alvo anda, bate e conjura à vontade dentro do raio.
- Ao cruzar a borda, é puxado para a âncora **e sangra**: 3 de dano no primeiro puxão, +1 por ponto de
  tensão, até 11 (cinco corações e meio). O dano tem intervalo próprio de 1 s, então quem insiste na
  borda leva um golpe por segundo, e não os cinco do tick da corrente. Ele ignora armadura e escudo, e
  a mensagem de morte nomeia quem lançou a corrente.
- Cada puxão seguido aumenta a tensão, e o próximo vem mais forte e mais caro. Ficar dentro alivia a
  tensão.
- Pérola, chorus, teleporte de magia do Iron's, enderman e troca de dimensão são **cancelados** com
  o estalo da corrente. Teleporte sem evento (outro mod) que o leve para longe faz a corrente
  arrancá-lo de volta **para a própria âncora** — o único ponto que a magia sabe ser chão firme,
  porque foi de lá que o alvo foi preso. (Antes a corrente o largava num ponto qualquer da borda,
  calculado nos três eixos: quem tinha sido teleportado para cima reaparecia no ar e despencava. Era
  essa a queda do nada.) O `/tp` da staff passa.

**Visual:** selo de elos na âncora e o círculo do limite no chão. A corrente, com elos alternados e
núcleo escuro, só aparece quando está tensionada, e estala em vermelho a cada puxão.

### Transposição — *Transpositio*
Troca de lugar instantânea com o alvo (alcance 24 + 6/nível). **A velocidade e a queda acumulada
também trocam**. Aceita aliado, para salvar alguém da lava ou trazer um colega para dentro da
muralha. Chefes são imunes (`aurorion_magia:imune_deslocamento`).
**Visual:** selo do End com lanças de luz nas duas pontas, arco de portal entre elas e colunas de
partículas subindo.

### Mão do Algoz — *Manus Carnificis*
Telecinese controlável por até 5 s. Enquanto o botão fica apertado, o alvo é levado para 4,5 blocos
à frente dos seus olhos: virar a mira o arrasta, olhar para cima o levanta. Ao soltar, ele conserva
o movimento, ampliado, mais um empurrão na direção da mira. Arremessado rápido que bate em parede ou
teto leva dano proporcional à velocidade.
**Visual:** filamentos violeta da mão ao alvo, anéis rúnicos girando na cintura dele e runas caindo.

### Mão Vazia — *Manus Vacua*
Arranca o item da mão principal do alvo e o joga no chão, entre vocês. **Agachado**, arranca o da
mão secundária (escudo, totem, foco). O item fica 2 s sem poder ser pego. Nada é destruído.
**Visual:** um selo prateado se abre na mão do alvo, de frente para você, com estilhaços de luz.

### Prostração — *Genua Flecte*
O alvo é virado de frente para você e cai de joelhos por 5 s + 1,5 s/nível. Ele quase não anda, não
pula e não corre. **As mãos também ficam atadas**: nenhum item responde (comida, arco, escudo, totem)
e nenhuma magia sai. A voz continua livre de propósito — a magia serve para ouvir um pedido de
desculpa, não para calar.
- **Duas conjurações:** a segunda no mesmo alvo manda levantar. O professor que libera o aluno, o
  carrasco que muda de ideia.
- Com o **Emotecraft**, o servidor força o emote `Kneel down` (embutido no nosso jar) e todos veem
  a pose. O emote é reiniciado se o jogador relogar ou trocar de dimensão.
- Sem o Emotecraft, o ajoelhado fica agachado.

**Visual:** três anéis rúnicos descem sobre os ombros, fica um selo lilás no chão e sobem almas de
tempos em tempos.
### Olhar Cativo — *Aspectus Captus*
"Olhe para mim quando eu falar com você." Por 2 s (+0,5 s por nível, 4 s no 5), a câmera e a
cabeça do alvo ficam presas em quem conjurou. Ele anda devagar (−60%) e fala normalmente, mas não
consegue desviar o rosto. Os outros veem a cabeça virar, porque a rotação sai do cliente do alvo
como qualquer movimento. Em mob, a IA encara o conjurador.

**Agachado, a praça inteira.** "Olhem para mim, todos vocês." Um olho enorme se abre sobre quem
conjura e todos no raio viram o rosto para ele — 14 blocos no nível 1, +4 por nível (30 no 5), até 24
pessoas e criaturas. É a magia de discurso, de julgamento, de entrada de vilão: ninguém consegue olhar
para outro lado enquanto você fala.

**Visual:** um olho violeta se abre sobre a cabeça do cativo, encarando o captor, com um fio de
olhar ligando os dois. Na tela de quem está preso aparece um túnel escuro (ver "Possessão"). Em área,
o olho é o do centro, do tamanho de um bloco e meio, com o círculo do alcance desenhado no chão.
### Voz Interdita — *Vox Interdicta*
Só em jogador, por 5 s + 1,25 s/nível (10 s no nível 5). Durante o efeito:
- o microfone no **Simple Voice Chat** não chega a ninguém (proximidade, grupo e sussurro);
- o chat de texto é recusado;
- nenhuma magia sai.

O alvo ainda anda, bate e foge. O corte de voz é um plugin do Voice Chat (`AurorionVoicePlugin`) que
consulta um mapa concorrente, porque roda na thread de áudio.

**Duas conjurações:** a primeira tira a voz, a segunda no mesmo alvo devolve, sem esperar o tempo
correr. Interrogatório é isso — tirar a palavra e devolvê-la quando convier.
**Visual:** colar negro na garganta com anel rúnico violeta girando e fumaça escura.

### Mundo Vazio — *Mundus Vacuus*
Herdeira do antigo Sequestro de Impulso: em vez de roubar o movimento de alguém, rouba **o mundo
inteiro** de quem olha. Só em jogador, alcance 20, por 10 s + 5 s/nível (30 s no 5).

O alvo continua exatamente onde estava, e todos continuam em volta dele — mas o cliente dele **para de
desenhar qualquer vivo**: pessoas, criaturas, montarias, nomes flutuantes. Ele fica sozinho num mundo
vazio. Leva dano de ninguém, ouve passos de ninguém, conversa com ninguém.

- **Ninguém some de verdade.** O servidor nunca deixa de saber quem está onde; o dano chega, o som
  toca, e para todos os outros a cena é normal. O que muda é só o que aquele par de olhos vê. É por
  isso que a magia é uma tortura e não uma vantagem: a pessoa apanha de um corredor vazio.
- **Duas conjurações:** a segunda no mesmo alvo devolve o mundo.
- **Não é invisibilidade.** Não há como usá-la para se esconder de alguém que você não atingiu, e ela
  não dá vantagem nenhuma a quem conjura.

**Visual:** nenhum selo brilhante — a magia é a ausência. Só um fio de runa fechando o corpo do alvo,
uma casca escura rente a ele e, na tela dele, uma sombra fria nas bordas com um chiado de riscos.
A tela quase não muda de propósito: se mudasse, ele saberia na hora que levou magia em vez de concluir
sozinho que os outros foram embora.
### Lacre Profano — *Sigillum Clausum*
Olhe para uma porta, alçapão, portão, baú, barril, **mochila no chão** ou qualquer outra coisa com
inventário e sele. Lacrar fecha o que estiver aberto.

| Nível | Quem abre | Duração |
|---|---|---|
| 1 | só você | 1 min |
| 2 | você e seu time | 3 min |
| 3 | você e seu time | 9 min |

- Conjurar no seu lacre o desfaz. No lacre de outra pessoa, com nível igual ou maior, **quebra**;
  com nível menor, o lacre resiste.
- O lacre bloqueia abrir, quebrar, **carregar embora** (Carry On) e explosões.
- Staff em criativo passa.
- Porta dupla (as duas metades) e baú duplo compartilham o lacre.

#### O que aceita lacre, nos mods do pack

A regra não é uma lista de mods. São cinco perguntas, na ordem:

1. está na tag `aurorion_magia:nao_selavel`? então nunca — é a saída de emergência da staff;
2. é porta, alçapão ou portão **pela classe**? quase todo mod de decoração (Macaw's, Quark,
   FramedBlocks, Handcrafted…) estende as do vanilla, então entra aqui sem ninguém listar nada;
3. está na tag `aurorion_magia:selavel`? entra — é para o que não estende as classes do vanilla;
4. o bloco tem `Container`? baú, barril, shulker e a maioria dos baús de mod;
5. **um funil conseguiria tirar item de lá?** essa é a que pega o pack inteiro. É a capacidade
   `ItemHandler` do NeoForge, e todo bloco com inventário a expõe — senão nenhum cano, funil ou
   máquina do pack o enxergaria. Mochila do Sophisticated Backpacks colocada no chão, barril e cofre
   do Sophisticated Storage, item vault do Create, baús de mods que nem estão instalados ainda: todos
   caem aqui, sem uma linha de código por mod.

A conferência só roda na conjuração, uma vez por lacre — nunca por tick.

**Carry On:** sair carregando o baú lacrado seria a brecha óbvia. Ele pega blocos no mesmo
`RightClickBlock` que nós, e também em prioridade `HIGH`; entre dois listeners de mesma prioridade a
ordem é a de registro, ou seja, sorte. Por isso o nosso está em `HIGHEST`.

Os lacres ficam em memória, com teto de 1024 por dimensão, e somem no reinício do servidor. Quem
entra na dimensão recebe os lacres ativos.

**Visual:** um selo violeta desenhado na face do bloco, respirando. Ele dá um clarão quando alguém
tenta abrir e se estilhaça quando é quebrado.

**Limites:** redstone ainda abre porta lacrada, e funil ainda puxa de baú lacrado.
### Queda Forçada — *Deiectio Corporis*
- **No ar** (pulo, levitação, elytra, voo de sobrevivência, preso na Mão do Algoz): tira elytra,
  levitação, queda lenta e voo, e crava o alvo para baixo. A altura vira dano de queda pelo vanilla.
- **No chão:** dano de impacto e `abatido` (sem pular) por 1,5 s + 0,5 s/nível.

**Agachado, tudo o que estiver no ar cai junto.** Não precisa mirar em ninguém: a onda abre no chão
sob quem conjura e todos dentro do raio recebem a mesma queda ao mesmo tempo — 6 blocos no nível 1, +2
por nível (14 no 5), até 16 alvos. É o fim de uma fuga de elytra em grupo, ou de uma invasão vindo pelo
alto. Quem já estava no chão só leva o impacto e fica sem pular.

É o contra natural das magias de movimento. Chefes são imunes.
**Visual:** selo de espinhos que desce sobre a cabeça, rastros de vento para baixo e, ao tocar o
chão, poeira do próprio bloco em anel com uma onda de choque. Em área, a onda abre do centro até a
borda rasgando o chão, e o selo de espinhos fica marcado com o tamanho do raio.
### Devorar Luz — *Lux Vorata*
Raio de 5 + nível blocos em volta de quem conjura. A primeira versão só apagava vela e fogueira, e na
prática não acontecia nada: o lugar continuava iluminado por tocha, lanterna e lâmpada de mod, e
ninguém sentia diferença. Agora a magia devora a luz em três camadas, e a do meio é a que importa.

1. **Apaga o que tem como apagar.** Qualquer bloco no raio que esteja aceso (`lit` e emitindo luz) se
   apaga: velas, bolos com vela, fogueiras, lâmpadas e lanternas de mod que usem `lit`. Nada é
   quebrado nem sai do lugar — acende de novo com isqueiro ou redstone. Fogo no chão se apaga junto.
   Fica de fora o que estiver na tag `aurorion_magia:inapagavel` (fornalha, lâmpada de redstone e
   afins: apagar o forno do cozinheiro não é magia de combate). A tag `aurorion_magia:apagavel`
   continua servindo para forçar blocos que não usam `lit`.
2. **Cega quem está dentro.** Todos no raio, **menos quem conjurou e os aliados de time dele**,
   recebem a **Escuridão** do vanilla pelo tempo da zona, mais um instante de Cegueira no baque. É a
   escuridão do Warden: a tela fecha em pulsos e o mundo some, com ou sem tocha na mão. Era isto que
   faltava — a magia agora *faz* alguma coisa com quem está ali.
3. **Deixa a zona escura** por 8 s + 2 s/nível: neblina negra fechando em ~5 blocos, desenhada pelo
   cliente, e quem estiver pegando fogo apaga.

Tocha e lanterna do vanilla continuam sem ter estado "apagada", e apagá-las exigiria quebrá-las, o que
não é magia de griefing — a Escuridão é o que cobre esse caso. Respeita proteção de spawn
(`mayInteract`). Quem **entra** na zona depois da conjuração pega só a neblina, não a Escuridão: a zona
não fica vigiando ninguém.

**Visual:** a luz é sugada para o centro em fumaça, fica uma mancha escura no chão com a borda
violeta se fechando e sobem partículas de vazio.
### Ferro Vinculado — *Ferrum Ligatum*
Só em jogador, por 10 s + 5 s/nível. A armadura e o que estiver na mão secundária ficam presos:
tirar pelo inventário, trocar por outra peça ou jogar fora com Q devolve a peça ao corpo. A
durabilidade continua sendo gasta normalmente.
**Visual:** três anéis de elos girando em volta do corpo, em sentidos alternados, com faíscas.

### Afogamento — *Submersio*
Alcance 18, em qualquer alvo. O alvo continua de pé no meio da praça, seco, e começa a se afogar: a
barra de bolhas dele esvazia, o corpo pesa (−35% de velocidade) e, quando o ar acaba, vem o
afogamento do vanilla, 2 de dano a cada meio segundo, até o tempo correr — 6 s no nível 1, +2 s por
nível (14 s no 5).

**Ela gasta o ar do vanilla, e não um contador próprio.** É a decisão que mais importa nesta magia:
com ela, tudo o que já existe em volta continua valendo de graça — a barra de bolhas some na tela do
alvo, Respiração no elmo segura mais tempo, poção de respirar embaixo d'água salva, leite tira o
efeito e sair dele enche o ar de volta como sair de um mergulho.

**Duas conjurações:** a segunda no mesmo alvo devolve o ar. Tirar o fôlego e devolvê-lo quando
convier é a mesma lógica da Voz Interdita — a magia serve para conduzir uma conversa, não só para
matar. A mensagem de morte nomeia quem conjurou (`aurorion_magia:submersio`).

**Visual:** bolhas escapando da boca do alvo (o único sinal, de fora, de que alguém está se afogando
em pé no meio da rua), anéis de água subindo pelo corpo e o selo de ondas no chão. Na tela dele, a
água fecha conforme o ar acaba, com a superfície balançando no alto — o ar que está logo ali e não se
alcança.

### Maremoto — *Unda Magna*
Não tem mira: é a magia de **abrir espaço**. Cercado numa viela, no meio de uma multidão ou prensado
contra a muralha, a onda tira todo mundo de cima de você ao mesmo tempo, apaga o fogo de quem estiver
queimando e varre até as flechas em voo.

| Nível | Raio |
|---|---|
| 1 | 7 |
| 2 | 9 |
| 3 | 10 |
| 4 | 12 |
| 5 | 13 |

- **Em pé**, a onda sai na direção em que você olha, num arco de 120° à frente.
- **Agachado**, ela sai em círculo: quem está cercado não tem um lado para escolher.
- Perto do centro a onda está inteira; na borda ela já se abriu e empurra menos.
- O empurrão é forte e o dano é pequeno de propósito: quem joga alguém de um penhasco com esta magia
  matou pela queda, e não pela água.
- Teto de 24 atingidos. Chefes (`imune_deslocamento`) e criativo ficam de fora.

**Visual:** a crista correndo do centro até a borda em respingo e espuma, e o selo de ondas quebradas
no chão.

### Cárcere de Água — *Carcer Aquae*
Uma esfera de água se fecha em volta do alvo (alcance 16) e o suspende no ponto em que ele foi pego,
por 8 s + 3 s/nível (20 s no 5). Ele não anda, não pula, não foge e não é levado por ninguém — mas
continua respirando (afogar é a outra magia da escola) e continua vendo e ouvindo tudo. É a magia de
**captura** da água: prender um só, inteiro, para conversar.

**A bolha é quebrável de fora.** Qualquer golpe de qualquer pessoa a estoura, e quem estava dentro sai
solto; o dano do golpe some na água. É o que separa esta magia de uma prisão: capturar alguém no meio
da praça sem ninguém poder tirá-lo de lá vira impasse; com resgate, vira cena. Conjurar de novo no
mesmo alvo também a desfaz.

O afogamento do Submersio **não** rompe a bolha: afogar alguém dentro dela é uma combinação válida das
duas magias da água, e não um jeito torto de se libertar.

O ponto fica no `persistentData` do preso, como a âncora do Vínculo: deslogar preso e voltar continua
preso, no mesmo lugar. Chefes ficam de fora.

**Visual:** três faixas de água fechadas em volta do corpo, escurecendo o que está atrás, com um anel
rúnico girando na cintura e o selo de ondas no chão.

### Vento Guardião — *Ventus Custos*
A defensiva da escola do vento. No instante da conjuração, todos dentro do círculo (raio 3,5 — o 7x7
circular) são arremessados para fora dele. Depois disso fica a **barreira**: enquanto durar, quem
tentar entrar é empurrado de volta, cada vez mais forte quanto mais fundo conseguir furar. Duração de
6 s no nível 1, +1,5 s por nível (12 s no 5).

- Quem ergueu a barreira **passa por ela à vontade**.
- Ela **não cura e não protege de dano**: flecha, magia e bola de fogo atravessam. O que ela compra é
  distância, que é a moeda de quem está em um contra três. Curar junto teria feito dela a única magia
  defensiva que alguém levaria.
- A barreira **não anda com você**: nasce onde você estava. Recuar para dentro dela é uma decisão;
  arrastá-la junto seria imunidade ambulante.

**Visual:** parede de vento fechada em volta do círculo, com o anel de vento no chão e um anel rúnico
girando no topo; ela estala como vidro ao acabar.

### Coluna de Vento — *Columna Venti*
A de exploração. Uma corrente ascendente de 3x3 e 6 blocos de altura fica em pé no chão por 5 s +1 s
por nível (9 s no 5), onde você estiver olhando (até 12 blocos), sempre em cima do primeiro chão
firme. Quem entrar nela sobe, e ganha **queda lenta** pelo tempo que sobrar da coluna: dar a volta
numa muralha, subir um penhasco, tirar o grupo de um buraco, descer de uma torre sem morrer.

**Ela não escolhe lado.** Quem conjurou, os aliados e quem está perseguindo sobem igual — e aí está a
graça: usada na hora errada, ela dá ao inimigo a mesma altura que deu a você. A única coisa que ela
nunca faz é machucar. Como ela nasce onde você olha, também serve para levantar outra pessoa.

**Visual:** funil ao contrário, estreito embaixo e aberto em cima, com a espiral do vento marcada no
chão.

### Turbilhão — *Turbo Ventorum*
A ofensiva. Um funil de vento nasce 2,5 blocos à sua frente e **anda em linha reta**, na direção em
que você estava olhando, acompanhando o relevo a 6 blocos por segundo. Quem estiver no caminho é
**puxado para o eixo** e levantado do chão: o furacão não empurra, ele recolhe. Enquanto a pessoa
estiver dentro, leva um golpe por segundo.

| Nível | Raio | Duração | Percurso | Dano/s |
|---|---|---|---|---|
| 1 | 2,0 | 4 s | 24 | 2,0 |
| 2 | 2,5 | 5 s | 30 | 2,25 |
| 3 | 3,0 | 6 s | 36 | 2,5 |
| 4 | 3,5 | 7 s | 42 | 2,75 |
| 5 | 4,0 | 8 s | 48 | 3,0 |

É o oposto exato do Maremoto: a onda abre espaço, o turbilhão junta gente. As duas na mesma briga
viram uma sequência — puxar o grupo para o eixo e jogar todo mundo do penhasco.

**Ele não atravessa parede.** Dois blocos à frente, se houver sólido **no peito e na cabeça** ao
mesmo tempo, ele se desfaz ali. Exigir as duas alturas é o que separa uma muralha de um tronco de
árvore: um furacão que morre no primeiro tronco de uma floresta não é magia ofensiva, é fogo de
artifício. É o que
impede a magia de ser um aríete de invasão: numa muralha ela morre do lado de fora. Teto de 20
atingidos por tick; chefes e criativo ficam de fora. A mensagem de morte nomeia quem conjurou
(`aurorion_magia:turbo_ventorum`), e armadura e encantamento continuam valendo — o vento não ignora
nada.

**Visual:** funil largo em cima e fechado embaixo, girando rápido, escurecendo o que cobre, com a
espiral correndo no chão e rajadas saindo dele.

#### As três de vento são uma entidade, e não um efeito

As outras vinte magias do mod são "efeito de status no alvo", porque o alvo já é conhecido na
conjuração. Estas três não têm alvo: elas são *um lugar* que reage a quem passar por ele depois — e o
turbilhão ainda anda. Isso precisa de um relógio, e a regra do módulo é não assinar
`ServerTickEvent` ([SDD §5.1](../SDD.md)).

Entidade resolve exatamente isso pelo caminho do vanilla: o jogo já tica entidade carregada, já a
sincroniza para quem está perto e já a descarrega com o chunk. Uma zona parada custa o mesmo que um
item no chão; não existindo zona nenhuma, custa zero. E **nenhum pacote nosso** entra nessas três
magias: forma, raio, altura e duração são sincronizados uma vez, no nascimento, e o relógio corre dos
dois lados pelo `tickCount` que o vanilla já incrementa. As partículas e a geometria saem do
`SpellZoneRenderer`, no cliente.

É um `EntityType` só (`aurorion_magia:zona_de_magia`) para as três formas, e não três: registro
sincronizado é conteúdo que nunca mais sai do modpack ([SDD §6.1](../SDD.md)).

### Magias proibidas ⛔

Tempus Sistere, Mortem Dico e Dolor Universus são magias **proibidas**. Jogador nenhum as consegue
sozinho; só a staff concede.

| Regra | Como |
|---|---|
| Não se crafta | `allowCrafting` e `canBeCraftedBy` sempre falsos (nem o config do Iron's liga) |
| Não cai em loot | `allowLooting` falso: nada de baú de estrutura ou drop |
| Não vem com a escola | `unlock school` **não** concede proibida; só `unlock spell <id>` |
| Não vem por fora | manuscrito do Restrictions ou "aprendido" não conta, nem com `authoritative = false` |

Para conceder a um personagem:

```
/aurorion spells unlock spell aurorion_magia:tempus_sistere Fulano
/createScroll aurorion_magia:tempus_sistere 3        (pergaminho do nível desejado, do Iron's)
```

No criativo, a aba **Aurorion — Magias** traz o pergaminho de cada uma das 23 magias em todos
os níveis, na ordem do registro (as proibidas por último). Ter o pergaminho não libera: conjurar
continua exigindo a liberação, e a staff passa pelo `staffBypass`.

O **nível** é o do pergaminho ou do livro. É ele que faz a magia escalar. Para marcar como proibida
uma magia de outro addon, use `forbiddenSpells` no config (a regra de concessão passa a valer; o
craft se desliga no config de magia do Iron's).

### Tempo Suspenso — *Tempus Sistere* ⛔
Tudo em volta para, **menos quem conjurou**: pessoas e criaturas ficam com o mesmo congelamento do
`/freeze` do `aurorion-utils`. Ninguém anda, pula, agacha, bate, usa item ou troca de slot. Todos
só olham, e mob congelado perde a IA. Flechas, tridentes e bolas de fogo no ar perdem o impulso e
caem.

| Nível | Raio | Duração |
|---|---|---|
| 1 | 6 | 5 s |
| 2 | 9 | 7 s |
| 3 | 12 | 9 s |
| 4 | 15 | 11 s |
| 5 | 18 | 13 s |

Ficam de fora a staff em criativo/espectador e os chefes (`imune_deslocamento`). Há um teto de 64
alvos. Sem o `aurorion-utils` no pack, a magia cai na `estase` deste mod, que tira andar e pular,
mas não trava o teclado nem as ações.
**Visual:** uma onda de gelo abre do centro até a borda e deixa um **relógio parado** no chão. O
selo não gira de propósito: o tempo não anda ali. Flocos ficam suspensos no raio. Cada congelado
ganha o anel de geada do `/freeze` e, na tela dele, a borda de geada.

### Sentença Final — *Mortem Dico* ⛔
"Eu declaro a morte." O alvo mirado (alcance 32, **inclui aliados**) morre na hora, como `/kill`.
O dano usa o tipo próprio `aurorion_magia:mortem_dico`, colocado nas tags `bypasses_*` do vanilla:
ignora armadura, resistência, encantamento, escudo, invulnerabilidade de criativo e **totem**. A
mensagem de morte nomeia quem conjurou. Se algum mod ainda segurar o alvo vivo, a magia cai no
`kill()` de verdade.

**A sentença coletiva.** Do nível 2 em diante a magia tem a segunda forma: conjurada **agachada**, a
sentença deixa de ser de um e passa a ser de todos. Um pentagrama do tamanho do raio se abre no chão e
tudo dentro dele morre ao mesmo tempo — aliado, inimigo, bicho, sem escolher lado e sem totem.

| Nível | Mirado | Agachado | Mana | Recarga |
|---|---|---|---|---|
| 1 | sim | — | 200 | 90 s |
| 2 | sim | raio 8 | 300 | 90 s |
| 3 | sim | raio 14 | 400 | 90 s |

Teto de 32 sentenciados. Chefes (`imune_deslocamento`) ficam de fora **só da área**: mirados um a um,
morrem igual — uma luta de chefe não acaba porque alguém agachou.

É uma morte comum para todo o resto: conta vida no `aurorion-vidas` e dropa o inventário pelas
regras normais.
**Visual:** um raio verde da mão ao peito, um clarão com almas saindo e um **pentagrama** verde que
fica no chão onde o alvo caiu, com lanças de luz baixando. Em área, o pentagrama tem o tamanho do raio,
com dezesseis lanças de luz e as almas subindo de todo o círculo.
### Tormento Coletivo — *Dolor Universus* ⛔
O Cruciatus em área. Todos no raio são **erguidos do chão** e ficam suspensos, paralisados de dor
(velocidade e pulo zerados, câmera tremendo), enquanto quem conjura segura o botão. A cada meio
segundo, todos levam o dano ao mesmo tempo. Soltar o botão solta todo mundo.

| Nível | Raio | Altura | Canalização | Dano/s por alvo |
|---|---|---|---|---|
| 1 | 5 | 1,5 | 3 s | 2 |
| 2 | 7 | 1,8 | 4 s | 3 |
| 3 | 9 | 2,1 | 5 s | 4 |
| 4 | 11 | 2,4 | 6 s | 5 |
| 5 | 13 | 2,7 | 7 s | 6 |

Os alvos são escolhidos uma vez, no início, com teto de 24.

**A tempestade.** A cada pulso cai um raio **só visual** (`setVisualOnly`) na borda do selo: não queima
bloco, não fere ninguém, não transforma porco em zumbi. Quem machuca é o pulso da magia, que já tem
número próprio; o raio está ali para a cena, junto com o rugido grave por cima da batida de coração.

**Visual:** um selo de espinhos do tamanho do raio sob quem conjura, uma mancha escura de sangue no
chão, feixes vermelho-negros da mão até cada suspenso e os raios caindo em volta.

### Possessão: a tela de quem é controlado

Quem está sob uma magia de controle vê e ouve diferente, para passar a ideia de possessão. Tudo é
desenhado no cliente de quem é afetado (`PossessionLayer` e `MagiaClientEvents`). Não há shader, e
por isso funciona com qualquer pacote de shaders. Não trafega nada novo pela rede. F1 não esconde o
efeito.

| Magia | Tela | Câmera | Som (só o afetado ouve) |
|---|---|---|---|
| Imperium Mentis | véu jade que respira, fios de marionete descendo do alto, ordens em latim surgindo e sumindo ("OBOEDI", "MEUS ES", "NON ES TUUS"...) | balança devagar, como se outro a movesse; o campo de visão respira | sussurros de caverna |
| Dolor Cruciatus / Dolor Universus | vinheta de sangue batendo como coração, veias rachando das bordas para dentro e crescendo com o tempo | tremor, campo de visão pulsando com o coração | coração disparado |
| Olhar Cativo | túnel escuro: só um círculo no centro fica visível | presa no captor, com zoom | coração lento |
| Prostração | peso escuro descendo do alto, sombra lilás nas bordas | campo de visão mais fechado | almas escapando |
| Voz Interdita | faixa negra subindo do pé da tela, com uma costura como boca fechada | — | — |
| Mão do Algoz | bordas violeta apertando enquanto é segurado | — | — |
| Mundo Vazio | quase nada: sombra fria nas bordas e um chiado de riscos horizontais | — | — |

Todos os efeitos de câmera respeitam a opção vanilla "Efeitos de distorção" (acessibilidade), e o
tremor respeita também `cameraShake`.

### Trocar escola e outros números sem recompilar

O Iron's 3.16 gera um arquivo por magia em
`config/irons_spellbooks_spell_config/aurorion_magia/<magia>.json`, e aceita override por datapack
em `data/aurorion_magia/irons_spellbooks_spell_config/<magia>.json`. Escola, nível máximo, raridade
mínima e cooldown saem daí. `/ironsSpellbooks config list` lista as chaves.

## Passivas

Uma **passiva** é uma marca que fica no personagem, e não uma magia que ele conjura. A diferença é o
ponto todo do sistema: magia se aprende, se grava num livro, se conjura, custa mana e entra em
recarga. Passiva se **recebe uma vez** — o pergaminho é consumido e a marca fica — e a partir daí ela
é parte de quem a pessoa é. A médica cura no toque porque é médica, não porque apertou um botão.

| Nome | Id | Interruptor | O que faz |
|---|---|---|---|
| Mão que Cura | `manus_medica` | não | bater em alguém cura em vez de ferir |
| Presença Aterradora | `presenca_terrivel` | sim | o mundo escurece, quem está perto sente medo e todos ao redor se prostram |

### Como uma passiva chega a alguém

```
/aurorion passivas conceder   <passiva> <alvos>   (staff)
/aurorion passivas remover    <passiva> <alvos>   (staff)
/aurorion passivas pergaminho <passiva> <alvos>   (staff)
/aurorion passivas ver <jogador>                  (staff)

/aurorion passivas minhas                         (qualquer um)
/aurorion passivas ligar    <passiva>             (o dono)
/aurorion passivas desligar <passiva>             (o dono)
```

A divisão de permissão é a parte que importa. **Conceder é da staff**, como a aula de magia: ninguém
ganha uma passiva jogando. **Ligar e desligar é do dono**, sem permissão nenhuma — a Presença
Aterradora existe para o vilão entrar em cena e sair dela, e pedir staff a cada entrada mataria a
mecânica.

`pergaminho` entrega o item em vez da marca: serve para colocar a passiva num baú de prêmio, na mão de
um NPC, ou dar em cena para a pessoa ler quando quiser. O **Pergaminho de Passiva** usa a mesma textura
do pergaminho do Iron's de propósito, mas faz o contrário dele: o do Iron's é gasto toda vez que a
magia sai, ou é transcrito para um livro; este se lê **uma vez** e some, e o que ele deixa não é uma
magia no livro, é uma marca. Ler de novo não faz nada, e o pergaminho não se gasta à toa.

No criativo, a aba **Aurorion — Magias** traz um pergaminho de cada passiva, depois de todas as magias.

**As passivas são do personagem**: morte definitiva zera tudo (`CharacterResetEvent`), como as
liberações de magia. Passiva com interruptor nasce **desligada** — quem recebeu uma aura de terror
escolhe quando entrar em cena, e não a recebe já aterrorizando a sala de aula.

Um item só para todas as passivas, com a passiva num componente de dados, e não um item por passiva:
item registrado é conteúdo que nunca mais sai do modpack ([SDD §6.1](../SDD.md)), e uma passiva nova
não deveria custar uma entrada permanente no registro. É o mesmo desenho do pergaminho do Iron's.

### Mão que Cura — *manus_medica*
Bater em alguém, com a mão ou com o que estiver nela, **cura meio coração por golpe** em vez de
ferir. Com o LSO instalado, cada golpe também trata aos poucos a parte mais ferida do corpo.

- Vale só para o **golpe corpo a corpo direto** (`minecraft:player_attack` com o próprio corpo como
  causa). Flecha, poção, magia e explosão da mesma pessoa continuam machucando normalmente: curar com
  arco a 40 blocos seria outra coisa.
- Arma de mod que soma um segundo dano com tipo próprio não vira cura — só o golpe base vira.
- Por padrão, **criatura hostil continua levando dano** (`healingTouchHealsHostiles = false`). Sem
  isso, a personagem ficaria literalmente incapaz de se defender de um zumbi: toda mordida seria
  respondida com cura. Quem quiser a leitura radical — "ela cura tudo o que toca, e por isso não
  luta" — liga a chave no config.
- Sem interruptor de propósito: ela não faz nada até a pessoa decidir bater em alguém, e a decisão já
  é o interruptor.

**Visual:** o acerto conserva a piscada vermelha, o som e o recuo normais. Também aparecem corações
e a nota de ametista. A saúde não diminui.

### Presença Aterradora — *presenca_terrivel*
O ar em volta de quem a carrega fica pesado.

| O que | Alcance padrão | Config |
|---|---|---|
| Medo: escuridão, tela preta fechando, tremor, batida de coração, névoa, vultos | 30 | `dreadRadius` |
| Prostração: os dois joelhos no chão | 30 (o raio inteiro) | `dreadKneelRadius` (0 desliga) |
| Criaturas perdem o alvo e fogem | 30 | `dreadRadius` |

- O medo **não tira vida nem atributo de ninguém**. O efeito `apavorado` é lido pelo cliente da
  própria pessoa e vira tela, som e névoa — ele não é arma de PvP disfarçada. O que muda no jogo é
  que criatura hostil no raio perde o alvo, foge, e não consegue mais atacar quem carrega a aura.
- **O mundo escurece de verdade.** Além da névoa preta, quem está dentro da aura recebe a *Escuridão*
  do vanilla (`dreadDarkens`, ligado): a luz dos blocos e do céu se apaga na tela dele. É o caminho
  que atravessa shader pack — o Iris respeita a iluminação do jogo, e não a neblina que a gente pede.
  Desligue a chave se a escuridão total estiver inviabilizando cena em lugar fechado.
- **Todos ao redor se prostram**, e não só quem chega perto: `dreadKneelRadius` nasce igual a
  `dreadRadius`. A pose é a prostração do Emotecraft (`kneel_down`, os dois joelhos, embutida no nosso
  jar); com `dreadProstrates = false` ela vira um joelho só (`kneel_one_knee`). Valor de
  `dreadKneelRadius` maior que `dreadRadius` é cortado para ele — quem não sente a aura não se
  prostra por ela —, e baixá-lo devolve a plateia de pé.
- A prostração **não ata as mãos**, ao contrário da magia Prostração: quem está no chão por medo
  continua conseguindo comer, beber, erguer o escudo e conjurar. A aura fica ligada por tempo
  indeterminado e vale para todo mundo que passar perto; se ela também tirasse o item da mão,
  atravessar a rua onde o vilão está deixaria de ser assustador e viraria impossibilidade de jogar.
  Ela **atrasa a saída**, isso sim: no chão, a velocidade cai 90% e o pulo zera, então sair de um raio
  de 30 blocos leva mais de um minuto de rastejo. É a dose que a chave de raio existe para ajustar.
- Ficam de fora: espectador, staff em criativo, entidade invulnerável (NPC de ofício, manequim) e, com
  `dreadSparesAllies` ligado, os **aliados de time** do portador — os capangas do vilão não se
  prostram para ele.
- Sair do raio é sair do efeito: os efeitos nos atingidos duram pouco mais que a varredura que os
  renova, então quem se afasta volta ao normal sozinho, sem ninguém varrer lista.
- Ligar e desligar vale **em qualquer lugar**: a aura é de vilão, e ela não é desligada por área
  segura. Quem decide quando ela acontece é quem a carrega.

**Visual:** mancha preta no chão, quatro faixas de treva girando em volta do corpo, fumaça negra
subindo e vultos rondando até 6 blocos. Na tela de quem está perto: o mundo apagado, véu preto
pulsando no ritmo do coração, vultos cruzando a periferia e a névoa fechando conforme a distância
diminui — na borda do raio é um peso no canto do olho; dos 60% para dentro, uma parede preta a 5
blocos do nariz.

**Som:** uma batida de coração de 100 bpm em laço, **só no cliente de quem está com medo**, com o
volume e o tom subindo conforme o portador se aproxima. Quem carrega a aura nunca ouve nada, e nenhum
pacote de áudio trafega.

## Integrações opcionais

| Mod | O que liga | Sem ele |
|---|---|---|
| Iron's Restrictions | UI de pesquisa/scroll/inscrição mostra o que não foi liberado | o gate de conjuração continua valendo |
| Emotecraft | pose de joelhos do Genua Flecte e a prostração da Presença Aterradora, vistas por todos | agachado |
| Simple Voice Chat | Vox Interdicta corta o microfone | corta só chat e magia |
| `aurorion-utils` | Tempus Sistere usa o freeze completo do `/freeze` | cai na Lentidão do vanilla (sem trava de teclado) |
| Carry On | baú lacrado também não pode ser carregado embora | nada muda: o lacre já bloqueia abrir e quebrar |
| Iris/Oculus | a névoa do Devorar Luz e da Presença Aterradora é desenhada em espaço de tela | a névoa do vanilla, que fica melhor |
| Sophisticated Backpacks/Storage | mochila e cofre no chão aceitam lacre | idem: a regra é por capacidade, não por mod |

- **Emotecraft:** ponte por reflexão (`compat/EmotecraftCompat`), porque os tipos vêm de um jar
  aninhado.
- **`aurorion-utils`:** ligação pelo **id do efeito** `aurorion_utils:congelado`, sem import. O
  utils reage ao efeito venha de onde vier.
- **Voice Chat:** plugin anotado com `@ForgeVoicechatPlugin`, que o próprio Voice Chat só carrega se
  estiver instalado.

## Performance (100+ jogadores)

Nada neste mod assina `ServerTickEvent` ou `PlayerTickEvent`. Tudo que é periódico roda **no tick do
efeito**, que o vanilla já faz da entidade afetada, e só nela.

| O que | Quando roda | Custo |
|---|---|---|
| Gate de conjuração | a cada tentativa de conjurar | 2 buscas em hash (+1 consulta de efeito) |
| Reconcile com o Iron's | login, comando, reset | ~150 entradas, sem pacote se nada mudou |
| Pulso do Cruciatus | a cada 10 ticks, só durante a canalização | 1 distância + 1 raycast + 1 payload |
| Corrente do Vinculum | 5×/s, só em quem está preso | 1 distância; puxão só fora do raio |
| Mão do Algoz | todo tick, só durante a canalização, 1 alvo | 1 vetor + 1 pacote de velocidade |
| Arremessado | todo tick, até 1,5 s, só no arremessado | leitura de colisão |
| Dominação | no feitiço e 1×/s só no mob dominado, se o alvo sumiu | `getEntitiesOfClass` num raio de 12 |
| Genua Flecte | 1×/s, só no ajoelhado | 1 chamada ao Emotecraft |
| Voz Interdita | por pacote de voz, na thread de áudio | 1 busca em mapa concorrente |
| Lacres | ao abrir/quebrar bloco e em explosão | 1 busca em `Long2ObjectMap` |
| Ferro Vinculado | quando o equipamento muda (evento vanilla) | busca no inventário, só no vinculado |
| Lux Vorata | uma vez por conjuração (cooldown 30 s) | varredura da esfera, até ~5 mil posições |
| Tempus Sistere | uma vez por conjuração (cooldown 120 s) | 1 busca no raio (teto 64); depois só o tick do efeito |
| Mortem Dico | uma vez por conjuração | 1 raycast + 1 dano |
| Dolor Universus | todo tick da canalização, só nos alvos dela (teto 24) | 1 vetor + 1 pacote de velocidade por alvo; 1 payload visual e 1 raio visual por pulso |
| Magia em área (agachado) | uma vez por conjuração | 1 busca no raio com teto de alvos; depois só os efeitos |
| Mundo Vazio | no feitiço; depois, no cliente do afetado | 1 consulta de efeito por corpo desenhado, só enquanto dura |
| Afogamento | a cada 10 ticks, só em quem está afogando | 1 leitura de ar; 1 dano depois que ele zera |
| Cárcere de Água | todo tick, só no preso | 1 distância; teleporte só quando ele saiu do lugar |
| Maremoto | uma vez por conjuração | 1 busca no raio (teto 24) + 1 busca de projéteis |
| Barreira / Coluna / Turbilhão | todo tick, só enquanto a zona existe | 1 busca no raio com teto de 20; sem zona, zero |
| Presença Aterradora (gente) | 1×/s, **por aura ligada** | nenhuma busca espacial: 1 volta em `level.players()`, teto 32 |
| Presença Aterradora (bicho) | 1×/2 s, **por aura ligada** | 1 busca em esfera de `dreadRadius` só em `Mob`, teto de 8 rotas; sem aura ligada, zero |
| Mão que Cura | por golpe corpo a corpo de quem a tem | 1 busca em hash |
| Varredura de modificador preso | login e renascimento | 14 consultas de atributo por jogador |

### Como os efeitos visuais são feitos sem pesar no TPS

A regra: **o servidor manda intenção, o cliente desenha**.

1. **Nenhuma partícula sai do servidor.** O servidor manda um `SpellVisualPayload` (tipo, ids das
   entidades, ttl, um ponto e um número: ~30 bytes) uma vez por conjuração. Vai só para quem
   rastreia o alvo, ou para quem tem o chunk carregado, no caso dos visuais presos a um ponto.
2. **O cliente acompanha as entidades sozinho.** `ClientSpellVisuals` guarda o visual e gera as
   partículas a cada tick, achando as entidades por id. Não trafega posição por tick.
3. **Sem pacote de "parar".** Visual de canalização (Cruciatus, Mão do Algoz) é reenviado a cada
   pulso com ttl curto e some sozinho.
4. **Selos como na Cerimônia.** `SigilRenderer` usa a técnica dos selos do `aurorion-ethereal`:
   - geometria luminosa em lote no `RenderLevelStageEvent`;
   - dois passes: "tinta" translúcida para o escuro e "luz" aditiva para o brilho;
   - desenhos fixos em `SigilGeometry`, montados uma vez.

   Um batch por passe por quadro, só quando há visual ativo.
5. **Estado de efeito não precisa de pacote nosso.** Tremor, controles invertidos, escurecimento e
   agachado lêem `player.hasEffect(...)`, que o vanilla sincroniza. Os efeitos usam
   `visible = false` para não gerar as bolhas de poção vanilla.
6. **Orçamento no cliente.**
   - No máximo 64 visuais simultâneos.
   - A opção vanilla "Partículas" divide tudo (Todas 1×, Reduzidas ½, Mínimas ¼).
   - Partículas só até `visualDistance`; selos até 64 blocos.

Para um visual novo:

```java
// 1. Novo tipo no FIM de SpellVisualPayload.Kind (o ordinal vai na rede) e suba PROTOCOL_VERSION.
// 2. No servidor, dentro do onCast da magia: um envio, nunca por tick.
MagiaNetwork.sendVisual(caster, target, SpellVisualPayload.Kind.MEU_VISUAL, duracaoEmTicks);
// 3. Particulas: um case em ClientSpellVisuals.particles(), respeitando o stride.
// 4. Geometria: um case em SigilRenderer.glow()/ink(), usando os desenhos de SigilGeometry.
```


### Névoa e shaders

`ViewportEvent.RenderFog` e `ComputeFogColor` são eventos do **pipeline do vanilla**. Quando o Iris
carrega um pacote de shaders, quem calcula a névoa passa a ser o fragment shader do pacote, com
uniformes próprios, e o plano distante que a gente pede simplesmente não é consultado. Na prática: a
escuridão do Devorar Luz existia para quem jogava sem shader e **sumia** para quem jogava com BSL,
Complementary ou Solas — que é quase todo o servidor.

A saída não é brigar com o shader: é desenhar a névoa **depois** dele.

| Com shader ligado | Sem shader |
|---|---|
| `FogLayer` pinta a névoa em espaço de tela, numa camada de HUD **abaixo** de tudo (névoa não pode cobrir a barra de itens nem o chat) | `MagiaClientEvents.onRenderFog` faz a névoa de verdade, que fica melhor |

O interruptor é `ShaderPacks` no `aurorion-core`, uma ponte reflexiva com a API v0 do Iris — sem o
Iris instalado a resposta é sempre "não" sem custo nenhum. Ele é reconsultado uma vez por segundo,
porque o jogador liga e desliga shader em jogo (K, no teclado padrão do Iris) e a névoa tem que trocar
de técnica junto. As duas nunca desenham ao mesmo tempo: somadas, escureceriam o dobro.

**As partículas não precisam de nada disso.** Partícula atravessa o pipeline do shader como qualquer
outra do jogo — e é por isso que a fumaça negra, os vultos, as bolhas e o vento são o *corpo* dos
efeitos, e a névoa é só o ar em volta. O mesmo vale para a `PossessionLayer`: ela é retângulo e texto
em cima do HUD, então funciona com qualquer pacote.

O `aurorion-areas` usa as mesmas duas peças na névoa da Floresta Negra.

## Configuração

`config/aurorion/magia-server.toml`: `staffBypass`, `authoritative`, `forbiddenSpells`,
`dominationRadius`, e a seção `[passivas]` com `dreadRadius`, `dreadKneelRadius`, `dreadProstrates`,
`dreadDarkens`, `dreadSparesAllies` e `healingTouchHealsHostiles`.

`config/aurorion/magia-client.toml`: `visualDistance` (até 32, o limite do vanilla para partículas),
`cameraShake` (0 desliga).

Tags de datapack (`data/aurorion_magia/tags/`):

| Tag | Uso |
|---|---|
| `entity_type/imune_dominacao` | Imperium Mentis |
| `entity_type/imune_deslocamento` | Transpositio, Mão do Algoz, Queda Forçada, Prostração |
| `block/selavel` | Lacre Profano: força o lacre em bloco que a regra geral não pega |
| `block/nao_selavel` | Lacre Profano: nunca lacra, mesmo tendo inventário |
| `block/apagavel` | Devorar Luz: força o apagamento em bloco que não usa `lit` |
| `block/inapagavel` | Devorar Luz: nunca apaga (fornalha, lâmpada de redstone…) |

## Build

Compila contra os jars locais (`compileOnly`) de `mod-servidor-referencia/`:
- Iron's Spells, trocável com `-PironsSpellbooksJar=...`;
- Voice Chat, trocável com `-PvoicechatJar=...`.

O Emotecraft não entra no build (a ponte é por reflexão).

O mod fica fora do `aurorion-runs` (`aurorion_runs_skip=true`), porque o run do ecossistema não
carrega o Iron's e a árvore dele. A validação é no pack do servidor.

O mod é obrigatório no cliente: as magias e os efeitos entram em registros sincronizados.

## Validar no pack

**Build e liberação**
- [ ] `./gradlew :aurorion-magia:build` compila (inclui `SpellGrantsTest`, com a regra de proibidas,
  `VoiceMuteTest` e `MagiaAssetsTest`, que cobra ícone + nome + descrição nos dois idiomas de cada
  magia).
- [ ] Cliente e servidor com o **mesmo jar**: o protocolo do `SpellVisualPayload` subiu para `6`
  (quatro tipos novos no fim do enum). Jar velho de um lado não conecta.
- [ ] `/aurorion spells unlock …`: Tab, seletores, id inexistente recusado; bloqueio sem liberação;
  ponte com o Restrictions (inscrição, scroll, reconcile no login).

**Por magia**
- [ ] Aba **Aurorion — Magias** no criativo: 23 magias, todos os níveis, mais um pergaminho de cada
  passiva no fim; magia desligada no config do Iron's some da aba.
- [ ] **Agachado, em área** (Queda Forçada, Olhar Cativo, Sentença Final 2+): em pé continua pegando
  um alvo; agachado pega todos; sem ninguém em volta, a magia é recusada sem gastar mana nem recarga.
- [ ] **Animação da mão** (o bug que motivou tudo): segurar Dolor Cruciatus, Mão do Algoz e Tormento
  Coletivo **até o tempo acabar sozinho** — o boneco tem de voltar ao normal. Conferir também soltando
  o botão antes, e num segundo cliente (o bug só aparecia para quem olhava de fora).
- [ ] **Modificador preso**: com alguém sob Cruciatus/Prostração/Olhar Cativo, derrubar o servidor no
  braço; ao voltar, a pessoa anda normalmente (varredura do `EffectCleanup` no login). Quem já estava
  travado pela antiga "estase" também tem de destravar no primeiro login com este jar.
- [ ] Olhar Cativo:
  - câmera presa no captor (inclusive se ele se mexer);
  - anda devagar, fala;
  - outros veem a cabeça virar;
  - mob encara;
  - olho e fio visíveis;
  - túnel na tela do cativo.
- [ ] Possessão: conferir cada linha da tabela "Possessão" com um segundo cliente sob cada magia.
  Conferir também com shaders do pack, com F1 e com "Efeitos de distorção" em 0.
- [ ] Proibidas:
  - não aparecem para craft nem em loot;
  - `unlock school irons_spellbooks:blood` **não** libera o Dolor Universus;
  - `unlock spell` libera;
  - `/createScroll <id> <nível>` gera o pergaminho.
- [ ] Tempus Sistere:
  - todos no raio congelam (jogador e mob), menos quem conjurou;
  - criativo e chefe ficam de fora;
  - flechas no ar caem;
  - soltam no fim;
  - relógio parado e onda de gelo visíveis;
  - sem o `aurorion-utils`, cai na estase.
- [ ] Mortem Dico:
  - mata jogador de armadura cheia, com totem na mão e em criativo; mata mob;
  - mensagem de morte com o nome de quem conjurou;
  - conta vida no `aurorion-vidas`;
  - raio verde e pentagrama visíveis;
  - nível 1 agachado continua sendo alvo único;
  - níveis 2 e 3 agachado matam tudo no raio (aliado incluído) e **não** matam chefe;
  - o pentagrama gigante aparece antes de os corpos sumirem.
- [ ] Dolor Universus:
  - todos no raio sobem e ficam suspensos;
  - dano simultâneo, câmera tremendo nos jogadores;
  - soltar o botão solta todos;
  - raio, altura, dano e canalização crescem por nível;
  - os raios em volta **não** queimam bloco, não ferem ninguém e não convertem mob;
  - desempenho com 24 alvos.
- [ ] Cruciatus e Imperium: comportamento anterior, agora com os selos (espinhos sob o alvo, coroa
  jade, selo que se abre no chão).
- [ ] Vinculum:
  - puxão ao cruzar o raio, e mais forte a cada tentativa;
  - **dano ao insistir na borda**: um golpe por segundo, crescendo com a tensão, e a mensagem de morte
    nomeia quem lançou a corrente;
  - pérola, chorus, Blink/Teleport do Iron's e portal negados com o estalo;
  - `/tp` da staff passa;
  - teleporte de outro mod para longe devolve o preso **à âncora**, de pé, sem queda;
  - relogar preso continua preso;
  - corrente aparece só quando tensionada.
- [ ] Mundo Vazio:
  - o alvo deixa de ver pessoas, mobs e nomes flutuantes, e continua vendo o próprio corpo em F5;
  - quem está em volta não nota nada;
  - o alvo continua tomando dano e ouvindo som de quem não vê;
  - conjurar de novo nele devolve o mundo, e o fim do tempo também;
  - relogar no meio do efeito volta com o mundo visível ou com o efeito ainda valendo, nunca com o
    mundo apagado sem efeito.
- [ ] Transpositio:
  - troca com jogador e com mob;
  - quem estava caindo transfere a queda;
  - aliado aceito, chefe recusado;
  - ninguém volta para a posição antiga (desync de teleporte).
- [ ] Mão do Algoz:
  - conduzir pela mira;
  - arremessar contra a parede (dano) e contra o teto;
  - soltar no alto (queda);
  - confirmar que o alvo ainda é lido no `onServerCastComplete`, para o arremesso acontecer.
- [ ] Mão Vazia: mão principal e, agachado, a secundária; escudo em uso; mob armado; 2 s sem pegar.
- [ ] Genua Flecte:
  - com Emotecraft, todos veem a pose, e ela volta após relogar/trocar de dimensão;
  - sem Emotecraft, agachado;
  - o alvo vira de frente para quem conjurou;
  - de joelhos, nenhum item funciona (comida, arco, escudo, totem) e nenhuma magia sai — mas botão,
    alavanca e porta continuam funcionando;
  - conjurar de novo no mesmo alvo manda levantar.
- [ ] Vox Interdicta:
  - microfone mudo em proximidade e em grupo;
  - chat recusado; magia bloqueada;
  - leite devolve a voz;
  - conjurar de novo no mesmo alvo devolve a voz.
- [ ] Cruciatus no alvo: nenhuma magia sai e nenhum item responde enquanto dura.
- [ ] Sigillum Clausum:
  - porta (as duas metades), porta dupla, baú duplo, barril, alçapão, portão;
  - **mods do pack**: mochila do Sophisticated Backpacks no chão, barril e cofre do Sophisticated
    Storage, item vault do Create, portas do Macaw's e do FramedBlocks, baú do Quark;
  - **Carry On**: com o baú lacrado, não dá para pegá-lo no colo;
  - dono, time (nível 2+), outro jogador e staff criativa;
  - desfazer o próprio lacre; quebrar com nível maior; resistir com nível menor;
  - explosão; quebrar o bloco;
  - selo visível para quem chega depois na dimensão;
  - conferir se sobrou alguma máquina de linha de produção que ficou selável sem querer — se ficou, o
    id vai para a tag `aurorion_magia:nao_selavel`, sem recompilar.
- [ ] Deiectio:
  - elytra, levitação (shulker), voo de mod, alvo preso na Mão do Algoz;
  - no chão, sem pular;
  - poeira do bloco no impacto;
  - agachado, todos no raio caem juntos e a onda abre no chão.
- [ ] Lux Vorata:
  - velas, bolos com vela, fogueiras, lâmpadas de mod com `lit` e fogo no chão apagam;
  - fornalha acesa e lâmpada de redstone **não** apagam (tag `inapagavel`);
  - todos dentro do raio ficam com Escuridão, menos quem conjurou e o time dele;
  - neblina negra dentro da zona, some ao sair;
  - proteção de spawn respeitada.
- [ ] Ferrum Ligatum:
  - tirar pelo inventário, shift-click, trocar clicando com outra peça, Q com a peça no cursor;
  - escudo na mão secundária;
  - a peça volta sem duplicar nem sumir.

- [ ] Submersio:
  - a barra de bolhas do alvo esvazia e o dano começa quando ela zera;
  - Respiração no elmo segura mais tempo; poção de respirar embaixo d'água salva; leite corta o efeito;
  - sair do efeito devolve o ar cheio;
  - conjurar de novo no mesmo alvo devolve o ar;
  - mensagem de morte com o nome de quem conjurou;
  - bolhas na boca vistas de fora, e a água fechando na tela do alvo.
- [ ] Unda Magna:
  - em pé, só quem está no arco de 120° à frente é empurrado; agachado, todo mundo;
  - quem estava pegando fogo apaga;
  - flecha e trident em voo são varridos;
  - o empurrão é mais forte perto do centro;
  - testar num penhasco: a morte é de queda, e não da onda.
- [ ] Carcer Aquae:
  - o preso boia, não anda, não pula e continua respirando;
  - qualquer um estoura a bolha batendo nela, e o golpe não fere o preso;
  - conjurar de novo no mesmo alvo desfaz;
  - deslogar preso e voltar: continua preso no mesmo ponto;
  - com Submersio junto, a bolha **não** estoura sozinha.
- [ ] Ventus Custos:
  - todos são arremessados para fora no instante da conjuração;
  - quem tenta voltar é empurrado, mais forte quanto mais fundo entrar;
  - quem conjurou entra e sai livremente;
  - flecha e magia **atravessam** a barreira;
  - andar para longe: a barreira fica onde nasceu.
- [ ] Columna Venti:
  - quem entra sobe ~6 blocos e ganha queda lenta;
  - funciona para aliado e para inimigo igual;
  - nasce no chão firme sob a mira, e não dentro de parede;
  - conjurada mirando o ar, cai no chão embaixo do ponto.
- [ ] Turbo Ventorum:
  - anda em linha reta acompanhando o relevo, subindo e descendo desnível;
  - puxa para o eixo e levanta quem estiver no caminho;
  - um golpe por segundo, com armadura valendo;
  - **desfaz ao bater numa parede**;
  - mensagem de morte com o nome de quem conjurou;
  - desempenho com 20 pessoas no caminho.
- [ ] Zonas de vento (as três): relogar e trocar de dimensão no meio de uma; derrubar o servidor com
  uma ativa — nenhuma pode voltar depois do restart (o tipo é `noSave`).

**Passivas**
- [ ] `/aurorion passivas conceder|remover|pergaminho|ver` exige staff; `ligar|desligar|minhas` não.
- [ ] Pergaminho: ler consome e grava a marca; ler de novo recusa **sem consumir**; a aba do criativo
  traz um de cada.
- [ ] Mão que Cura:
  - soco e golpe de arma em jogador curam em vez de ferir, com corações e som;
  - flecha, poção e magia da mesma pessoa continuam machucando;
  - hostil continua levando dano com o padrão, e passa a ser curado com
    `healingTouchHealsHostiles = true`;
  - cada golpe cura meio coração e, com o LSO, a parte mais ferida aos poucos;
  - o alvo conserva a animação de dano, o som e o recuo, sem perder saúde.
- [ ] Presença Aterradora:
  - `/aurorion passivas ligar presenca_terrivel` acende a aura e `desligar` a apaga **no mesmo tick**;
  - a 30 blocos já se sente tudo: mundo escuro (Escuridão do vanilla), véu preto, tremor, coração,
    névoa **e** a prostração, com a pose do Emotecraft vista por todos;
  - `dreadProstrates = false` troca a prostração por um joelho só; `dreadKneelRadius = 10` devolve o
    comportamento antigo (a plateia de longe fica de pé);
  - `dreadDarkens = false` mantém a névoa e tira a Escuridão;
  - no chão por medo, comer/beber/escudo/magia **continuam** funcionando;
  - sair do raio volta ao normal em pouco mais de um segundo (bicho, em pouco mais de dois);
  - aliado de time fica de fora (e passa a entrar com `dreadSparesAllies = false`);
  - staff em criativo e espectador ficam de fora; manequim e NPC de ofício não recebem nada;
  - criatura hostil perde o alvo, foge e não consegue atacar o portador;
  - quem carrega a aura **não** ouve o coração, não vê a névoa e não escurece;
  - relogar com a aura ligada: ela volta sozinha; morrer e renascer: idem;
  - leite/`/effect clear` no portador não desliga a aura: ela volta no tick seguinte (e **não**
    entra em recursão — foi o `StackOverflowError` de 0.3.0 no desligamento do servidor);
  - `/aurorion personagem` (morte definitiva) apaga a passiva e a aura junto.
- [ ] Dois portadores de aura perto um do outro: uma névoa só, sem dobrar o escurecimento nem tocar
  dois corações.

**Visual e desempenho**
- [ ] Todos os selos aparecem, inclusive na face de porta/baú, e somem no fim; nada fica preso na
  tela depois de relogar.
- [ ] Shaders do pack (Iris/Oculus) com os RenderTypes de tinta/luz. A cerimônia do ethereal usa
  os mesmos e serve de referência.
- [ ] **Névoa com shader ligado e desligado** (tecla K no Iris, em jogo): dentro do Devorar Luz e
  dentro de uma aura de terror, a névoa tem que aparecer nos dois casos, e **nunca as duas
  técnicas ao mesmo tempo** (a tela ficaria escura demais). Conferir com BSL, Complementary e
  Solas, que são os do pack.
- [ ] A névoa de tela não cobre a barra de itens nem o chat.
- [ ] Desempenho com 20+ magias ativas na mesma área (teto de 64 visuais).
- [ ] Ícones das magias e dos efeitos aparecem (arte provisória; troque por resource pack).

## Áudio de imersão

Novos efeitos discretos do Epidemic, registros, triggers e validação pendente:
[guia de áudio](../docs/IMMERSION-AUDIO.md). Trilha e sons anteriores preservados;
sem compilação ou testes em jogo nesta máquina.
