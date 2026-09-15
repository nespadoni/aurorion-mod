# Aurorion — Briefing de economia

> **Status: proposta, nada disso existe em código ainda.** Este documento não descreve o que o
> ecossistema faz; descreve o que se pretende construir e o que já está decidido. Tudo aqui é
> discutível, menos o que estiver marcado como **restrição** — essas são consequências de código que
> já está no ar.

## 0. Para que serve este documento

Serve para uma conversa de design sobre **a economia do servidor Aurorion**: qual moeda, quanto vale,
quem cria dinheiro, quem destrói, e o que obriga um jogador a trabalhar.

Quem lê isto não conhece o servidor, então as seções 1 a 4 são contexto: o que já existe, o que cada
mod impõe à economia e quais regras de arquitetura qualquer proposta precisa respeitar. A seção 5 é o
modelo proposto até agora, com números. A seção 7 é o que falta decidir — é ali que a discussão deve
acontecer.

---

## 1. O servidor em cinco linhas

- **Minecraft 1.21.1 / NeoForge 21.1**, modpack pesado, servidor dedicado, **80 a 90 jogadores**.
- É um servidor de **RP com temática escolar/mágica**: os jogadores são alunos de Ethereal, divididos
  em cinco casas, com aulas, eventos e provas conduzidos por uma staff ativa.
- Existe **morte com consequência**: cada jogador tem 5 vidas; ao zerar é exilado numa dimensão
  própria (o Limbo) e tem 48 horas reais para alguém ir buscá-lo. Se ninguém for, o personagem morre
  em definitivo e a pessoa recomeça com outro personagem, do zero.
- **Quase todo o mundo é trancado**: só o overworld é livre. As outras dimensões abrem em janelas
  agendadas (os "trens").
- Há um ecossistema de 12 mods próprios, num monorepo, escritos sob um documento de arquitetura
  (`SDD.md`) que é levado a sério.

**O problema que motivou esta conversa:** hoje não existe dinheiro no servidor. A única moeda de
qualquer coisa é a **vida** (resgatar alguém do Limbo custa 1 vida de quem vai). Queremos vender dois
itens novos de recuperação de morte, e isso obrigou a pergunta "vender por quê?".

**O que o dono do servidor quer da economia, nas palavras dele:** uma economia de *país de PIB
baixo*. Rentabilidade que obrigue o jogador a trabalhar, a sobreviver e principalmente a **explorar**.
Com o sistema de vidas, muitos jogadores não vão querer sair da vila inicial — então a economia
precisa girar em torno da vila (ofícios, serviços) **e** recompensar quem se arrisca a sair.

---

## 2. Os mods que já existem, e o que cada um impõe à economia

