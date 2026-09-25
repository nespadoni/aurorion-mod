# Aurorion Areas

Áreas narrativas administradas pela staff para Minecraft 1.21.1 / NeoForge 21.1.248.
Depende de `aurorion-core`. Módulo incluído no monorepo e descoberto automaticamente por `aurorion-runs`.

**Estado:** áreas de casa e escalada da Floresta Negra adicionadas em 20/09/2026; build e testes do
monorepo validados na mesma data. JAR instalável em
`../build/jars-servidor/aurorion_areas-neoforge-1.21.1-0.3.0.jar`.

Depende do `aurorion-core` **0.2.0+** (contrato `HouseGate`). A barreira de casa também precisa do
`aurorion-ethereal` no pack para ter efeito; sem ele o resto do módulo funciona igual.

## O que está implementado

- Círculos exatos e polígonos simples, inclusive côncavos; união de partes e subtração de recortes.
- Altura própria por forma. Uma sala pode ocupar apenas um andar.
- Prioridade por regra, com herança nas sobreposições.
- Voo, magia, spawn hostil, dano hostil e PvP controlados no servidor.
- Exceções por personagem, regra e área.
- **Áreas de casa**: barreira invisível que só deixa passar quem é daquela casa do `aurorion-ethereal`.
- **Água tratada por área**: só a torneira, ou toda a água da área, enche o cantil purificado (Legendary Survival Overhaul).
- Vida e dano de monstros definidos no nascimento, sem acumular multiplicadores ao recarregar chunks.
- Ambientes por datapack: sons individuais, escuridão, cegueira curta, sombra periférica, neblina e ataques invisíveis.
- **Neblina compatível com shaders**: com um pacote carregado no Iris, ela é desenhada em espaço de tela, e não pelo `RenderFog` do vanilla — que o shader ignora.
- Comandos de edição, inspeção e visualização exclusivos de staff (permissão 2).
- API pública e regras com nomes próprios para futuras profissões.

Nenhuma área é criada em coordenadas arbitrárias ao instalar. Fora das áreas, tudo começa permitido, sem ambiente e sem multiplicadores adicionais. `/area mundo` define a regra de fundo **da dimensão onde o comando é executado**.

## Escola circular, sala de treino e vila

Posicione-se no centro da escola e use um raio adequado ao mapa:

```mcfunction
/area circulo 120
/area altura -64 320
/area criar escola
/area prioridade escola 10
/area perfil escola aurorion_areas:escola
/area nome escola Escola de Aurorion
/area visualizar escola
```

O círculo usa seu X/Z como centro. A seleção já nasce usando a altura completa da dimensão — inclusive
o teto Y 4064 do Higher Heights. Para escolher outra faixa, use `/area altura <mínimo> <máximo>`
**antes de criar a área**. Os limites de Y são inclusivos.

Para uma sala irregular, inicie uma seleção e caminhe pelos cantos do contorno em ordem:

```mcfunction
/area selecao
/area ponto
```

Repita `/area ponto` em cada canto (mínimo 3; não repita o primeiro para fechar).
Também é possível informar coordenadas absolutas: `/area ponto 100 250`.
Depois, usando as alturas reais da sala:

```mcfunction
/area altura 70 76
/area visualizar
/area criar sala_treino
/area prioridade sala_treino 20
/area perfil sala_treino aurorion_areas:treino
```

O perfil de treino **só libera magia**. Voo, monstros e PvP continuam seguindo as regras da escola nas posições onde as duas áreas se sobrepõem. Criar uma área interna não a vincula à escola: a herança depende da posição e da prioridade.

Crie a vila da mesma forma e aplique `aurorion_areas:vila`.

### Relevo e formas complexas

| Comando | Uso |
|---|---|
| `/area selecao` | Inicia um polígono novo nesta dimensão |
| `/area ponto [x z]` | Adiciona um vértice ao contorno |
| `/area desfazer` | Remove o último vértice da seleção |
| `/area circulo <raio>` | Inicia uma seleção circular no seu X/Z |
| `/area altura <min> <max>` | Altera as alturas da seleção atual |
| `/area criar <id>` | Cria área usando a seleção |
| `/area adicionar <id>` | Acrescenta a seleção à união de formas da área |
| `/area recortar <id>` | Subtrai a seleção de todas as partes da área |
| `/area remover_forma <id> parte\|recorte <índice>` | Corrige uma forma já salva; índice começa em 1 |
| `/area visualizar [id]` | Desenha o contorno da seleção ou de uma área por 30 segundos |
| `/area ver <id>` | Lista formas, alturas, regras e exceções |
| `/area listar` | Lista áreas de todas as dimensões |
| `/area remover <id>` | Remove a definição da área |

A seleção permanece disponível depois de criar/adicionar/recortar. `altura` altera a seleção; não edita retroativamente uma forma salva. Para substituir uma forma, adicione a nova e remova a antiga pelo índice.

Recortes pertencem à mesma área e retiram **todas** as regras dessa área naquela posição. Para liberar só magia, use outra área com prioridade maior. Um recorte não remove a proteção de outra área sobreposta.

