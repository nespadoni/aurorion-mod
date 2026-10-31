# Comunicado da UF - 31/10/2026

**A ficha ganhou vida no jogo.** Seus atributos e perícias agora decidem se a magia sai, se a peça fica
pronta e quem vence um duelo. E tudo isso cabe num menu novo, que aparece na sua frente. Isto é só o
começo.

## Menu do personagem

- **Para abrir**, faça o gesto: com as duas mãos vazias, segure o botão direito e puxe a mira para baixo,
  rápido. Também abre com um toque na tecla da ficha.
- **Ele aparece flutuando na sua frente**, no mundo, em primeira ou em terceira pessoa. Você pode andar
  e olhar em volta, e o painel acompanha.
  - Aponte com a mira e clique com o esquerdo para escolher.
  - O direito volta um passo, e Esc fecha.
- **Perfil:** o seu personagem de corpo inteiro, com a armadura e o que você segura, e a sua ficha
  completa: vida, stamina, sanidade, P.E., condições, combate e atributos. Clique num atributo para
  rolar o teste.
- **Interações:** meditar, e o que dá para fazer com quem está perto (por enquanto, desafiar para duelo).
- **Quem está perto vê você usando:** o braço faz o gesto e aparece um painel holográfico borrado na sua
  frente. Ninguém lê o que está no seu.
- **Avisos na sua frente:** conquistas e avisos da ficha chegam num cartão flutuante com o seu
  personagem.

## Meditar

- Sente-se e recupere vida devagar. Funciona no chão, fora de combate e longe de monstros.
- Andar, atacar ou levar dano quebra a concentração.

## Magia com a ficha

- **Toda magia agora tem um teste:** Vitral + Astros & Mistos.
  - **Falhou:** a magia não sai e o custo é cobrado inteiro.
  - **Falha crítica:** você fica **Exausto** e tonto.
  - **Acerto crítico:** a magia sai mais forte.
- **Despertar Arcano I, II e III:** só um professor concede, e eles liberam as magias.

| Nó | Libera |
|---|---|
| Despertar I | magias comuns e incomuns |
| Despertar II | magias raras, épicas e lendárias |
| Despertar III | magias proibidas |

- **Especialidade de magia:** depois do Despertar I, cada um escolhe **uma** das 9 escolas na Trama.
  - Na sua escola você ganha vantagem e bônus no teste, a magia fica mais barata e mais rápida, e o poder
    aumenta.
  - Trocar de escola só pelo respec.

## Fabricar com a ficha

- **Tudo o que se fabrica tem um teste de Criação:** bancada, fornalha, mesas e oficinas dos mods,
  inclusive as do Apotheosis.
  - Falhou: os ingredientes se perdem e sai uma **Peça Arruinada**.
  - Crítico num item difícil: **obra-prima**, com a sua assinatura ou uma peça a mais.
- **Máquinas do Create e do Oritech** usam a ficha de quem as colocou, com Criação e **Tecnologia**.
- Sua profissão dá vantagem no que é dela.

## A ficha em outras partes do jogo

- **Medicina** decide a cura do médico.
- **A comida do chef** sai com qualidade.
- **Diplomacia e Intimidação** baixam o preço com os NPCs.
- **Astros & Mistos** decide a mesa de encantamento: o crítico devolve o lápis, a falha crítica perde o
  encanto.
- **Furtividade** ajuda a abrir fechaduras.
- **Natureza** ajuda na pesca.
- **Último fôlego:** num golpe que seria fatal, um teste de Fortitude pode deixar você com 1 de vida, uma
  vez por dia.

## Duelos

- **Desafie alguém pelo menu**, em Interações ou em Duelo, ou com `/duelo`. Quem recebe vê um convite no
  meio da tela e responde apontando: ○ aceita, × recusa. Os dois escolhem juntos até que vida vai a luta:

| Limite | Como acaba |
|---|---|
| Primeiro sangue | no primeiro dano |
| Porcentagem | quando a vida chega à marca combinada |
| **Até a morte** | quem zerar a vida da ficha perde o personagem para sempre |

- **Uma arena se forma:** uma parede de luz cerca os duelistas, ninguém foge do campo, e quem está perto
  assiste.
- **Contagem regressiva** com os dois nomes, VS e "LUTEM!".
- **A luta é por turnos e usa a sua ficha:** atacar, defender, técnicas equipadas e magias do seu
  Despertar.
- **Reação:** quando um golpe vem, um anel fecha no instante do impacto. Clique esquerdo apara, direito
  esquiva.
  - Clicar no tempo certo dá bônus, mas quem decide é o dado.
  - Aparar sem parar cansa a guarda.
  - O aparo perfeito pode virar um contra-ataque.
- **O dano segue o sistema novo do RPG**, escalado pela sua Alma. Críticos caem com raio.
- **No fim**, um cartão mostra vitória ou derrota, o dano causado e recebido, golpes, defesas, críticos e
  o XP ganho.
- **Regras justas:**
  - desistir nunca mata;
  - cair a conexão ou reiniciar o servidor não mata ninguém;
  - um convite por vez, e ninguém troca os termos;
  - XP só de duelo lutado de verdade;
  - durante o duelo o mundo continua perigoso.

## Isto é só o começo

O menu vai receber muito mais nas próximas atualizações:

- o resto da ficha, com perícias, técnicas, história, vínculo e rolagem de dados;
- a mesa do mestre;
- a sua Casa;
- a carteira;
- conquistas;
- **missões**.

