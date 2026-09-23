# Aurorion Limbo

A dimensão de exílio, **o prazo que corre dentro dela** e o registro do que aconteceu.

Quem decide que alguém está exilado continua sendo o [aurorion-vidas](../aurorion-vidas/) — este mod
não conta vida nem cobra morte. Ele dá ao exílio as duas coisas que faltavam: **um relógio** e **uma
memória**.

## O ciclo

1. Você zera as vidas. O `aurorion-vidas` te exila; o Limbo abre um registro e começa a contar.
2. O servidor sabe que *alguém* caiu — sem nome, por padrão.
3. Você tem **48 horas reais** para alguém te tirar de lá.
4. Nas **últimas 5 horas**, se ninguém tentou, a coleira cai e caminhar passa a valer alguma coisa.
5. Depois de **1000 a 2000 blocos** caminhados, a **Porta do Esquecido** aparece. Você atravessa
   sozinho — e fica registrado que ninguém veio.

## As três saídas

| Saída | Como | O que fica registrado |
|---|---|---|
| **Resgate** | Alguém devolve vida (`/vidas dar`, ou o ritual quando existir) | `RESGATE` |
| **A Porta do Esquecido** | Caminhar o bastante na janela final, sem ninguém ter tentado | `PORTA_ATRAVESSADA` |
| **O prazo vencer** | Ninguém veio e a caminhada não foi feita | `PRAZO_VENCIDO` |

O resgate pela staff devolve o jogador ao overworld e completa as vidas até
`vidasAoSerResgatado`. Se estiver offline, o retorno espera o próximo login e o prazo para de
correr. Se um teleporte for cancelado ou não houver chegada segura, o registro permanece aberto
para tentar novamente. A Porta só registra a saída e concede a vida definitivamente depois da
chegada confirmada.

## A Porta do Esquecido

A ideia: se ninguém se deu ao trabalho de ir buscar, a pessoa foi tão esquecível que o próprio Limbo
a deixa ir. É o **piso** da mecânica — ninguém morre em definitivo porque a casa dominante decidiu
não aparecer.

### Por que o sorteio acontece uma vez, e não por tick

A distância alvo é sorteada **uma única vez**, entre `blocosMinimo` e `blocosMaximo`, no momento em
que a janela abre. Do lado de dentro isso é indistinguível de "uma certa chance de aparecer em algum
momento" — a pessoa não sabe quanto falta e a Porta surge sem aviso. Do lado do servidor:

- Não roda gerador aleatório por tick, por exilado.
- **Não existe o caso cruel** de andar 3000 blocos e o dado nunca cair. Quem andou o combinado sempre
  encontra a saída.
- O sorteio fica gravado: relogar no meio da caminhada não reembaralha nada.

### De onde vem a distância

Das estatísticas que o **vanilla já conta**: andar, correr e agachado. Medir passo com um listener de
tick seria trabalho por jogador por tick para descobrir um número que o jogo já tem.

Voar e nadar ficam de fora de propósito: a ficção é a caminhada longa, e elytra atravessando o Limbo
em trinta segundos não deveria pagar o mesmo preço.

### Uma tentativa de resgate desliga a Porta

Se alguém tentou buscar — mesmo falhando — a pessoa **não foi esquecida**, e a Porta nunca arma para
ela. Hoje isso é marcado na mão com `/limbo tentativa <jogador>`; quando o ritual de resgate existir,
ele chama `LimboManager.markRescueAttempt` e o comando vira só a correção manual.

Isso também vale se a Porta já estiver armada ou visível. Estender o prazo para fora da janela
final desarma a Porta e reinicia a caminhada quando a janela abrir de novo.

### A moldura

A Porta nasce somente em espaço livre, dentro da borda e em chunks carregados. Posição,
orientação, material e dimensão são salvos para remover apenas os blocos que formam a moldura,
mesmo depois de mudar a config. Resgate, vencimento e tentativa de resgate também removem a Porta.
Se o chunk estiver descarregado, a limpeza espera por ele sem forçar carga.

Molduras da versão inicial, sem esses metadados, ficam como decoração: a passagem é desativada,
mas a staff precisa remover os blocos manualmente.

### A coleira

Antes da janela final, o exilado não se afasta mais que `raioDaColeira` do ponto de chegada — uma
busca de 15 minutos precisa de uma área, não de um mundo infinito. **A coleira cai sozinha quando a
Porta arma**: é o momento em que o Limbo deixa de ser sala de espera e vira um lugar para vagar
procurando saída.