`/area visualizar` desenha o contorno **cravado no mundo**: ele não acompanha a câmera nem sai de dentro do personagem. Azul indica partes; vermelho indica recortes. As linhas fortes marcam o piso e o teto reais de cada forma e aparecem a qualquer distância; a parede translúcida entre elas acompanha a sua altura, aparecendo numa faixa de 24 blocos acima e abaixo da câmera, para mostrar o limite de onde você está em vez de uma torre de 400 blocos. O contorno atravessa blocos de propósito: dentro de um prédio, um limite escondido atrás da parede não ajuda a conferir nada. Nenhum bloco do mundo é modificado.

O desenho exige o módulo `aurorion_areas` **no cliente**. Quem não o tem recebe a prévia antiga por partículas, e o próprio comando avisa que é por isso que o contorno sólido não apareceu.

Limites: 512 áreas; 32 formas por área (partes + recortes); 128 vértices por polígono; raio de 0,5 a
100.000; alturas absolutas de até 30.000.000. Polígonos cruzados e degenerados são rejeitados.

## Prioridades, regras e exceções

| Comando | Efeito |
|---|---|
| `/area prioridade <id> <n>` | Maior número prevalece; faixa -10000 a 10000 |
| `/area perfil <id> <perfil>` | Substitui o conjunto de regras pelo preset |
| `/area regra <id> <regra> permitir\|negar\|herdar` | Altera apenas uma regra |
| `/area ambiente <id> <perfil>\|nenhum\|herdar` | Define, remove ou herda ambiente |
| `/area monstros <id> vida\|dano <fator>` | Define fator de 0,1 a 20, ou -1 para herdar |
| `/area casa <id> <casa>\|nenhum` | Vincula a área a uma casa, ou remove o vínculo |
| `/area excecao <id> <jogador> <regra> true\|false` | Concede ou remove autorização individual |
| `/area ativar <id> true\|false` | Liga/desliga a área preservando sua definição |
| `/area nome <id> <nome>` | Define o nome descritivo |
| `/area mundo <perfil>\|herdar` | Define/limpa o padrão da dimensão atual |
| `/area aqui [jogador]` | Mostra regras efetivas, área de origem e sobreposições |

Regras nativas: `voo`, `magia`, `monstros`, `dano_monstros`, `pvp`, mais duas de **concessão**:
`casa` (passe de visitante de uma área de casa) e `agua_pura` / `agua_pura_pia` (água tratada).
Ver abaixo.

**Proibição e concessão são leituras opostas do mesmo cadastro.** As cinco nativas são proibições:
valem no mapa inteiro até alguém negar. `agua_pura` é uma concessão: **não existe em lugar nenhum
até alguém permitir**. Por isso não adianta deixá-la em `herdar` e esperar que funcione — sem um
`permitir` explícito, a resposta é não.
`monstros negar` impede spawn; `dano_monstros negar` impede dano causado por hostis. PvP considera as posições de atacante e vítima.

A decisão é independente para cada regra: vence a área de maior prioridade que tenha valor explícito. Em empate, vence o id alfabeticamente menor. `herdar` remove o valor local; não significa `permitir`. Ambiente, multiplicador de vida e multiplicador de dano também resolvem separadamente.

Para permitir que um personagem voe na escola:

```mcfunction
/area excecao escola NomeDaConta voo true
/area aqui NomeDaConta
```

Remova com `false`. O nome é o da conta Minecraft (também aceita seletores de jogadores); o dado usa UUID. A autorização permite usar a habilidade que o personagem já tem, sem conceder voo. Ela tem a prioridade daquela área: uma sala de prioridade maior com voo explicitamente negado requer sua própria exceção. O reset de personagem do Aurorion remove suas autorizações, incluindo resets recuperados após falha.

NPCs e outras entidades não recebem restrições de voo/magia. Fake players também têm bypass. Para NPCs que usam tipos classificados como monstros, há a tag de tipos `aurorion_areas:exempt_entities` e a tag individual de entidade `aurorion_areas_npc`. A tag individual deve estar definida **antes do spawn** quando a área impede monstros (por exemplo, no NBT de invocação do NPC).

Espectadores e, por padrão, staff em criativo ignoram restrições pessoais e ambientes. **Esta é a causa mais comum de "criei a área e nada acontece":** quem acabou de criar a área quase sempre está em criativo, e por isso continua voando dentro dela. `/area criar` e `/area mundo` avisam na hora quando você está nessa situação, e `/area aqui` mostra o bypass a qualquer momento. Para avaliar a experiência real, consulte um jogador de sobrevivência ou desative `creativeStaffBypass` pela configuração do servidor.

## Áreas de casa (barreira invisível)

Uma área pode pertencer a uma das casas do `aurorion-ethereal`. Quem não é daquela casa **não
atravessa o limite dela**:

```mcfunction
/area criar casa_sylvara
/area prioridade casa_sylvara 30
/area casa casa_sylvara aurorion_ethereal:sylvara
```

O autocomplete oferece as casas do datapack carregado, e um id que não existe é recusado na hora.
`/area casa <id> nenhum` desfaz o vínculo. O vínculo fica no cadastro da área, não nas regras: `/area
perfil` **não** o apaga, porque um preset compartilhado entre várias áreas não pode carregar junto a
dona de uma delas.

### Como a barreira se comporta