| Mod | O que faz hoje | O que isso significa para a economia |
|---|---|---|
| **aurorion-core** | Biblioteca. Persistência por UUID (`SavedData`), identidade de personagem (`CharacterData`), busca de ponto seguro (`SafeSpot`), GUI de NPC compartilhada | É onde uma carteira virtual moraria. **Restrição:** `CharacterResetEvent` apaga *tudo* que o personagem anterior tinha quando há morte definitiva — cada mod apaga a sua parte |
| **aurorion-talk** | Falas viram balões acima da cabeça e somem do chat. Mensagem privada (`/msg`, `/tell`, `/w`) é **recusada** para jogador comum | Negociação é pública por design. Não existe sussurro para combinar preço — o comércio acontece à vista dos outros, ou fora do jogo |
| **aurorion-essentials** | `/fakename`, `/ajuda`, e **limpeza de itens no chão a cada 60 min** | **Restrição:** qualquer coisa que dependa de item parado no chão é apagada em até uma hora. Moeda perdida no chão é um ralo natural da economia |
| **aurorion-utils** | `/abduzir`, `/congelar` — ferramentas de cena para a staff | Neutro |
| **aurorion-aeonita** | **O mod de conteúdo**: é onde item e bloco são registrados. Lingotes de Aeonita (3 cores), blocos 9:1, o Altar de Seleção | **Restrição de arquitetura:** conteúdo permanente mora aqui, porque mod de mecânica sai do modpack quando o ato acaba — e aí todo item dele vira ar no baú de quem guardou. **Uma moeda física tem que nascer aqui**, não no mod que a gasta |
| **aurorion-ethereal** | As 5 casas (datapack), a Cerimônia de Vinculação, e o ranking: `/pontos aluno` e `/pontos casa`, exibidos num projetor | **Pontos não são dinheiro** e não devem virar: são o ranking escolar, e gastá-los faria o placar mentir sobre o que cada aluno fez. Mas a **casa** é o agrupamento social pronto para ser agente econômico (caixa coletivo, obra financiada pela casa) |
| **aurorion-portais** | Toda dimensão exceto o overworld é trancada; o acesso abre em janelas agendadas por datapack (os "trens", com estação e plataforma de chegada). Quem tem **passe** atravessa fora da janela | **O gancho mais forte para despesa recorrente.** `TransitPass` (dimensão + validade + usos) já é gravado por UUID e o código foi escrito esperando um item que o conceda: bastaria chamar `PassData#grant`. Vender passagem de trem é quase de graça de implementar |
| **aurorion-mundos** | Vários overworlds, cada um com seed, barreira e portais próprios | Geografia econômica: mercados podem ser diferentes por mundo, e o custo de atravessar é controlável |
| **aurorion-vidas** | 5 vidas por jogador, morte gasta uma, zerar exila para o Limbo | **É o que dá peso a tudo.** O medo de morrer é o que faz um item de recuperação valer dinheiro, e é o que trava o jogador na vila |
| **aurorion-limbo** | A dimensão de exílio, o prazo de 48h, a Porta do Esquecido, a morte definitiva, e o **Oráculo** | O Oráculo é o vendedor natural (ver §3). O mod também tem a **auditoria** que deve ser copiada pela economia: toda ação vira uma linha JSON em disco, e a staff lê por RCON (`/limbo relatorio`) ou por webhook do Discord |
| **aurorion-personagem** | Nome e sobrenome do personagem; na morte definitiva, identidade nova e progressão zerada | **Decisão econômica pendente:** o dinheiro morre junto com o personagem? Saldo virtual seria zerado pelo `CharacterResetEvent`; moeda física guardada num baú **não** seria |

### O Oráculo, em detalhe (é a loja)

O Oráculo é o NPC do Limbo. Hoje ele mostra quem está exilado e vende a passagem de resgate — que
custa **1 vida** de quem paga. Três fatos que importam:

1. **Ele não é uma entidade registrada.** É *qualquer mob com a tag `aurorion_oraculo`*, colocado
   pela staff. Qualquer criatura do modpack vira Oráculo sem uma linha de código.
2. Ele já tem **tela própria** (com tema visual compartilhado) e uma **árvore de diálogo** escrita em
   datapack, que consulta condições do servidor ("há alguém no Limbo?", "você tem vida para pagar?").
3. Está decidido transformá-lo num **"Xur"** (referência a Destiny 2): um mercador que **aparece num
   lugar diferente a cada dia**, sorteado entre N pontos cadastrados pela staff, com **estoque
   limitado por rotação**.

---

## 3. O que vai existir: `aurorion-espolio`

Mod novo, já decidido em desenho, ainda não escrito. É ele que precisa de preço, e por isso esta
conversa existe.

**O problema:** quando alguém morre, o inventário cai no chão. Com 80 pessoas, um servidor de RP e
uma morte que já custa uma vida, perder tudo é punição em cima de punição.

**A solução escolhida, e por que ela é essa:**

- Na morte, o mod captura **tudo** que a morte produziu (inventário, armadura, e o que mods de slot
  como o Curios injetam) e **marca aqueles drops**: eles não desaparecem sozinhos e a limpeza
  periódica não os toca por um tempo.