## O controle da staff — RCON e webhook

O pedido era "tudo via RCON". Metade disso funciona exatamente assim; a outra metade não, e vale
saber por quê antes de montar o bot.

> **RCON é de mão única, e a mão é a de fora.** Um cliente se conecta ao servidor e manda um comando;
> o servidor responde e pronto. O servidor **não abre conexão RCON com ninguém** — então "me avise no
> instante em que alguém atravessou a Porta" não é uma coisa que RCON saiba fazer.

As duas metades, cada uma pelo caminho que funciona:

| O que você quer | Como | Onde mora |
|---|---|---|
| **Puxar** o estado quando quiser | Seu bot roda `/limbo relatorio` por RCON | `LimboCommand` |
| **Empurrar** o evento na hora | Webhook do Discord, POST assíncrono | `DiscordSink` |

Dá para usar só uma. Um bot que já fala RCON e só precisa de um painel "quem está no Limbo agora" não
precisa de webhook nenhum. O webhook existe para quando **o instante** importa — e, para a Porta do
Esquecido, importa: é o gancho de RP nascendo.

### A saída do `/limbo relatorio` é feita para ser parseada

Contra a regra do resto do ecossistema, este comando **não** usa texto traduzível. Dois motivos que
só valem aqui: quem lê é um bot, e RCON não tem cliente para resolver tradução — o servidor resolveria
com o idioma da hospedagem.

```
limbo v1 exilados=2 agora=1757800000
exilado nome=Fulano uuid=… online=sim vidas=0 prazo_s=12345 vencido=nao resgate_tentado=nao esquecido=0 porta=armada andou=430/1600
exilado nome=Ciclano uuid=… online=nao vidas=0 prazo_s=3600 vencido=nao resgate_tentado=sim esquecido=1 porta=fechada
```

Chave e valor separados por espaço: lê a olho nu e dá `split` do outro lado. **Os nomes das chaves são
contrato com o bot** — chave nova se adiciona, chave velha não se renomeia. O `v1` no cabeçalho existe
para o dia em que isso deixar de ser verdade.

A chave adicional `retorno_pendente=sim` indica que a vida já foi devolvida, mas falta concluir
o retorno ao overworld. O nome de quem saiu sozinho continua disponível mesmo após expirar no
cache de perfis do servidor.

### A auditoria é a fonte da verdade

Tudo vira uma linha em `<mundo>/aurorion_limbo/auditoria.jsonl`, **sempre**, independente de webhook e
de config. Um objeto JSON por linha, porque quem lê não é o jogo: lê com `tail -f`, cresce sem
reescrever nada, e sobrevive a um rollback do save.

```json
{"ts":"2026-09-13T21:04:11Z","evento":"PORTA_ATRAVESSADA","uuid":"…","nome":"Fulano","vidas":1,"prazo_s":0,"andou":1643,"saidas_esquecido":1,"detalhe":"prazo restante era 9120s"}
```

`/limbo auditoria [n]` devolve as últimas linhas, mais novas primeiro — as mesmas linhas JSON, para o
bot dar parse direto no que leu.

A leitura busca somente o fim do arquivo em blocos de 8 KiB, com limite de aproximadamente 2 MiB
por consulta. Uma última linha interrompida por queda do processo é ignorada. Ajustes de prazo e
tentativas aparecem como `PRAZO_AJUSTADO` e `TENTATIVA_RESGATE`. O timestamp é o mesmo no arquivo
e no webhook, mesmo se a entrega atrasar.

## O espólio da morte — Fio da Volta e Relicário

Dois itens de uso único que o Oráculo vai vender (a loja ainda não existe; por enquanto só se
obtêm por `/give`). Os dois valem para a **última morte fora do Limbo do personagem atual** — a
chave é o personagem, não a conta, então cada personagem de uma mesma conta tem a sua — e **não
caem quando você morre**: estão na tag `aurorion_core:kept_on_death`, cuja regra mora no core.

| Item | Id | O que faz |
|---|---|---|
| **Fio da Volta** | `aurorion_limbo:fio_da_volta` | Segurar por 2 s leva ao lugar exato da última morte, em qualquer dimensão |
| **Relicário** | `aurorion_limbo:relicario` | Chama de volta para o inventário os drops daquela morte que ainda existem no mundo |