Os duelos também vão ganhar ainda mais efeitos para quem assiste. Encontrou um bug ou tem uma ideia?
Fale com a staff.

# Menu do personagem vira mod próprio (fase 1 de 5) - 31/10/2026

Core 0.7.0, Menu 0.1.0 (novo), Ficha 0.3.0 e Intenção 0.3.0, junto com Trama 0.2.0, Magia 0.10.0,
Profissões 0.6.0, Vidas 0.3.0 e Limbo 0.4.8. Ficha e Intenção exigem `aurorion_core [0.6.9,)`. Compilado com
`gradlew buildAll` em 31/10/2026, com os testes de todos os módulos passando. O menu em estilo de MMO deixa de ser do duelo e vira o centro do personagem. As próximas fases
trazem o resto da ficha, a mesa do mestre, a Casa, a carteira, as conquistas e as missões, e no fim o
`/ficha` fica só para admin.

- **Core 0.6.9:**
  - `CharacterMenu`: registro de seções e interações no cliente, com host opcional no padrão dos gates.
  - O desenho do menu sai do intencao e vem para cá: `MenuUi`, as texturas lisas (agora com o painel
    holográfico borrado), a fonte Rajdhani e o gerador.
  - `EmotecraftBridge` toca emotes por reflexão.
- **Menu 0.1.0 (`aurorion-menu`, novo):**
  - **Painel no mundo, não uma tela:** flutua na frente da pessoa, em primeira e em terceira pessoa, e
    segue quando ela anda ou vira a cabeça. Aponta-se com a mira. Clique esquerdo escolhe, direito volta,
    a rodinha rola, Enter confirma e Esc fecha. A câmera fica livre.
  - Abre e fecha pelo gesto da mão ou por um toque na tecla da ficha. A tecla própria vem sem atribuição,
    porque Y colide com Cataclysm e hazentouvelib.
  - **Avisos na frente da pessoa:** cartão branco no estilo de resultado, com o personagem ao lado.
    Conquistas do vanilla e avisos da ficha vão para ele (`CharacterMenu.notice`).
  - Quem está perto vê o braço fazer o gesto e um painel holográfico borrado na frente da pessoa.
  - **Interações:** Meditar e as ações sobre jogadores próximos.
  - **Meditar:** o emote do Emotecraft do servidor, ou uma pose sentada sem ele; recupera vida devagar
    fora de combate e para ao andar, apanhar ou atacar.
- **Ficha:** a seção **Perfil** no menu, com a vitrine do personagem (corpo inteiro, armadura e mãos) e o
  conteúdo da antiga aba Ficha: recursos, vital, condições
  com explicação, combate e atributos clicáveis para rolar. Usa o mesmo pacote de abrir. A tela antiga
  continua para o resto.
- **Verificação geral (cinco revisões, uma simulação de 20 mil duelos por cenário) e correções:**
  - Duelo, segurança:
    - um convite por vez;
    - desistir nunca mata;
    - `fugaMata` desligado por padrão;
    - reinício do servidor encerra os duelos sem matar;
    - nada de imortalidade (dano do mundo vale, máximo de 30 rodadas);
    - XP só de duelo lutado, com intervalo por dupla;
    - tempo da reação conferido pela chegada do pacote;
    - montaria não escapa do campo;
    - intervalo entre desafios.
  - Duelo, regra: o crítico de ataque é só o 20 natural (§07); a regra genérica de teste dava 15–20% de
    crítico.
  - Duelo, efeitos e placar:
    - raio de efeito desenhado no cliente, só para a plateia;
    - golpes e críticos contam só quando entram;
    - Defender sempre aparece no registro;
    - a saída de um terceiro não tira a vez de quem age.
    - Protocolo de rede 5.
  - Menu, lógica:
    - Ações aparece com o menu já aberto;
    - a coluna não duplica (atualização no próximo quadro);
    - fechar o menu devolve o convite para a frente da pessoa, com o prazo que restava;
    - Ações some com o duelo pausado;
    - clicar em item desativado não fecha colunas;
    - desistir pelo comando.
  - Menu, render:
    - erro de desenho não derruba o jogo nem deixa sombra, luz ou recorte presos;
    - F1 esconde e desliga o painel;
    - a mão em primeira pessoa não entorta;
    - o clique volta a prender o mouse depois de alt-tab;
    - o `MenuUi` devolve o blend padrão;
    - as texturas saem da memória de vídeo ao fechar;
    - a presença de quem sai de vista é esquecida;
    - a profundidade da tela do menu não depende de quantas camadas de HUD há.
  - Menu, opções e compatibilidade:
    - o gesto exige as duas mãos vazias e a mira no ar;
    - `gesto` e `conquistasNoCartao` na config do cliente;
    - meditar com limite de pedidos e quebrando só com dano de verdade;
    - presença desligada não vaza a flag do menu.
  - Ficha: a ficha pedida pelo menu não abre a tela antiga se o menu fechou antes de ela chegar.
  - Testes novos: `attackCritsOnlyOnNatural20` e `clientTimingIsCheckedAgainstArrival`.
- **Intenção, dano do duelo pelo RPG v0.3:** `piso((d12 + MO) × E)`, com E = Vida de referência da alma ÷ 150.
  - Pacotes: básico 3d12; técnicas H1 4d12, H2 5d12, H3 6d12, H4 8d12, H5 12d12; magias pela raridade.
  - Crítico dobra os dados (MO uma vez, depois E).
  - Ordem do dano conforme o §10.
  - Um duelo resolve em 4–6 rodadas.
  - A ficha passa a guardar a alma no retrato.
  - Teste novo: `DuelScaleTest`, com os exemplos do próprio documento (76, 70, 250).