Não é congelamento nem prisão: é uma **parede**. Quem tenta entrar volta à posição que ocupava um
tick antes — menos de um passo —, perde a velocidade e recebe um aviso curto na barra de ação com um
baque abafado. Continua andando normalmente para qualquer outro lado. A checagem é por posição, não
por porta: elytra, teleporte, pérola e queda batem na mesma parede.

| Situação | O que acontece |
|---|---|
| Jogador **da** casa | Entra normalmente |
| Jogador de outra casa, ou sem casa | Barrado |
| **Criativo** | Passa por qualquer casa, **com ou sem OP** |
| **Sobrevivência/aventura** | Barrado, **com ou sem OP** |
| Espectador | Passa |
| NPC (`aurorion_areas_npc`) e fake player | Passam |

Essa é a **diferença deliberada** para as outras regras do módulo, que usam `creativeStaffBypass`
(criativo **e** permissão 2). Aqui vale o modo de jogo, e só ele: a barreira é ficção do mundo, não
ferramenta de moderação. Um administrador jogando em sobrevivência participa da ficção como qualquer
pessoa; para atravessar, ele troca para criativo. `/area aqui` mostra a casa da posição, a casa do
jogador e se ele passa ou está barrado.

### Passe de visitante

A barreira reaproveita a máquina de exceções, então não há cadastro novo para aprender:

```mcfunction
/area excecao casa_sylvara NomeDaConta casa true    # uma pessoa entra
/area regra   casa_sylvara casa permitir            # porta aberta (um evento, por exemplo)
```

O reset de personagem do Aurorion remove esses passes junto com as outras autorizações.

### Áreas de casa sobrepostas

Quando duas áreas com dona cobrem o mesmo ponto, vale a de **maior prioridade** — a mesma regra do
ambiente. É assim que uma embaixada de Ignivar dentro do bairro de Sylvara funciona. Uma área de
prioridade maior **sem** dona não desempata nada: quem não opina sobre casa não tira a dona de
ninguém.

### Quem entrou sem passar pela porta

Deslogar dentro, nascer dentro ou ser teleportado para dentro não deixa ninguém preso: sem posição
anterior guardada, o módulo empurra a pessoa para fora da forma em que ela está (pelo raio, se for
círculo; pela face mais próxima, se for polígono) e procura chão firme ali com o `SafeSpot` do core.
Isso acontece no máximo uma vez por segundo.

Sem o `aurorion_ethereal` no pack, **a barreira não barra ninguém** e o motivo aparece uma vez no log
— o contrário (todo mundo sem casa, logo todo mundo barrado) muraria o mapa inteiro por um jar
faltando. O vínculo continua gravado e volta a valer quando o mod voltar.

## Floresta Negra e exterior

Crie o contorno da floresta com a seleção e aplique:

```mcfunction
/area criar floresta_negra
/area prioridade floresta_negra 10
/area perfil floresta_negra aurorion_areas:floresta_negra
```

Para tornar os monstros mais fortes em toda a dimensão fora das áreas seguras:

```mcfunction
/area mundo aurorion_areas:exterior
```

| Preset | Comportamento |
|---|---|
| `escola`, `vila`, `seguro` | Nega voo, magia, spawn hostil, dano hostil e PvP; sem ambiente; fatores 1 |
| `treino` | Permite apenas magia; restante herdado |
| `exterior` | Permite as cinco regras; vida ×1,5, dano ×1,25; sem ambiente |
| `floresta_negra` | Ambiente da floresta; vida ×2, dano ×1,5; demais regras herdadas |

Todos os ids acima usam o prefixo `aurorion_areas:`.

A floresta inclui, **no momento da entrada**:

- 23 sons inquietantes do Minecraft (passos, madeira estalando, respiração, sculk, vocalizações de warden, ghast, enderman, vex, phantom e raposa), a cada 5–13 segundos, em posições próximas de cada jogador.
- Darkness de 5 segundos, cegueira de 2 segundos e sombra periférica, a cada 20–38 segundos.
- Neblina com alcance de 26 blocos e transição gradual.
- Ataque invisível de 1 ponto de dano (meio coração antes das reduções), a cada 35–65 segundos.
- Uma frase na barra de ação, a cada 22–48 segundos: a voz da própria pessoa ("Eu deveria sair daqui.", "Não olha para trás.").
- Ataques não letais por padrão: o dano final desse tipo é limitado para deixar pelo menos 1 ponto de vida.

### Quanto mais tempo, pior

O bloco `dread` do ambiente faz tudo acima apertar conforme o relógio de permanência corre. A escala
vai de 0 (na entrada) a 1 (depois de `ramp_seconds`, hoje **5 minutos**), e cada campo interpola
linearmente entre o valor configurado e o auge:

| No auge (5 min lá dentro) | Entrada | Auge |
|---|---|---|
| Dano do ataque invisível | 1 | **5** (`damage_scale`) |
| Intervalo de sons, pulsos, ataques e frases | 100% | **35%** (`interval_scale`, ou ~3× mais frequente) |
| Darkness do pulso | 5 s | **14 s** (`darkness_bonus_seconds: 9`) |
| Cegueira do pulso | 2 s | **7 s** (`blindness_bonus_seconds: 5`) |
| Sombra periférica | 0,65 | **0,9** (`vignette_bonus: 0.3`, cortado no teto do sistema) |
| Apagão de tela cheia | **nenhum** | **0,92 por 5 s** (`blackout`) |
| Neblina | 26 blocos | **~8 blocos** (`fog_scale: 0.3`) |
| Cor das frases | cinza-lilás `#8A7F9B` | vermelho `#9E2B2B` (`peak_color`) |

