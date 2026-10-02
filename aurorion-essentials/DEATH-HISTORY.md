# Histórico de mortes

Snapshot em `LivingDeathEvent`, prioridade HIGHEST, antes dos drops e do reset do personagem.
A gravação só é enfileirada no fim do tick, depois do cancelamento final do evento.
Morte cancelada por PlayerRevive ou outro mod não produz histórico nem aviso administrativo.
Mods que alterem o inventário antes desse listener precisam ser verificados no pack real.

## Conteúdo

UUID, nome real, causa, horário UTC, dimensão, posição, orientação, inventário, armadura,
offhand, ender chest, cursor, Curios normais/cosméticos, Accessories normais/cosméticos, efeitos, XP,
fome e NBT completo serializado pelo jogador. O NBT bruto e a serialização própria do Curios ficam arquivados.
Dados em bancos externos, arquivos próprios ou somente em memória não fazem parte desse NBT.

O pack tem **dois** sistemas de acessório que não se falam: Curios (Relics, Malum, Iron's
Spellbooks, Cataclysm…) e Accessories (Artifacts, Simple Hats). Os dois são capturados, por reflexão,
sem tornar nenhum obrigatório. Falha só no Accessories marca o registro com
`[Accessories incompleto]` e não invalida o resto.

`restore ... confirm` substitui os slots presentes no snapshot e os efeitos, XP e fome.
Slots Curios e Accessories precisam continuar disponíveis; item/mod removido ou mudança de DataVersion
recusa a operação. O cursor salvo vai para um slot vazio do inventário salvo; sem espaço,
use recuperação individual. O destinatário precisa estar vivo, conectado e com menus fechados.
`give ... confirm` recupera uma pilha num slot livre, sem sobrescrever nada. Curios recuperado
individualmente vai para a mochila. Os índices aparecem no chat ao consultar o inventário.

Nunca se chama `Player.load`: não se revertem UUID, vidas Aurorion, personagem, missões,
posição, saúde zero da morte ou anexos arbitrários. Magias ligadas a outra entidade podem
exigir tratamento pelo mod de origem, mesmo com o efeito registrado. O histórico permanece
após a troca de personagem.

## Espólio, Relicário e itens mantidos na morte

O id do registro vem do `DeathId` do `aurorion-core` e é o mesmo que o `aurorion_limbo` grava nos
drops daquela morte. Assim o id de `/deathhistory view` é o mesmo que aparece em `morte=` na
auditoria do Relicário e do Fio da Volta.

- **Espólio que já voltou.** Quando o Relicário chama itens de volta, o core registra isso
  (`DeathClaims`). A lista marca a linha com `[espolio ja voltou: N pilha(s)]` e o `view` mostra o
  aviso. `restore` e `give` recusam, e só seguem com `confirm duplicar`, porque restaurar uma cópia
  por cima dos itens que já voltaram os duplicaria.
- **Itens mantidos na morte** (tag `aurorion_core:kept_on_death`, ex.: Fio da Volta e Relicário)
  ficaram com o jogador e nunca são restaurados. O `restore` completo pula esses itens e devolve os
  que o destinatário tem agora; o `give` recusa. O ender chest não conta: não cai na morte.
- **Personagem.** Cada snapshot grava `Character` e `CharacterName` (quando o personagem já tem
  nome), mais `FakeName`/`FakeNamePlain`: o nome pelo qual as pessoas chamavam quem morreu, gravado
  na hora. A lista mostra esse nome, o que separa as mortes quando a mesma conta tem vários
  personagens — e é por ele que se procura. Snapshots antigos, sem esses campos, continuam legíveis e
  caem no nick da conta.

## Comandos (OP 2+)

```text
/deathhistory <pessoa> [pagina]
/deathhistory view <pessoa> <n>
/deathhistory tp   <pessoa> <n>
/deathhistory view <id> [aba]
/deathhistory tp <id>
/deathhistory devolver <pessoa> <n> confirm [duplicar]
/deathhistory devolver <id> confirm [duplicar]
/deathhistory pegar <pessoa> <n> confirm [duplicar]
/deathhistory pegar <id> confirm [duplicar]
/deathhistory give <id> <indice> <destinatario> confirm [duplicar]
/deathhistory restore <id> <destinatario> confirm [duplicar]
```

### A tela da morte (`view`)

Um baú de seis linhas montado só no servidor (o cliente vê um baú comum). A última linha tem as abas
**Inventário**, **Curios e acessórios** e **Ender chest**, um papel com a causa, data, local, XP,
efeitos e o estado das recuperações, e as setas de página.

- **Inventário:** cada item onde estava — armadura e mão secundária na primeira linha (o cursor no
  fim dela), o inventário no meio, a hotbar embaixo.