- **Intenção, duelo para live e plateia:**
  - Arena desenhada no cliente: parede de luz, anel, faíscas e raios de efeito na abertura, nos críticos
    e na derrota. Ficou mais leve: o servidor parou de mandar 32 pacotes de partícula por jogador a cada
    meio segundo.
  - Contagem com faixas de cinema, VS, número saltando, anel e "LUTEM!".
  - Resultado num cartão no meio da visão, com o placar e o XP do vanilla (`xpVitoria`, `xpDerrota`).
  - Desafio, começo e fim saem do chat (que fica só para quem joga sem o mod). Protocolo de rede 4.
- **Intenção, reação no duelo vira teste:** clicar no tempo certo não basta mais para anular o golpe.
  - A reação é d20 + Corpo a corpo (aparo) ou Acrobacia (esquiva) contra o total do ataque como CD.
  - O tempo dá +5 (perfeito) ou +2 (na janela).
  - Aparo bem-sucedido corta metade do dano. O contra-ataque só vem passando por 10 ou mais no instante
    perfeito. Esquiva é tudo ou nada.
  - Cada defesa seguida dá −2 na próxima (guarda cansada); Defender no turno zera.
  - Tudo configurável em `[duelo]`. Teste novo: `DuelReactionTest`.
- **Intenção, convite de duelo na frente da pessoa:** cartão no meio da visão; aponta e clica em ○ ou ×,
  com a barra do prazo. Some sozinho se o duelo começar por outro caminho (`CharacterMenu.dismissPrompt`).
- **Intenção:** o menu próprio e a tecla Y saem. O duelo registra **Ações** (só na sua vez), **Duelo** e
  "Desafiar para duelo" em Interações, e o convite vira a janela ○/× do host. O HUD usa o `MenuUi` do core.
- **Verificação:** compilado com `javac` contra os jars do NeoForge do cache (core, menu, ficha,
  intencao), sem Gradle e sem rodar o jogo. `CharacterMenuTest` e `SheetCheckTest` passaram fora do
  Gradle.

# A ficha decide magia e ofício: teste, Despertar Arcano, especialidade - 30/10/2026

Core 0.6.8, Ficha 0.2.0, Trama 0.2.0, Magia 0.10.0, Profissões 0.6.0, Vidas 0.3.0, Intenção 0.2.0 e Limbo
0.4.8 (ainda não publicadas). A ficha do
site deixa de só mostrar: atributos e perícias decidem se a magia sai e se a peça fica pronta.

- **Core 0.6.8:** contratos novos, todos no padrão do `LivesGate`.
  - `CharacterSheet`: o retrato da ficha, lido de memória.
  - `SkillTreeGate` e `SkillTreeChangedEvent`: os nós da Trama, sem importar a Trama.
  - `SheetCheck`: a regra única do teste, d20 + bônus contra CD, com crítico, falha crítica e vantagem.
  - Os quatro mods passam a exigir `aurorion_core [0.6.8,)`.
- **Ficha 0.2.0, retrato dos testes:** SavedData `aurorion_ficha_stats`.
  - Atualiza a cada leitura `ok` do site, numa leitura no login de todos (`retrato.lerNoLogin`) e numa
    releitura a cada 30 min com a pessoa online.
  - Bônus de magia: Vitral + Astros & Mistos. Bônus de ofício: o bônus de teste de Criação.
  - Ficha sem construção aprovada não entra; quem pergunta usa o próprio bônus padrão.
- **Trama 0.2.0:** 283 nós.
  - **Despertar Arcano I, II e III** (`AR1`–`AR3`): só um professor libera, com
    `/trama no conceder|remover|ver`. Vale offline, o respec mantém e o reset de personagem apaga.
  - **9 especialidades de magia** (`ES_*`): compradas com ponto da Trama depois do AR1, exclusivas entre
    si; trocar é pelo respec.
- **Magia 0.10.0:**
  - **Liberação pelo nó** (`liberacao.modo = "no"`): I = comuns/incomuns, II = raras/épicas/lendárias,
    III = proibidas. A liberação por aula vira exceção. Sem a Trama, volta sozinho ao modo aula.
  - **Teste a cada conjuração**:
    - falha: a magia não sai e o custo é cheio (mana e recarga; o pergaminho se perde);
    - falha crítica: também Exausto (efeito novo, lento e sem conjurar) e tontura;
    - acerto crítico: a magia sai com +1 nível, o que vale para toda magia.
  - **Especialista**: vantagem e +2 no teste, −15% de mana, −20% de recarga, +20% de poder da escola.
  - A oficina arcana (mesa de inscrição, forja, bigorna arcana, caldeirão) exige o Despertar I.
  - A ficha mostra o Despertar, a especialidade e a chance de falhar por raridade.