### O apagão

`blackout` é o único campo do pulso que **nasce** do medo em vez de só crescer com ele: na entrada é
zero, e no auge a tela fica preta por inteiro — HUD junto — por `blackout_seconds`. Depois disso volta
para a escuridão pesada de sempre (Darkness + sombra periférica), que continua correndo.

**A duração é separada da do Darkness de propósito.** O Darkness do pulso chega a 14 s no auge; se o
apagão durasse tudo isso, com criatura em cima, deixaria de ser susto e viraria impossibilidade de
jogar. Cinco segundos de preto total, e o resto do pulso em escuridão que ainda deixa jogar, assusta
mais. `blackout_seconds` no JSON muda isso; o apagão nunca passa do que sobra do pulso.

A tela fecha rápido (~1 s) e abre devagar (~2,5 s): cair na escuridão de repente assusta, sair dela
no mesmo tempo parece um piscar de olhos e desmancha o susto. F1 não escapa — quem esconde o HUD
dentro da floresta não ganha visão noturna de brinde.

O medo **zera ao sair** e a próxima entrada recomeça do sussurro mais manso — não há decaimento
gradual, de propósito: um estado que continua correndo fora da floresta seria invisível para quem
está jogando e impossível de explicar.

### Aos sete minutos, a floresta mata

`lethal_after_seconds: 420`. Até os sete minutos o dano escalado ainda para em 1 ponto de vida —
a pessoa fica pendurada no fio, e qualquer outra coisa a mata. Passados os sete, o ataque troca para
o tipo de dano letal e **passa a matar de verdade, gastando uma vida do `aurorion-vidas`**.

A escalada de dano termina antes disso de propósito: aos 5 minutos o golpe já está em 5 pontos
(2,5 corações) a cada 12–23 segundos. Os dois minutos seguintes são o aviso — a pessoa apanhando
forte, na neblina fechada, sabendo que algo mudou — e só então a morte entra. Quem quiser desligar
isso põe `lethal_after_seconds: 0` no JSON e dá `/reload`; o resto da escalada continua igual.

Os relógios começam na entrada e não reiniciam a cada passo. Sons e frases são privados: não há
monstros falsos, nem transmissão para outros jogadores, nem mensagem no chat. Ao sair, novos eventos
param e a imagem desaparece gradualmente. Efeitos de poção já aplicados terminam pelo seu prazo
curto; não removemos poções de outras fontes.

A neblina reaperta em degraus de meio bloco, no máximo uma vez por segundo — sem isso, a escalada
custaria um pacote por jogador por tick dentro da floresta.

### O que nasce lá dentro

A partir de 45 segundos (`start_after_seconds`), a floresta começa a **povoar em volta de quem está
lá**, em três camadas — o que aparece na entrada não é o que aparece aos seis minutos:

| Camada | A partir de | Criaturas |
|---|---|---|
| Entrada | 0 | Phantom (voando), Corpse Fly, Restless Spirit, Gloomoth |
| Meio | 35% do medo (~1min45) | Decaying Zombie, Dread Hound, Decrepit Skeleton, Foliaath, Watcher |
| Auge | 70% (~3min30) | Nightmare Stalker, Seared Spirit, Skeleton Thrasher, Spiders Mother |
| Pesadelo | 85% (~4min15) | Forsaken |

Vêm do **Born in Chaos**, **Alex's Caves**, **Mowzie's Mobs** e do vanilla. Id de mod que não estiver
instalado é **ignorado em silêncio**, igual aos sons: a mesma lista serve a um pack com Born in Chaos
e a um sem ele, e o Phantom garante que algo apareça mesmo num pack sem mod de monstro nenhum.

A leva cresce de 1 para 3 criaturas conforme o medo sobe, e a partir de metade do medo (`hunt_from`)
elas **já nascem sabendo onde você está** em vez de procurar.

> ⚠️ As criaturas nascem dentro da área, então pegam os multiplicadores dela: **×2 de vida e ×1,5 de
> dano**. Um `alexscaves:forsaken` com o dobro de vida, aos quatro minutos, na escuridão, é de
> propósito — mas é o primeiro id a tirar da lista se ficar cruel demais.

#### Como isso não vira um exército

Três travas independentes, porque uma sozinha falharia:

- **Teto por jogador** (`max_nearby: 7`): antes de criar qualquer coisa, o módulo conta as criaturas
  **dele** vivas em volta daquele jogador. Ficar duas horas na floresta não acumula nada.
- **Nenhuma é persistente**: o despawn do vanilla leva embora tudo o que fica longe de jogador. A
  floresta volta ao normal sozinha quando ninguém está nela.
- **Nunca gera chunk**: um ponto fora de chunk carregado é descartado, em vez de forçar a geração
  dele na thread do servidor.