- **Na morte**, o Limbo grava dimensão e posição exata, e marca cada drop (inclusive os do Curios)
  com o id daquela morte. Os drops marcados ficam protegidos do despawn e da limpeza periódica do
  `aurorion_essentials` por `minutosDeProtecaoDosDrops`.
- **O Fio** leva à posição exata quando ela é segura. Quem morreu no ar, na água ou na lava vai para
  o chão seguro mais próximo; sem chão nenhum, ou com a dimensão trancada pelo `aurorion_portais`,
  o Fio recusa e **não é gasto**.
- **O Relicário** chama de volta, nunca restaura: o que outro jogador pegou ficou com ele. A área da
  morte é carregada por alguns segundos (ticket temporário), os itens marcados voltam para o
  inventário e o que não couber cai aos pés. Se nada restou, o Relicário é devolvido.
- Nenhum dos dois funciona no Limbo. Morrer no Limbo não substitui a morte anterior — o exilado
  resgatado ainda pode buscar o espólio da queda.
- Cada uso vira linha na auditoria (`FIO_USADO`, `RELICARIO_USADO`) com `morte=<id>`. O id é o
  `DeathId` do core, o mesmo do `/deathhistory view` do essentials.
- Quando o Relicário devolve algo, o core registra (`DeathClaims`) e o `/deathhistory restore`
  daquela morte passa a exigir `confirm duplicar`.

## Comandos

Todos são staff (nível 2).

| Comando | O que faz |
|---|---|
| `/limbo` ou `/limbo relatorio` | Estado do Limbo em `chave=valor`. **É este que o bot roda por RCON** |
| `/limbo esquecidos` | Quem já saiu sozinho, e quantas vezes |
| `/limbo auditoria [linhas]` | Últimas linhas do log, mais novas primeiro |
| `/limbo tentativa <jogador>` | Marca que alguém tentou resgatar — desliga a Porta para essa pessoa |
| `/limbo prazo <jogador> <horas>` | Ajusta o prazo antes do vencimento. **Zero mata o personagem imediatamente** |
| `/limbo retornar <jogador>` | Retira com segurança quem ficou no Limbo sem estar exilado; não altera vidas |
| `/limbo finale previa` | Prévia local com música junto ao fechamento dos olhos; não mata nem desconecta; Esc fecha durante a cena |

## Config

`config/aurorion_limbo-server.toml`:

| Chave | Padrão | O que faz |
|---|---|---|
| `horasDePrazo` | `48` | Horas reais no Limbo. Tempo com o servidor desligado **não conta** |
| `vidasAoSerResgatado` | `2` | Precisa ser maior que o da Porta, senão ninguém prefere ser resgatado |
| `janelaEmHoras` | `5` | Últimas N horas em que a Porta pode aparecer |
| `blocosMinimo` / `blocosMaximo` | `1000` / `2000` | Intervalo do sorteio da caminhada |
| `blocoDaMoldura` | `minecraft:crying_obsidian` | Só aparência; a saída é a proximidade |
| `raioDaColeira` | `300` | Zero desliga |
| `anunciarQuedaNoChat` | `false` | Anuncia no servidor que alguém caiu |
| `anunciarNomes` | `false` | Se o anúncio diz **quem** caiu |
| `webhookUrl` | `""` | Vazio desliga o push |
| `tambemNoLog` | `true` | Repete a auditoria no log do servidor |
| `raioDoRelicarioEmChunks` | `1` | Raio, em chunks, onde o Relicário procura os drops (1 = 3x3) |
| `minutosDeProtecaoDosDrops` | `120` | Drops de morte não somem nem são limpos por esse tempo. Zero desliga |

⚠️ **O Limbo não recebe ninguém até você apontar o exílio para ele.** Em
`config/aurorion_vidas-server.toml`:

```toml
exileDimension = "aurorion_limbo:limbo"
```

O mod avisa no log do boot se isso não estiver feito. Não corrigimos sozinhos de propósito:
sobrescrever a config de outro mod em silêncio é pior que o problema que resolveria.

Para manter o anúncio anônimo, defina também `announceExileInChat = false` no Vidas (já é o padrão): o anúncio próprio
dele cita o nome e é independente de `anunciarNomes` do Limbo.

## A dimensão