- **Profissões 0.6.0, teste de ofício:**
  - d20 + Criação contra a CD do item: datapack `aurorion/oficio`, ou a heurística (equipamento 12,
    comida 8, o resto 2, mais a raridade).
  - Falha: os ingredientes se perdem e sai a **Peça Arruinada** (item novo).
  - Crítico em CD alta: **obra-prima**, com a assinatura do personagem ou uma peça a mais.
  - Vantagem no domínio da profissão.
  - Itens do Iron's exigem o Despertar I.
  - Onde vale:
    - bancada e 2x2, com clique e shift-click;
    - mesa de ferraria;
    - fornalhas: usa a ficha de quem colocou, mesmo offline;
    - Create: processamento, crafter mecânico e montagem sequencial, com a ficha de quem montou a máquina
      (Criação + **Tecnologia**); sem dono, bônus de máquina;
    - suporte de poções: o frasco que falha vira poção mundana;
    - Farmer's Delight: panela, fogão, frigideira (bloco e mão), tábua de corte;
    - Let's Do (Farm & Charm, Candlelight): panelas, torrador e fogão, com a ficha de quem abriu o menu;
    - Oritech: toda máquina de receita, também com Criação + Tecnologia;
    - Apotheosis (vantagem do arcanista): reforja que falha devolve o item original, lapidação perde as
      gemas, aumento de afixo paga o custo sem subir;
    - Cooking for Blockheads: a cozinha, por prato;
    - Malum (altar de espíritos) e Mystical Agriculture (altares de infusão e despertar);
    - estações com `onTake` próprio: Sophisticated, Iron's, Cataclysm, LSO e Refurbished.
  - Máquinas dos mods em `oficio.modsComDono` gravam quem as colocou e usam a ficha dessa pessoa.
  - **Outros testes da ficha:**
    - médico: a cura testa Medicina (crítico dá Regeneração; falha cura só parte) e o tratamento de membro
      pode falhar;
    - cozinheiro: a qualidade do prato varia com a Criação;
    - NPCs de ofício: Diplomacia dá desconto; Intimidação, se render mais, arrisca ofender o NPC;
    - mesa de encantamentos: Astros & Mistos (crítico devolve a lápis, falha crítica perde o encanto);
    - Locks Reforged: Furtividade reduz o desgaste da gazua e segura o progresso;
    - pesca (vanilla, Aquaculture, Starcatcher): Natureza dá peixe a mais ou deixa escapar.
  - Levantamento das máquinas de todos os mods do pack em `aurorion-profissoes/OFICIO-ADAPTADORES.md`,
    com o que ainda falta (Productive Bees, Refurbished, Create Additions, Vinery e outros).
- **Intenção 0.2.0, duelo com a ficha (`/duelo`):** combate por turnos feito pelo jogo, no estilo de MMO.
  - Desafio com o limite de vida escolhido pelos dois (primeiro sangue, porcentagem ou até a morte).
  - Campo de não fuga e aviso para quem está perto.
  - Ataque, defesa, técnicas da ficha e magias do Despertar.
  - Reação cronometrada: clique esquerdo apara, direito esquiva; aparo perfeito contra-ataca.
  - No duelo até a morte, quem zera a vida da ficha perde o personagem, com a cena de fim do Limbo direto.
  - `/intencao combate` leva o mesmo motor para dentro da batalha de intenção.
  - **Menu do personagem em estilo de MMO**, liso e nas cores Aurorion. Abre com o gesto (mão vazia,
    botão direito, puxar para baixo) ou a tecla Y. Tem Perfil, Duelo (desafiar quem está perto com o
    limite), Ações (a sua vez) e janelas de confirmação/convite com ○ e ×. O HUD usa o mesmo desenho.
    Arte original gerada por `tools/generate_ui.py`, com a fonte Rajdhani (OFL).
- **Core 0.6.8 (mais):** técnicas equipadas no retrato da ficha; `CharacterSpells` (a magia oferece as do
  livro equipado) e `CharacterFinale` (o Limbo encerra um personagem). Limbo 0.4.8 responde o fim.
- **Vidas 0.3.0, último fôlego:** num golpe fatal, um teste de Fortitude da ficha deixa a pessoa com 1 de vida,
  uma vez por dia, sem cobrar vida.
- Compilado com `javac` contra os jars do NeoForge do cache, sem Gradle e sem rodar o jogo. Testes
  unitários novos passaram fora do Gradle: `SheetCheckTest`, `SkillCatalogTest` e `CraftDifficultyTest`.

# Aba Habilidades da ficha com os dados do jogo - 30/10/2026

Core 0.6.7, Profissões 0.5.4; Magia 0.9.0, Trama 0.1.3 e Ficha 0.1.0 (ainda não publicadas). A aba
Habilidades da ficha (tecla K) mostra o que o personagem tem **dentro do jogo**, sem comando.

- **Core 0.6.7 — `character/CharacterAbilities`:** contrato novo. Cada mod dono do dado registra um
  provedor na construção; a ficha consulta quando uma View é montada (abrir, atualizar, vincular,
  espiar). Nada por tick, só leitura de memória, tetos de 8 grupos × 64 itens. Provedor que lança fica
  de fora daquela abertura e vai uma vez para o log. Ninguém exige ninguém: sem a ficha, os provedores
  não rodam; sem um mod, o grupo dele não aparece.
- **Grupos:**
  - *Magias* (Magia): escolas liberadas (com quantas magias entregam), magias soltas, proibidas
    marcadas, staff com `staffBypass`; sem liberação, um aviso de que magia se aprende em aula.
  - *Passivas* (Magia): cada passiva, ligada/desligada, de tecla ou sempre ativa.
  - *Profissão* (Profissões): a profissão e o que ela libera, com os números do config em uso.
  - *Trama do Eco* (Trama): pontos conquistados, barra até o próximo ponto (XP e tempo), nós comprados;
    os Fios somados numa linha.
  - *Personagem* (pela própria ficha, com `HouseGate` e `LivesGate`): Casa e vidas restantes.
- Nomes e descrições de magia e passiva vão com a chave de tradução: o cliente mostra em português,
  mesmo com o servidor dedicado só conhecendo o `en_us`.
