# Telefone e modo streamer — 04/10/2026

Mudanças no código do **Aurorion Essentials 0.7.4**, para integrar o **MikasRevs Phone
1.3.4-neoforge-1.21.1-compat** (`mattupolisphone112.jar`). O telefone original permanece intacto.

## Contatos e conversas

O telefone original grava somente os contatos do aplicativo Mensagens, sem seu histórico. Os DMs do
Gram também ficam apenas em memória. A integração passa a salvar e restaurar os dois históricos,
incluindo o estado de leitura e as referências às fotos. Os dados ficam no computador de quem joga,
na pasta `mattupolis_phone/personagens/<UUID>/`, junto dos dados do celular daquele personagem.

A agenda nativa continua sendo usada. A migração dos dados antigos também reconhece a conta
principal em servidor offline-mode; o UUID desse servidor pode diferir do UUID da conta Mojang.
Entrar e sair, reiniciar o Minecraft e trocar de personagem preservam cada celular separadamente.
O reset definitivo de personagem continua limpando o celular antigo. Arquivo de histórico inválido
é preservado, com aviso no log, em vez de ser substituído por um histórico vazio.

A gravação do histórico usa uma fila fora da thread do jogo, consolidada a cada 20 ticks. Quando
há duas agendas da conta principal, contatos antigos são acrescentados sem sobrescrever os novos,
desde que não exista registro de reset nem histórico já criado; nos demais conflitos, os arquivos
originais ficam preservados para recuperação, sem importação automática.

Esta persistência é local: não transfere as conversas automaticamente para outro computador.
Conversas já perdidas antes da atualização não podem ser reconstruídas pelo telefone.

## Fakename no Gram e no MattuTweet

O diretório de fakenames recebido no login agora é vinculado à conexão antes do primeiro desenho;
ele não é mais apagado quando a primeira tela do celular monta seu índice. Isso corrige a resolução
de autores offline. Comentários do Gram/MattuTweet e autores de tweets, inclusive citações, recebem
o fakename na renderização, assim como suas iniciais e os nomes já tratados pelas telas do telefone.

O nome exibido é resolvido pela conta original do autor, inclusive quando o tweet guardou um nome
antigo. O nome real continua sendo a chave interna para abrir perfis, seguir, excluir posts e enviar
mensagens. É necessário ter o Essentials atualizado também no servidor para receber os nomes
offline. Sem um fakename cadastrado ou sem informação desse jogador, permanece o nome original.

## Fotos da galeria e importação do PC (0.7.4)

**Mensagens.** Os botões “Phot” e “Loc” (texto cortado) viraram ícones no mesmo espaço, antes do
campo de texto: **clipe**, **câmera** e **localização**. O clipe abre a galeria do personagem; ao
tocar numa foto, ela é enviada pelo mesmo caminho da câmera do Mensagens (`sendMessagePhoto` do
telefone): entra na conversa e, com o contato online, segue pelo pacote de foto do próprio telefone.
O seletor é o do DM do Gram, reaproveitado; o voltar retorna à conversa. Em modo avião, avisa e não
abre.

**Gram.** O “+” de novo post pergunta **Câmera** ou **Galeria**. A galeria leva à mesma tela de
legenda que a câmera abre (`PhoneInstagramCaptionScreen`), com os mesmos limites de post e cooldown;
a foto da galeria não é apagada nem movida. Stories, foto de perfil do Gram, avatar/banner do
MattuTweet e Marketplace já tinham seletor de galeria no telefone original. O MattuTweet não tem
tweet com imagem (o pacote de postagem leva só id e texto).

**Importar do PC.** Na galeria, o botão com seta no topo à direita abre a janela de escolher arquivo
do Windows (TinyFileDialogs, que vem com o Minecraft) fora da thread do jogo. Também dá para
**arrastar** PNG/JPG do Windows para a janela do jogo com a galeria aberta; vários arquivos entram em
fila. O comando continua existindo:

```text
/celular importar C:\Users\Neto Spadoni\Pictures\foto.jpg
```

PNG e JPG, até 10 MB e 16 megapixels; a imagem é convertida para PNG, reduzida para no máximo 2048
px no maior lado e copiada para a galeria do personagem, sem alterar o original. Os avisos aparecem no
balão do próprio celular. Em tela cheia, a janela do Windows pode abrir atrás do jogo.

## Modo streamer

Detalhes do filtro estão também em [Modo streamer](../aurorion-essentials/STREAMER-MODE.md).

| Comando local | Comportamento |
|---|---|
| `/streamer on` ou `/streamer ativar` | Oculta avisos de comandos, teleporte, mudança de modo de jogo, morte, entrada/saída, conquistas e avisos administrativos identificados do Aurorion. |
| `/streamer off` ou `/streamer desativar` | Desativa o filtro. |
| `/streamer status` ou `/streamer` | Mostra o modo atual. |
| `/streamer total` | Oculta também outros avisos enviados como SYSTEM; falas de NPC/plugins que sejam texto literal também podem desaparecer. |