`data/aurorion_limbo/dimension/limbo.json` usa relevo, cavernas, aquíferos e minérios do Overworld,
mas fixa o bioma `aurorion_limbo:limbo_forest` em toda a dimensão. A superfície é podzol sobre terra
infértil, com uma floresta escura cerca de 50% mais densa que a dark forest vanilla. Animais ainda
aparecem em menor quantidade e a tabela normal de monstros continua ativa, então é possível viver e
buscar recursos enquanto o resgate não chega.

O céu é o do End, o relógio fica parado em 18000, não existe luz do céu e a luz ambiente é zero.
Dentro da dimensão, **Darkness I é infinito**: leite, `/effect clear` e curas de outros mods não o
removem. A saída limpa o efeito imediatamente. Um ambiente grave toca ao fundo e, a cada 18–42
segundos, um som de monstro nasce em algum ponto ao redor do jogador. Esses sons não criam entidades;
os monstros reais vêm do spawn vanilla.

As árvores são geradas uma vez, quando o chunk nasce. Construir e editar ruínas depois não aciona
nenhuma regeneração e não há rotina que substitua blocos prontos.

Esta versão é bem mais cara para gerar que o Limbo plano. **Pré-gere com Chunky** a área de chegada,
o raio da coleira e os caminhos prováveis da Porta antes de liberar a dimensão. Chunks existentes
mantêm o terreno antigo; para converter tudo de uma vez, use um backup e recrie somente a pasta da
dimensão do Limbo com o servidor desligado.

## A apresentação

O Limbo tem uma identidade própria: **grafite azulado e frio** no exílio, **âmbar** quando alguém
vem, **ferrugem** no que é irreversível — o mesmo âmbar que o `aurorion-vidas` já usa para uma vida,
contra o vermelho da vida do vanilla.

### Três camadas, escolhidas uma vez no boot

O mod **não importa classe de mod nenhum**. Tudo que o Limbo fala passa pelo `LimboNarrator`:

| Camada | Quando | O que entrega |
|---|---|---|
| `ImmersiveNarrator` | Immersive Messages no pack | Cenas com máquina de escrever nos momentos fortes; topo e rodapé nos avisos e na abertura da passagem |
| `NativeNarrator` | **padrão** | Painel e cena próprios do mod, desenhados pelo cliente |
| `ChatNarrator` | último recurso | Chat e actionbar |

A ordem importa: o nativo é a base, e não o plano B. O Immersive Messages entra por uma ponte
**reflexiva de propósito** — compilar contra ele exigiria o jar num `libs/`, um binário no git e um
build que quebra na máquina de quem não copiou o arquivo, por um mod que o design trata como
opcional. Aqui o acoplamento é só de execução, resolvido uma vez na carga da classe.

Cada camada **herda** da de baixo: quando uma entrega falha — mod atualizado com API diferente,
canal ausente no cliente — a chamada ao `super` faz a mensagem sair mesmo assim. Numa mecânica em
que dá para perder o personagem por não ter visto um aviso, **silêncio é a pior falha possível**.

### Manchete e corpo são coisas diferentes

Cada aviso tem duas partes: uma **manchete** curta (`aurorion_limbo.title.*`, na Cinzel) e o **corpo**
(`aurorion_limbo.queda`, `.coleira`, …). A regra ao escrever ou traduzir: a manchete nunca repete o
corpo, e **o corpo precisa continuar fazendo sentido sozinho** — é ele que sai no chat quando não há
HUD nenhum. Um corpo que dependa da manchete vira uma frase truncada no fallback.

### O painel, e por que ele não custa um pacote por segundo

Enquanto o prazo corre, o exilado tem um painel discreto no canto: nome do lugar, relógio e a etapa
em que está. Mas o relógio **não viaja pela rede**.

O servidor só fala quando a *etapa* muda — caiu, armou, a Porta apareceu, o prazo venceu — e o
cliente conta os segundos sozinho entre um snapshot e o próximo, com um reenvio a cada minuto para
corrigir a deriva. Se isso fosse um pacote por segundo por exilado, seria o único lugar do mod com
tráfego proporcional a *tempo* em vez de a *evento*, que é exatamente o que a [SDD §7.3](../SDD.md)
proíbe.

Quem decide reenviar compara a etapa já enviada com a atual (`ExileRecord#syncDue`). É de propósito
que seja assim, e não uma chamada em cada transição: **uma transição nova, inventada depois, não
nasce esquecida**. Login, respawn e troca de dimensão invalidam o que a tela sabia — são os três
momentos em que o cliente esquece tudo.

