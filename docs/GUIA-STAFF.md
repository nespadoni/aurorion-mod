# Guia da staff — mods Aurorion

Guia de operação do servidor: o que o jogador vê, os comandos de cada mod (com permissão), as configs
que importam, os procedimentos mais comuns e como instalar uma atualização. Para mexer no código,
veja o [Guia de desenvolvimento](GUIA-DESENVOLVIMENTO.md). Detalhes completos estão no `README.md`
de cada mod.

Comandos e configs conferidos no código da branch `fix/correcoes-magias-simplymore-2026-10-17`
(18/10/2026).

## Convenções

| Termo | Significado |
|---|---|
| **Nível 2** | `hasPermission(2)`: OP nível 2 ou mais (ou console/RCON). É a permissão de "staff" em quase todos os mods |
| **Nível 4** | Só OP máximo ou console. Usado apenas em `/personagem alt remover` |
| **livre** | Qualquer jogador pode usar |
| `<jogador>` (perfil) | Aceita jogador **offline** (argumento de perfil). Marcado como *perfil* nas tabelas |
| `<jogador>` (online) | Só jogador conectado (seletor de entidade, ex.: `@p`, `@a`) |
| `<quantia>` | Dinheiro: `12`, `12,3` ou `12.3`, no máximo uma casa decimal e **maior que zero**. 1 óbolo = 10 fragmentos |
| `[...]` | Argumento opcional |

**Onde ficam as configs.** Todas em `config/aurorion/` na pasta do servidor, com o nome
`<mod>[-variante]-<tipo>.toml`. Exemplos: `config/aurorion/magia-server.toml` e
`config/aurorion/essentials-privacy-server.toml`.

- Arquivos `-server` e `-client` costumam valer ao salvar.
- Arquivos `-startup` só valem depois de reiniciar o servidor.
- Arquivos antigos no formato `config/aurorion_<mod>-server.toml` são ignorados. O servidor avisa no
  log quando ainda encontra algum. Alguns READMEs e o README da Trama ainda citam esse formato antigo
  ou `serverconfig/`; o caminho certo é `config/aurorion/`.
- Conteúdo de datapack (casas, horários de portal, ligações de mundos, presets de área) vale após
  `/reload`.

---

## 1. Procedimentos comuns

### 1.1 Vidas e Limbo

| Tarefa | Comando |
|---|---|
| Ver as vidas de alguém | `/vidas ver <jogador>` |
| Dar vidas (também **resgata** do Limbo) | `/vidas dar <jogador> [1-20]` (padrão 1) |
| Tirar vidas | `/vidas tirar <jogador> [1-20]` |
| Definir valor exato | `/vidas definir <jogador> <0-20>` |
| Mandar para o Limbo | Não há comando direto. Use `/vidas definir <jogador> 0` e depois `/kill <jogador>`: o renascimento cai no Limbo |
| Tirar do Limbo alguém que **não** está exilado (ficou preso lá) | `/limbo retornar <jogador online>`. Não mexe em vidas |
| Estado do Limbo | `/limbo` (ou `/limbo relatorio`), `/limbo esquecidos`, `/limbo auditoria [1-200]` |
| Mudar o prazo de um exilado | `/limbo prazo <jogador> <horas 0-336>` |
| **Morte definitiva** | `/limbo prazo <jogador> 0`. Exige exílio ativo; se preciso, mande para o Limbo antes |
| Impedir a Porta do Esquecido para alguém (houve tentativa de resgate) | `/limbo tentativa <jogador>` |
| Ver o epílogo da morte definitiva | `/limbo finale previa` |
| Definir o ponto de exílio (só se `exileDimension` não for o Limbo) | Em pé no lugar, `/vidas exilio aqui`; conferir com `/vidas exilio ver` |

Com `/vidas dar`, o exilado é levado a um lugar seguro perto do spawn do overworld (se estiver
offline, no próximo login) e fica com pelo menos `vidasAoSerResgatado` (2) vidas.

Personagem com morte definitiva não ganha vidas: `dar` e `definir` ficam em 0.

### 1.2 Personagem, morte definitiva e alt

| Tarefa | Comando |
|---|---|
| Liberar **uma** criação de personagem novo depois de morte definitiva | `/personagem liberar <jogador>` |
| Desfazer a liberação ainda não usada | `/personagem revogar <jogador>` |
| Ver identidade, ID do personagem e reservas | `/personagem ver <jogador>` |
| Corrigir a grafia do nome (vale offline, aplica no próximo login) | `/personagem renomear <jogador> <Nome> <Sobrenome>`. Nomes com espaço vão entre aspas |
| Criação travada (nome reservado) | `/personagem cancelar <jogador>`. **Não ressuscita** ninguém |
| Criar um alt da staff (a partir da conta principal) | `/personagem alt criar` (nível 2). Pode repetir para vários alts |
| Trocar para o alt ou voltar | `/personagem trocar [perfil]`. `principal` volta à conta principal. A troca **desconecta e reconecta** |
| Listar alts | `/personagem alt ver [jogador]` |
| Desfazer o vínculo de um alt (os dados ficam no mundo) | `/personagem alt remover <perfil>` (**nível 4** ou console) |

Não existe comando que "ressuscite" ou reinicie um personagem vivo. O reset acontece só no caminho
liberar → tela de criação → confirmação. Nesse caminho a pessoa é desconectada e inventário, vidas,
casa, saldo, profissão, magias, passivas, Trama e celular do personagem antigo são apagados.

O alt nasce **sem OP**. A troca exige `enforce-secure-profile=false` no `server.properties` (ver o
README do Personagem). `/realname`, os avisos de área e `/personagem ver` mostram a conta real.

### 1.3 Economia