- **Curios e acessórios:** só os slots ocupados do Curios e do Accessories (normais e cosméticos) e
  slots extras de inventário de outros mods, paginados.
- **Ender chest:** os 27 slots.

O tooltip de cada item mostra o índice do `give` (`#12 · curios/ring/0`) e se ele é mantido na morte
ou já foi recuperado. `view <id> [aba]` abre direto na aba 1, 2 ou 3.

**Somente consulta, exceto no criativo.** Com a staff no modo criativo, clicar num item **pega o
item de verdade**, como num baú: clique comum (cursor), shift-clique (inventário), tecla numérica
(hotbar). Cada item sai uma vez só e some da tela, como num baú: a retirada grava o mesmo recibo do
`give`, vai para o log e trava
`devolver`/`pegar`/`give` daquele item. Item mantido na morte, já recuperado, ou de morte cujo espólio
já voltou pelo Relicário não sai pela tela. Fora do criativo, todo clique na tela é recusado e o que
já saiu continua visível, marcado "Já recuperado".

### Devolver e pegar

- **`devolver`** entrega ao **dono da morte** (precisa estar online) o que caiu: inventário,
  armadura, mão secundária, cursor, Curios e Accessories. Cada item volta ao **slot de origem** quando
  ele está vazio — armadura no corpo, anel no Curios, chapéu no Accessories —, senão ao primeiro slot
  livre do inventário. A pessoa recebe um aviso no chat.
- **`pegar`** faz o mesmo para o **seu** inventário (para entregar em mãos, ou quando o dono está
  offline). Tudo vai para slots livres; nada é equipado.

Os dois **nunca sobrescrevem nada** — só ocupam slot vazio —, e ficam de fora: o ender chest (não cai
na morte), itens mantidos na morte, itens já recuperados por qualquer caminho e, no `devolver`, o item
**idêntico** que já está no mesmo slot (keepInventory, item que não cai, ou a pessoa já catou e
guardou no mesmo lugar). O que não couber fica sem recibo: libere espaço e repita o comando, e só o
que faltou é entregue. O resumo no chat diz quantos foram, quantos já estavam com a pessoa, quantos já
tinham saído e o nome dos que não couberam, e avisa se o personagem atual da conta não é o que morreu.

O `view` imprime no chat os botões **[Devolver ao dono]** e **[Pegar para mim]**: eles só
**preenchem** o comando (com `confirm`, ou `confirm duplicar` quando o Relicário já devolveu o
espólio); o Enter é da staff.

Mesmo fluxo do `restore`: backup do destinatário, reserva dos recibos na fila de IO, conferência de
que o inventário não mudou, entrega, save imediato. Falha no meio desfaz (esvazia de novo os slots
ocupados).

### Achar a pessoa sem saber a UUID

Ninguém decora UUID, e num servidor de RP a staff quase nunca sabe o nick da Mojang de quem morreu:
sabe o **nome do personagem**, que é o que aparece no chat, na tab e sobre a cabeça. Por isso
`<pessoa>` aceita, nesta ordem de esforço:

1. a UUID da conta, se alguém tiver;
2. o nome de personagem de quem está online (`FakeNameRegistry`);
3. o nick da Mojang de quem está online;
4. o nome de personagem de quem está **offline** (`FakeNameData`, que fica em disco);
5. o nick da Mojang de quem está offline (cache de perfis do vanilla);
6. **um nome usado em alguma morte salva** (índice do histórico) — pega até quem já trocou de nome
   depois de morrer.

Os passos 1 a 5 são síncronos. Só o 6 lê disco, e vai junto com a leitura do histórico, na mesma ida
à fila de IO.

A comparação é a que uma pessoa faria de cabeça: **sem as cores** (o nome fica em disco com os
códigos `&`), sem diferença de maiúscula e sem espaço sobrando. Nome com espaço vai entre aspas, e o
Tab já sugere com elas:

```text
/deathhistory "Bella Noob"
/deathhistory view "Bella Noob" 1
```

O `<n>` é o número que a própria lista mostra no começo de cada linha — `#1` é a morte mais recente.
É o que permite trabalhar pelo console, sem UUID nenhuma. `give` e `restore` continuam exigindo o
`<id>`: são destrutivos, e ter de copiar o id do registro é uma trava a favor.

Cada linha da lista mostra o nome que a pessoa usava **na hora da morte**, e não o de hoje: trocar de
nome amanhã não reescreve o que aconteceu ontem. A lista também imprime a conta resolvida, para o caso
raro de dois personagens terem usado o mesmo nome em épocas diferentes.