Os efeitos são locais ao cliente: as partículas da Porta, o escurecimento da névoa e a interpolação
do relógio não geram tráfego nenhum, e as partículas respeitam a configuração de partículas do
jogador e só aparecem perto da Porta do próprio dono.

### A fonte

`assets/aurorion_limbo/font/limbo.json` usa a **Cinzel** — uma serifada clássica, de registro
lapidar, que é o que o Limbo pede. Ela entra por provider `ttf` **do vanilla**, sem depender do
Caxton nem de nenhum mod de tipografia.

As três referências vanilla depois dela **não são enfeite**: são elas que resolvem acento, cirílico e
CJK que a Cinzel não tem. Sem elas, um nome com caractere fora do alfabeto latino viraria caixinha
justamente no momento mais dramático do servidor.

A fonte é **dado, não código** — trocar é substituir o `.ttf` e a entrada do JSON, e dá para fazer
por resource pack sem tocar no jar. Se for modificá-la, o nome reservado "Cinzel" não pode
acompanhar a versão modificada; ver [CREDITS.md](../CREDITS.md).

### O Oráculo itinerante

O Oráculo tem **corpo próprio**: entidade `aurorion_limbo:oraculo`, humanoide, com a skin em
`assets/aurorion_limbo/textures/entity/oraculo.png`. Trocar a aparência é trocar esse PNG — o modelo
reaproveita a camada `ModelLayers.PLAYER` do vanilla, então não há definição de modelo para mexer.

Ele **não nasce sozinho**. A staff invoca um:

```mcfunction
/summon aurorion_limbo:oraculo
```

O chunk do Oráculo pode descarregar quando ninguém está perto, mas isso não cancela a rotação.
Ao mover, o servidor recupera a entidade pelo UUID salvo e cria um ticket temporário somente para o
chunk antigo. Como entidades são lidas de forma assíncrona, o destino pendente fica salvo e é
concluído assim que o NPC reaparece no índice, inclusive após reinício; nenhuma ilha fica carregada
permanentemente. Se o Oráculo estiver a até 5 blocos de um jogador, ele olha para o jogador mais
próximo; essa busca roda uma vez a cada 5 ticks e percorre apenas a lista de jogadores da dimensão.

O contrato antigo continua valendo: um mob comum com a tag `aurorion_oraculo` também atende, e
`OracleEntity.isOracle` é o único lugar que decide isso. Mundos onde a staff já marcou um esqueleto
seguem funcionando sem mudança.

**Cadastrar os lugares da rotação** — vá até cada ponto e grave:

| Comando | Efeito |
|---|---|
| `/oraculo local ponto <nome>` | Grava sua posição e direção como ponto de rotação |
| `/oraculo local listar` | Lista os pontos e marca o sorteado de hoje |
| `/oraculo local remover <nome>` | Remove um ponto |
| `/oraculo onde` | Diz em que ponto ele está e a posição real dele |
| `/oraculo mover [nome]` | Sorteia na hora, ou força um ponto — para testar sem esperar o dia virar |

Todos exigem permissão de staff. A raiz `/oraculo`, sem argumento, continua **sem** exigência de
permissão, porque é ela que o diálogo do ADM executa; a trava dela é de posição, não de permissão.

Os pontos ficam no SavedData `aurorion_limbo_oraculo`, não na config: coordenada é dado de mundo, e
cadastrar andando até o lugar é melhor que digitar número em TOML. Limite de 64 pontos.

**A rotação** acontece uma vez por dia, na hora real definida em `horaDaRotacao` (padrão `0`, ou
seja meia-noite). É o relógio da máquina do servidor, não o do Minecraft — o tempo do jogo pula com
cama e `/time`, e uma rotação presa a ele apareceria duas vezes numa noite e nenhuma na outra.

O dia é guardado como número, não como "já rodei hoje": um servidor que passou a madrugada desligado
volta, percebe que o dia mudou e sorteia na hora. O sorteio evita repetir o lugar de ontem quando há
mais de um ponto. Na primeiríssima partida ele só marca o dia, sem teleportar nada de surpresa.

**O resgate acompanha a rotação.** É um Oráculo só, e ele muda de lugar. Vale lembrar que o exílio
tem prazo (`horasDePrazo`, padrão 48h): quem tem alguém no Limbo precisa achar o Oráculo dentro
desse prazo. Se isso se mostrar duro na prática, as saídas são anunciar a posição a cada rotação ou
manter um segundo Oráculo fixo com a tag — os dois continuam funcionando sem mudar código.

