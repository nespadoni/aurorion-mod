# Aurorion Ficha

A ficha de RPG do site (motor v0.3) dentro do jogo: ver a ficha, rolar testes de atributo e
perícia pelo **motor oficial do site**, o mestre pedir testes aos jogadores e escolher qual ficha
cada perfil usa. Tudo por tela; os comandos existem para quem joga sem o mod.

## Para o jogador

1. Vincule a conta do site como no diário: no site, Perfil → RPG → **No jogo** (ou Perfil → Diário →
   **Vincular minha conta Minecraft**) gera o código; no jogo, `/vincular CODIGO` — ou cole o código na
   própria ficha (a tecla da ficha sem conta vinculada mostra o passo a passo): ela manda o `/vincular`
   e abre a ficha sozinha quando o site confirmar.
2. A **tecla da ficha** (padrão: a tecla à esquerda do 1 — `'` no teclado ABNT2, `` ` `` no americano;
   troca em Opções → Controles → Aurorion Ficha) ou `/ficha` abre a ficha do seu personagem atual.
   **Tocar** abre a ficha; **segurar** abre a **roda da ficha** (abaixo).

**No menu do personagem.** Com o `aurorion-menu` instalado, tocar a tecla da ficha (ou fazer o gesto da
mão e escolher **Perfil**) abre o Perfil no painel que flutua na sua frente: a vitrine com o seu
personagem, a armadura e as mãos; cabeçalho com Alma, Marca e elemento; vida (com
a temporária), stamina, sanidade e P.E.; estado vital, condições com a explicação no hover, ultimate e
cargas; os números de combate e os atributos. Apontar para um atributo e clicar rola o teste (o cartão do
d20 sai no HUD). Os avisos da ficha aparecem no cartão de aviso do menu.
O pedido ao servidor é o mesmo `Action("abrir")`: só o cliente muda. Sem ficha (conta não vinculada,
escolher personagem), **Vincular / escolher ficha** abre a tela de sempre. As outras abas (perícias,
técnicas, vínculo, dados, mesa do mestre) entram no menu nas próximas fases; até lá a tela antiga continua
pelo `/ficha` e pela roda (segurar a tecla).

**A roda da ficha.** Segure a tecla da ficha: uma roda abre no meio da tela, no mesmo jeito da roda de
passivas da magia — mova o mouse até a fatia e solte a tecla. **Ficha** abre a ficha; **Dados** abre a
ficha já na rolagem livre; **Habilidades** abre na aba 5; **Mesa do mestre** só aparece para quem é
mestre. Com um **pedido do mestre**, o centro da roda vira o cartão do pedido (quem pediu, o teste, a
CD se o mestre deixou visível, o motivo e um arco com o tempo que falta) e aparecem **Rolar** (dourada,
no alto) e **Recusar** (do lado oposto). Também valem clique esquerdo/direito numa fatia, as teclas
1–9 pela posição e Enter (rola o pedido; sem pedido, abre a ficha). Soltar no centro ou Esc fecha sem
fazer nada. Não pausa o jogo e só escurece o fundo. A legenda embaixo diz o que a fatia apontada faz —
ou por que está apagada ("Rolando…", "o pedido terminou").

| Aba | O que tem |
|---|---|
| **Ficha** | Retrato (sua skin), nome, título, Alma, Legado, Marca/Elemento; barras de Vida (com Vida temporária), Stamina, Sanidade e P.E.; condições com o efeito no tooltip; Defesa, RD, Movimento, Iniciativa, Fortitude, Ressonância; os 6 atributos — **clique num atributo para testá-lo**. |
| **Perícias** | As 22 perícias agrupadas por atributo, com o bônus total do teste e os pontos de treino. **Clique para rolar**: CD opcional e "secreto". |
| **Técnicas** | As técnicas da ficha (equipadas em dourado) e a **História** (idade, origem, alinhamento, personalidade, aparência). |
| **Vínculo** | Qual ficha este perfil usa e a lista do que ele pode usar. Fica aqui o aviso "aguardando aprovação no site" quando a construção ainda não foi aprovada. |

**Rolar.** O resultado aparece na própria janela e num cartão à direita do HUD (perto do meio da altura,
abaixo dos efeitos e do minimapa; lugar, tamanho e tempo em `ficha-client.toml`): o d20 rola
por ~0,8 s, assenta no natural e o resultado entra logo depois — bônus, total, CD e **sucesso**
(verde), **falha** (vermelho), **crítico** (dourado, com faíscas) ou **falha crítica** (o dado
treme). Com vantagem/desvantagem os dois d20 rolam e o descartado fica apagado e riscado. Quem
estiver por perto (24 blocos, mesma dimensão) vê um cartão menor e uma linha no chat com os detalhes
no hover. Rolagem **secreta** só chega a você e aos mestres online. "Dados" no rodapé faz rolagem
livre (`2d6+3`, `4d6kh3`…). Entre apertar a tecla da ficha e a ficha chegar do site, o HUD mostra
"Abrindo a ficha…" logo acima do lugar do pedido do mestre.

**Ficha antiga.** Ao entrar com a conta principal, se a sua ficha ativa ainda não tem a construção nova
(e você não enviou uma para aprovar), um aviso aparece uma vez por dia no HUD: "Sua ficha ainda não foi
atualizada no site — aperte <a sua tecla> para ver". Sem o mod, a mesma coisa vem numa linha de chat
com o link do site. Enquanto isso, a rolagem livre e os pedidos de **dados** do mestre funcionam.

**Pedido do mestre.** Um cartão aparece no alto, no centro do HUD (abaixo do Jade e das barras de chefe;
configurável), com um sino baixo — "O mestre pede: teste
de Furtividade" (ou, num pedido de dados, "Rolar 2d6"), quem pediu, a CD (se o mestre deixou visível), vantagem/desvantagem, o motivo e o
tempo que falta (fica âmbar nos últimos 20 s), com a dica "Segure ' para responder" (o nome da sua
tecla da ficha). Responda pela **roda** (segurar a tecla, soltar em **Rolar** ou **Recusar**) ou pelos
botões **Rolar**/**Recusar** no rodapé da ficha e no cabeçalho da mesa — "Rolando…" até o resultado
chegar. As teclas "Rolar/Recusar o teste pedido pelo mestre" existem em Controles **sem tecla de
fábrica** (Y e U colidiam no modpack); quem as atribuir vê "Y rola · U recusa" no cartão. Vários
pedidos ao mesmo tempo: o mais novo em cima, com "+N pedidos".

**Alts (`/personagem alt`, `/personagem trocar`).** A conta principal usa a sua ficha ativa do
site. Um alt precisa escolher qual ficha ou NPC interpreta: no login de um alt sem ficha, o HUD e o
chat avisam; a aba **Vínculo** (ou `/ficha vincular`) mostra as opções — sua ficha, os NPCs ligados
a você e, para staff, NPCs livres (usar um NPC livre o liga à sua conta no site). A mesma escolha
existe no site, em Perfil → RPG → "No jogo".

**Sem o mod** (cliente vanilla): `/ficha` mostra um resumo e o link do site; os pedidos do mestre
chegam no chat com `[Rolar]` e `[Recusar]` clicáveis; `/ficha vincular` lista as fichas com
`[Usar]`.

## Para o mestre

A **mesa do mestre** (`/ficha mestre`, ou a tecla "Abrir a mesa do mestre", sem padrão) lista os
jogadores até 48 blocos. Marque um ou mais, escolha o atributo e a perícia (ou "só o atributo"),
a CD — **visível** ou **oculta** (o jogador vê só sucesso/falha) —, vantagem/desvantagem, secreto
e um motivo opcional, e envie. **Dados** (o botão ao lado dos atributos, ou a tecla 7) troca o teste
por uma rolagem livre: atalhos 1d20, 1d100, 2d6, 1d6, 1d4 e um campo para qualquer fórmula do motor
(`NdF±K`, `kh`/`kl`/`dh`/`dl`; conferida a cada tecla com a mesma gramática do site), CD opcional — num
d20 ela se lê como num teste; nos outros, o total precisa chegar nela —, secreto e motivo. Não há
vantagem nos dados (o site recusa; `2d20kh1` faz o papel). Dados não usam a ficha: funcionam até para
quem ainda tem a **ficha antiga** — a linha "ficha antiga" na mesa deixa de ser aviso nesse modo. As respostas chegam ao vivo à direita (total, sucesso/falha,
recusou, erro) e ficam lá até você sair do servidor. **Ficha** ao lado do jogador abre a ficha
dele só para leitura.

Quem é mestre: o **perfil** com permissão de OP ≥ `permissaoMestre` (2). O alt de
`/personagem alt` nasce sem OP de propósito ("acabou o `/deop` para jogar") e por isso **não é
mestre**: no alt a staff joga, e mestra pela conta principal. Não há herança da permissão da conta
dona — um alt de staff com a mesa veria as rolagens secretas e o bônus dos NPCs dos outros jogadores.
**Vantagem, desvantagem e atributo alternativo** também exigem que o site confirme a sua conta como
staff; sem isso o teste volta com "exige um mestre confirmado pelo site". Teste comum (com ou sem CD)
não depende dessa confirmação.

## Como funciona

- **Servidor** (`server/FichaServer`): fala com o site pelo `SiteApi` do `aurorion-integracao`,
  com a credencial do servidor. Perfil (`profile_uuid`), conta dona (`account_uuid`, de
  `AltData.ownerOf`), nome do perfil e personagem (`GameFacts.subject`) saem sempre da sessão no
  servidor, nunca da tela. Rotas: `POST …/integration/v1/rpg/sheet`, `/candidates`, `/bind`,
  `/roll` e `/peek`.
- **Ficha em cache** por perfil por `cacheSegundos` (20 s): com a cópia com menos de 5 s, reabrir a
  tela não chama o site; de 5 a 20 s, a tela abre na hora com a cópia e uma conferida sai em segundo
  plano (a tela só é mandada de novo se a versão mudou). Com a tela aberta, ela mesma pergunta "a ficha
  mudou?" (ação `atualizar`, no máximo 1 a cada 10 s) e, se mudou, recebe a nova com o aviso "A ficha
  mudou no site…". Rolar, vincular e sair descartam a cópia; outro personagem no mesmo perfil não a
  reaproveita.
- **Pedido de dados**: `kind=free` no `Ask` (protocolo 2: o pacote ganhou `formula`) e no
  `GmRequests.Spec` (fórmula canônica, sem perícia, atributo nem modo). A resposta vai ao `/rpg/roll`
  com `kind: "free"`, `formula`, `label` (o motivo, para o histórico do site), `dc` e `secret` — sem
  `advantage` nem `gm_*`. O site não compara a CD na rolagem livre: o servidor do jogo aplica
  `total ≥ CD` antes de distribuir o cartão, e o cartão mostra a fórmula pedida (não o motivo).
- **Ficha antiga no login da conta principal**: uma chamada a `/rpg/sheet` 5–8 s depois de entrar
  (sorteado, para logins juntos não virarem rajada; com a cópia do cache fresca, nenhuma), na mesma
  infraestrutura da conferida de alt (`CHECKING`, sessão conferida na volta). Avisa só com
  `has_build=false` e `pending_review=false`; uma vez por perfil a cada 24 h (em memória, sobrevive ao
  logout; reiniciar o servidor zera). A resposta entra no cache: abrir a ficha logo depois não chama o
  site. O servidor não sabe a tecla de cada cliente: o aviso leva `{tecla}` e o cliente põe o nome
  configurado.
- **Convite de alt**: no login de um alt sem ficha, o HUD avisa e a ficha abre sozinha na aba Vínculo
  (`PROMPT`, sem `OPEN`). Se o jogador estiver com outra tela (carregando o mundo, criação de
  personagem, inventário), o cliente guarda o convite e abre uma vez quando ela fechar
  (`ScreenEvent.Closing` arma, o tick seguinte abre); descarta depois de 60 s ou se chegar outra tela
  da ficha.
- **Rolagem idempotente**: cada rolagem leva um `command_id` (UUID) sorteado no servidor. Sem
  resposta (rede, 5xx, 429), a mesma chamada sai mais uma vez 1,5 s depois — o executor do site
  devolve o resultado já gravado em vez de rolar de novo. Pedido do mestre usa um `command_id` por
  alvo, sorteado na criação do pedido: até rolar o mesmo pedido duas vezes é seguro. Uma rolagem por jogador
  viaja por vez.
- **Quem vê** (`server/RollAudience`): pública → quem rolou + quem está até `raioPublico` blocos na
  mesma dimensão; secreta → quem rolou + mestres online; pedida pelo mestre → o mestre que pediu
  sempre recebe. Para o público, o bônus de um **NPC** sai do cartão (número privado do mestre, como
  o `Public()` do motor) e a CD oculta pelo mestre não aparece para ninguém além dos mestres. Quem
  diz que o bônus é privado é o `roll.private` da resposta do site; a última ficha vista ser NPC (ou
  um alt ainda sem ficha vista) só reforça. Rolagem livre nunca esconde (a fórmula é do jogador).
  Como no motor, natural e total continuam públicos.
- **Respostas do site**: `busy` no `/roll` é o limite de rolagens por dono no site ou o personagem
  preso num encontro de mesa do site — não "outra rolagem a caminho" (essa o servidor do jogo já
  barra); o pedido do mestre volta a aceitar resposta, com o mesmo `command_id`. `forbidden` mostra
  o motivo que o site manda em `message`; HTTP 503 `{code: "unavailable"}`, 5xx e 429 contam como
  "site fora". `foreign` (perfil vinculado a outra conta) pode vir de qualquer rota. HTTP 401/403
  viram `auth` (credencial da integração) e 404 vira `missing` (site sem as rotas da ficha): os dois
  dizem "avise a staff". Respostas que chegam depois de o servidor parar são descartadas.
- **Pedidos do mestre** (`server/GmRequests`): só em memória, id curto (o que o vanilla digita em
  `/ficha pedido <id>`), vencem em `pedidoExpiraSegundos` sem relógio — o prazo é conferido quando
  alguém toca no pedido e os vencidos saem ao criar outro.
- **Modelos** (`sheet/`): o servidor lê a resposta do site com tetos (textos, listas) e manda ao
  cliente só o que a tela usa, em JSON com os mesmos nomes do contrato; o cliente lê com o mesmo
  parser. Campo novo ou de outro tipo vira valor neutro, nunca exceção.
- **Cliente** (`client/`): telas em HTML/CSS do **TesseraUI** (`assets/aurorion_ficha/ui/ficha.css`,
  o mesmo tema do diário), em painéis de posição fixa; barras, rosto da skin, números grandes e o
  d20 são desenhados pelo Java por cima. As classes de cliente só carregam depois do teste de `Dist`.

## Custo

Nada roda por tick no servidor.

| Quando | O que custa |
|---|---|
| Abrir a ficha (tecla da ficha, `/ficha`) | 0 chamadas com cópia de menos de 5 s; de 5 a 20 s, abre com a cópia + 1 conferida em segundo plano; sem cópia, 1 chamada assíncrona a `/rpg/sheet` |
| Tela aberta | ação `atualizar` no máximo 1 a cada 10 s por jogador; a tela só volta pela rede se a versão mudou |
| Aba Vínculo | 1 chamada a `/rpg/candidates` na primeira vez que a aba abre |
| Rolar | 1 chamada a `/rpg/roll` (+1 reenvio só se o site não respondeu); depois, uma passada pela lista de online para decidir quem vê. Se o site disser que o perfil ficou sem ficha (`needs_binding`/`no_sheet`), +1 `/rpg/sheet` para levar a tela aberta ao Vínculo |
| Pedido do mestre (teste ou dados) | só memória; 1 chamada por resposta de jogador |
| Login da conta principal | 1 chamada a `/rpg/sheet` 5–8 s depois (sorteado), que já fica no cache e no retrato dos testes; nenhuma com a cópia do cache fresca, ou com `avisarFichaAntiga` e `retrato.lerNoLogin` os dois desligados |
| Retrato dos testes | 1 `/rpg/sheet` a cada `retrato.renovarMinutos` por jogador online (padrão 30); consultar o retrato (magia, ofício) é um `HashMap.get`, sem rede |
| Login de um alt | 1 chamada a `/rpg/sheet` ~4 s depois (ou quando o personagem ganha nome, `CharacterNamedEvent`); aviso no máximo 1 vez a cada 10 min por perfil (desligável em `avisarAltSemFicha`) |
| Ver ficha de jogador (mestre) | 1 chamada a `/rpg/peek` |
| Cliente, HUD | sai na primeira linha sem cartão; com cartões, alguns `fill` e textos — cortados, medidos e quebrados uma vez por cartão/pedido, não por frame; efeitos conferidos a cada 250 ms; barras de chefe pelo evento do NeoForge; minimapa do JourneyMap lido (reflexão) no máximo a cada 1 s e modo streamer a cada 500 ms, só com algo na tela |
| Cliente, telas | HTML remontado só quando algo muda; o d20 anima pelo relógio, sem estado por frame |
| Cliente, roda | nada fechada além de conferir, no tick, se a tecla da ficha está segurada; aberta, ~15 arcos e alguns textos por quadro, cortados uma vez por pedido; nenhum pacote para abrir |

A ficha registra a seção **Ficha** no `ServerDiagnostics` do core: chamadas, falhas, última
resposta, rolagens em andamento, fichas em cache, pedidos em memória e os raios configurados.
**Pendente:** o `/aurorion diagnostico` do Essentials hoje só imprime as seções "Integração" e
"Diário" (lista fixa em `DiagnosticoCommand`); falta acrescentar "Ficha" lá para ela aparecer.

## Config — `config/aurorion/ficha-server.toml`

Lida ao vivo (não precisa reiniciar). Nada de credencial aqui: endereço e token são da integração.

| Chave | Padrão | O que faz |
|---|---|---|
| `rolagens.raioPublico` | 24 | Blocos (mesma dimensão) em que a rolagem pública aparece para os outros |
| `rolagens.cacheSegundos` | 20 | Validade da ficha lida do site para reabrir a tela; 0 desliga |
| `mestre.permissaoMestre` | 2 | Nível de OP (1–4) do perfil para abrir a mesa, pedir testes e ver rolagens secretas. O alt não tem OP, então não é mestre |
| `mestre.raioMesa` | 48 | Blocos em que a mesa lista os jogadores |
| `mestre.pedidoExpiraSegundos` | 180 | Prazo de um pedido de teste |
| `textos.avisarAltSemFicha` | true | Consultar o site no login de um alt e avisar se ele não tem ficha |
| `textos.avisarFichaAntiga` | true | Consultar o site no login da conta principal e avisar (1×/dia por perfil) se a ficha ainda é antiga; desligado, esse login não chama o site |
| `textos.altSemFicha` | "Este personagem ainda não tem ficha…" | Texto desse aviso |
| `textos.pedidoTitulo` | "O mestre pede" | Abertura do pedido no HUD e no chat |
| `textos.semVinculo` | orientação do `/vincular` | Para quem não vinculou a conta |
| `textos.siteFora` | "O site não respondeu agora…" | Quando o site não responde |
| `retrato.lerNoLogin` | true | Ler a ficha no login para o retrato dos testes, mesmo sem aviso a dar |
| `retrato.renovarMinutos` | 30 | Releitura do retrato com a pessoa online; 0 desliga |
| `retrato.magiaAtributo` / `retrato.magiaPericia` | `vit` / `astros_e_mistos` | Bônus do teste de magia: atributo efetivo + valor treinado da perícia |
| `retrato.oficioPericia` | `criacao` | Bônus do teste de ofício: o bônus de teste completo da perícia (atributo + treino + Marca) |

### O retrato dos testes (`server/SheetStatsStore`, 0.2.0)

A ficha responde o contrato `CharacterSheet` do core: um retrato do que o site disse da última vez
(atributos efetivos, perícias, derivados e os dois bônus prontos — magia e ofício), em memória e no
SavedData `aurorion_ficha_stats`. A magia e as profissões fazem os testes do jogo com ele, sem chamar o
site e sem importar a ficha.

- Atualiza a cada leitura com código `ok` (abrir, atualizar, login, renovação); `no_sheet` e
  `needs_binding` apagam o retrato do perfil. Ficha sem construção aprovada não entra.
- Fica em disco para o dono offline de uma fornalha e para depois de um restart.
- O reset de personagem (`CharacterResetEvent`) apaga.
- Sem retrato, quem pergunta usa o bônus padrão **dele** (`magia-server.toml`, `profissoes-server.toml`).

## Config do cliente — `config/aurorion/ficha-client.toml`

Só deste computador (o servidor não lê nem manda nada daqui); lida ao vivo. Os padrões saíram do pack
real: canto superior direito = minimapa do JourneyMap (`TopRight`, 30 % da altura) e ícones de efeito;
superior esquerdo = HUD de grupo do Simple Voice Chat (4,4) e minimapa do Map Atlases (`UPPER_LEFT`);
inferior esquerdo = chat e ícone de voz; topo central = Jade e barras de chefe.

| Chave | Padrão | O que faz |
|---|---|---|
| `cartoes.ancora` | `DIREITA_MEIO` | Onde fica a pilha de rolagens: `DIREITA_TOPO` (o lugar antigo, colado nos efeitos), `DIREITA_MEIO`, `DIREITA_BAIXO`, `ESQUERDA_TOPO`, `ESQUERDA_MEIO`, `ESQUERDA_BAIXO`, `CENTRO_TOPO`. `*_MEIO` começa a 42 % da altura (ou abaixo do que ocupa o topo, o que for mais baixo); `*_BAIXO` empilha para cima, acima da barra de itens / do chat |
| `cartoes.deslocamentoX` / `deslocamentoY` | 0 / 0 | Ajuste fino em pixels da GUI (positivo = direita / para baixo) |
| `cartoes.escala` | 1.0 | 0.75–1.25 |
| `cartoes.segundosSuas` / `segundosOutros` | 9 / 6 | Tempo do seu cartão (e da resposta a um pedido seu) e do cartão de outro jogador |
| `cartoes.maximo` | 6 | Cartões na pilha ao mesmo tempo (o que não cabe na altura espera escondido) |
| `cartoes.rolagensDosOutros` | `TODAS` | `PEDIDAS` mostra só as que responderam a um pedido do mestre; `NENHUMA`, só as suas. A linha de chat chega igual |
| `cartoes.desviarMinimapa` | true | Com o JourneyMap: lê o minimapa ativo (ligado, posição, `sizePercent`, rótulos) e começa a pilha abaixo dele (`TopRight`/`Custom` à direita) ou para antes dele (`BottomRight`). Sem o mod, ou com outra API, vale só a âncora |
| `pedido.ancora` | `CENTRO_TOPO` | Pedido do mestre, avisos e "Abrindo…". `CENTRO_TOPO` fica a 18 % da altura (mín. 56 px, abaixo do Jade), abaixo das barras de chefe, sem descer até a mira; `ESQUERDA_TOPO` é o lugar antigo. A pilha de rolagens sempre desvia dele |
| `pedido.deslocamentoX` / `deslocamentoY` / `escala` | 0 / 0 / 1.0 | Como os dos cartões |
| `privacidade.seguirModoStreamer` | true | Com o `/streamer on` (ou `total`) do Essentials, os cartões mostram só o que um vizinho veria (abaixo) e a mesa do mestre ganha o selo "MODO STREAMER" |
| `privacidade.ocultarSegredos` | false | O mesmo, sempre, com ou sem o Essentials |

**Escondendo segredos** (modo streamer ou `ocultarSegredos`): rolagem **secreta** vira um cartão
"Rolagem secreta" com um d20 apagado e "?" — sem teste, natural, total, nem a cor do resultado; a **CD
oculta** pelo mestre some da linha ("CD oculta"); o **bônus** de testes vira "?" ("nat 12 + ? = 17")
porque o cartão não diz se é número de NPC (rolagem livre fica inteira — a fórmula é de quem rolou).
Natural, total e sucesso/falha das públicas continuam, como para um vizinho. A **mesa** do mestre não
esconde nada (ela é a ferramenta dele); o selo no cabeçalho, com tooltip, lembra que o HUD está
filtrando. O Essentials guarda o modo em `essentials-streamer-client.toml`; a ficha lê o
`StreamerConfig.MODE` dele por reflexão (sem dependência: sem o Essentials, ou se a classe mudar, um
aviso no log e vale só `ocultarSegredos`).

## Comandos

| Comando | Quem | O que faz |
|---|---|---|
| `/ficha` | todos | Abre a ficha (sem o mod: resumo, comandos e link do site) |
| `/ficha rolar <perícia\|atributo> [cd] [secreto]` | todos | Teste pelo motor do site; TAB sugere os 22 + 6 ids |
| `/ficha dados <fórmula>` | todos | Rolagem livre (`2d6+3`) |
| `/ficha pedido [id] [recusar]` | todos | Responde ao pedido do mestre (sem id: o mais novo) |
| `/ficha vincular` | todos | Aba Vínculo, ou lista clicável no chat |
| `/ficha vincular <sheet\|npc> <id>` / `limpar` | todos | Vincula este perfil / volta ao padrão |
| `/ficha mestre` | mestre | Mesa do mestre (sem o mod: os comandos abaixo) |
| `/ficha mestre pedir <jogadores> <teste> [cd]` | mestre | Pedido pelo chat; `<teste>` também aceita uma fórmula (`2d6`, `1d20+2`) |
| `/ficha mestre pedir <jogadores> dados <fórmula> [cd]` | mestre | Pedido de dados (rolagem livre); TAB sugere 1d20, 1d100, 2d6, 1d6, 1d4. (`dados:2d6` não dá: `:` não entra numa palavra sem aspas do Brigadier) |
| `/ficha mestre ver <jogador>` | mestre | Ficha do jogador (resumo no chat sem o mod) |
| `/ficha mestre cancelar <id>` | mestre | Tira o pedido de quem ainda não respondeu (o cartão some do HUD deles com o motivo na barra de ação; a mesa marca "cancelado") |
| `/ficha teste [variante]` | cliente | Telas e HUD com dados fictícios (abaixo); variante desconhecida lista as outras |

## Teclas e roda

| Tecla (Controles → Aurorion Ficha) | Padrão | O que faz |
|---|---|---|
| Ficha (tocar: abre · segurar: roda) | à esquerda do 1 (`'` ABNT2, `` ` `` americano) | **Tocar**: abre a ficha. **Segurar** 0,3 s: a roda |
| Rolar o teste pedido pelo mestre | sem tecla | Rola o pedido do HUD (só ativa com um pedido no HUD) |
| Recusar o teste pedido pelo mestre | sem tecla | Recusa o pedido do HUD (idem) |
| Abrir a mesa do mestre | sem tecla | A mesa (o mestre também chega a ela pela roda) |

- **Tocar ou segurar** (`FichaKeys`): o mesmo esquema do `PassiveKey` da magia. Segurar 6 ticks
  (0,3 s) abre a `FichaRoda`; soltar antes disso abre a ficha — **ao soltar**, porque só então se sabe
  que não era segurar (50–300 ms a mais que antes, nada perto da ida ao site). Tocar = ficha ficou como
  atalho porque é o uso mais comum; a roda é um segurar de distância. Fechar uma tela com a tecla ainda
  apertada não conta como toque nem reabre a roda.
- **A roda** (`client/FichaRoda`) é toda do cliente: abrir não manda nada ao servidor, só existe
  enquanto aberta (nada por tick além de conferir a tecla segurada) e desenha arcos com a mesma faixa de
  triângulos da roda de passivas, no tema violeta/dourado da ficha. Fatias: Ficha, Dados, Habilidades,
  Mesa do mestre (só mestre) e, com pedido, Rolar e Recusar. As fatias não mudam de lugar debaixo do
  mouse: um pedido que chega com a roda aberta só a reorganiza quando o mouse volta ao centro; trocar
  o pedido mostrado trava Rolar/Recusar por 0,45 s. Rolar/Recusar respondem ao pedido **mostrado** (não
  ao que estiver no topo do HUD no instante do clique).
- **Abrir já na aba/diálogo**: o servidor não sabe de abas; o cliente guarda o destino pedido pela
  roda (`dados`/`habilidades`, 15 s) e o aplica quando a ficha própria chega (`FichaScreen.openOn`). Um
  erro de "abrir" (site fora) descarta o destino.
- **Quem é mestre, no cliente**: `LocalPlayer.hasPermissions(permissaoMestre)`. O vanilla já manda o
  nível de OP do perfil no login e a cada `/op`/`/deop`, e a config SERVER chega sincronizada: nenhum
  pacote novo. O alt nasce sem OP, então a roda dele não tem a mesa; o servidor confere de novo ao abrir.
- **Y/U**: eram o padrão de rolar/recusar, mas Y colide com a habilidade de peitoral do Cataclysm e a
  `ability_2` do hazentouvelib, e U com a roda de passivas da magia. Agora ficam sem tecla; os ids
  mudaram (`key.aurorion_ficha.pedido_rolar`/`pedido_recusar`) para o `options.txt` de quem já jogava
  não manter Y/U. Atribuídas, só ficam ativas com um pedido no HUD (contexto `FichaKeys.WHEN_ASKED`) e
  valem também dentro da ficha, da mesa e da roda. `FichaKeys.answerHint()` dá a dica do cartão do HUD.
- A da ficha era K, que colidia de fábrica com Iris (ligar shaders), Pufferfish's Skills, Quark (travar
  rotação) e o Kubedex do KubeJS; a nova é a única livre no modpack. Não colide com Shift+G (economia),
  H (magia) nem B (balão). Os textos ao jogador usam o nome da tecla configurada (o do servidor diz "a
  tecla da ficha" ou deixa o cliente pôr o nome).

## Testar sem servidor nem site

Com o mod e o TesseraUI no cliente, em qualquer mundo (inclusive singleplayer sem integração):

- **`/ficha teste`** — a ficha completa de uma personagem fictícia: as 22 perícias, recursos com
  Vida temporária, três condições, sete técnicas, história, Marca Solaris. Rolagens simuladas
  (com um vizinho rolando logo depois, para ver os cartões menores), dados livres, vínculo com
  candidatos fictícios. 6 s depois chega um pedido do mestre no HUD (feche a tela e segure a tecla da
  ficha para responder pela roda).
- **`/ficha teste roda`** — a roda da ficha de um jogador, sem pedido (Ficha, Dados, Habilidades), no
  modo de clique. **`rodapedido`** — a mesma com um pedido do mestre: o cartão no centro, o arco do
  tempo, Rolar (dourada) e Recusar. **`rodamestre`** — a do mestre, com a Mesa do mestre;
  **`rodamestrepedido`** — as seis fatias. As fatias respondem localmente: Ficha/Dados/Habilidades abrem a
  ficha fictícia no lugar certo, a Mesa abre a mesa inventada, Rolar rola o pedido. Para o modo de
  segurar, use a tecla da ficha de verdade num mundo qualquer (a roda abre com qualquer servidor).
- **`/ficha teste alt`** — um alt sem ficha, como no login: aviso no HUD e a ficha abrindo sozinha
  na aba Vínculo (convite `PROMPT`). **`altlogin`** — o mesmo convite 3 s depois: abra o inventário
  antes e veja a ficha esperar ele fechar.
- **`/ficha teste mudou`** — a ficha aberta "aguardando aprovação" recebe 3 s depois a versão
  aprovada, sem `OPEN`, com o aviso "A ficha mudou no site…".
- **`/ficha teste recusado`** — a aba Vínculo; vincular o "Capitão Ordrik" volta `forbidden` (outra
  staff o levou) e a lista nova chega sozinha, sem ele.
- **`/ficha teste semficha`** — a ficha abre normal; a próxima rolagem volta `needs_binding` (erro
  no diálogo) e, logo depois, a resolução nova leva a tela ao Vínculo com a lista — como o servidor.
- **`/ficha teste ocupado`** — a próxima rolagem volta `busy` (o site segurou: personagem num
  encontro): aviso de espera no diálogo, botão destravado para repetir.
- **`/ficha teste auth`** / **`missing`** — os erros de configuração do servidor ("avise a staff");
  "Atualizar" repete o mesmo erro, como o site.
- **`/ficha teste cancelado`** — um pedido no HUD que o mestre cancela 4 s depois (o cartão sai
  sozinho e a barra de ação diz "O pedido de Procurar foi cancelado pelo mestre."). Na mesa
  (`mestre`), o botão de cancelar marca "cancelado" em quem ainda não respondeu; as respostas
  fictícias incluem recusa, "saiu do servidor antes de o site responder" e quem nunca responde (fica
  para cancelar ou vencer).
- **`/ficha teste mestre`** — a mesa com dez jogadores inventados (dois sem o mod, a Nhara num alt
  sem ficha e o Dário sem ficha ativa); cada resposta passa por "rolando" antes do resultado, com
  atraso aleatório, um ou outro recusa; Nhara e Dário respondem e voltam erro ("está num alt que
  ainda não escolheu ficha"). "Ficha" abre a ficha de um deles (a do Orren mostra "aguardando
  aprovação"; a da Nhara e a do Dário, o motivo de não haver ficha).
- **`/ficha teste antiga`** — ficha que o jogador ainda não atualizou no site: aviso "Sua ficha ainda não foi
  atualizada no site", botões **Atualizar no site** e **Rolagem livre**, selo "Ficha antiga"; na mesa, Eli Antigo
  aparece como "ficha antiga" — num teste ele volta erro ("peça Dados"); em **Dados** responde normal.
- **`/ficha teste avisoantiga`** — o aviso de ficha antiga do login da conta principal, pelo mesmo caminho do
  pacote: o texto traz o nome da tecla da ficha que você configurou (desatribua para ver "use /ficha").
- **`/ficha teste dados`** — um pedido de dados no HUD ("Rolar 2d6", CD 8 no total): Rolar (roda ou ficha) rola os dois d6 e o
  cartão mostra total e sucesso/falha. Na mesa (`mestre`), **Dados** (ou 7) pede 1d20/2d6/qualquer fórmula
  aos jogadores fictícios.
- **`/ficha teste aprovacao`** / **`vazio`** / **`erro`** — construção aguardando aprovação; ficha
  mínima com Vínculo sem candidatos; conta do site não vinculada (o passo a passo; colar qualquer
  código e "Vincular" faz de conta que o site confirmou, e a ficha abre sozinha).
- **`/ficha teste pedido`** — o pedido do mestre no HUD (de novo: o segundo, com CD oculta, secreto
  e motivo longo em duas linhas). **`pedidos`** — três de uma vez ("+2 pedidos"), o do topo com
  25 s; **`prazo`** — um com 24 s, para ver a contagem âmbar e a saída ao vencer.
- **`/ficha teste hud`** — todos os tipos de cartão em sequência: crítico seu, sucesso e falha de
  vizinhos, desvantagem, secreto com bônus de NPC escondido, falha crítica, resposta a pedido com
  vantagem e CD oculta, nome longo, rolagem livre sem d20.
- **`critico`**, **`falha`**, **`vantagem`** — natural 20 (inclusive "crítico, mas falha");
  natural 1 e falha comum; vantagem e desvantagem com os dois d20.
- **`pilha`** — oito cartões em pouco mais de um segundo: empilhar, o limite de seis, o corte acima
  da barra de itens.
- **`aviso`** — só o aviso de alt sem ficha. **`carregando`** — o "Abrindo a ficha…" e a ficha
  chegando depois. **`sitefora`** — o "Abrindo…" e o erro de site fora na barra de ação, sem tela.

O selo **TESTE LOCAL** identifica o modo. Nada vai ao servidor nem ao site. No `aurorion-runs`
(`:aurorion-runs:runClient`), o agregador já carrega a ficha e copia o TesseraUI para a pasta
`mods/` do cliente, como no diário. Diferente do diário, `/ficha` sem argumento continua indo ao
servidor também nos runs de desenvolvimento — use `/ficha teste`.

## Dependências

- `aurorion-integracao` ≥ 0.2.0 e `aurorion-core` ≥ 0.6.4 (dos dois lados — no singleplayer o
  servidor integrado roda no cliente). 0.6.4 porque o `ServerDiagnostics` entrou no core depois do
  0.6.3.
- **TesseraUI 1.1** no cliente (`mod-servidor-referencia/tesseraui-1.1.jar`; LGPL-3.0). O servidor
  não precisa dele.
- Backend com as rotas `/api/v1/integration/v1/rpg/*` do contrato "Ficha do site dentro do jogo".

## O que validar ao compilar

Nada disto rodou com o Minecraft nesta máquina. O módulo inteiro (95 classes) foi compilado com
`javac` contra os jars do NeoForge do cache do Gradle, e os 86 testes rodaram fora do Gradle; o que
depende de jogo aberto está listado abaixo.

- `./gradlew :aurorion-ficha:test` — `SheetModelTest`, `SheetRobustnessTest`, `CatalogTest`,
  `CatalogParityTest`, `DiceFormulaTest`, `GmRequestsTest`, `RollAudienceTest`, `SheetCacheTest`,
  `FichaServerCodesTest`, `FloodGuardTest`, `AskCodecTest`.
- `./gradlew :aurorion-ficha:compileJava` — conferir em especial:
  - 0.2.0: `SheetStatsStore` (SavedData pelo `SavedDataAccess`), `CharacterSheet.provide` no construtor
    do mod, o `onReset(CharacterResetEvent)` e a corrente de `scheduleRenewal` (uma por conexão);
  - `KeyMapping(String, IKeyConflictContext, InputConstants.Type, int, String)` (mesma forma do
    `PassiveKey` da magia); `RegisterGuiLayersEvent.registerAbove(VanillaGuiLayers.CHAT, …)`;
  - `PlayerFaceRenderer.draw(GuiGraphics, PlayerSkin, x, y, size)`; `ConfirmLinkScreen(BooleanConsumer, String, boolean)`;
  - `FichaSheet` ganhou o componente `legacy` (texto do `legacy_level`) com um construtor de
    compatibilidade de 21 argumentos para o `FichaTestSession`: conferir que os dois compilam;
  - `ClientCommandHandler`: `/ficha teste` no cliente e `/ficha` (sem argumento) indo ao servidor.
    Conferido no bytecode do NeoForge 21.1: o cliente só devolve o comando ao servidor em
    `dispatcherUnknownCommand`/`dispatcherUnknownArgument` — por isso o nó `ficha` do cliente não tem
    `executes`; `/ficha teste xyz` cai no argumento guloso do ramo e lista as variantes;
  - teclas de pedido com contexto próprio (`IKeyConflictContext` anônimo em `FichaKeys.WHEN_ASKED`) e o
    `@SubscribeEvent(priority = LOWEST, receiveCanceled = true)` de
    `CustomizeGuiOverlayEvent.BossEventProgress`; `SimpleSoundInstance.forUI(SoundEvent, float, float)`
    com `SoundEvents.NOTE_BLOCK_BELL.value()`; `KeyMapping.getKey()`/`getKeyModifier()`;
    `LanguageManager.getSelected()`;
  - TesseraUI: `TesseraTemplateRenderer.build(template, model, clicks, submits, inputs, x, y, w, h)`,
    `onclick` em `<col>`/`<row>` (o renderer chama `TesseraPanel.onClick`), `onsubmit` nos inputs.
- `runClient` + `/ficha teste` em escala de GUI 2, 3 e 4 e janela pequena (1280×720):
  - cabeçalho (rosto da skin dentro da moldura, nome dourado, chips de Elemento/grau), abas, barras
    com as cores certas (Vida vermelha, Stamina âmbar, Sanidade índigo, P.E. ciano) e Vida temporária;
  - **hover** nas linhas de perícia, nos atributos, nos candidatos e nos botões (o CSS usa `:hover`
    em `row`/`col` — se o Tessera não aplicar, trocar a raiz por `<button>`);
  - o texto das linhas cabe (cortado com "…" e tooltip quando não cabe); paginação pela roda do mouse;
  - diálogo de rolagem: CD, Enter rola, Esc fecha, "Secreto"; d20 girando e pousando, moldura do
    resultado mudando de cor; a tecla **I** não liga o overlay de depuração do Tessera;
  - HUD — QA visual, com `/ficha teste hud`, `critico`, `falha`, `vantagem`, `pilha`, `pedido`,
    `pedidos`, `prazo`, `aviso`, `carregando` e `sitefora`, em escala 2, 3 e 4 e em 854×480:
    - d20: rola ~0,8 s (número trocando cada vez mais devagar, quicando), cresce um pouco e assenta
      sem "salto" de tamanho, e só então o total e o resultado entram (encolhendo até o tamanho,
      subindo 2 px); natural 20 com faíscas e borda dourada que pulsa ~1,4 s e fica acesa; natural 1
      tremendo; vantagem/desvantagem com o segundo d20 na frente, apagado e com risco vermelho, o
      número dele legível;
    - sucesso/falha legíveis de relance (cor do total + faixa atrás da palavra); cartões entrando
      pela direita com fade, saindo com fade, os de baixo subindo quando um some;
    - pilha abaixo dos ícones de efeito **e das barras de chefe** (invocar um chefe ou usar
      `/bossbar`), sem cobrir a barra de itens nem o texto de ação; em tela estreita (escala 4) a
      pilha pula o cartão do pedido em vez de passar por cima;
    - com o JourneyMap (minimapa `TopRight`, 30 %): a pilha começa abaixo do mapa e dos rótulos dele
      (conferir a folga dos rótulos, que é estimada); trocar o minimapa para `BottomRight` ou o
      preset 2 e ver a pilha acompanhar em ~1 s; desligar o minimapa → a pilha volta a 42 %;
    - pedido no centro (`CENTRO_TOPO`) abaixo do Jade olhando para um bloco e abaixo de uma barra de
      chefe, sem cobrir a mira em 854×480; com o grupo do Voice Chat e o Map Atlases à esquerda, nada
      por cima deles; cada âncora de `ficha-client.toml` e `escala` 0.75/1.25 (bordas e textos nítidos);
    - `/streamer on` (Essentials) → `/ficha teste pilha` (tem secreta e bônus de NPC) e `mestre`
      (resposta com CD oculta): secreta como "Rolagem secreta" com "?", bônus "?", "CD oculta" no lugar
      do número; a mesa mostra o selo "MODO STREAMER" (tooltip no hover); `/streamer off` → os cartões
      que estão na tela se refazem inteiros no frame seguinte;
    - pedido: desliza ao chegar com um sino baixo, quem pediu à direita, motivo em até duas linhas,
      a dica "Segure ' para responder" (com a tecla da ficha configurada; atribua Y/U às teclas de pedido
      e veja "Y rola · U recusa"), "Rolando…" depois de rolar pela roda, saída deslizando ao responder, contagem
      e barra âmbar com a borda pulsando nos últimos 20 s, "+2 pedidos";
    - teclas de pedido atribuídas, sem pedido no HUD, não fazem nada **e não roubam a letra** de outro
      mod; tocar a tecla da ficha (padrão à esquerda do 1; no ABNT2 o nome mostrado deve ser `'`) abre a
      ficha com "Abrindo a ficha…" no canto se o site demorar; segurar abre a roda;
    - roda (`/ficha teste roda`, `rodapedido`, `rodamestre`, `rodamestrepedido` e a tecla segurada): abre
      crescendo ~150 ms sem pausar o jogo; fatia apontada sobe 4 px com borda dourada; nomes cabem nas
      fatias (Mesa do mestre encolhe) em escala 2, 3 e 4 e em 854×480; o cartão do pedido cabe no centro
      ("Teste de Furtividade" inteiro, motivo em até duas linhas) e o arco do tempo fica âmbar nos últimos
      20 s; soltar no centro e Esc fecham sem nada; 1–6 e Enter; Recusar mostra "Pedido … recusado." na
      barra de ação; Dados abre a ficha já no diálogo, Habilidades já na aba 5; um alt (sem OP) não vê a
      Mesa; com a roda aberta, um pedido que chega não troca as fatias debaixo do mouse;
    - aviso de alt que chega com a tela aberta só começa a contar ao fechá-la;
    - F1 esconde tudo; F3 deixa só o pedido do mestre; com a ficha ou a mesa aberta os cartões não
      aparecem por baixo;
    - erro sem tela (`sitefora`) na barra de ação; com o inventário aberto, a mesma mensagem vai ao
      chat;
  - `/ficha teste mestre`: seleção múltipla, atributo/perícia, CD oculta, modos, respostas chegando,
    "Ficha" abrindo por cima da mesa e "Voltar" retornando a ela; modo **Dados**: atalhos, campo da
    fórmula ficando vermelho com `2d6*3` (tooltip com o motivo, botão de pedir travado), atalhos de CD só
    com um d20, sem os botões de vantagem, "ficha antiga" sem o âmbar, foco do campo sobrevivendo à
    remontagem a cada tecla, Tab passando por fórmula → CD → motivo;
- Servidor com integração e o backend do contrato:
  - conta principal com ficha ativa → `/ficha` abre direto; sem ficha → aba Vínculo com `no_sheet`;
  - alt sem vínculo → aviso no login (HUD + chat clicável), escolher NPC livre como staff, trocar,
    "Padrão"; vínculo velho mostra "Vínculo antigo";
  - rolar perícia/atributo/dados com e sem CD, secreto; vizinho a 20 e a 30 blocos; outra dimensão;
  - derrubar o backend no meio de uma rolagem → mensagem de site fora; religar e repetir sem
    rolagem duplicada no histórico do site;
  - pedido de **dados** (`2d6`, CD 8) para quem tem ficha antiga e para quem tem construção: os dois
    rolam, o cartão mostra "2d6 = 9 · CD 8" e sucesso; o histórico do site guarda o motivo como rótulo;
    vanilla recebe "◆ O mestre pede: rolar 2d6 · CD 8 [Rolar] [Recusar]"; `/ficha mestre pedir @a dados 1d100`
    e `/ficha mestre pedir @a 2d6 10`;
  - conta principal com ficha antiga entrando: aviso no HUD ~5–8 s depois, com o nome da tecla; relogar
    no mesmo dia não repete; com a construção enviada (aguardando aprovação) não avisa; sem o mod, a
    linha com o link; abrir a ficha logo depois do aviso não chama o site (diagnóstico: chamadas);
  - mestre (OP 2, staff no site) pedindo com CD oculta + vantagem; mestre sem staff no site →
    `gm_required`; cliente vanilla respondendo por `[Rolar]` e `/ficha pedido <id> recusar`;
  - NPC rolado: o dono vê o bônus, o vizinho não; ficha sem build aprovada → `no_build`;
  - `forbidden` ao vincular NPC de outro jogador mostra o motivo do site; limpar o vínculo na conta
    principal sem ficha ativa (`no_sheet`) atualiza a tela em vez de dar erro;
  - alt de staff (sem OP): sem `/ficha mestre` no TAB e sem ver rolagens secretas;
  - mestre sai do servidor → o cartão do pedido some do HUD de quem não respondeu, com "O pedido de …
    terminou: o mestre saiu do servidor." na barra de ação; alvo sai → a mesa mostra "saiu do servidor";
  - alt sem ficha respondendo a um pedido → a mesa mostra "… está num alt que ainda não escolheu
    ficha" (e não o texto dirigido ao jogador); a própria rolagem com a ficha aberta, depois de o
    vínculo cair no site → erro no diálogo e a tela vai sozinha ao Vínculo;
  - integração desligada (`FactBridge.site()` vazio) → a tecla da ficha mostra o motivo na barra de ação na hora (o
    "Abrindo a ficha…" não espera os 10 s); a aba Vínculo destrava o "Atualizar";
  - alt sem ficha entrando: a ficha abre sozinha no Vínculo ~4 s depois; entrando com a criação de
    personagem ou o inventário aberto, abre ao fechar a tela (uma vez só, até 60 s); relogar em menos
    de 10 min não repete o aviso;
  - `/ficha mestre cancelar <id>` (ou o botão da mesa) → o cartão some do HUD de quem não respondeu,
    com o motivo na barra de ação, e a mesa marca "cancelado"; quem já estava "rolando" ainda chega;
  - backend com credencial errada (401/403) e sem as rotas (404) → "avise a staff" na tela;
  - `/ficha mestre pedir …` digitado por um mestre com o mod → "Pedido … enviado" na barra de ação;
    `/ficha vincular npc <id>` pelo chat → a confirmação na barra de ação;
  - desconectar com cartões e pedido no HUD e entrar em outro servidor (ou trocar de alt) → HUD
    limpo, mesa sem as respostas do servidor anterior;
  - `ServerDiagnostics` com a seção Ficha (o `/aurorion diagnostico` do Essentials ainda precisa
    listá-la).
- Servidor dedicado sobe sem TesseraUI e sem carregar classe de `client/`; cliente vanilla entra
  normalmente (canal opcional).