- Com a ficha aberta, o `atualizar` manda a View de novo quando as habilidades mudaram, mesmo com a
  ficha do site igual.
- **Faixas do core:** Ficha, Magia, Profissões e Trama passam a exigir `aurorion_core` `[0.6.7,)`.

# Aviso de área no chat da staff e permissão de entrada - 22/10/2026

Áreas 0.6.6. O `/area aviso <id> true` só colocava a entrada na fila do bot; no jogo não aparecia nada.

- **Chat da staff:** toda entrada numa área vigiada (e o `/area avisos teste`) sai na hora para quem tem
  OP online, com o prefixo `[Áreas]`. Não depende do bot.
- **Criativo/espectador:** continua sem disparar o aviso, mas agora mostra na barra de ação
  `Área vigiada ...: criativo/espectador não dispara o aviso.` — antes o teste em criativo parecia defeito.
- **Permissão de entrada em qualquer área (`/area entrada`):** a parede da barreira de casa, decidida
  por pessoa. `fechar` + `permitir <jogador>` faz lista de convidados; `negar <jogador>` barra alguém
  numa área aberta. `/area entrada <id>` mostra o estado e as listas. Criativo/espectador passam.
  Regra nativa nova `entrar`; exceção nova `negar:<regra>`, que nega qualquer regra a uma pessoa só.

# Bolso do Vazio vira um vazio de verdade - 18/10/2026

Magia 0.7.0. A passiva de Iris deixa de ser uma réplica do overworld.

- **Dimensão nova, `aurorion_magia:vazio`:** não gera nada. Cada Iris tem uma célula fixa, e na primeira
  entrada ganha **um bloco** no chão (`[vazio].blocoInicial`, padrão pedra). O que ela constrói fica.
- **Criativo para a dona** lá dentro. Na saída ela volta ao modo de antes.
- **Nada entra, nada sai:**
  - na porta ficam guardados o inventário (armadura e mão secundária inclusive) e os slots do Curios e do
    Accessories de todos que entram: Iris, marcados e invasores;
  - na saída, o que se pegou lá dentro some e o que era da pessoa volta;
  - se os slots extras não puderem ser lidos, a entrada é recusada, em vez de deixar algo passar.
- **Bloqueados lá dentro:** baú do ender, cama e âncora de renascimento.
- **Cair no vazio** devolve a pessoa ao bloco inicial.
- **Segurança:**
  - os pertences guardados são gravados na hora da entrada;
  - morte, logout e crash devolvem tudo;
  - quem estava no bolso antigo na troca recebe os pertences de volta no login.
- **Portais:** a viagem do ritual passa pela trava de dimensão sem precisar de `freeDimensions`. A staff
  em criativo que entrou pelo ritual não usa mais o atalho de staff para sair.
- A config `espelhaPosicao` saiu. A pasta `dimensions/aurorion_magia/bolso_do_vazio` do mundo fica sem uso.

# Verificação geral do repositório - 18/10/2026

Revisão de todos os módulos, com as chamadas de API conferidas nas fontes do NeoForge 21.1.248 e nos
jars do pack. Correções:

- **Economia 0.3.3:**
  - a bolsa inicial **somava errado**: ela substituía o saldo. O evento de personagem nomeado também
    dispara na renomeação pela staff, e quem tinha dinheiro voltava a 4 óbolos;
  - o canal ficou `optional()`, como o mod promete, e cobrar quem não tem o mod é recusado sem crash.
- **Essentials:**
  - `/deathhistory devolver` podia planejar dois itens no mesmo slot e apagar um;
  - os grupos do celular resolviam nick pela Mojang por HTTP na thread do servidor, e 16 nicks
    inventados travavam o tick. Agora só olham a memória;
  - `getName()` devolve uma cópia;
  - a faixa de versão do Voice Chat ganhou mínimo.
- **Limbo:**
  - um exilado com Vínculo na mão saía do Limbo com 0 vidas ao "resgatar" outro;
  - a passagem de resgate agora reconfere o alvo (se ele já saiu, a passagem fecha e a vida volta);
  - uma travessia recusada não se repete mais a cada tick;
  - o Limbo carrega depois do portais, para a liberação de dimensão funcionar sempre.
- **Magia:**
  - a chegada no Bolso do Vazio caía enterrada ou no ar, porque a altura era lida sem o chunk
    carregado;
  - Possessão, Olhar Anacrônico, borda do Bolso, Corrente, Transpositio e Reino das Almas agora
    teleportam sem o adiamento do Twilight Teleport;
  - entrar e sair do Bolso tem 3 s de intervalo.
- **Talk:**
  - mandar estilo para um cliente sem o mod derrubava a conexão no login (o canal é opcional);
  - 1 troca de estilo por segundo;
  - o reset de personagem avisa os outros clientes.
- **Areas:**
  - um `/reload` não manda mais avisos falsos de "saiu/entrou" para a staff;
  - pet ou invocação com dono conta como o dono na regra de PvP.
- **Ethereal:**
  - o reset de personagem passa pelo `HouseManager` (quadros, tab, Trama e site deixam de mostrar a
    casa antiga);
  - um texto de projetor que não lê não apaga mais o bloco;
  - o pedido do Protetor tem limite de frequência;
  - uma falha passageira da economia não esvazia os quadros até o restart.
- **Profissões:**
  - o invocador de chefe do Apotheosis é barrado também quando usado num bloco;
  - a regra padrão do Apotheosis lista as receitas de gema, soquete e sigilo uma por uma, e os
    upgrades de equipamento ficam com o ferreiro.