| Tarefa | Comando |
|---|---|
| Ver saldo de alguém (online) | `/saldo <jogador>` |
| Dar / tirar / definir saldo | `/economia dar\|tirar\|definir <jogador> <quantia>` |
| Zerar saldo | `/economia tirar <jogador> <quantia ≥ saldo>`. O resultado para em 0. `definir ... 0` é **recusado**, porque a quantia tem de ser maior que zero |
| Cofre da casa | `/economia casa <casa> saldo\|dar <q>\|tirar <q>\|definir <q>\|cofre <0-3>` |
| Salário da casa | `/economia casa <casa> salario` (ver) · `salario <quantia> <dias 1-365>` · `salario pagar` · `salario desligar` |
| Preços das melhorias da casa (em **fragmentos**, -1 desativa) | `/economia preco` (lista) · `/economia preco <cofre1\|cofre2\|cofre3\|protetor1\|protetor2> <fragmentos>` |
| Preço do terreno por bloco | `/economia zona <0-4> <fragmentos_por_bloco>` |
| Terrenos de um personagem | `/economia terrenos <jogador>` |

`<casa>` sem namespace vira `aurorion_ethereal:<casa>` (ex.: `venthra`). Todo personagem novo
recebe 4 óbolos uma vez. O saldo é do **personagem**: morte definitiva zera. Os comandos
`/economia preco` e `/economia zona` gravam direto em `config/aurorion/economia-server.toml`.

### 1.4 Casa (Ethereal) e pontos

| Tarefa | Comando |
|---|---|
| Ver / listar | `/casa ver <jogador>` · `/casa listar` |
| Definir casa sem rito | `/casa definir <jogador> <casa>` |
| Tirar a casa | `/casa limpar <jogador>` |
| Rito de Vinculação (revelação pública) | `/casa cerimonia <jogador online> [casa]`: usa a casa já cadastrada ou atribui e revela de uma vez |
| Cancelar rito | `/casa cerimonia cancelar <jogador>` |
| Mural/cofre da casa | Olhando para o mural: `/casa mural vincular <casa>` · `/casa mural protetor <casa> <0-2>` |
| Pontos (somam; valor negativo tira) | `/pontos casa <casa> <n>` (bônus da casa) · `/pontos aluno <jogador> <n>` (conta no ranking de alunos e no total da casa) · `/pontos missao <jogador>` (registra uma missão concluída) · `/pontos ver` |
| Configurar um Projetor Aeônico | Clique direito no projetor (nível 2) |

### 1.5 Profissão

| Tarefa | Comando |
|---|---|
| Atribuir profissão | `/profissao definir <jogador online> <medico\|ferreiro\|cozinheiro\|arcanista\|corretor\|nenhuma>`. É uma por personagem; a nova substitui a anterior |
| Ver profissão | `/profissao ver <jogador>` |
| Permitir Mending no item da mão (exceção da staff) | `/profissao mending_adm <true\|false>` |

A profissão aparece como "registrado ✔" no app Serviços e some na troca ou na morte definitiva do
personagem.

### 1.6 Magia

| Tarefa | Comando |
|---|---|
| Liberar uma magia | `/aurorion spells unlock spell <id> <alvos>` (ex.: `aurorion_magia:umbra_viva`) |
| Liberar uma escola inteira | `/aurorion spells unlock school <escola> <alvos>` (ex.: `irons_spellbooks:blood`) |
| Liberar todas as escolas | `/aurorion spells unlock todos <alvos>` |
| Bloquear magia / escola | `/aurorion spells lock spell\|school <id> <alvos>` |
| Ver o que alguém tem | `/aurorion spells list <jogador>` |
| Passivas | `/aurorion passivas conceder\|remover\|pergaminho <passiva> <alvos>` · `/aurorion passivas ver <jogador>` |

- **Nenhuma magia vem liberada.** O jogador conjura a magia se ela ou a escola dela foi liberada.
- **Magias proibidas nunca vêm com a escola.** Nem `unlock school` nem `unlock todos` as concede:
  é preciso `unlock spell` uma a uma. Elas também não têm craft nem loot. As 12 proibidas do
  Aurorion são `tempus_sistere`, `mortem_dico`, `dolor_universus`, `sphaera_spiritus`,
  `possessio_corporis`, `iustitia_demaciae`, `mors_ex_profundis`, `ceifar_vida`, `entregar_vida`,
  `reino_almas`, `vazio_infinito` e `santuario_malevolente`, todas no namespace `aurorion_magia`.
  Magias de outros addons entram nessa regra pela chave `liberacao.forbiddenSpells`.
- A liberação é guardada **por conta** e apagada no reset de personagem. É reaplicada no login e a
  cada comando, e espelhada no Iron's Restrictions.
- Com `liberacao.authoritative = true` (padrão), o que o jogador aprendeu por fora (manuscrito,
  `DefaultLearntSpells`) é esquecido. Deixe `DefaultLearntSpells = []` no config do Iron's
  Restrictions.
- Staff (nível 2) conjura tudo sem liberação enquanto `liberacao.staffBypass = true`.
- Passivas: `aurorion_magia:manus_medica`, `presenca_terrivel`, `colheita_almas`,
  `dominio_refinado`. O jogador usa `/aurorion passivas minhas` e liga ou desliga a Presença
  Aterradora com `/aurorion passivas ligar|desligar aurorion_magia:presenca_terrivel`.
- **`aliados.magiasIgnoramTime`** (`config/aurorion/magia-server.toml`, padrão `true`):
  - Com `true`, magia acerta qualquer pessoa de qualquer time, mesmo com fogo amigo desligado no
    time. Vale para as magias do Aurorion e do Iron's. Servidor sem PvP e área com `pvp` negado
    continuam protegendo. Espada e flecha comuns seguem a regra normal de fogo amigo.
  - Com `false`, a magia poupa o próprio time, como no Iron's puro.
  - A Presença Aterradora tem chave própria: `passivas.dreadSparesAllies`.