`duplicar` só é aceito (e só é exigido) quando o espólio daquela morte já voltou pelo Relicário.

Histórico inclui jogadores offline conhecidos pelo servidor. A tela usa baú vanilla com todos
os movimentos recusados no servidor: shift, teclas numéricas, arraste, drop e clone. Páginas têm
54 slots; espaços vazios preservam o índice. Navegação e identificação dos slots ficam no chat.
A consulta e cada callback assíncrono verificam novamente OP 2 e conexão.
Teleporte procura chão seguro na coluna da morte; em espectador visita o ponto exato.

## Armazenamento e falhas

Em `<mundo>/aurorion/death-history/`:

- `records/<id>.nbt`: snapshots comprimidos.
- `players/<UUID>.nbt`: índice compacto por jogador, mais recente primeiro.
- `names.nbt`: nome usado numa morte → conta. É o que faz `/deathhistory "Bella Noob"` funcionar
  meses depois, com a pessoa offline. Cada morte grava aqui o nome do personagem, o nome falso e o
  nick da conta. Nome repetido aponta para a conta da morte mais recente; teto de 4096 nomes, podando
  os mais antigos (o `/fakename` é livre, então sem teto o índice cresceria para sempre).
- `backups/<id>.nbt`: estado anterior à recuperação; aceita `view` e `restore` pelo ID.
- `restores/<id>.nbt`: um recibo por item (`item_<n>`) ou pela restauração completa (`full`):
  administrador, destinatário, tipo (`give`, `devolver`, `pegar`, `criativo`, `restore`), backup,
  data e estado. Qualquer caminho que entregue o item `n` grava `item_<n>` e trava os outros.

Config `config/aurorion/essentials-death-history-server.toml`: `enabled=true`, `deathsPerPlayer=100`
(10–1000). Retenção remove somente mortes antigas daquele jogador. Backups e recibos são
conservados para auditoria e devem entrar na rotina de backup/arquivo do servidor.
Cada leitura NBT tem limite descomprimido de 32 MiB. A captura usa estimativa máxima de 16 MiB por snapshot e 64 MiB pendentes; a fila de escrita limita snapshots a 64 MiB. Se a captura exceder o orçamento, conserva somente causa/localização e marca o registro como parcial. Falha de serialização também fica explícita; nunca se anuncia um inventário completo nesse caso.

Objetos vivos são serializados no evento, na thread do servidor. Compressão, escrita, listagem
e leitura usam uma thread dedicada, fila de 128 trabalhos. Não se varrem jogadores/inventários
em ticks comuns. Escrita usa temporário, sync e rename atômico (exigido do filesystem).
Na parada, a fila tem até 30 s para drenar; falhas são registradas no log. Um encerramento
abrupto pode perder snapshot ainda não publicado. O botão de histórico só aparece após gravar.

Backup e reserva persistente precedem a recuperação. Antes de alterar, reconfirmam-se conexão,
permissão e inventário/XP. Repetição é recusada. Recuperação completa bloqueia parciais; uma
parcial impede completa posterior, mas permite outros índices. Não se recolhem drops nem se
detecta se itens já foram encontrados: a staff decide se a restituição é devida.

`RESERVED` e `FAILED` permanecem bloqueados para impedir duplicação após falha/crash. `ABORTED`
(nada foi entregue: o inventário mudou durante a reserva, a pessoa caiu, quem pediu perdeu o OP)
**apaga o recibo** e libera o item para outra tentativa. Consulte log, backup e destinatário antes
de intervenção manual no recibo; não há retry automático.

Na thread do servidor há ainda uma trava em memória (`RecoveryLedger`): a retirada no criativo
acontece no clique, sem esperar disco, e um `devolver` com a reserva ainda na fila não pode entregar o
mesmo item. Todo caminho marca ali antes de ir ao disco e confere ali antes de entregar.
`APPLIED` significa concluída. O save vanilla do destinatário é solicitado após aplicar,
e o journal é finalizado depois. Esses arquivos não formam uma transação atômica única;
a reserva é conservadora diante de interrupções.

## Rede de segurança: morte que trava o servidor

`ServerPlayer.die` começa disparando o `LivingDeathEvent`. Se um listener lança exceção ali, ela sobe
pelo `die`, pelo tick do servidor e derruba o loop inteiro — e o jogador fica **no meio da própria
morte**: vida zero, `dead = false`, sem tela de morte, sem respawn, sem drop. Foi o crash de
23/09/2026: o `jonesbounty` (`OnplayerkillProcedure`, `ArrayList.remove(-1)`) estourou numa morte
disparada pelo PlayerRevive, e o servidor caiu com gente presa em zero de vida.