- **Mundos:** a trava de spawner deixa colocar bloco encostado nele.
- **Serviços:**
  - pedido aberto cancelado sem ninguém ter aceitado é apagado, e não guardado por um dia;
  - 20 s entre pedidos novos.
- **Intenção:** nome com teto de 48 caracteres e id de som com teto de 256 no tema.
- **Utils:**
  - o cache de camada sólida também é zerado no tick depois da recarga de modelos;
  - nome da `freeze_anchor` traduzido.
- **Faixas de core nos mods.toml:** economia, essentials, talk, diário e integração exigiam um core
  mínimo mais antigo do que as classes que usam.
- **Versões:** economia 0.3.3, integração 0.2.3 e diário 0.1.4, porque o conteúdo mudou e a versão
  anterior já estava no pack.
- **Tradução:** `pt_br` do celular (MikasRevs Phone), dentro do essentials.
- Código preparado sem build nesta máquina, conforme AGENTS.md.

# Copycats+ mais leve no cliente - 18/10/2026

- Utils 0.2.5 (cliente e servidor; precisa chegar aos clientes pelo AutoModpack):
  - fim do crash "building chunk meshes" do Copycats multiestado;
  - o cliente não reprocessa os vizinhos a cada copycat carregado;
  - `isBlockInSolidLayer` fica em cache por estado;
  - o Sodium testa primeiro a condição barata de face escondida.
  Só correções de baixo risco; o cache de quads ficou de fora. Detalhes em
  `aurorion-utils/CRASH-FIXES.md`.
- Dica para os jogadores: *Atualizações de chunk* do Sodium em "Em breve".
- Código preparado sem compilação ou teste nesta máquina, conforme AGENTS.md.

# Batalha de intenção no jogo - 18/10/2026

- Intenção 0.1.0 (novo mod, servidor e cliente): `/intencao iniciar|clima|encerrar|empate|listar|recuperar`.
  O mestre chama os participantes para o Plano da Intenção (`aurorion_intencao:plano_intencao`)
  enquanto a cena acontece na mesa do Discord:
  - Chamado com palpebras e nomes frente a frente; Eco em transe no lugar de cada um.
  - Arena por tema de datapack (`aurorion/intencao_tema/`), com o Tribunal de Vitrais procedural nas
    cores das Casas e fragmentos de memória clicáveis.
  - Veredito com aura para o vencedor e o perdedor de joelhos.
  - Sem efeito mecânico. Detalhes no README do `aurorion-intencao`.
  - Revisão:
    - A ida e a volta passam pela trava do `aurorion-portais` com autorização de um tick. Sem isso,
      ninguém entrava no plano, e quem entrasse não saía.
    - O veredito é por lado (time contra time).
    - As arenas ficam na menor célula livre, sem sair da borda do mundo.
    - A âncora fica no chão quando o chamado pega alguém no ar.
    - O fim é isolado por participante, então uma falha não prende os outros.
    - O som ambiente não é mais recriado a cada tick quando não toca.
    - As rachaduras do veredito passaram de ~3.500 para ~200 retângulos por quadro.
    - Os textos do tema ficam limitados a 1024 caracteres.
- Magia (revisão):
  - A roda de passivas desenha com culling desligado (antes ficava invisível).
  - O branco do Clarão cobre itens e tooltips de telas abertas.
  - Fechar a roda com `H` ainda apertado não dispara a passiva nem reabre a roda.
  - Toque rápido em `H` dentro do mesmo tick não se perde mais.
  - O leite não cura o Clarão.
- Magia 0.7.0: o Clarão passa a cegar como a flashbang do CS:
  - tela 100% branca por 5/4/3 s (olhando/de lado/de costas), sumindo devagar só no fim;
  - cobre também inventário e ESC abertos, e abafa o som;
  - raio de 24 + 4 por nível, e parede não protege (`[clarao].exigeVisao` religa a regra).
  Config existente mantém `raio = 10`: ajustar à mão.
- Magia 0.7.0 (protocolo 11, atualizar também no cliente): **roda de passivas**.
  - Segurar `H` abre a roda com as passivas de usar e de ligar; soltar escolhe.
  - A Presença Aterradora liga e desliga sem comando, pela roda ou pelo toque em `H` para quem não tem
    passiva de usar.
  - Quem tem mais de uma passiva de tecla escolhe pela roda.
- Essentials: `PhoneGroupPayloads` não compilava (`type(...)` resolvia para o `type()` do próprio record).
- Core 0.6.6: `compat/TwilightTeleportCompat` (antes copiado no Limbo e no Magia). Limbo e Magia
  passam a usar o do core e exigem `aurorion_core` 0.6.6 ou mais novo.
- Código preparado sem compilação ou teste nesta máquina, conforme AGENTS.md.

# Apotheosis, dificuldade por mundo e representantes - 18/10/2026

- Profissões 0.5.3: Apotheosis só para arcanista — mesas de reforja, salvamento, lapidação e aumento
  (tag `aurorion_profissoes:exclusivo/arcanista`) e as receitas de gema, soquete e sigilo da mesa de
  ferraria (`receitasExclusivas = ["apotheosis=arcanista"]`). Mecanismo reaproveitável: tag de bloco e de
  item `exclusivo/<profissao>` e regras de receita por mod, id de mod ou receita exata.
