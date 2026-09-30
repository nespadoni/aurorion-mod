# Aurorion Serviços

O app **Serviços** dentro do celular (MikasRevs/Mattupolis Phone): quem trabalha anuncia e liga o
"Trabalhando", quem precisa encontra um profissional ou faz um pedido para a área inteira, e quem
emprega publica vaga. Tudo termina numa **conversa no próprio celular** — o app aproxima as pessoas;
preço, combinação e pagamento são RP (e PIX) na conversa.

Depende do `aurorion-core` e do `aurorion-profissoes` (que traz a `aurorion-economia`). O telefone é
opcional: sem ele o mod carrega, guarda os dados e não mostra nada.

## Onde aparece

- **Ícone na tela inicial** do celular (maleta laranja), com o número de pedidos esperando resposta.
  O telefone coloca sozinho um app novo no primeiro espaço livre; dá para mover e esconder como os
  outros. Também aparece na busca da tela inicial e na **Biblioteca de Apps** (categoria "Aurorion").
- **Notificações do celular** (tela de bloqueio, painel, ilha dinâmica) com som, e um aviso na tela
  — só para quem está com o celular no inventário. Tocar na notificação abre os pedidos recebidos.
- O app usa a moldura, a capinha, o tema claro/escuro e o brilho do celular, na mesma escala 3.

## As abas

| Aba | O que tem |
|---|---|
| **Buscar** | Anúncios por área (todas as profissões do `aurorion-profissoes` + "Outros"). Quem está **trabalhando** vem primeiro (bolinha verde), depois quem está online (amarela). Quem tem a profissão registrada pela staff naquela área ganha **✔** e sobe na lista. Tocar abre o anúncio: **Pedir serviço** (profissional online) ou **Conversar**. Embaixo, "Não achou? Pedido aberto". |
| **Pedidos** | *Meus pedidos* e *Recebidos*. O profissional aceita ou recusa; qualquer dos dois conclui ou desiste depois de aceito. **Aceitar leva direto para a conversa** com o cliente. |
| **Vagas** | Vagas de trabalho por área. **Candidatar-se** avisa quem contrata e abre a conversa com ele. |
| **Perfil** | Meus anúncios (criar, editar, remover) e minhas vagas com os candidatos, cada um com "Conversar". |

O botão **Trabalhando / Parado** fica no topo para quem tem anúncio.

## Os fluxos

- **Pedido direto** (a partir de um anúncio): o profissional precisa estar online. Ele recebe a
  notificação; quem pediu cai na conversa com ele, com a mensagem já escrita.
- **Pedido aberto** (para a área inteira): todos os que estão **trabalhando** com anúncio naquela área
  recebem a notificação. O primeiro que aceitar leva — a thread do servidor é uma só, então "o
  primeiro" é exato — e cai na conversa com o cliente, que é avisado de quem aceitou.
- **Entrou no servidor**: quem tem pedido direto esperando é avisado; quem anuncia numa área com
  pedidos abertos é lembrado de ligar o "Trabalhando".

## Regras

| | |
|---|---|
| Anúncios por personagem | 3 |
| Pedidos aguardando por personagem | 3 |
| Vagas por personagem | 3 (até 50 candidatos cada) |
| Título / descrição / preço / texto do pedido | 40 / 160 / 24 / 120 caracteres (sem código de cor) |
| Pedido aberto sem ninguém aceitar | expira em 2 h |
| Pedido aceito e nunca concluído | vira concluído em 24 h |
| Pedido finalizado (concluído, cancelado, recusado, expirado) | some em 24 h |
| Vaga | fica aberta 14 dias |
| Anúncio de quem não entra | sai depois de 30 dias |
| "Trabalhando" | só em memória; cai no logout |
| Staff (permissão 2) | remove qualquer anúncio ou vaga pelo próprio app |

**Por personagem.** Tudo é da conta do personagem — o alt é outra conta, então tem os próprios
anúncios. Na morte definitiva (`CharacterResetEvent`) sai tudo o que era do morto, inclusive as
candidaturas dele em vagas dos outros.

**Nomes.** Todo nome no app é o do personagem (o exibido de quem está online; o gravado no
`CharacterData` de quem está offline). O nick da conta só viaja para o celular abrir a conversa,
porque o telefone roteia mensagem pelo nick.

## Arquitetura

- `data/` — `Anuncio`, `Pedido`, `Vaga`, `Categorias` e `ServicosRules` (regras puras, testadas sem
  o jogo); `ServicosData` é o `SavedData` (`data/aurorion_servicos.dat`).
- `server/ServicosManager` — valida cada ação e responde com a aba inteira, já com os botões que
  aquela pessoa pode usar em cada linha. O cliente não decide nada.
- `network/` — `ServicosQueryPayload` e `ServicosActionPayload` (cliente → servidor),
  `ServicosPagePayload` e `ServicosNotifyPayload` (servidor → cliente). Canal opcional.
- `client/ServicosScreen` — a tela; `client/PhoneBridge` — tudo o que vem do celular, por reflexão
  (o jar do telefone não entra no classpath, igual ao essentials e à economia).
- `mixin/` (config `aurorion_servicos.phone.mixins.json`, não obrigatório, `require = 0`) — o app no
  catálogo e na biblioteca, abrir pelo ícone, escala 3 e as notificações.

O ícone é gerado por `tools/gerar-icone-servicos.py` (sem dependência).

## Validar no build

- `./gradlew :aurorion-servicos:test` — `ServicosRulesTest`; com `AURORION_PHONE_JAR` apontando para
  `mod-servidor-referencia/mattupolisphone112.jar`, também o `PhoneAppContractTest`, que confere no jar
  cada método e construtor que os mixins e a ponte usam.
- Dois clientes (`aurorion-runs`: `runClient` e `runClient2`), os dois com celular:
  - [ ] o ícone aparece na tela inicial e na biblioteca; o app abre na escala do celular e "<" volta;
  - [ ] anunciar, ligar "Trabalhando"; o outro vê a bolinha verde e o ✔ (com `/profissao definir`);
  - [ ] pedido direto: o profissional recebe a notificação; quem pediu cai na conversa com o texto;
  - [ ] pedido aberto: só quem está trabalhando na área é avisado; o segundo a aceitar recebe
        "Alguém já atendeu esse pedido."; o primeiro cai na conversa;
  - [ ] tocar na notificação abre "Pedidos → Recebidos";
  - [ ] vaga: candidatar-se avisa o dono e abre a conversa; o dono vê o candidato no Perfil;
  - [ ] deslogar derruba o "Trabalhando"; reset de personagem apaga anúncios, pedidos e vagas;
  - [ ] sem o celular no inventário, nada de notificação.