- **Relicário** — item de uso único, comprado do Oráculo. Usar chama de volta **só o que ainda
  existir no mundo**. O que outro jogador já pegou, ficou com ele.
- **Fio da Volta** — item de uso único. Teleporta ao local exato da morte.
- Os dois **não caem quando você morre** (senão o sistema inteiro não funciona), só podem ser
  obtidos comprando, e somem no uso.

**A razão de "chamar de volta" em vez de "restaurar uma cópia" é econômica:** uma cópia duplicaria
todo item que outra pessoa tivesse saqueado. Numa economia de torneira fechada, item criado do nada é
inflação — e com 80 jogadores isso escala rápido.

**Consequência que importa para o preço:** o Relicário é o produto mais caro do servidor e vai ser o
**ralo principal** da economia. O preço dele não é um número solto: é o que fecha a conta de quanto
dinheiro entra por semana (ver §5.7).

---

## 4. Restrições de arquitetura (uma proposta que viole isso não serve)

Estas não são preferências; são regras que o código já segue e cuja violação quebra algo concreto.

1. **Nada de config em arquivo para o que o dono ajusta.** Ele não quer editar TOML/JSON na mão: o
   que for ajustável precisa de comando ou tela in-game. Config de servidor (TOML) é aceitável para
   *regra*; coisas do dia a dia, não.
2. **Coordenada nunca mora em config.** Local de qualquer coisa (o ponto de exílio, e futuramente os
   pontos do Oráculo) é gravado por comando no save do mundo, porque coordenada precisa acompanhar o
   que foi construído e sumir junto quando o mundo for trocado.
3. **Conteúdo é datapack; comportamento é código.** Listas de preços, itens que o posto compra e
   cotas são conteúdo — devem poder mudar com `/reload`, sem recompilar.
4. **Item permanente nasce no mod de conteúdo** (`aurorion-aeonita`). Um mod de mecânica pode ser
   removido do modpack; se a moeda tiver nascido nele, todo dinheiro guardado em baú vira ar.
5. **Tudo é medido contra 80 jogadores, não contra 1.** Nada de trabalho por jogador por tick; rede é
   snapshot no login e delta depois.
6. **Toda entrada vinda do cliente é hostil até o servidor validar.** Uma tela de compra nunca é
   autoridade sobre preço ou estoque.
7. **Auditoria não é opcional.** Emissão e gasto de dinheiro viram linha em disco (`.jsonl`), com
   relatório legível por RCON. Sem isso ninguém consegue responder, daqui a três meses, por que
   existem N óbolos no servidor.
8. **Anti-alt por padrão.** Qualquer renda automática precisa de cota por personagem, ou contas
   secundárias viram fazenda de dinheiro.

---

## 5. O modelo proposto

### 5.1 A regra que decide tudo: torneira e ralo

**Torneira (a única):** a staff. Aula dada, evento, missão, prova, RP bem feito. **Nada de drop de
mob, nada de receita de craft, nada de loot table** — senão a moeda vira farm em uma semana e
qualquer preço escrito hoje estará errado no mês que vem.

**Ralo:** o Oráculo. O que ele vende **destrói** o dinheiro, não repassa a ninguém.

Consequência direta, e é a conclusão mais importante deste documento:

> **Numa economia de torneira fechada, o tamanho da economia é exatamente o que a staff injeta.
> Ninguém enriquece sem que outro empobreça.** Portanto o desenho não começa pelos preços — começa
> pela injeção semanal, e o preço é consequência.

Isso também responde à pergunta "o construtor que presta serviço não vai ficar rico?": **vai, e está
certo que fique.** O que não pode é ele enriquecer *da torneira*. Da carteira dos outros, pode — isso
se chama especialização. O controle não é limitar o salário dele, é existir algo caro o bastante para
ele querer comprar; senão o dinheiro para de circular no bolso do mais rico e a economia morre.

### 5.2 A unidade