- Mundos 0.2.0: perfis de mundo por datapack (`aurorion/perfis_mundo/`) — vida, dano, armadura,
  velocidade, faro e XP dos monstros, teto de spawn e densidade de estruturas por dimensão;
  `/mundos dificuldade global|mundo|ver|aplicar`; perfis prontos `normal`, `dificil`, `brutal`,
  `pesadelo` e `exploracao` (nenhum ligado por padrão). Trava de spawners (`[spawners].travar`): sem
  ovo, runa, Carry On nem colocar spawner fora do criativo da staff.
- Magia 0.7.0 (protocolo 10, atualizar também no cliente): Clarão (*Fulgor Caecans*), Garra do Abismo
  (*Manus Abyssi*, a ultimate do Malzahar) e as passivas dos representantes — Salto Quântico (Robs),
  Olhar Anacrônico (Gretta), Lua Minguante (Iacy) e Bolso do Vazio (Iris, dimensão
  `aurorion_magia:bolso_do_vazio`) —, com a tecla de passiva (`H`).
- Código preparado sem compilação ou teste nesta máquina, conforme AGENTS.md.

# Áreas 0.6.5 - 18/10/2026

- `/area aviso <id> true|false`: avisa no Discord (via `aurorion-bot`, por RCON) quando alguém entra
  na área. O bot puxa a fila com `/area avisos puxar`; `/area avisos teste` confere a ponte.
  Nova opção `entryAlertCooldownSeconds` (padrão 60). Detalhes no README do `aurorion-areas`.
- Código preparado sem compilação ou teste nesta máquina, conforme AGENTS.md.

# Correções preparadas - 17 e 18/10/2026

- Magia 0.6.1: proteção da recarga do Barril Explosivo, exceções de cooldown
  limitadas à magia correta, transferência de vidas em Ceifar/Entregar e checagens
  de time que preservam autoria e efeitos de acerto.
- Vidas 0.2.5: transferência de uma vida com validação dos dois saldos antes da escrita.
- Utils 0.2.4: proteção do cooldown de stun do Simply More no cliente, incluindo
  dummies; mantém a proteção de uso da Mimi-Cry.
- Profissões 0.5.2: mesa de ferraria somente para ferreiros (staff criativa liberada) e
  revalidação da receita a cada retirada por shift-click, contra a duplicação ao aprimorar.
- Magia: chamadas restantes de `ToggleCooldown.skipNext` atualizadas para a nova assinatura.
- Essentials 0.8.0: grupos de conversa no app Mensagens do celular (servidor como fonte da
  verdade, entrega offline) e Gram com posts, perfil e avatar persistidos no servidor, fotos em
  disco deduplicadas e enviadas sob demanda; reflexão do histórico do celular em cache.
- Otimizações sem mudança de jogabilidade (Core 0.6.5, Personagem 0.3.2, Aeonita 0.3.3, Áreas 0.6.4,
  Ethereal 0.3.5, Limbo 0.4.7, Mundos 0.1.3, Serviços 0.1.3, Talk 0.1.3, Trama 0.1.2, Magia, Profissões).
  Detalhes em [docs/AUDITORIA-OTIMIZACAO-2026-10-18.md](docs/AUDITORIA-OTIMIZACAO-2026-10-18.md).
- Guias: [docs/GUIA-DESENVOLVIMENTO.md](docs/GUIA-DESENVOLVIMENTO.md) e [docs/GUIA-STAFF.md](docs/GUIA-STAFF.md).
- Código preparado sem compilação ou teste em jogo nesta máquina, conforme AGENTS.md.
  Roteiro de validação e instalação em [docs/CORRECOES-MAGIAS-SIMPLYMORE-2026-10-17.md](docs/CORRECOES-MAGIAS-SIMPLYMORE-2026-10-17.md).

# Release local - 08/10/2026

- Magia 0.6.0: Reino das Almas, Vazio Infinito e Santuário Malevolente (cortes somente
  em entidades), Colheita de Almas e Domínio Refinado; efeitos ampliados de Lux e
  Genki Dama, com esfera crescendo durante a carga de até 30 segundos.
- Inclui as correções de Umbra Viva e Barril Explosivo, dano/duração dobrados,
  controles até reconjurar, Ceifar Vida, Entregar Vida, Esconjurar e desbloqueio de
  todas as escolas pelo comando de staff.
- Profissões 0.5.1: correção dos marcadores de Body Damage e NBT após cura.
- Os 19 módulos foram buildados e reunidos em `build/jars-servidor`.
  Magia 0.6.0 precisa ser atualizada também nos clientes (protocolo 9).

Manifesto, hashes, validações e ensaios pendentes em [releases/2026-10-08](releases/2026-10-08/README.md).
Esta é uma entrega local; ainda não foi instalada no servidor ou no modpack.

# Comunicado da UF - 05/10/2026

## Novas magias

Vinte e cinco magias novas chegaram à Aurorion. Como todas as outras, nenhuma vem liberada: é preciso aprender com a staff.

