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