Isto **não é spawn natural**: não mexe no `NaturalSpawner`, não ocupa cota de mob do chunk e não
depende de luz nem de bioma. Mas continua obedecendo a regra `monstros` da área — se ela negar spawn
hostil, estas criaturas são recusadas junto com as naturais, sem nenhuma linha de código a mais. Elas
também passam pelo `FinalizeSpawnEvent`, então os outros mods do pack ajustam equipamento e variante
como fariam com qualquer spawn.

Uma tentativa a cada 28–55 segundos (encurtando com o medo) **por jogador que está dentro**, nunca
por tick e nunca para quem está fora.

### Água tratada nas áreas (Legendary Survival Overhaul)

Duas regras, e a diferença entre elas é o que você provavelmente quer:

```mcfunction
/area regra academia agua_pura_pia permitir    # só o que sai da torneira
/area regra oasis    agua_pura     permitir    # toda a água de dentro
```

| Regra | O que fica purificado |
|---|---|
| `agua_pura_pia` | Só o cantil enchido num bloco da tag `aurorion_areas:water_taps` |
| `agua_pura` | **Toda** água enchida dentro da área — torneira, caldeirão, poça, rio que passe por dentro |

**Para a Academia, use `agua_pura_pia`.** Assim a torneira do refeitório dá água tratada e o lago do
pátio continua dando água de lago, dentro da mesma área. `agua_pura` é para um lugar onde a água é
limpa por ser dali — um oásis, uma fonte sagrada.

As duas somam: uma área pode conceder as duas, e basta uma conceder. Um recorte de prioridade maior
com `negar` volta a ser água normal.

| Comando | Efeito |
|---|---|
| `/area regra academia agua_pura_pia permitir` | Torneiras da Academia dão água tratada |
| `/area regra laboratorio agua_pura_pia negar` | Um recorte de prioridade maior volta a ser água normal |
| `/area excecao poco Fulano agua_pura true` | Só aquele personagem tira água limpa dali |

#### Quais blocos contam como torneira

A tag `data/aurorion_areas/tags/block/water_taps.json` vem com as **26 pias de cozinha do Refurbished
Furniture** — que são, no seu pack, as únicas que o LSO sabe encher cantil: a integração dele só
conhece `KitchenSinkBlockEntity` e `BasinBlockEntity`. As pias do Cooking for Blockheads e do Macaw's
não enchem cantil de jeito nenhum, então não adianta colocá-las na tag.

**Bacia (`basin`) ficou de fora de propósito** — água parada numa bacia não é água tratada. Se você
quiser incluir, é uma linha no datapack:

```json
{ "id": "refurbished_furniture:oak_basin", "required": false }
```

Use sempre `"required": false`: sem isso, um pack sem o mod recusa o datapack inteiro.

#### Por que não pelo encantamento nem por bloco

O LSO já resolve isso pelo lado errado: o encantamento `Purity` no cantil purifica **qualquer** água —
rio, lago, pântano — e com isso apaga a diferença entre lugar seguro e lugar perigoso, que é a
mecânica que este módulo existe para sustentar. Aqui quem purifica é o **lugar**, e a tag só diz o que
conta como encanamento dentro dele.

O gancho é o `CanteenItem.fill` do LSO, não o clique: clicar numa parede com um cantil de água de rio
na mão também "termina" dentro da área, e enganchar no clique purificaria água velha sem ninguém ter
enchido nada. Pelo `fill` passam todos os caminhos — água natural, caldeirão e a integração de
pia/bacia do Refurbished. Cantil grande vale igual: ele herda o `fill`.

Cantil vazio, água de chuva, poção e água já purificada não são tocados — a regra diz **de onde a
água veio**, não reescreve o conteúdo do cantil.

Sem o LSO no pack, nada disto é carregado e o resto do módulo funciona igual. Validado contra o jar
**2.4.7.2** pelo `LsoThirstContractTest` (pulado sem `AURORION_LSO_JAR`).

### Aviso à staff

Um lugar que mata não pode matar em silêncio. O bloco `alert` do ambiente avisa **quem tem OP
nível 2+** em três momentos, no chat (e sempre no log do servidor, mesmo sem ninguém online):

```
[Áreas] Fulano entrou em Floresta Negra — minecraft:overworld 118 71 -342
[Áreas] Fulano está há 7min em Floresta Negra: os ataques já podem matar. — minecraft:overworld 96 68 -370
[Áreas] Fulano saiu de Floresta Negra depois de 8min 14s.
```

Deslogar lá dentro também fecha o aviso (`desconectou dentro de ...`) — senão a pessoa ficaria "lá
dentro" para sempre no histórico de quem modera, que é exatamente o que alguém faria para escapar.

O nome é o da **conta**, não o fakename: quem modera precisa saber quem é a pessoa, a mesma fronteira
que sustenta o `/realname`. O nome do lugar é o `/area nome` da área que aplicou o ambiente, caindo
no id do ambiente quando ele vem do padrão da dimensão.

| Campo de `alert` | Padrão (com o bloco presente) | O que faz |
|---|---|---|
| `enter` | `true` | Avisa quando alguém entra |
| `lethal` | `true` | Avisa quando o relógio de `lethal_after_seconds` estoura |
| `exit` | `true` | Avisa quando sai ou desconecta |
| `cooldown_seconds` | `120` | Silêncio mínimo entre dois avisos de entrada do mesmo jogador |