### 1.7 Áreas

Fluxo básico, com nível 2:

1. `/area selecao` e depois `/area ponto` em cada vértice. Para um círculo:
   `/area circulo <raio>`.
2. `/area altura <min> <max>`.
3. `/area criar <id>`.
4. `/area perfil <id> <escola|exterior|floresta_negra|seguro|treino|vila>` (presets
   `aurorion_areas:...`), ou regra a regra: `/area regra <id> <regra> permitir|negar|herdar`.
5. Conferir com `/area visualizar <id>`, `/area ver <id>` e, no local, `/area aqui [jogador]`.

| Regra | Controla |
|---|---|
| `voo` | Voo (itens, efeitos, montarias e magias de voo) |
| `magia` | Conjurar magia |
| `monstros` / `dano_monstros` | Surgimento e dano de monstros |
| `pvp` | Dano entre jogadores (também para magias) |
| `casa` | Barreira de casa (só membros passam) |
| `agua_pura` / `agua_pura_pia` | Água tratada do LSO no local / nas torneiras |

| Outros comandos | |
|---|---|
| Forma | `/area adicionar <id>` · `/area recortar <id>` · `/area remover_forma <id> parte\|recorte <n>` · `/area desfazer` |
| Ajustes | `/area prioridade <id> <n>` (maior vence) · `/area nome <id> <nome>` · `/area ativar <id> true\|false` · `/area casa <id> <casa>\|nenhum` |
| Ambiente | `/area ambiente <id> <ambiente>\|nenhum\|herdar` (ex.: `aurorion_areas:floresta_negra`) |
| Monstros | `/area monstros <id> vida\|dano <0.1-20 ou -1 para herdar>` |
| Exceção por personagem | `/area excecao <id> <jogador> <regra> true\|false` |
| Padrão da dimensão | `/area mundo <perfil>\|herdar` |
| Aviso no Discord | `/area aviso <id> true\|false` (avisa quando alguém entra; staff em criativo/espectador não dispara) · `/area avisos` (lista e fila) · `/area avisos teste` |
| Listar / remover | `/area listar` · `/area remover <id>` |

Staff em criativo com nível 2 ignora regras e ambientes (`creativeStaffBypass`). A barreira de casa
deixa passar criativo e espectador.

### 1.8 Portais e mundos

| Tarefa | Comando |
|---|---|
| Quadro de horários (livre) | `/portais` ou `/portais horarios` |
| Abrir uma linha agora | `/portais abrir <linha> [minutos 1-1440]` (ex.: `aurorion_portais:nether`) |
| Fechar uma linha | `/portais fechar <linha>` |
| Passe individual | `/portais passe dar <jogador> <dimensão> [usos 1-999] [minutos]` (padrão: 1 uso, sem prazo) |
| Ver / tirar passes | `/portais passe ver <jogador>` · `/portais passe tirar <jogador> [dimensão]` |
| Mundos extras (todo `/mundos` é nível 2) | `/mundos` · `/mundos ligacoes` · `/mundos borda <dimensão> ver\|set <tamanho> [segundos]\|centro <x> <z>\|aviso distancia <blocos>\|aviso tempo <s>\|dano porbloco <v>\|dano zona <blocos>` |

Staff nível 2 e jogadores em criativo ou espectador atravessam as dimensões trancadas
(`excecoes.bypassPermissionLevel`, `excecoes.bypassCreative`). Quem está dentro quando a janela
fecha fica preso, inclusive ao morrer (`keepPlayersOnDeath`).

### 1.9 Oráculo do Limbo

| Tarefa | Comando |
|---|---|
| Criar | `/summon aurorion_limbo:oraculo`, ou marcar qualquer mob com a tag `aurorion_oraculo` |
| Pontos de rotação | `/oraculo local ponto <nome>` · `/oraculo local listar` · `/oraculo local remover <nome>` |
| Localizar / mover agora | `/oraculo onde` · `/oraculo mover [nome]` |

`/oraculo` e `/oraculo comprar <fio|relicario>` são livres, mas só funcionam a até 8 blocos de um
Oráculo (é o diálogo do ADM que os executa). O resgate pelo Oráculo custa 1 vida a quem resgata, que
precisa ter pelo menos 2.

---

## 2. Referência por mod

### Aurorion Core (0.6.5)

Biblioteca obrigatória, sem comandos e sem config. Visível ao jogador só num ponto: itens da tag
`aurorion_core:kept_on_death` ficam com o jogador na morte. A tag é preenchida pelo Limbo.

### Aurorion Talk (0.1.3) — balões de fala

**Jogador:** a fala dos outros jogadores aparece em balões acima da cabeça e sai do HUD do chat. A
tecla **B** personaliza cor e estilo do balão. Sussurro bloqueado para quem não é staff.

**Comandos:** nenhum. O mod bloqueia `/msg`, `/tell` e `/w` para jogadores abaixo do nível 2.

| Config | Chave | Padrão | Efeito |
|---|---|---|---|
| `talk-server.toml` | `privateMessages.blockPrivateMessages` | true | Recusa os comandos de sussurro |
| | `privateMessages.commands` | `["msg","tell","w"]` | Comandos bloqueados |
| | `privateMessages.bypassPermissionLevel` | 2 | Quem ainda pode sussurrar |
| | `privateMessages.denyMessage` | "Em Aurorion ninguém sussurra…" | Resposta ao jogador (vazio = silêncio) |
| `talk-client.toml` (cliente) | `chat.hidePlayerChat`, `balloons.*` | true, … | Preferências locais de cada jogador |

Com os padrões, o Essentials (`privacy.restrictPrivateMessages`) já tira `/msg` do jogador comum.
Ele recebe "comando desconhecido" e a `denyMessage` do Talk não aparece. É dedução a partir do
código, não testada.