**1 óbolo = uma hora de trabalho braçal no vilarejo** (carregar, cavar, colher, empilhar). É o piso:
ninguém paga menos que isso por uma hora do tempo de alguém, e todo preço novo se julga perguntando
"isso vale quantas horas de peão?".

*("Óbolo" é a moeda que se pagava ao barqueiro dos mortos — casa com o Oráculo e com o Limbo. O nome
é discutível; a unidade não.)*

### 5.3 Renda por perfil (semana de ~6 a 8 horas de jogo)

| Perfil | De onde vem | Óbolos/semana |
|---|---|---|
| **Só aparece** | uma aula ou evento | 3–5 |
| **Trabalha na vila** | ofício + cota do posto + bicos | 12–20 |
| **Especialista** (construtor, ferreiro com fama) | serviço a outros jogadores | 20–35 |
| **Explorador** | contratos e material que só existe longe | 30–70, **podendo voltar com zero** |

O casual ganhar 3–5 é proposital: ele não passa fome, mas não compra nada. Para ter qualquer coisa
além do básico é preciso trabalhar ou se arriscar. É o PIB baixo pedido.

### 5.4 Preços

| Bem | Óbolos | Lido como |
|---|---|---|
| Refeição, reparo simples | 1 | uma hora |
| **Fio da Volta** (TP ao local da morte) | **10** | duas semanas do casual, meia semana do trabalhador |
| **Relicário** (chamar o espólio de volta) | **40** | dois meses do casual, duas semanas do trabalhador |
| Cosmético comum | 5–15 | |
| Estoque raro do Oráculo | 60–150 | o ralo de quem enriqueceu |

Serviço entre jogadores (não cria dinheiro, redistribui):

| Serviço | Preço |
|---|---|
| Peão (cavar, carregar, coletar) | 1/hora — é o piso, por definição |
| Construtor competente | 3–4/hora → casa pequena de 10 a 15 |
| Obra de sede ou instituição | 60–150, paga pela **casa**, não por um indivíduo |

Repare no efeito: uma casa pequena custa mais que a semana inteira de um jogador casual. A maioria
constrói com as próprias mãos, e contratar construtor vira coisa de grupo ou de quem já prosperou —
que é exatamente como funciona um lugar pobre.

### 5.5 A segunda torneira, com cota: o Posto do vilarejo

Se a única entrada for evento de staff, a economia só existe quando a staff está online, e quem joga
de madrugada vive num servidor sem dinheiro. A saída é um **posto de compra**: preço fixo, cota
diária por personagem, automatizado.

- Exemplos: 32 trigo → 1 óbolo · 8 ferro bruto → 2 · peixe ou caça → 1.
- **Teto de 3 óbolos por dia por personagem.** Quem joga todo dia e enche a cota chega a ~20 por
  semana — e esse é, de propósito, o **teto da vida de vila**. Passou disso, só saindo.
- Cota por personagem, com reset diário, para não virar fazenda de conta alt.
- A lista de itens e preços é **datapack**, não código.

### 5.6 Vila e mundo

> **Na vila você sobrevive. Fora dela você enriquece.**

O explorador precisa ganhar cerca de **3x** o vileiro por hora; abaixo disso, arriscar uma vida é
burrice matemática e ninguém sai. O que fecha o ciclo com elegância:

**Quem paga o Relicário é justamente o explorador.** 40 óbolos é impagável para quem fica e é meia
semana de quem sai. O item mais caro do jogo existe para ser comprado por quem se arrisca — e o risco
é o que gera o dinheiro para comprá-lo.

Os dois lados ficam dependentes: o explorador precisa de comida, reparo e equipamento da vila; a vila
precisa do material raro que só ele traz. **Esse é o comércio de verdade; o resto é enfeite.**

### 5.7 A conta tem que fechar

```
Entra:  40 jogadores ativos × ~12/semana                 ≈ 480 óbolos/semana
Sai:    8 Relicários (320) + cosméticos (~100)
        + moeda perdida na morte e na limpeza (~50)      ≈ 470 óbolos/semana
```