O bloco mora no **ambiente**, e não na área: quem é perigoso é o ambiente, e três manchas de floresta
não deveriam precisar ligar o aviso três vezes. **Ambiente sem o bloco `alert` não avisa nada** —
nenhum ambiente existente começa a falar sozinho.

Chat, e não barra de ação, ao contrário de todo o resto que este módulo manda: este precisa ficar no
histórico, para quem entrar dez minutos depois conseguir rolar para cima e ver que alguém entrou na
floresta e não saiu.

`lethal: true` no ataque seleciona outro tipo de dano, que pode matar e acionar o sistema de vidas,
sem esperar pelo relógio. Não letal limita o golpe ambiental; não torna o jogador imune a outros
perigos.

### Conteúdo por datapack

Presets: `data/<namespace>/aurorion/area_rules/<id>.json`.
Ambientes: `data/<namespace>/aurorion/area_ambience/<id>.json`.

Exemplo de preset:

```json
{
  "flags": { "voo": "deny", "magia": "allow" },
  "ambience": "meupack:bosque",
  "mob_health": 2,
  "mob_damage": 1.5
}
```

Chaves ausentes herdam. Flags usam `allow/deny/inherit` no JSON. `aurorion_areas:none` cancela explicitamente ambiente herdado.

Use o [ambiente de exemplo](src/main/resources/data/aurorion_areas/aurorion/area_ambience/floresta_negra.json) como ponto de partida. Sons aceitam ids registrados de qualquer mod. Para vozes/sussurros gravados, um resource pack pode substituir os áudios de ids utilizados, ou uma integração pode registrar novos eventos. O módulo já funciona com sons vanilla, mas não inclui gravações de fala inéditas.

Limites de ambiente: intervalos 1–3600 segundos; até 32 sons; volume 0,01–2; pitch 0,5–2; distância dos sons 2–16; darkness até 30 segundos; cegueira até 15; dano por golpe até 10; neblina até 256 blocos. Dano 0, lista de sons vazia e durações 0 desligam seus respectivos eventos.

Limites do `pulse`: `blackout` 0–1 e `blackout_seconds` 1–30. Limites de `spawns`: até 32 criaturas,
`weight` 1–100, `from`/`hunt_from` 0–1, distâncias 2–64, `max_nearby` 0–24, `count`/`peak_count` 0–8,
`start_after_seconds` 0–3600. Limites do `dread`: `ramp_seconds` 1–3600; `damage_scale` 1–20; `interval_scale` 0,1–1; `fog_scale` 0,05–1; bônus de darkness até 30 s e de cegueira até 15 s; `vignette_bonus` até 0,9; `lethal_after_seconds` 0–3600 (0 = nunca). Limites de `whispers`: até 32 frases de 1 a 96 caracteres, `color` e `peak_color` em `#RRGGBB`. Limite de `alert`: `cooldown_seconds` 0–3600.

**Ambiente sem `dread`, `whispers`, `alert` ou `spawns` se comporta exatamente como antes destes campos existirem** — os dois são opcionais e seus padrões são neutros. Escrever uma frase nova é editar o JSON e dar `/reload`: as frases são texto literal, não chave de tradução, então aparecem iguais inclusive para quem não tem o módulo no cliente (mesmo motivo de os sons serem ids soltos).

`/reload` atualiza os ambientes ativos. Presets de regras são **copiados** ao aplicar `/area perfil`: editar o JSON do preset exige reaplicá-lo às áreas ou ao mundo. O conteúdo de um ambiente, por outro lado, é consultado pelo id e atualizado na recarga. JSON inválido é registrado no log e ignorado pelo catálogo; verifique o log de recarga ao editar.

## Neblina e shaders

`ViewportEvent.RenderFog` e `ComputeFogColor` são eventos do **pipeline do vanilla**. Quando o Iris
carrega um pacote de shaders, quem calcula a neblina passa a ser o fragment shader do pacote, com
uniformes próprios, e o plano distante que a gente pede simplesmente não é consultado. Na prática, a
neblina da Floresta Negra existia para quem jogava sem shader e **sumia** para quem jogava com BSL,
Complementary ou Solas — que é quase todo o servidor.

A saída não é brigar com o shader: é desenhar a neblina **depois** dele.

| Com shader ligado | Sem shader |
|---|---|
| `AreaClient.screenFog` pinta a mesma cor e a mesma densidade em espaço de tela, depois de o quadro estar composto | `AreaClient.fog` faz a neblina de verdade, que fica melhor |

O interruptor é [`ShaderPacks`](../aurorion-core/src/main/java/com/aurorion/core/client/ShaderPacks.java)
no `aurorion-core` (ponte reflexiva com a API v0 do Iris: sem o Iris instalado a resposta é sempre
"não", sem custo nenhum), e o desenho é o
[`ScreenFog`](../aurorion-core/src/main/java/com/aurorion/core/client/ScreenFog.java). A resposta é
reconsultada uma vez por segundo, porque o jogador liga e desliga shader em jogo (K, no teclado padrão
do Iris). As duas técnicas nunca desenham ao mesmo tempo: somadas, escureceriam o dobro.