O `DeathListenerGuardMixin` envolve **um** ponto — `CommonHooks.onLivingDeath`, onde o evento é
disparado — num `try/catch`. O listener quebrado perde o turno dele e o resto da morte acontece
normalmente: mensagem, drop, contagem de vida no `aurorion-vidas`, respawn. O stack trace inteiro vai
para o log em `ERROR`, com o nome do mod culpado.

Engolir exceção não é o certo; o certo é o mod culpado não lançar. Mas entre "um mod de recompensa
perde um registro" e "o servidor de oitenta pessoas cai e alguém fica morto-vivo", a escolha é óbvia —
e o log existe para que a primeira metade não seja esquecida. **A correção de verdade continua sendo
remover ou atualizar o `jonesbounty`.**

O mixin é `require = 0` e mora num config próprio (`aurorion_essentials.deathguard.mixins.json`,
`required: false`): se uma versão futura do NeoForge mudar a linha, o jogo sobe sem a rede em vez de
não subir. Quem avisa que ela sumiu é o `DeathGuardTargetTest`, que confere o alvo no bytecode.

## Validar na outra máquina

Não executar Gradle nesta máquina de edição. Esta implementação ainda não foi compilada.

- Compilar Essentials; executar `DeathHistoryStoreTest`, `FakeNameLookupTest`,
  `PrivacyMixinTargetTest` e `DeathGuardTargetTest`.
- Busca por nome: `/deathhistory "Bella Noob"` com a pessoa online, offline, depois de ela trocar de
  nome, e com o nome em caixa diferente. Conferir o Tab (nomes com espaço vêm entre aspas), o nick da
  conta, a UUID, e a mensagem de "não achei ninguém" para um nome inventado.
- `view`/`tp` pelo número da linha, do console e do chat; número maior que o total recusado;
  `view <id>` e `view <pessoa> <n>` continuam funcionando lado a lado.
- Botão `[Historico]` do aviso de morte abre a lista da pessoa certa.
- Morte normal, PvP, modded, vazio, keepInventory, desaparecimento, equipamentos, mochilas e cursor.
- Curios normal/cosmético, slots expandidos e itens que adicionam slots; sem Curios/API incompatível.
- PlayerRevive/totem/cancelamento não grava; morte após sangramento grava uma vez.
- Última vida/Limbo/reset: captura anterior ao reset e histórico conservado.
- OP/não-OP, gamerule false e todas as visibilidades de time: ADMINS gera uma linha privada;
  NOBODY nenhuma; EVERYONE conserva regra vanilla. Tela de morte continua informando a causa.
- Offline, paginação, reinício, retenção e consulta/restauração de backup por ID.
- Tentar todas as retiradas na tela e revogar OP enquanto aberta.
- Restaurar ao dono e outro destinatário; conferir componentes, efeitos, XP, Curios, Accessories
  (Artifacts, Simple Hats) e ender chest.
- Tela: as três abas, setas de página com muitos Curios, tooltip com `#n`, papel de informações.
  Fora do criativo nenhum clique tira item (inclusive shift, tecla numérica, arrastar, duplo clique,
  Q, clique do meio). No criativo: clique, shift e tecla numérica pegam o item **sem** o texto do
  tooltip; o mesmo item não sai duas vezes (fechar e abrir de novo, reiniciar o servidor); o recibo
  `criativo` aparece em `restores/<id>.nbt` e a linha no log.
- `devolver` com a pessoa online e offline; armadura/Curios/Accessories voltando ao slot de origem;
  inventário cheio (resumo com os que faltaram) e repetição entregando só o resto; item idêntico já
  no slot não duplica; ender chest e Fio da Volta/Relicário ficam de fora.
- `pegar` com e sem espaço; com o dono já tendo recebido parte pelo `devolver`.
- `restore` completo recusado depois de qualquer `give`/`devolver`/`pegar`/retirada no criativo.
- Repetição, comandos simultâneos, desconexão, morte, menu aberto e troca de itens durante IO.
- Falhas de disco/fila/rename antes do commit não alteram inventário; simular crash após reserva.
- Teleporte para dimensão ausente, borda, lava e vazio; recusar sem ponto seguro fora de espectador.
- Mesmo id no `view` e em `morte=` da auditoria do Limbo; Relicário usado aparece na lista e exige
  `confirm duplicar`; restore completo com Fio/Relicário no snapshot e no inventário atual não duplica.

A ponte opcional usa a [API pública de Curios 1.21.1](https://github.com/TheIllusiveC4/Curios/blob/1.21.1/neoforge/src/main/java/top/theillusivec4/curios/api/type/capability/ICuriosItemHandler.java), resolvida por reflexão para não tornar Curios obrigatório.