### O Oráculo e o padrão dos próximos NPCs

O ADM conduz a conversa escrita do Oráculo. O arquivo entregue pelo mod é
`aurorion_limbo:oraculo_do_limbo`; quando o jogador pede os nomes, o servidor envia um snapshot com
exilados, prazos, presença e custo e abre a lista dinâmica. Sem ADM, interagir com o Oráculo abre a
lista diretamente, então o resgate não depende da integração cosmética. O valor antigo
`oraculo_do_limbo` do TOML é reconhecido e convertido em memória para o ID do datapack.

A conversa e a lista usam a mesma paleta de grafite azulado, osso e ferrugem. O ADM recebe as nove
cores pelo bloco `style` do JSON. Ele não oferece fonte por diálogo na versão 0.7.3; a lista nativa
usa `aurorion_limbo:limbo`, e Caxton pode assumir sua renderização quando estiver instalado. Não há
injeção global na tela do ADM, que mudaria também NPCs de outros mods.

A passagem pertence a quem pagou por ela. Outros jogadores enxergam e atravessam apenas o efeito
visual; somente o dono é levado ao Limbo. Quando ele chega, a passagem fecha no mesmo tick e os
Vínculos de Alma são colocados no inventário com uma sincronização explícita para o cliente. Se o
inventário estiver cheio, os itens caem aos pés do resgatador.

As peças reutilizáveis ficam em `aurorion-core/client/gui`: `NpcScreenTheme` guarda fonte e paleta,
`NpcPanelScreen` fornece moldura, cabeçalho, linhas, etiquetas e rolagem, e `AurorionButton` fornece
o botão narrativo. Um NPC novo cria seu tema e implementa somente medida, widgets e conteúdo.
Immersive Messages continua nos avisos e cenas do Limbo; colocá-lo sobre o diálogo esconderia as
escolhas que o jogador precisa ler. Depois da escolha, abertura da passagem, travessia e conclusão
do Vínculo passam pelo Immersive Messages com a fonte do Limbo; sem ele, usam o painel nativo e,
como último recurso, o chat.

### Cinematic Respawn

O Cinematic Respawn continua ativo nas mortes comuns. Na morte que deixa o contador em zero, o
Limbo preserva o fluxo vanilla de morte/respawn: a animação cinematográfica mantém a entidade local
com zero de vida enquanto controla a câmera e pode não terminar quando o servidor troca a dimensão
de respawn. Depois do respawn, a chegada e os avisos visuais do próprio Limbo assumem a cena.

Essa compatibilidade é um mixin de cliente `@Pseudo`: se o Cinematic Respawn não estiver instalado,
o alvo não existe e nada é aplicado.

### PlayerRevive

O PlayerRevive age antes do contador de vidas. A primeira morte deixa o jogador caído; se alguém o
reanima, nenhuma vida é gasta. Desistir ou deixar o sangramento terminar conclui a morte e consome
uma vida normalmente. Se essa era a última, o respawn segue para o Limbo.

Com zero vidas, o estado de caído é desativado. Login e respawn também limpam uma marca de
sangramento que tenha sobrado de uma queda antiga ou de um servidor interrompido, evitando câmera,
pose e HUD presos dentro do Limbo. A integração usa mixin `@Pseudo` e reflexão resolvida apenas
nesses eventos raros; PlayerRevive e CreativeCore continuam opcionais.

## Como testar

```bash
./gradlew :aurorion-runs:runServer
./gradlew :aurorion-runs:runClient
```

Validação automatizada, sem cliente gráfico:

```bash
./gradlew :aurorion-limbo:test
./gradlew :aurorion-limbo:runGameTestServer
./gradlew -PlimboRealWorldgen :aurorion-limbo:runGameTestServer
./gradlew buildAll
```

O GameTest usa um mundo separado em `aurorion-limbo/run/gameTestServer`, dois jogadores simulados
e os mods Vidas e Portais. Exercita travessia simultânea, cancelamento, tentativa, prazo zero,
Darkness irremovível, limpeza do efeito na saída, relatório para RCON e resgate online/offline com
reconexão. A execução com `-PlimboRealWorldgen` repete o ciclo gerando chunks com a floresta real;
a execução padrão usa terreno plano para ser rápida e determinística. Não envia webhook.
A execução de compatibilidade também pode carregar os jars reais de PlayerRevive e CreativeCore;
ela confirma que exilados não entram em outra quase-morte e que estado antigo é limpo e sincronizado.
Aparência no cliente, Immersive Messages e carga de 80 jogadores
precisam de validação no modpack.