**A névoa que se vê passar é partícula.** Neblina desenhada, seja pelo plano distante ou pela tela, é
uma cor: ela não tem volume, não se move e não passa entre as árvores. Por isso o ambiente também
solta fumaça e cinza baixas em volta de quem está dentro — no máximo três por tick, num cubo de oito
blocos, divididas pela opção "Partículas" do vanilla. Partícula atravessa o pipeline do shader como
qualquer outra do jogo, então esse pedaço aparece igual nos três casos.

A sombra periférica e o apagão já eram retângulos desenhados em cima do HUD, e nunca dependeram do
shader.

## Integrações e limites atuais

- **Vanilla:** fiscaliza `abilities.flying` e elytra no servidor; oferece aterrissagem curta ao interromper voo. Não retira `mayfly` nem restaura habilidades antigas de outro mod.
- **Iron’s Spells:** ponte opcional com `SpellPreCastEvent` cancelável e cancelamento de conjuração em andamento na entrada de uma restrição. Também classifica `irons_spellbooks:angel_wing` como voo e remove o efeito `irons_spellbooks:angel_wings` de jogadores em área proibida. Fontes conferidas no [evento oficial](https://github.com/iron431/irons-spells-n-spellbooks/blob/1.21/src/main/java/io/redspace/ironsspellbooks/api/events/SpellPreCastEvent.java), [Utils](https://github.com/iron431/irons-spells-n-spellbooks/blob/1.21/src/main/java/io/redspace/ironsspellbooks/api/util/Utils.java) e [registro de efeitos](https://github.com/iron431/irons-spells-n-spellbooks/blob/1.21/src/main/java/io/redspace/ironsspellbooks/registries/MobEffectRegistry.java). API incompatível é indicada no log. A versão instalada precisa de validação no pack.
- **Create, addons, jetpacks e vassouras:** não existe bloqueio universal confiável para qualquer implementação de movimento. Itens de uso, efeitos e montarias conhecidos podem ser classificados pelas tags abaixo. Equipamentos passivos, teclas próprias e veículos com movimentação própria precisam consultar a API/integrar seu mecanismo específico quando a lista do pack estiver disponível.
- **Magia:** bloqueia a conjuração local via integração. Não apaga projéteis/feitiços já lançados nem interpreta todos os tipos de dano mágico de terceiros.
- **Zona segura:** bloqueia novos hostis pelos eventos de spawn/entrada, dano hostil e aquisição de alvo protegido. Não apaga mobs já salvos nem vasculha chunks. Mobs carregados de disco, inclusive os pré-gerados pelo worldgen, são preservados; alvos já adquiridos podem continuar sendo perseguidos, mas o dano hostil é vetado.
- **Força:** vale para novos monstros. Mobs existentes não são recalculados ao atravessar fronteiras ou mudar um preset. O multiplicador de dano acompanha a entidade causadora, incluindo projéteis com dono reconhecido.
- **NPCs:** continuam com suas mecânicas; classifique NPCs de tipo hostil pelas exceções de entidade antes do spawn.
- **Legendary Survival Overhaul:** ponte opcional de sede — a regra `agua_pura` promove a água posta no cantil a purificada. Alcança `ThirstUtil` por reflexão e `CanteenItem.fill` por mixin `@Pseudo` com `requiredMods`, então nenhuma classe do LSO aparece em assinatura nossa. API incompatível é registrada no log e a regra deixa de purificar, sem derrubar nada.
- **Casas (`aurorion-ethereal`):** a ligação é o contrato `HouseGate` do `aurorion-core`, não um import — os dois mods continuam independentes. O Ethereal publica "em que casa está este jogador"; as áreas perguntam. Sem o Ethereal instalado, a barreira fica inativa e avisa no log.
- **Cliente:** regras, sons, criaturas e poções são do servidor; neblina, sombra periférica, apagão e o contorno de `/area visualizar` precisam deste módulo no cliente. O canal de estado subiu para a versão `2` (o apagão entrou no payload): um cliente com o jar antigo simplesmente não negocia o canal e fica sem neblina e sem apagão — nunca uma desconexão. Os payloads são opcionais e só são enviados a quem negociou o canal. Neblina de água/lava e neblina já mais densa são preservadas.

Tags para datapack (pastas singulares do Minecraft 1.21):

| Caminho dentro de `data/aurorion_areas/tags/` | Efeito |
|---|---|
| `item/flight_items.json` | Bloqueia uso de itens classificados como voo |
| `item/magic_items.json` | Bloqueia uso de itens classificados como magia |
| `entity_type/flight_mounts.json` | Impede montar e desmonta jogadores de veículos classificados |
| `mob_effect/flight_effects.json` | Remove efeitos explicitamente classificados como voo |
| `entity_type/hostiles.json` | Acrescenta tipos à classificação hostil |
| `entity_type/exempt_entities.json` | Isenta tipos da classificação hostil (NPCs, por exemplo) |
| `block/water_taps.json` | Blocos que contam como torneira para a regra `agua_pura_pia` |

Os eventos de uso não desativam automaticamente armaduras ou acessórios passivos. Entradas de mods opcionais devem usar `{"id":"mod:objeto","required":false}`.

A configuração nativa é registrada em `config/aurorion/areas-server.toml`: `enabled`, `creativeStaffBypass`, `houseBarrier` (liga/desliga a barreira das áreas de casa) e `flightSpells` (ids Iron classificados como voo). Desligar o sistema suspende a aplicação de regras/eventos; modificadores de vida já atribuídos a mobs continuam persistidos.

### API para profissões e outros mods

A API é para a thread do servidor. Exemplos:

```java
AreaApi.allows(player, AreaRule.FLIGHT);
AreaApi.allows(player, AreaRule.MAGIC);
AreaApi.allows(player, "profissoes:coletar");
AreaApi.allowsAt(level, x, y, z, player.getUUID(), "profissoes:coletar");
```

As consultas com jogador consideram bypass. `allowsAt` é a política da posição, incluindo exceções explícitas, sem bypass criativo.

A staff pode cadastrar a regra futura agora:

```mcfunction
/area regra escola "profissoes:coletar" negar
```

As aspas permitem `:` no argumento. A regra só passa a afetar uma profissão quando o código daquela profissão a consulta.

## Persistência e desempenho

Geometria, regras de mundo e exceções ficam no SavedData `aurorion_areas_regions` do overworld, separadas por dimensão. O conteúdo JSON é armazenado como bytes UTF-8 em NBT para não depender do limite de strings NBT. Salvamento segue o autosave do mundo; seleções e previews são temporários.

Se as definições não puderem ser lidas (JSON inválido, versão desconhecida ou formato NBT inesperado), o NBT original é preservado e a edição fica bloqueada. Nenhuma área parcialmente carregada entra em vigor. O erro aparece no log; é preciso corrigir o arquivo e reiniciar o servidor. Resets de personagem também ficam pendentes para que as exceções antigas sejam removidas após o reparo.

Índice espacial de caixas por dimensão (BVH), reconstruído apenas ao editar. Não há índice proporcional ao tamanho da área nem carregamento de chunks. Cada jogador reutiliza sua resolução de regras; a geometria só é consultada novamente quando a posição ou o cadastro muda. Sons/ataques usam relógios por jogador, e não buscas de entidades.

Polígonos guardam coordenadas em arrays e calculam a tolerância das bordas na criação, evitando recalcular o comprimento das arestas a cada consulta. Eventos de dano e alvo reutilizam as regras do jogador; os padrões imutáveis da dimensão também são reutilizados. Prévia inativa retorna imediatamente, sem consultar o mapa de administradores.

O contorno é **um único pacote** para o administrador que pediu, com a geometria daquela área apenas, e nada mais trafega durante os 30 segundos: a animação é toda local. A geometria por quadro é orçada — com muitas formas, a banda vertical engrossa e os círculos perdem lados, em vez de o número de vértices crescer junto. Sem o módulo cliente, a prévia cai para no máximo 96 partículas privadas a cada 10 ticks. Estado visual de tamanho fixo é enviado na mudança e nos pulsos. Nenhuma lista de áreas ou posições de outros jogadores é sincronizada ao cliente.

## Validação no ambiente do pack

Os testes do módulo cobrem concavidade, bordas, alturas, recortes, rejeição de contornos inválidos, precedência, herança, exceções, reset de personagem, dimensões, serialização e comparação do índice com resolução exaustiva. Incluem atualização dos padrões em resultados reutilizados, isolamento das exceções por personagem, preservação de dados ilegíveis e rejeição de carregamento parcial.

Conferir a neblina **com shader ligado e desligado** (tecla K no Iris, em jogo), dentro da Floresta Negra: ela tem que aparecer nos dois casos, com a mesma cor, e **nunca as duas técnicas ao mesmo tempo**. Conferir com BSL, Complementary e Solas, que são os do pack, e que a neblina de tela não cobre a barra de itens nem o chat.

Quando o código for levado ao ambiente de execução, conferir escola/sala/exterior, entrada voando de elytra, personagem autorizado, NPC voando, Iron's Spells com conjuração em andamento, spawn natural/ovo/spawner, proteção contra projéteis hostis, entrada/saída da floresta, reload e reconexão. Confirmar os efeitos visuais com os shaders e os demais mods de neblina do pack.

Da água tratada, conferir em jogo com `agua_pura_pia` ligada na Academia: encher numa pia de cozinha do Refurbished (deve sair `Cantil (purificado)`), encher numa poça ou num caldeirão **dentro da mesma área** (deve sair normal), encher no rio fora dela (normal), o cantil grande na pia, e uma bacia (normal, por não estar na tag).

Da barreira de casa, conferir em jogo: membro entrando, não-membro batendo na parede, entrada de elytra e por pérola, criativo atravessando **sem OP**, sobrevivência barrada **com OP**, jogador sem casa, passe de visitante por exceção, deslogar dentro e voltar, e uma embaixada de outra casa dentro do bairro.

Da floresta, conferir a escalada ao longo dos cinco minutos (frequência dos sons, neblina fechando, duração da cegueira, frases aparecendo, dano crescendo, apagão de tela cheia surgindo e as três camadas de criaturas) e, aos sete, a virada letal: que a morte acontece, que a mensagem de morte sai traduzida e que ela desconta uma vida no `aurorion-vidas`. Conferir também os três avisos à staff com um segundo cliente opado, incluindo o de desconexão lá dentro.