Se entrar mais do que sai por várias semanas seguidas, os preços entre jogadores sobem sozinhos — é
assim que se detecta inflação sem planilha. A correção é **cortar cota ou subir o preço do Oráculo**,
nunca imprimir mais.

Ninguém acerta isso de primeira, e não precisa. O que precisa é **medir**: cada emissão e cada compra
viram linha num `.jsonl`, e um `/obolo relatorio` por RCON devolve estoque total, injeção e ralo da
semana — a mesma estrutura do `/limbo relatorio` que já existe e já é lida por um bot.

---

## 6. As perguntas em aberto

São essas que a discussão precisa resolver. Estão em ordem de impacto.

**1. Qual é a despesa recorrente obrigatória?**
Sem uma despesa que volta toda semana, o jogador cauteloso simplesmente não compra nada e o dinheiro
nunca circula — a economia vira decoração. Candidatos:
- **Passagem de trem** (`aurorion-portais`): tecnicamente quase pronto, temático, e cobra justamente
  de quem viaja, que é quem ganha mais. O risco é encarecer a exploração que se quer incentivar.
- **Aluguel de lote no vilarejo**: cobra de quem fica, que é quem ganha menos.
- **Reparo de equipamento pago**: cobra proporcional ao uso, mas precisa de mecânica nova.

**2. Moeda física (item) ou carteira virtual (saldo)?**
A recomendação atual é **física primeiro**, porque o servidor já escolheu que loot é coisa no mundo
(o Relicário só traz de volta o que ainda existe), e uma moeda que é item obedece à mesma física: dá
para guardar em baú, entregar na mão sem comando, montar loja com funil, e **perder na morte**. Custa
quase nada de código. Carteira virtual exige comando, HUD, sync, persistência e integração com o
reset de personagem — infra que só se paga quando existir loja de jogador, imposto ou banco.
**Consequência a decidir junto:** a morte definitiva zera saldo virtual, mas não esvazia o baú de
moedas do personagem morto. Isso é bug ou é feature (herança, espólio de família, achado por outro
jogador)?

**3. Uma denominação ou três?**
Recomendação: **uma só**. Três moedas (bronze/prata/ouro) só se pagam quando os preços passam de dois
dígitos; se um dia inflarem, tiers entram com receita 9:1, como nugget → lingote, sem reescrever
nada.

**4. O que exatamente o posto compra, e por quanto?**
É a lista que define quais ofícios existem de fato no vilarejo. Precisa de: itens, preço, cota
diária, e se a cota é por personagem ou por conta.

**5. Quais são os contratos de exploração?**
O que faz sair valer a pena: entregas de material que só existe longe, pagas em faixa de 10 a 25, e
possivelmente o próprio Oráculo pedindo material diferente a cada rotação (bem Xur). Precisa de uma
lista e de uma faixa de preço.

**6. A casa é agente econômico?**
Existe caixa coletivo de casa, com alguém autorizado a gastar? É o que torna possível a obra grande
(60–150) e a rivalidade entre casas pelo dinheiro — mas é mecânica nova, com permissão e auditoria
próprias.

**7. Quanto a staff pode emitir, e com que controle?**
Se qualquer membro da staff pode dar dinheiro, a torneira é do tamanho da disciplina do time.
Opções: teto semanal por membro da staff, ou emissão só por evento registrado, ou apenas relatório
sem teto (confiança com transparência).

---

## 7. O que é melhor **não** decidir agora

Deliberadamente fora de escopo até a primeira versão rodar por algumas semanas: banco com juros,
leilão automatizado, imposto, bolsa de valores, câmbio entre mundos, e qualquer coisa que exija
saber o preço "de mercado" de alguma coisa. Todos dependem de dados que ainda não existem — e a
economia inteira pode ser recalibrada com dois números (a cota do posto e o preço do Relicário)
enquanto continuar simples.