### Aurorion Essentials (0.8.0)

**Jogador:** fakename no chat, na nametag e no tab, com o nome do tab na cor da casa. Celular por
personagem (contatos, conversas, fotos importadas do PC). Aviso antes de cada limpeza de itens no
chão. Entrada, saída, conquista e morte ficam ocultas por padrão. Modo streamer no cliente.

**Celular — grupos no Mensagens (0.8.0):** o "+" do app abre "Nova conversa" ou "Novo grupo". Grupos
de até 16 pessoas (10 por personagem), só texto; quem está offline recebe ao entrar (fila de 50
mensagens por 7 dias). O criador adiciona/remove; ao sair, a posse passa ao membro mais antigo. Os
grupos ficam no servidor em `data/aurorion_essentials_phone_groups.dat`; o reset de personagem tira
a conta de todos os grupos.

**Celular — Gram (0.8.0):** posts, nome, bio e foto de perfil ficam no servidor e aparecem para todos
após reinícios. As fotos ficam em `mattupolis_phone/aurorion_gram/` (raiz do servidor), deduplicadas,
e são enviadas sob demanda. Na primeira subida, o `server_social.properties` antigo é migrado e uma
cópia fica em `server_social.properties.antes-aurorion`. **Incluir `mattupolis_phone/` no backup.**
Remover o Essentials depois da migração faz as fotos do Gram pararem de carregar (restaurar a cópia).

| Comando | Permissão |
|---|---|
| `/fakename set <nome>` · `/fakename clear` | livre |
| `/fakename clear <jogador online>` | 2 |
| `/realname <fakename>` | 2 |
| `/ajuda [descrição]` (chama a staff online; 1 pedido a cada 30 s por conta) | livre |
| `/gritar` · `/gritar lista` · `/gritar <pessoa> <distância>` · `/gritar <pessoa> desligar` (alcance de voz no Simple Voice Chat) | 2 |
| `/deathhistory [jogador] [página]` | 2 |
| `/deathhistory view\|tp <id>` ou `view\|tp <jogador> <nº da morte>` | 2 |
| `/deathhistory devolver\|pegar <id \| jogador nº> confirm [duplicar]` (devolver ao dono / pegar para si) | 2 |
| `/deathhistory restore <id> <alvo> confirm [duplicar]` · `/deathhistory give <id> <item> <alvo> confirm [duplicar]` | 2 |
| `/aurorion diagnostico` (estado da integração com o site e do diário) | 2 |
| `/streamer [status\|on\|ativar\|off\|desativar\|total]` (cliente, local) | livre |
| `/celular importar <caminho>` (cliente; PNG/JPG até 10 MB) | livre |

Com `deathMessagesInChat = ADMINS`, a staff recebe uma linha `[admin]` em cada morte, com botões
[TP], [Inventario] e [Historico]. Guia completo:
[aurorion-essentials/DEATH-HISTORY.md](../aurorion-essentials/DEATH-HISTORY.md).

| Arquivo | Chave | Padrão | Efeito |
|---|---|---|---|
| `essentials-server.toml` | `cleanup.enabled` / `intervalMinutes` / `warningSecondsBefore` | true / 60 / 60 | Limpeza de itens e orbes no chão |
| | `cleanup.cleanItems` / `cleanExperienceOrbs` | true / true | O que a limpeza remove |
| `essentials-privacy-server.toml` | `privacy.joinLeaveMessages` | NOBODY | Quem vê entrada e saída (EVERYONE/ADMINS/NOBODY) |
| | `privacy.advancementMessages` | NOBODY | Quem vê conquistas |
| | `privacy.deathMessagesInChat` | ADMINS | Mortes só para a staff, com botões |
| | `privacy.restrictPrivateMessages` | true | `/msg`, `/tell` e `/w` só para nível 2 |
| `essentials-death-history-server.toml` | `enabled` / `deathsPerPlayer` | true / 100 | Histórico de mortes |
| `essentials-voice-server.toml` | `voice.onlyOpenGroups` / `allowGroupPasswords` / `maxShoutDistance` | true / false / 256 | Grupos do Voice Chat e alcance máximo do `/gritar` |
| `essentials-tablist-server.toml` | `houseColor` | true | Nome do tab na cor da casa |
| `essentials-streamer-client.toml` (cliente) | `streamer.mode` | OFF | Preferência do modo streamer |

### Aurorion Utils (0.2.4)

**Jogador:** feixe de luz da abdução e congelamento real, com borda de gelo na tela. Também corrige
crashes de cliente do Simply More (atordoamento do Cindergorge e da frigideira, Mimi-Cry).

| Comando | Permissão |
|---|---|
| `/abduzir <jogador> [cor]`: puxa até você | 2 |
| `/abduzir <jogador> <destino> [cor]`: leva até outro jogador | 2 |
| `/abduzir voltar <jogador> [cor]`: devolve ao ponto de origem | 2 |
| `/freeze <jogadores> [segundos 1-86400]` · `/unfreeze <jogadores>` | 2 |

Cores: `roxo`, `lilas`, `magenta`, `rosa`, `vermelho`, `carmesim`, `laranja`, `dourado`, `amarelo`,
`lima`, `verde`, `turquesa`, `ciano`, `azul`, `anil`, `cinza`, `branco` ou hexadecimal `RRGGBB`.

Config `utils-server.toml`, seção `abduction`:

| Chave | Padrão |
|---|---|
| `holdDurationTicks` | 40 |
| `ascentHeightBlocks` | 40 |
| `ascentDurationTicks` | 60 |
| `retractDurationTicks` | 20 |
| `beamRadius` | 1.5 |
| `beamColorRgb` | `"9B30FF"` |

O README do Utils traz valores antigos.

### Aurorion Aeonita (0.3.3)