O modo padrão ao instalar é desativado. A escolha é salva no cliente em
`config/aurorion/essentials-streamer-client.toml` e continua após reiniciar o jogo. Cada pessoa
controla o próprio filtro, sem depender de permissão de OP.

O filtro atua em mensagens novas recebidas pelo chat, antes de exibição e narração. Chat dos
jogadores, `/me`, `/say`, mensagens de time e sussurros com os envelopes vanilla permanecem
visíveis. O modo `on` preserva também texto literal de RP/NPC. O modo `total` serve para mods que
enviam logs sem identificação; use-o sabendo que esses mods podem usar a mesma rota para falas.
Mensagens que um mod inserir diretamente no HUD, sem passar por ChatListener, ficam fora do filtro.

Mensagens antigas já no histórico podem ser limpas com **F3+D** antes de iniciar a live. O filtro
não muda OP, execução dos comandos, gamerules, logs do servidor, tela de morte, actionbar ou toasts.
O feedback dos comandos locais de streamer e importação permanece visível.

## Compilação e instalação

Compilado em 04/10 a pedido, com Java 21 e o telefone real habilitado nos testes de contrato:
157 testes, todos aprovados, nenhum ignorado. O JAR `aurorion_essentials-neoforge-1.21.1-0.7.4.jar`
está em `build/jars-servidor`; o registro da entrega está em
[releases/2026-10-04](../releases/2026-10-04/README.md). Falta instalar e validar no jogo.

O JAR de referência do telefone e o instalado na instância indicada têm o mesmo SHA-256:
`2657b87eacb1f6aba8fa53b4bd587ec4515c9c464679ec82b486d18e9516827e`.
Na pasta sincronizada informada foram encontrados Essentials **0.4.0** e Core **0.4.0**, mais antigos
que estes fontes. O Essentials atual exige Core **0.6.1 ou posterior**; o Core deste repositório é
**0.6.4**. Não basta substituir somente o Essentials mantendo esse Core 0.4.0.

Na máquina de compilação, com Java 21 e os requisitos do projeto:

```powershell
$env:AURORION_PHONE_JAR = (Resolve-Path 'mod-servidor-referencia/mattupolisphone112.jar').Path
./gradlew :aurorion-core:build :aurorion-essentials:build
```

O ambiente `AURORION_PHONE_JAR` habilita os testes de contrato contra os métodos e campos do telefone
real. Validar também o carregamento dos mixins no cliente e em servidor dedicado, inclusive sem o
telefone instalado. A integração de fakenames offline deve ser atualizada no servidor; histórico,
importação e streamer executam no cliente. Instalar as versões compatíveis do Core e Essentials no
cliente/servidor e publicar os arquivos pela origem do AutoModpack para que não sejam revertidos
na próxima sincronização. Preservar os arquivos do celular antes do teste de reset definitivo.

## Validação em jogo

1. Salvar contato, enviar/receber texto e foto em Mensagens e no DM do Gram; relogar e depois
   reiniciar o Minecraft imediatamente após receber uma mensagem. Confirmar histórico, contato,
   fotos e estado de leitura sem reenvios; verificar desempenho com um histórico longo.
2. Entrar com outro personagem e voltar ao primeiro. Confirmar que cada um vê seu próprio celular;
   testar a migração da conta principal em offline-mode e o reset definitivo em um personagem de teste.
3. Cadastrar/trocar fakename, publicar tweet/comentário e abrir Gram/MattuTweet de outro jogador.
   Conferir autores, comentários, iniciais, citações e perfis; repetir com o autor offline e relogar.
   Curtir, seguir, abrir perfil, excluir conteúdo próprio e enviar mensagens devem continuar funcionando.
4. Importar PNG transparente e JPG pelo botão da galeria, arrastando para a janela e pelo comando,
   com caminho com espaços. No Mensagens, enviar foto pelo clipe (contato online e offline) e usar
   câmera/localização pelos ícones; no Gram, “+” → Galeria → legenda → postar. Confirmar a galeria e o envio normal
   da foto. Testar arquivo inválido, maior que o limite e desconexão durante a importação.
5. Como OP, ativar `/streamer on` e executar `/tp`, `/gamemode creative`, `/gamemode survival`, um
   comando inválido e uma morte. Confirmar ausência desses avisos e presença de chat/RP; repetir
   usando `/me`, `/say` e sussurros. Os outros jogadores e o log do servidor não devem mudar.
6. Conferir `/streamer total`, status e desativação; reiniciar para verificar a preferência salva.
   Confirmar que a tela de morte, actionbar e feedback dos comandos locais permanecem disponíveis.
