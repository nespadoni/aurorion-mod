# Aurorion Magia

Addon do [Iron's Spells 'n Spellbooks](https://github.com/iron431/irons-spells-n-spellbooks) para o
Aurorion. **Nenhuma magia vem liberada**: a staff ensina magias soltas ou escolas inteiras por
personagem, em aula, com `/aurorion spells`. O estado é espelhado no Iron's Restrictions.

Dezessete magias autorais (três delas **proibidas**), cada uma com um **nome conhecido** e uma **invocação** (o id). As magias
servem para duelo, captura, interrogatório, perseguição, fuga, invasão e RP do dia a dia.

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
| Sequestro de Impulso | `furtum_impetus` | Evocação | instantânea, 2 fases | roubar e devolver movimento |
| Transposição | `transpositio` | Ender | instantânea | trocar de lugar com o alvo |
| Mão do Algoz | `manus_carnificis` | Evocação | contínua 5 s | segurar e arremessar |
| Mão Vazia | `manus_vacua` | Evocação | instantânea | desarmar |
| Prostração | `genua_flecte` | Eldritch | instantânea | forçar a ajoelhar |
| Olhar Cativo | `aspectus_captus` | Eldritch | instantânea | prender o olhar em quem conjura |
| Voz Interdita | `vox_interdicta` | Eldritch | instantânea | tirar a voz |
| Lacre Profano | `sigillum_clausum` | Ender | instantânea | trancar porta/baú |
| Queda Forçada | `deiectio_corporis` | Ender | instantânea | derrubar quem voa |
| Devorar Luz | `lux_vorata` | Eldritch | longa 1 s | apagar luzes e escurecer |
| Ferro Vinculado | `ferrum_ligatum` | Sangue | instantânea | travar a armadura |
| Tempo Suspenso ⛔ | `tempus_sistere` | Ender | longa 1 s | congelar tudo em volta |
| Sentença Final ⛔ | `mortem_dico` | Eldritch | instantânea | morte instantânea |
| Tormento Coletivo ⛔ | `dolor_universus` | Sangue | contínua 3–7 s | Cruciatus em área, no ar |

Os ids completos são `aurorion_magia:<invocação>`. Escola, nível máximo, raridade e cooldown são
só o padrão; ver "Trocar escola" abaixo.

### Dolor Cruciatus
Mira do Iron's, alcance 20. Enquanto o botão fica apertado (até 4 s), dá um pulso a cada 10 ticks.
O pulso confere se o alvo está vivo, no alcance e à vista, causa dano sem empurrão, renova o efeito
`cruciatus` (velocidade e pulo zerados) e reenvia o feixe. O alvo jogador sente um tremor de câmera.
**Visual:** feixe em hélice vermelho e negro da mão ao peito, e um selo de espinhos sob o alvo.

### Imperium Mentis
- **Mob:** fica `dominado` (8 s + 4 s/nível) e ataca o vivo mais próximo que não seja o mestre, um
  aliado de time, um pet do mestre ou outro dominado do mesmo mestre.
- **Jogador:** fica `desorientado` (4 s + 1 s/nível). Os controles invertem, a tela escurece e ele
  não usa magia nem item.
- **Imunes:** tag `aurorion_magia:imune_dominacao`.
- **Visual:** espiral jade/prata descendo pelo corpo, coroa rúnica girando na cabeça e um selo que
  se abre no chão.

### Vínculo do Carrasco — *Vinculum Carnificis*
O ponto onde o alvo foi atingido vira a âncora de uma corrente de 6 blocos, por 10 s + 2 s/nível.
- O alvo anda, bate e conjura à vontade dentro do raio.
- Ao cruzar a borda, é puxado para a âncora. Cada puxão seguido aumenta a tensão, e o próximo vem
  mais forte. Ficar dentro alivia a tensão.
- Pérola, chorus, teleporte de magia do Iron's, enderman e troca de dimensão são **cancelados** com
  o estalo da corrente. Teleporte sem evento (outro mod) que o leve para longe faz a corrente
  arrancá-lo de volta. O `/tp` da staff passa.

**Visual:** selo de elos na âncora e o círculo do limite no chão. A corrente, com elos alternados e
núcleo escuro, só aparece quando está tensionada, e estala em vermelho a cada puxão.

### Sequestro de Impulso — *Furtum Impetus*
Usa o recast do Iron's, então o cooldown só começa depois da devolução.
1. **Roubar:** mira uma criatura, flecha, tridente ou item em voo. O alvo para no lugar (criatura
   fica em `estase` por meio segundo) e o impulso vai para a sua mão por até 30 s. O valor guardado
   é a velocidade × (1,4 + 0,2/nível), no mínimo 0,6.
   - **Sem nada na mira**, rouba o seu próprio movimento: anula o knockback que você acabou de
     levar, ou a queda.
2. **Devolver:** o alvo mirado recebe o impulso na direção do seu olhar. Sem alvo, você recebe o
   impulso e sai em arranque. Empurrão forte vira arremesso, e bater em parede machuca.

**Visual:** partículas sugadas do alvo para a mão, anel que se fecha nele, orbe duplo girando na
mão enquanto o impulso está guardado e onda de choque na devolução.

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
pula e não corre, mas **fala** e continua com as mãos livres.
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
**Visual:** um olho violeta se abre sobre a cabeça do cativo, encarando o captor, com um fio de
olhar ligando os dois. Na tela de quem está preso aparece um túnel escuro (ver "Possessão").

### Voz Interdita — *Vox Interdicta*
Só em jogador, por 5 s + 1,25 s/nível (10 s no nível 5). Durante o efeito:
- o microfone no **Simple Voice Chat** não chega a ninguém (proximidade, grupo e sussurro);
- o chat de texto é recusado;
- nenhuma magia sai.

O alvo ainda anda, bate e foge. O corte de voz é um plugin do Voice Chat (`AurorionVoicePlugin`) que
consulta um mapa concorrente, porque roda na thread de áudio.
**Visual:** colar negro na garganta com anel rúnico violeta girando e fumaça escura.

### Lacre Profano — *Sigillum Clausum*
Olhe para uma porta, alçapão, portão, baú, barril ou outro contêiner (tag `aurorion_magia:selavel`
para blocos de outros mods) e sele. Lacrar fecha o que estiver aberto.

| Nível | Quem abre | Duração |
|---|---|---|
| 1 | só você | 1 min |
| 2 | você e seu time | 3 min |
| 3 | você e seu time | 9 min |

- Conjurar no seu lacre o desfaz. No lacre de outra pessoa, com nível igual ou maior, **quebra**;
  com nível menor, o lacre resiste.
- O lacre bloqueia abrir, quebrar e explosões.
- Staff em criativo passa.
- Porta dupla (as duas metades) e baú duplo compartilham o lacre.

Os lacres ficam em memória, com teto de 1024 por dimensão, e somem no reinício do servidor. Quem
entra na dimensão recebe os lacres ativos.

**Visual:** um selo violeta desenhado na face do bloco, respirando. Ele dá um clarão quando alguém
tenta abrir e se estilhaça quando é quebrado.

**Limites:** redstone ainda abre porta lacrada, e funil ainda puxa de baú lacrado.

### Queda Forçada — *Deiectio Corporis*
- **No ar** (pulo, levitação, elytra, voo de sobrevivência, preso na Mão do Algoz): tira elytra,
  levitação, queda lenta e voo, e crava o alvo para baixo. A altura vira dano de queda pelo vanilla.
- **No chão:** dano de impacto e `abatido` (sem pular) por 1,5 s + 0,5 s/nível.

É o contra natural das magias de movimento. Chefes são imunes.
**Visual:** selo de espinhos que desce sobre a cabeça, rastros de vento para baixo e, ao tocar o
chão, poeira do próprio bloco em anel com uma onda de choque.

### Devorar Luz — *Lux Vorata*
Raio de 5 + nível blocos em volta de quem conjura:
- apaga velas, bolos com vela e fogueiras (tag `aurorion_magia:apagavel`; lanternas mágicas de
  outros mods entram por datapack);
- apaga quem estiver pegando fogo;
- deixa uma **zona de escuridão** por 8 s + 2 s/nível. Quem está dentro enxerga uma neblina negra
  fechando em ~5 blocos.

Não aplica Darkness em ninguém: é o ambiente que escurece. Tocha e lanterna vanilla não têm estado
"apagada", e apagá-las exigiria quebrá-las, o que não é magia de griefing. A zona cobre esse caso.
Respeita proteção de spawn (`mayInteract`).
**Visual:** a luz é sugada para o centro em fumaça, fica uma mancha escura no chão com a borda
violeta se fechando e sobem partículas de vazio.

### Ferro Vinculado — *Ferrum Ligatum*
Só em jogador, por 10 s + 5 s/nível. A armadura e o que estiver na mão secundária ficam presos:
tirar pelo inventário, trocar por outra peça ou jogar fora com Q devolve a peça ao corpo. A
durabilidade continua sendo gasta normalmente.
**Visual:** três anéis de elos girando em volta do corpo, em sentidos alternados, com faíscas.

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

No criativo, a aba **Aurorion — Magias** traz o pergaminho de cada uma das 17 magias em todos
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

É uma morte comum para todo o resto: conta vida no `aurorion-vidas` e dropa o inventário pelas
regras normais. Tem um nível só.
**Visual:** um raio verde da mão ao peito, um clarão com almas saindo e um **pentagrama** verde que
fica no chão onde o alvo caiu, com lanças de luz baixando.

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
**Visual:** um selo de espinhos do tamanho do raio sob quem conjura, uma mancha escura de sangue no
chão e feixes vermelho-negros da mão até cada suspenso.

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

Todos os efeitos de câmera respeitam a opção vanilla "Efeitos de distorção" (acessibilidade), e o
tremor respeita também `cameraShake`.

### Trocar escola e outros números sem recompilar

O Iron's 3.16 gera um arquivo por magia em
`config/irons_spellbooks_spell_config/aurorion_magia/<magia>.json`, e aceita override por datapack
em `data/aurorion_magia/irons_spellbooks_spell_config/<magia>.json`. Escola, nível máximo, raridade
mínima e cooldown saem daí. `/ironsSpellbooks config list` lista as chaves.

## Integrações opcionais

| Mod | O que liga | Sem ele |
|---|---|---|
| Iron's Restrictions | UI de pesquisa/scroll/inscrição mostra o que não foi liberado | o gate de conjuração continua valendo |
| Emotecraft | pose de joelhos do Genua Flecte, vista por todos | agachado |
| Simple Voice Chat | Vox Interdicta corta o microfone | corta só chat e magia |
| `aurorion-utils` | Tempus Sistere usa o freeze completo do `/freeze` | só a estase (sem andar/pular) |

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
| Dolor Universus | todo tick da canalização, só nos alvos dela (teto 24) | 1 vetor + 1 pacote de velocidade por alvo; 1 payload visual por pulso |

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

## Configuração

`config/aurorion/magia-server.toml`: `staffBypass`, `authoritative`, `forbiddenSpells`, `dominationRadius`.

`config/aurorion/magia-client.toml`: `visualDistance` (até 32, o limite do vanilla para partículas),
`cameraShake` (0 desliga).

Tags de datapack (`data/aurorion_magia/tags/`):

| Tag | Uso |
|---|---|
| `entity_type/imune_dominacao` | Imperium Mentis |
| `entity_type/imune_deslocamento` | Transpositio, Mão do Algoz, Queda Forçada, Prostração |
| `block/selavel` | Lacre Profano, além de portas, alçapões, portões e contêineres |
| `block/apagavel` | Devorar Luz |

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
- [ ] `./gradlew :aurorion-magia:build` compila (inclui `SpellGrantsTest`, com a regra de proibidas, e `VoiceMuteTest`).
- [ ] `/aurorion spells unlock …`: Tab, seletores, id inexistente recusado; bloqueio sem liberação;
  ponte com o Restrictions (inscrição, scroll, reconcile no login).

**Por magia**
- [ ] Aba **Aurorion — Magias** no criativo: 17 magias, todos os níveis; magia desligada no config do
  Iron's some da aba.
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
  - raio verde e pentagrama visíveis.
- [ ] Dolor Universus:
  - todos no raio sobem e ficam suspensos;
  - dano simultâneo, câmera tremendo nos jogadores;
  - soltar o botão solta todos;
  - raio, altura, dano e canalização crescem por nível;
  - desempenho com 24 alvos.
- [ ] Cruciatus e Imperium: comportamento anterior, agora com os selos (espinhos sob o alvo, coroa
  jade, selo que se abre no chão).
- [ ] Vinculum:
  - puxão ao cruzar o raio, e mais forte a cada tentativa;
  - pérola, chorus, Blink/Teleport do Iron's e portal negados com o estalo;
  - `/tp` da staff passa;
  - relogar preso continua preso;
  - corrente aparece só quando tensionada.
- [ ] Furtum Impetus:
  - roubar de jogador correndo, mob, flecha em voo e tridente;
  - roubar o próprio knockback e a própria queda;
  - devolver em outro alvo e em si mesmo;
  - cooldown só depois da devolução;
  - timeout de 30 s limpa o ícone.
  - Conferir se a velocidade estimada de **jogador** (andar/correr) é roubada. O servidor só a
    estima pela diferença de posição.
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
  - o alvo vira de frente para quem conjurou.
- [ ] Vox Interdicta:
  - microfone mudo em proximidade e em grupo;
  - chat recusado; magia bloqueada;
  - leite devolve a voz.
- [ ] Sigillum Clausum:
  - porta (as duas metades), porta dupla, baú duplo, barril, alçapão, portão;
  - dono, time (nível 2+), outro jogador e staff criativa;
  - desfazer o próprio lacre; quebrar com nível maior; resistir com nível menor;
  - explosão; quebrar o bloco;
  - selo visível para quem chega depois na dimensão.
- [ ] Deiectio:
  - elytra, levitação (shulker), voo de mod, alvo preso na Mão do Algoz;
  - no chão, sem pular;
  - poeira do bloco no impacto.
- [ ] Lux Vorata:
  - velas, bolos com vela e fogueiras apagam;
  - neblina negra dentro da zona, some ao sair;
  - proteção de spawn respeitada.
- [ ] Ferrum Ligatum:
  - tirar pelo inventário, shift-click, trocar clicando com outra peça, Q com a peça no cursor;
  - escudo na mão secundária;
  - a peça volta sem duplicar nem sumir.

**Visual e desempenho**
- [ ] Todos os selos aparecem, inclusive na face de porta/baú, e somem no fim; nada fica preso na
  tela depois de relogar.
- [ ] Shaders do pack (Iris/Oculus) com os RenderTypes de tinta/luz. A cerimônia do ethereal usa
  os mesmos e serve de referência.
- [ ] Desempenho com 20+ magias ativas na mesma área (teto de 64 visuais).
- [ ] Ícones das magias e dos efeitos aparecem (arte provisória; troque por resource pack).

## Áudio de imersão

Novos efeitos discretos do Epidemic, registros, triggers e validação pendente:
[guia de áudio](../docs/IMMERSION-AUDIO.md). Trilha e sons anteriores preservados;
sem compilação ou testes em jogo nesta máquina.