**Jogador:** lingotes de Aeonita comestíveis que brilham, blocos de armazenamento, o Altar de
Seleção e sete capas animadas (uma por casa, mais "sem casa" e Desvinculados). Os lingotes na mão
dão luz dinâmica.

Sem comandos. Única config, no cliente, em `aeonita-client.toml`: `dynamicLight.enabled` (true) e
`dynamicLight.lightLevel` (15). A auditoria de 18/10 aponta a luz dinâmica como custo alto no
cliente: desligar resolve em máquina fraca.

### Aurorion Ethereal (0.3.5) — casas

**Jogador:** cinco casas: Venthra, Sylvara, Nyx, Ignivar e Aetheris. O altar mostra a casa do
jogador. O rito de 18 s tem círculo, runas, música e o nome revelado, e é visto num raio de 96
blocos. O Projetor Aeônico mostra rankings de casas, alunos, missões, vidas, duelos e riqueza. O
mural da casa tem cofre, melhorias e protetor.

Comandos: `/casa` (livre, mostra a sua casa); `/casa listar|ver|definir|limpar|mural|cerimonia` e
todo o `/pontos` são nível 2. Ver [1.4](#14-casa-ethereal-e-pontos).

| `ethereal-server.toml` | Padrão | Efeito |
|---|---|---|
| `houses.ceremonyRequired` | true | A casa é dada pela staff com `/casa cerimonia`. Com `false`, o altar abre a escolha livre |
| `houses.allowRechoose` | false | Permite refazer a vinculação |
| `houses.announceInChat` | true | Anuncia a vinculação no chat |
| `houseUpgrades.protectorICooldownDays` / `protectorIICooldownDays` | 30 / 14 | Recarga do Protetor Arcano, em dias reais |

As casas ficam no datapack: `data/<ns>/aurorion/houses/*.json`.

### Aurorion Áreas (0.6.4)

**Jogador:** sente as regras (voo, magia, PvP, monstros), os ambientes narrativos (névoa, vinheta,
escuridão, sussurros, ataques invisíveis) e a barreira de casa. A staff recebe alerta no chat quando
alguém entra, sai ou fica tempo demais numa área letal.

Comandos: todo o `/area` é nível 2. Ver [1.7](#17-áreas).

| `areas-server.toml` | Padrão | Efeito |
|---|---|---|
| `enabled` | true | Liga e desliga regras e ambientes de todas as áreas |
| `creativeStaffBypass` | true | Criativo com nível 2 ignora regras e ambientes |
| `houseBarrier` | true | Liga a barreira de casa |
| `flightSpells` | `["irons_spellbooks:angel_wing"]` | Magias tratadas como voo |

As áreas ficam salvas no mundo, não em datapack. Os presets e ambientes ficam em
`data/<ns>/aurorion/area_rules/` e `data/<ns>/aurorion/area_ambience/`.

### Aurorion Portais (0.1.3)

**Jogador:** só o overworld é livre. As outras dimensões abrem em janelas agendadas. Há aviso no chat
antes da partida e contagem regressiva na barra de ação. Ver [1.8](#18-portais-e-mundos).

| `portais-server.toml` | Padrão | Efeito |
|---|---|---|
| `transito.enforce` | true | `false` destranca tudo |
| `transito.freeDimensions` | `["minecraft:overworld"]` | Dimensões nunca trancadas |
| `transito.lockUnscheduledDimensions` | true | Dimensão sem linha fica trancada |
| `excecoes.bypassPermissionLevel` | 2 | Nível que ignora as travas (5 = ninguém) |
| `excecoes.bypassCreative` | true | Criativo e espectador ignoram |
| `excecoes.keepPlayersOnDeath` | true | Quem morre dentro renasce dentro |
| `avisos.announceInChat` / `countdownSeconds` | true / 60 | Avisos e contagem |

As linhas ficam em `data/<ns>/aurorion/linhas/*.json`. As que vêm no mod: Nether no sábado às 20:00
por 10 min, End no domingo às 21:00 por 15 min.

### Aurorion Mundos (0.1.3)

**Jogador:** portais de obsidiana em certas regiões levam a outros overworlds (`mundo_dois`,
`mundo_tres`), cada um com sua borda. O terreno deve ser pré-gerado: por padrão nenhuma travessia
gera chunk.

| `mundos-server.toml` | Padrão | Efeito |
|---|---|---|
| `mundos.seeds` | `mundo_dois=284119730051`, `mundo_tres=-762045118829` | Seed de cada mundo. Ter seed é o que torna o mundo gerenciado |
| `mundos.allowRuntimeGeneration` | `[]` | Mundos onde o portal pode criar o portal de destino |
| `barreira.defaultSize` | 10000 | Diâmetro inicial da borda |
| `portais.defaultSearchRadius` | 16 | Raio de busca do portal de destino |

As ligações ficam em `data/<ns>/aurorion/ligacoes/*.json`. O Portais tranca esses mundos por padrão.
Para liberar, inclua-os em `freeDimensions` ou crie uma linha.

### Aurorion Vidas (0.2.5)

**Jogador:** corações âmbar acima da barra de fome. Cada morte gasta uma vida. Com zero vidas, ele
renasce no exílio (o Limbo) e não sai por conta própria. As magias Ceifar Vida e Entregar Vida
**transferem** uma vida entre dois jogadores.

| `vidas-server.toml` | Padrão | Efeito |
|---|---|---|
| `vidas.maxLives` | 5 | Vidas por personagem e número de ícones |
| `vidas.ignoreCreative` | true | Morrer em criativo ou espectador não gasta vida |
| `exilio.exileDimension` | `aurorion_limbo:limbo` | Para onde vai quem zera. O README diz Nether, mas o padrão no código é o Limbo |
| `exilio.blockExileExit` | true | O exilado não sai da dimensão |
| `avisos.announceExileInChat` / `announceLifeLoss` | false / false | Anúncios públicos |

No cliente, `vidas-client.toml`: `hud.showHud` (true).

### Aurorion Limbo (0.4.7)

**Jogador:** floresta escura com céu do End e escuridão permanente. Um painel mostra o prazo em horas
reais. O Oráculo vende o Fio da Volta e o Relicário. Nas últimas horas, caminhar pode revelar a
Porta do Esquecido. Quando o prazo acaba: epílogo, "Você está morto" e desconexão.

Comandos: todo o `/limbo` é nível 2. `/oraculo` tem partes livres. Ver [1.1](#11-vidas-e-limbo) e
[1.9](#19-oráculo-do-limbo). A saída de `/limbo relatorio` é texto estável `chave=valor`, feito para
leitura por RCON e bots.

| `limbo-server.toml` | Padrão | Efeito |
|---|---|---|
| `prazo.horasDePrazo` | 48 | Horas reais até a morte definitiva. Servidor desligado não conta |
| `prazo.vidasAoSerResgatado` | 2 | Vidas mínimas ao ser resgatado |
| `porta.janelaEmHoras` | 5 | Janela final em que a Porta pode aparecer |
| `porta.blocosMinimo` / `blocosMaximo` | 1000 / 2000 | Caminhada necessária para a Porta |
| `porta.raioDaColeira` | 2000 | Distância máxima da âncora antes da janela (0 desliga). O README diz 300 |
| `porta.raioDeDispersao` | 400 | Dispersão da chegada e dos renascimentos |
| `resgate.custoEmVidas` / `vidasMinimas` | 1 / 2 | Custo do resgate pelo Oráculo |
| `resgate.minutosDaPassagem` | 15 | Duração da passagem aberta |
| `resgate.tagDoOraculo` | `aurorion_oraculo` | Tag que transforma um mob em Oráculo |
| `resgate.horaDaRotacao` | 0 | Hora real da rotação diária do Oráculo |
| `espolio.minutosDeProtecaoDosDrops` | 120 | Proteção dos itens da morte contra sumiço e limpeza |
| `espolio.precoDoFioDaVoltaEmObolos` / `precoDoRelicarioEmObolos` | 10 / 40 | Preços (0 tira da venda) |
| `avisos.anunciarQuedaNoChat` / `anunciarNomes` | false / false | Anúncio de queda |
| `auditoria.tambemNoLog` | true | Copia a auditoria para o log |

| Outros arquivos | Conteúdo |
|---|---|
| `limbo-finale-server.toml` | Tempos, música, frase e parágrafos do epílogo |
| `limbo-auditoria-startup.toml` | `auditoria.webhookUrl`: webhook do Discord para a auditoria. **Exige reinício** |

A auditoria fica em `<mundo>/aurorion_limbo/auditoria.jsonl`.

### Aurorion Personagem (0.3.2)

**Jogador:** no primeiro login fica em espectador, sem chat e sem comandos, até escolher nome e
sobrenome. Com o mod no cliente aparece uma tela; sem ele, o jogador usa
`/personagem criar <Nome> <Sobrenome>`. Depois da morte definitiva, o login é recusado até a staff
liberar. Ver [1.2](#12-personagem-morte-definitiva-e-alt).

Comandos livres: `/personagem criar` (só quando retido), `/personagem ver`, `/personagem trocar`
(só quem tem alt cadastrado) e `/personagem alt ver`.

`personagem-server.toml`:

- Textos da tela: `titulo`, `boasVindas`, `recomeco`, `regra`, `saudacao`, `primeirasPalavras`,
  `semAutorizacao`, `apagando`, `trocando`.
- `cobrarDeQuemJaJoga` (true): pede nome a quem já jogava, sem apagar nada.
- `altLigado` (true): liga os alts da staff. Com `false` eles são desligados sem apagar dados.

### Aurorion Economia (0.3.2)

**Jogador:** óbolos e fragmentos por personagem. `/saldo` e `/pagar <jogador online> <quantia>` são
livres. Com **Shift+G** olhando para outro jogador, abre um menu para cobrar, vender terreno
(corretor) e, com Profissões, pedir atendimento. O app de banco do celular usa esse saldo. Ver
[1.3](#13-economia).

| `economia-server.toml` | Padrão | Efeito |
|---|---|---|
| `houseUpgrades.cofre1..3`, `protetor1..2` | -1 (desativado) | Preço das melhorias da casa, em fragmentos |
| `land.zone0..zone4` | 1, 2, 3, 4, 5 | Fragmentos por bloco² (Periferia de Greymor, Residencial, Academia, Centro comercial, Premium) |
| `land.minimumPriceFragments` / `maximumSideBlocks` | 1 / 256 | Limites do terreno |

### Aurorion Magia (0.6.1)

**Jogador:**

- 55 magias autorais do Aurorion; 12 delas proibidas.
- Passivas.
- Domínios: Reino das Almas, Vazio Infinito e Santuário Malevolente.
- Selos e efeitos desenhados no cliente.
- Nada conjura sem liberação da staff.

Ver [1.6](#16-magia).

| `magia-server.toml` | Padrão | Efeito |
|---|---|---|
| `liberacao.staffBypass` | true | Nível 2 conjura tudo sem liberação |
| `liberacao.authoritative` | true | Só vale a lista do Aurorion; o que foi aprendido por fora é esquecido |
| `liberacao.forbiddenSpells` | `[]` | Magias de outros addons tratadas como proibidas (ex.: `irons_spellbooks:black_hole`) |
| `aliados.magiasIgnoramTime` | true | Magia ignora times e fogo amigo; PvP desligado e áreas sem PvP continuam protegendo |
| `passivas.dreadRadius` / `dreadKneelRadius` | 30 / 30 | Raio do medo e da prostração da Presença Aterradora |
| `passivas.dreadProstrates` / `dreadSparesAllies` | true / true | Dois joelhos; poupa o próprio time |
| `passivas.healingTouchHealsHostiles` | false | Mão que Cura também cura hostis |
| `destruicao.esferaQuebraBlocos` | true | Explosão da Esfera Espiritual quebra blocos |
| `destruicao.limiarJustica` / `limiarProfundezas` | 0.25 / 0.20 | Vida mínima para execução |
| `roleplay.controlesAteReconjurar` / `controlesSilenciam` | 8 controles | Controles que duram até reconjurar, Esconjurar ou leite / que também silenciam voz e chat |
| `roleplay.raioEsconjurar` | 8 | Raio do Esconjurar |
| `dominios.*` | Reino 600 ticks; Vazio raio 24, 400 ticks; Santuário raio 200, 600 ticks | Domínios. O raio 200 do Santuário foi apontado como pesado na [auditoria de 18/10](AUDITORIA-OTIMIZACAO-2026-10-18.md) e pode mudar |

No cliente, `magia-client.toml`: `visualDistance` (32) e `cameraShake` (1.0).

O tempo de recarga e o dano de cada magia seguem a config do próprio Iron's:
`config/irons_spellbooks_spell_config/aurorion_magia/<magia>.json`.

### Aurorion Profissões (0.5.2)

**Jogador:** uma profissão por personagem.

- **Médico:** cura e trata ferimentos do LSO.
- **Ferreiro:** bigorna e mesa de ferraria. A partir da 0.5.2 a mesa de ferraria é exclusiva dele.
- **Cozinheiro:** comida de qualidade que estraga mais devagar.
- **Arcanista:** encantamento acima do nível comum.
- **Corretor:** venda de terreno.

O atendimento entre jogadores é pedido pelo Shift+G. Os NPCs de ofício (enfermeira, ferreira, chef,
arcanista, mercador) têm loja em óbolos.

| Comando (todos nível 2) | |
|---|---|
| `/profissao definir\|ver\|mending_adm` | Ver [1.5](#15-profissão) |
| `/npc criar <id>` · `/npc definir <id>` (NPC a até 4 blocos) · `/npc remover` · `/npc listar` | NPCs de ofício |
| `/npc skin padrao` · `/npc skin <textura> [wide\|slim]` | Aparência |
| `/npc recarregar` · `/npc estoque <id> repor` · `/npc exemplo exportar` | Catálogo e estoque |

| `profissoes-server.toml` | Padrão | Efeito |
|---|---|---|
| `enabled` | true | Liga as regras de profissão |
| `commonEnchantmentMaxLevel` / `specialistEnchantmentMaxLevel` | 3 / 10 | Teto de encantamento para todos / para o arcanista |
| `chefQualityLevel` | 3 | Qualidade (Quality Food) da comida do cozinheiro |
| `severeInjuryThreshold` / `firstAidCeiling` | 0.35 / 0.5 | Ferimento grave e teto do primeiro socorro |
| `chefFoodDecayMultiplier` / `amateurFoodDecayMultiplier` | 0.5 / 2.0 | Velocidade com que a comida estraga (FoodSpoil) |
| `fixFoodSpoilThaw` | true | Correção do descongelamento do FoodSpoil |

O catálogo de NPCs vem no jar. Ajustes vão em `config/aurorion/npcs_extras.json` e valem com
`/npc recarregar`. Esmeralda é recusada como moeda.

### Aurorion Serviços (0.1.3)

**Jogador:** app "Serviços" no celular, com as abas Buscar, Pedidos, Vagas e Perfil e o botão
"Trabalhando". Pedidos e candidaturas terminam numa conversa no celular. Sem o celular instalado, o
app não aparece.

Sem comandos e sem config. Staff nível 2 pode remover qualquer anúncio ou vaga pelo próprio app. Os
limites são fixos no código: 3 anúncios, 3 pedidos abertos e 3 vagas por pessoa; pedido aberto
expira em 2 h e vaga em 14 dias.

### Aurorion Integração (0.2.2) — site

**Jogador:** `/vincular <código>` (livre) liga a conta ao site, com o código gerado no site. Mortes,
Limbo, portais e trocas de casa aparecem na linha do tempo do site.

| `integracao-startup.toml` (**exige reinício**) | Padrão | Efeito |
|---|---|---|
| `integracao.habilitado` | false | Liga o envio |
| `integracao.url` | `""` | Base da API (ex.: `https://aurorionstudios.cloud/api/v1/integration/v1`) |
| `integracao.token` | `""` | Mesmo valor do `GAME_API_TOKEN` do site. **Segredo**: não compartilhar o arquivo |
| `integracao.spoolMaxMiB` | 8 | Tamanho máximo da fila em disco (`<mundo>/aurorion_integracao/pendentes.jsonl`) |

Para conferir o estado, use `/aurorion diagnostico`.

### Aurorion Diário (0.1.3)

**Jogador:** `/diario` (livre) abre o diário do personagem. Rascunhos se salvam sozinhos, e o
jogador pode publicar e despublicar. É preciso ter vinculado a conta com `/vincular`. Sem o mod no
cliente, o comando manda um link do site. Com o site fora do ar, o servidor guarda o texto em
`<mundo>/aurorion_diario/pendentes.json` e sincroniza depois.

Sem config própria: usa a URL e o token da Integração.

### Aurorion Trama do Eco (0.1.2)

**Jogador:** `/trama` abre a árvore (tela do Pufferfish's Skills), `/trama progresso` mostra os
pontos e `/trama respec` refaz a árvore. Todos são livres. O respec é grátis na primeira vez; depois
tem recarga de 24 h e exige 15 s fora de combate. São até 45 pontos por personagem, ganhos com
tempo ativo, biomas novos e conquistas, nunca com abates. Sem casa, a árvore fica trancada.

| `trama-server.toml` | Padrão |
|---|---|
| `enabled` | true |
| `respecCooldownHours` | 24 |
| `progression.firstPointXp` / `xpCostIncrement` | 80 / 20 |
| `progression.firstPointMinutes` / `minuteCostIncrement` | 15 / 4 |
| `progression.xpPerActiveMinute` / `dailyPracticeXpCap` | 10 / 900 |
| `progression.firstBiomeXp` / `rewardedBiomeLimit` | 120 / 40 |
| `progression.firstAdvancementXp` / `advancements` | 150 / 38 conquistas |

Staff não tem comando na Trama.

---

## 3. Instalar uma atualização no servidor

Os jars vêm da máquina de build, na pasta `build/jars-servidor/`, com o nome
`<mod>-neoforge-1.21.1-<versão>.jar`. Cada entrega tem um `releases/<data>/README.md` que lista o
que mudou e os cuidados, mais o `SHA256SUMS.txt` com os hashes. Esta máquina de desenvolvimento
**não compila**.

### 3.1 Passo a passo

1. Ler o `releases/<data>/README.md` (ou o `docs/...-<data>.md` da correção) e conferir quais mods
   mudaram e se algum precisa ir junto com outro.
2. Conferir os hashes com o `SHA256SUMS.txt`.
3. **Parar o servidor e fazer backup** do mundo e das pastas `mods/` e `config/aurorion/`.
4. Na pasta `mods/` do servidor, **apagar o jar antigo** de cada mod atualizado e colocar o novo.
   Deve ficar **uma versão por mod**. O NeoForge carrega a mais nova, mas versões antigas
   acumuladas já apareceram no pack: 15 módulos duplicados em 04/10.
5. Publicar os mesmos jars na **origem do AutoModpack** do servidor, para os clientes sincronizarem
   no próximo login. Trocar jar só na pasta do cliente não basta: a sincronização seguinte desfaz a
   troca.
6. Subir o servidor e conferir no log se os mixins se aplicaram e se não há aviso de config antiga.
   Rodar `/aurorion diagnostico` se mexeu em Integração ou Diário.
7. Fazer o roteiro de ensaio da entrega com dois clientes, se a mudança envolver outro jogador.

### 3.2 O que vai onde

| Jar | Servidor | Clientes (via AutoModpack) |
|---|---|---|
| `aurorion_core` | sim | sim, porque os outros mods exigem |
| `aurorion_aeonita`, `aurorion_ethereal`, `aurorion_utils`, `aurorion_limbo` | sim | **obrigatório** (itens, blocos, entidades) |
| `aurorion_magia` | sim | **obrigatório, mesma versão** (protocolo 9). Cliente com outra versão não entra |
| `aurorion_economia`, `aurorion_profissoes`, `aurorion_servicos`, `aurorion_trama`, `aurorion_diario` | sim | **obrigatório** |
| `aurorion_talk`, `aurorion_essentials`, `aurorion_areas`, `aurorion_personagem`, `aurorion_vidas` | sim | recomendado: sem ele o jogador entra, mas perde a parte visual (balões, telefone, névoa, tela de nome, HUD) |
| `aurorion_portais`, `aurorion_mundos` | sim | recomendado: as mensagens são traduzidas pelo jar do cliente |
| `aurorion_integracao` | sim | só se o cliente tiver o Diário, que o exige |
| `c2me-neoforge-mc1.21.1-0.4.0-alpha.0.120.jar` (remendado, sem ScalableLux) | sim | sim. Substitui o C2ME original, que derrubava o cliente |

Dependências de terceiros que precisam estar no pack:

| Mod | Exigido por |
|---|---|
| GeckoLib | aeonita |
| Iron's Spells 3.16.3 + irons_lib + Curios + PlayerAnimator | magia |
| Pufferfish's Skills 0.19.x | trama |
| TesseraUI ≥ 1.1 (só cliente) | profissões e diário |

São opcionais, com integração automática: Iron's Restrictions, Simple Voice Chat, Emotecraft, LSO,
Quality Food, FoodSpoil, Mattupolis Phone, Immersive Messages, ADM, Simply More, Alex's Mobs (para o
guaxinim do Capricho; sem ele, o alvo vira raposa).

### 3.3 Entrega pendente (preparada em 17–18/10, ainda não compilada)

Instalar **juntas** depois de compilar e validar na máquina de build:

| Mod | Versão |
|---|---|
| Magia | 0.6.1 |
| Vidas | 0.2.5 |
| Utils | 0.2.4 |
| Profissões | 0.5.2 |
| Essentials | 0.8.0 (grupos no celular, Gram salvo no servidor) |
| Core | 0.6.5 (exigido pelo Personagem 0.3.2) |
| Personagem | 0.3.2 |
| Aeonita, Áreas, Ethereal, Limbo, Mundos, Serviços, Talk, Trama | 0.3.3, 0.6.4, 0.3.5, 0.4.7, 0.1.3, 0.1.3, 0.1.3, 0.1.2 (otimizações) |

- Todos esses vão também para **todos** os clientes (via AutoModpack); Essentials e Talk são obrigatórios no cliente para grupos, Gram e balões.
- A Magia 0.6.1 pede Vidas 0.2.5 ou superior no servidor para Ceifar e Entregar Vida.
- Manter `aliados.magiasIgnoramTime = true` na config da Magia.

Roteiro de ensaio: [CORRECOES-MAGIAS-SIMPLYMORE-2026-10-17.md](CORRECOES-MAGIAS-SIMPLYMORE-2026-10-17.md).

O pack tem dois jars do Citadel (`citadel-1.21.1-2.7.6.jar` e `citadel-up-0.2.0.jar`) com o mesmo
modId. Só um carrega: remova o desnecessário.