- **Ultimates:** Centelha Final (*Lux Finalis*), Têmpera do Destino (*Temperies Fati*), Puxão Biônico (*Manus Rapax*), Campo Estático (*Campus Staticus*), Barril Explosivo (*Dolium Ardens*), Bomba Megainfernal (*Pyrobolus Infernalis*), Tempestade de Corvos (*Procella Corvorum*) e Colheita Farta (*Messis Uberrima*).
- **Bigorna Celeste** (*Incus Caelestis*): uma bigorna cai do céu onde o alvo está. Quem ficar parado morre; quem andar escapa.
- **Capricho** (*Mutatio Ferae*): transforma o alvo num guaxinim por alguns segundos.
- **Kit de sangue:** Transfusão (*Transfusio Sanguinis*), Poça de Sangue (*Lacus Sanguinis*), Maré de Sangue (*Aestus Sanguinis*) e Hemopraga (*Pestis Sanguinea*).
- **Kit de sombra:** Shuriken Laminado (*Stellae Laminatae*), Sombra Viva (*Umbra Viva*), Corte Sombrio (*Sectio Umbrae*) e Marca Fatal (*Signum Mortis*). As sombras também lançam e cortam junto com você.
- **Kit de arco** (só com arco ou besta na mão): Flecha de Reconhecimento (*Sagitta Exploratrix*), Flecha de Choque (*Sagitta Fulminis*) e Fúria do Caçador (*Furor Venatoris*).
- **Proibidas**, concedidas só pela staff:
  - Esfera Espiritual (*Sphaera Spiritus*): carregue uma esfera de energia e arremesse; a explosão abre cratera.
  - Possessão (*Possessio Corporis*): entre no corpo de outra pessoa, ande e fale por ela.
  - Justiça Demaciana (*Iustitia Demaciae*): só com espada na mão; executa quem está com pouca vida.
  - Morte Vinda das Profundezas (*Mors ex Profundis*): executa quem está com pouca vida e devolve a magia na hora.

## Mudanças nas magias

- As magias agora acertam qualquer pessoa, de qualquer time, mesmo com o fogo amigo do time desligado. Vale para as magias da Aurorion e do Iron's. Áreas sem PvP continuam protegendo.
- Devorar Luz e Voz Interdita (agachado) não poupam mais o seu time.

## Atualize o modpack

A versão nova da magia é obrigatória também no cliente: sem ela, não dá para entrar no servidor.

# Comunicado da UF - 03/10 e 04/10/2026

## NPCs de ofício (03/10)

- Os NPCs de ofício foram refeitos: enfermeira, ferreira, chef, arcanista e mercador agora têm aparência própria e uma loja nova, com abas, o saldo da carteira e o item de verdade em cada oferta.
- Os preços seguem a economia do Ato 2 e são pagos em óbolos. Esmeralda deixou de ser aceita como moeda.
- Novos serviços: cura e reparo conforme a gravidade do ferimento ou o desgaste do item, encantar item, concentrar e prolongar poções, e etiqueta com o nome que você escolher (com a ferreira).
- A arcanista agora vende livros encantados e poções fortes e longas, com estoque que renova todo dia.

## Celular (04/10)

- Os contatos e as conversas do Mensagens e dos DMs do Gram agora ficam salvos, mesmo depois de sair, reiniciar o jogo ou trocar de personagem. Cada personagem tem o próprio celular.
- O Gram e o MattuTweet passam a mostrar sempre o nome do personagem em posts, tweets, citações e comentários, inclusive de quem está offline.
- Fotos da galeria:
  - No Mensagens, "Phot" e "Loc" viraram ícones, e o novo clipe envia uma foto da galeria na conversa.
  - No Gram, o "+" de novo post agora pergunta se você quer usar a câmera ou uma foto da galeria.
- Agora dá para trazer imagens do PC para a galeria do celular de três jeitos:
  - pelo botão com seta no topo da galeria;
  - arrastando a imagem para a janela do jogo com a galeria aberta;
  - pelo comando `/celular importar <caminho>`.

  Valem PNG e JPG de até 10 MB.

## Modo streamer (04/10)

- Novo modo streamer para quem faz live com OP. O chat deixa de mostrar avisos de comandos, teleporte, morte, troca de modo de jogo, entrada/saída e conquistas, mas continua mostrando o chat e o RP dos jogadores.
- `/streamer on` liga o modo, `/streamer off` desliga e `/streamer status` mostra o modo atual. `/streamer total` esconde também os demais avisos do sistema.
- Cada jogador controla o próprio modo, e a escolha fica salva no jogo.

## Correções (04/10)

- Corrigida a queda do servidor causada pelas samambaias Sun Fern e Ice Fern quando uma erva daninha do Crop Critters tomava o lugar delas durante o crescimento.
- Fotos enviadas pelo Mensagens agora aparecem para quem recebe, em vez de "Photo could not be loaded". A mesma correção vale para fotos nos DMs do Gram e para avatar e capa do MattuTweet. Fotos que já chegaram também voltam a abrir.

## Novos mods

- Waystones
- Kaleidoscope Tavern
- Productive Bees
- Mystical Agriculture

## Mods Bloqueados

- Waystones (100%)
- Productive Bees (Liberado perante Ticket/Lore)
- Mystical Agriculture (Liberado perante Ticket/Lore)

# Comunicado da UF - 24/09/2026

- Seis novas magias de agua e vento e as passivas Mao que Cura e Presenca Aterradora chegaram a Aurorion.
- Medicos agora podem curar outro personagem pelo menu Shift+G, restaurando vidas, saude e ferimentos. A Mao que Cura recupera meio coracao por golpe, mantendo a reacao ao impacto.
- A Presenca Aterradora ficou do tamanho do nome: dentro do raio o mundo apaga, a nevoa preta fecha o ar e todos ao redor se prostram no chao. As maos ficam livres: da para comer, erguer escudo e conjurar. As criaturas continuam perdendo o alvo e fugindo.
- A neblina e os efeitos visuais foram ajustados para funcionar com shaders, inclusive nas areas narrativas.
- O Oraculo agora mostra o nome do personagem; o Painel Aeonico destaca quem tem menos vidas restantes no lugar do placar de mortes.