Com `exileDimension` apontando para o Limbo:

1. `/gamemode survival` e `/vidas definir Dev1 1`, depois `/kill` — você cai no Limbo.
2. `/limbo relatorio` — a linha do exilado aparece com `porta=fechada`.
3. `/limbo prazo Dev1 4` — com `janelaEmHoras=5`, isso já entra na janela. Na próxima varredura a
   coleira cai e a Porta arma. Configure `blocosMinimo`/`blocosMaximo` para 50 **antes** deste passo
   se quiser um teste curto — o alvo já sorteado não muda com a config.
4. Ande a distância sorteada. A Porta aparece na sua frente.
5. Entre nela: você volta ao overworld com 1 vida, e a auditoria ganha a linha
   `PORTA_ATRAVESSADA`.
6. `/limbo esquecidos` — seu nome com `vezes=1`.

Para testar o resgate, `/vidas dar Dev1 1` a qualquer momento: a varredura percebe que você deixou de
estar exilado e fecha o registro como `RESGATE`.

## Estrutura

```
exile/     ExileRecord (estado de um exilado), LimboData (SavedData), LimboManager (regras),
           ForgottenDoor (caminhada, autorização), DoorFrame (identidade da moldura)
narrate/   LimboNarrator (interface) → ImmersiveNarrator → NativeNarrator → ChatNarrator,
           ImmersiveBridge (reflexão), LimboText (as falas, num lugar só)
network/   LimboNetwork, LimboStatusPayload (o painel), LimboNoticePayload (a cena)
client/    ClientLimbo (estado da tela), LimboHudLayer, OracleScreen e LimboNpcThemes
report/    AuditEvent, AuditLog (jsonl no save), DiscordSink (webhook assíncrono)
recall/    DeathData (última morte por personagem), DeathRecall (marca de drops, Fio e Relicário)
item/      SoulBondItem, ReturnThreadItem (Fio da Volta), ReliquaryItem (Relicário)
environment/ LimboEnvironment — Darkness persistente e sons espaciais de baixa frequência
event/     LimboServerEvents — morte, login, varredura de 1s, destrave da travessia;
           RecallEvents — morte, drops e o relógio do Relicário
command/   LimboCommand — a saída estável que o bot lê por RCON
```

Testes: `src/test/` (21 unitários, sem servidor) e `src/gameTest/` (um servidor de verdade, dois
jogadores simulados, o ciclo inteiro). Rodam com `:aurorion-limbo:test` e
`:aurorion-limbo:runGameTestServer`.

## Morte definitiva

O prazo vencido agora encerra o personagem, com música, subida de câmera, fechamento dos olhos,
epílogo em rolagem e desconexão um minuto após “Você está morto.”. A conta não é banida.
Configuração, identidade de personagem e roteiro de validação estão em
[MORTE-DEFINITIVA.md](MORTE-DEFINITIVA.md).

## O que ainda não existe

Deliberado, para não construir em cima de decisão não tomada:

- **Os Faróis** — pontos adicionais para a economia de informação sobre quem está onde. O Oráculo,
  a passagem paga e o Vínculo de Alma já fazem o ciclo de resgate.
- **Criação de outro personagem e reset completo da progressão**. A morte definitiva e o bloqueio já existem; o fluxo de criação ainda será implementado. Ver [morte definitiva](MORTE-DEFINITIVA.md).

E uma coisa que não é decisão, é só verificação que falta:

- **Ninguém viu o painel com olho humano ainda.** O servidor, a rede e o desenho estão testados
  (21 unitários e um gameTest com dois jogadores simulados), mas contraste, tamanho da Cinzel em
  1080p e o comportamento com o HUD cheio de outros mods do pack só se julgam abrindo o jogo. É o
  primeiro item para a próxima sessão, e é rápido: ver [Como testar](#como-testar).

Decisões de design e por quê: [SDD](../SDD.md).

## Áudio de imersão

Novos efeitos discretos do Epidemic, registros, triggers e validação pendente:
[guia de áudio](../docs/IMMERSION-AUDIO.md). Trilha e sons anteriores preservados;
sem compilação ou testes em jogo nesta máquina.
