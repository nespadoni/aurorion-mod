# Aurorion Profissões

Minecraft 1.21.1 / NeoForge 21.1.248 / Java 21. Mecânicas de ofício integradas ao menu
presencial do `aurorion-economia`. Instalar **no servidor e nos clientes**, junto com os dois mods.
Os JARs dos mods integrados continuam separados; nenhum é embutido neste módulo.

## Uso pelos jogadores

- **Shift+G**, olhando para outro jogador, abre o menu presencial configurável da Economia.
- Quem tem a profissão de médico pode escolher **Curar pessoa** nesse menu para restaurar a saúde
  e todos os ferimentos corporais do alvo, sem alterar as vidas perdidas por morte.
- Quando o alvo é médico, ferreiro, cozinheiro ou arcanista, o menu oferece o atendimento dele.
- O corretor está cadastrado como profissão; a seleção e venda retangular de terrenos entra na
  próxima etapa da integração territorial.
- O cliente segura o equipamento, alimento ou poção na **mão principal**.
- O profissional segura os materiais na **mão secundária**. O painel explica o que falta.
- O cliente escolhe; o profissional recebe uma janela para **aceitar ou recusar**.
- Ambos permanecem a até 4 blocos, com linha de visão. Cada pedido vale 30 segundos.
  Trocar os itens, afastar-se, morrer ou desconectar invalida o atendimento.
- Os materiais e a experiência são do profissional. O item continua com o cliente durante
  todo o atendimento; não há inventário intermediário nem entrega por comando.

Interface baseada em `NpcPanelScreen` do Core, com Cinzel, fundo escuro, detalhes dourados,
linhas de serviço e rolagem. Nenhum comando de jogador é necessário.

## Uma profissão por personagem

A staff atribui uma profissão. Atribuir outra substitui a anterior. O dado é persistido no mundo,
sobrevive à morte comum e é removido pelo evento de criação de um novo personagem do Aurorion.

Comandos **somente de staff**, nível de permissão 2:

```text
/profissao definir <jogador> medico
/profissao definir <jogador> ferreiro
/profissao definir <jogador> cozinheiro
/profissao definir <jogador> arcanista
/profissao definir <jogador> corretor
/profissao definir <jogador> nenhuma
/profissao ver <jogador>
/profissao mending_adm true
/profissao mending_adm false
```

`mending_adm` autoriza o equipamento na mão principal; não adiciona o encantamento.
A staff pode encantá-lo com as ferramentas administrativas e entregá-lo ao jogador.
A autorização acompanha aquele equipamento. Criativo com OP pode usar a bigorna para preparar itens.

## Médico — Legendary Survival Overhaul

Integração com **2.4.7.2**, usando as oito partes do corpo reais do LSO.
Precisa do sistema de dano localizado do LSO habilitado.

- Todos continuam usando bandagens, ervas e gesso como primeiros socorros do LSO em ferimentos leves.
- Medkit e tônico, que tratam o corpo inteiro, só podem ser usados por personagens médicos.
  Ao terminar o uso, eles tratam as oito partes reais do LSO e removem lesões graves.
- Quando a saúde de um membro cai para **35% ou menos**, a lesão fica marcada como grave.
- Primeiros socorros estabilizam uma lesão grave até **50%** da saúde do membro.
  Repetir curas não remove a lesão; a marca sobrevive ao salvamento do jogador.
- Outro médico pode tratar o membro pela UI: **1 medkit ou tônico do LSO** restaura a saúde desse membro
  e remove a lesão. Não há atendimento clínico de si mesmo.
- A opção **Curar pessoa** do menu Shift+G restaura saúde e ferimentos, mas nunca recupera
  vidas perdidas por morte. Ela exige a profissão de médico e proximidade, mas não consome medkit.
- Os insumos do atendimento usam a tag `aurorion_profissoes:medical_supplies`. Os consumíveis
  globais exclusivos usam `aurorion_profissoes:doctor_only_healing`; ambas aceitam extensão por datapack.

Sem o LSO, o módulo inicia normalmente e o atendimento médico mostra a integração indisponível.

## Ferreiro e Mending

- A bigorna só funciona para ferreiros. O servidor confere também a retirada do resultado.
- Combinar dois equipamentos para reparar na grade de criação ou na pedra de amolar fica bloqueado.
  Desencantar **um** item na pedra de amolar continua permitido.
- Atendimento pela UI exige bigorna a até 3 blocos do ferreiro. Cada material adequado recupera
  até **25% da durabilidade máxima** e custa **1 nível** do ferreiro. Materiais insuficientes
  para o reparo completo permitem um reparo parcial, informado antes do aceite.
- Encantamentos existentes são preservados durante o reparo. A bigorna não permite ao ferreiro
  acrescentar encantamentos acima do limite comum.
- Mending é removido dos novos resultados de loot/pesca e das novas ofertas de aldeões.
  É excluído da mesa e dos serviços; encantamentos comuns já existentes ficam sem efeito de reparo.
- A exceção é o equipamento marcado pela staff com `mending_adm`.

Livros e ofertas antigos podem continuar aparecendo no mundo; não podem aplicar Mending útil
por estas mecânicas. Não foi feita migração destrutiva dos inventários existentes.

## Cozinheiro — Quality Food + FoodSpoil

Integrações com **Quality Food 2.3.6** e **FoodSpoil 1.1.7**:

| Produção | Qualidade | Velocidade de deterioração |
|---|---|---|
| Cozinheiro | Diamante, nível 3 | 0,5× a taxa normal |
| Outro personagem ou automação sem autor | Comum | 2× a taxa normal |

Aplica-se a alimentos com o componente `minecraft:food`, no crafting e nos caminhos de
cozimento do Quality Food: retirada de fornalhas e suas integrações de cozinha que passam
por `Utils.useQuality`, além de receitas que passam por `QualityUtils.applyQuality`.
Fogueiras produzem comida comum, pois não identificam o cozinheiro na saída.
Ingredientes colhidos e loot mantêm a qualidade natural do Quality Food.

O cozinheiro também pode **finalizar um lote de até 16 alimentos** pela UI, usando **1 frasco de mel**,
com devolução da garrafa. O lote precisa ter pelo menos **60% de frescor** e ainda não ter preparo
profissional. A finalização mantém a idade: não recupera comida estragada.
Transferir comida pronta a outro jogador mantém qualidade e conservação.

O empilhamento especial do FoodSpoil não mistura qualidades/preparos diferentes.
A receita de mistura do FoodSpoil é desabilitada para lotes marcados, porque seu cálculo não
considera as taxas diferentes; o empilhamento de preparos iguais pelo inventário continua disponível.

Sem FoodSpoil não há deterioração adicional. Sem Quality Food não há qualidade premium nem
finalização pela UI; o marcador de conservação ainda é aplicado nos eventos vanilla suportados.

### Remendo: comida congelada apodrecia ao descongelar

O FoodSpoil **1.1.7** tem um bug que este módulo corrige, e que não tem nada a ver com profissões:
congelar não parava o relógio, só escondia o resultado.

Em `FoodData.calculateFreshness`, os estados `FROZEN` e `THAWING` devolvem 100% sem calcular nada.
O frescor real sai de `SnapshotFreshness − (agora − SnapshotTime) × taxa`. Ao **congelar**, o mod
grava um snapshot correto; ao **descongelar** — nos três lugares onde isso acontece
(`onContainerClose`, `onPlayerTick` e `processContainerThawing`) — ele faz só
`setState(FRESH)` + `setThawStart(0)` e **nunca reancora o `SnapshotTime`**.

Resultado: no instante em que sai do congelamento, todo o tempo que a comida passou congelada é
cobrado de uma vez. Com a taxa padrão (0,3% por minuto de MC = **6% por dia de jogo**), 100% de
frescor somem em ~17 dias de jogo — que são **menos de 6 horas de servidor no ar**. Ou seja: qualquer
comida que passe uma noite congelada sai do congelador em 0% e vira carne podre na hora, pelo
`RottenConversionHandler`. Num servidor 24/7 esse é o caso normal, não a exceção.

O remendo é um `@Inject` em `FoodData.setState`: ao sair de `FROZEN`/`THAWING`, reancora o
`SnapshotTime` para agora e **mantém** o `SnapshotFreshness` gravado no congelamento. A comida volta
do congelador com o frescor que entrou. Escrevemos só o campo do tempo, para conviver com uma
eventual correção oficial.

Fica em `setState`, e não nos três handlers, porque a troca de estado é o evento — os handlers são
só quem o dispara, e um quarto que apareça numa versão nova passa pelo mesmo lugar.

`fixFoodSpoilThaw` desliga o remendo. Ele **não** passa por `enabled`: desligar profissões não pode
devolver o bug do congelador.

O `FoodSpoilContractTest` valida os três pontos contra o jar instalado, inclusive que a origem
**ainda** não corrigiu o bug — se corrigir, o teste falha avisando que o remendo pode ser apagado.
Ele é pulado sem `AURORION_FOODSPOIL_JAR`.

### Correção: xadrez roxo e preto na comida sem qualidade

Comida feita por quem não é cozinheiro aparecia no slot com o quadrado roxo e preto de textura
faltando por cima. A causa era nossa, na interação com um detalhe do Quality Food 2.3.6.

O Quality Food só pula o desenho do ícone de qualidade quando `quality == Quality.NONE` —
**comparação por referência** (o mesmo `==` está em `Quality.getType()` e em
`QualityUtils.isValidQuality`). O `FoodCompat.finish` gravava o próprio `Quality.NONE` no componente
da comida comum. Salvar o item ou mandá-lo ao cliente devolve uma **cópia** igual ao NONE, mas outro
objeto: ela passa no teste, o tipo cai em `QualityType.NONE`, cujo ícone é `quality_food:none` — um
sprite que não existe. Daí a textura faltando.

- **Comida nova:** sem qualidade agora é **sem o componente**; nada de NONE gravado.
- **Comida que já existe:** o `QualityUtilsMixin` troca qualquer cópia do NONE (e do
  `PLAYER_PLACED`) pelo original em `QualityUtils.getQuality` — conserta a tela até nos baús, sem
  varrer o mundo. No login o componente "none" também sai do inventário e do ender chest, para a
  comida antiga voltar a empilhar com a nova.
- O empilhamento especial do FoodSpoil trata "sem componente" e "none" como o mesmo preparo.

O `QualityFoodContractTest` confere contra o jar que o Quality Food **ainda** compara por referência:
se uma versão nova corrigir, o teste falha avisando que o `aurorion$canonicalNone` pode sair. É pulado
sem `AURORION_QUALITY_FOOD_JAR`.

## Arcanista

- Qualquer personagem pode usar as duas primeiras opções da mesa de encantamentos, com nível máximo
  **III** por encantamento. A terceira opção aparece bloqueada e explica que exige um arcanista.
- Arcanistas podem usar as três opções normais da mesa sem esse corte, até o limite configurado.
- A escolha também é validada pelo servidor; alterar o cliente ou forjar o pacote não libera a terceira opção.
- Na UI, o arcanista inscreve os encantamentos de um **livro encantado real**, consumindo-o,
  sobre equipamento compatível ou livro comum. Exige mesa a até 3 blocos e **2 níveis de XP
  por nível acrescentado**. Não cria níveis que não existam no livro e não combina livros iguais.
- O limite especializado padrão é **X**: um livro Protection V vindo do pack pode ser aplicado;
  a mesa vanilla continua gerando apenas os níveis que suas receitas permitem.
- Poções básicas continuam disponíveis no suporte. Os aprimoramentos de **redstone/glowstone**
  são feitos pelo arcanista na UI, com suporte a até 3 blocos e um catalisador por poção.
  Usa as receitas reais do jogo; não inventa amplificadores ou durações além delas.
- Suportes automáticos também não produzem esses dois aprimoramentos.

## NPCs de ofício

NPCs que cobrem o atendimento **quando não há um profissional jogador por perto**.

**O catálogo mora no mod** (`aurorion_profissoes/npcs.default.json`, dentro do jar): enfermeira,
ferreira, chef, arcanista e mercador, com skins, serviços, lojas e preços da Economia do Ato 2.
**Atualizar o jar atualiza os NPCs** — nada fica congelado na config do servidor.

Ajustes do servidor vão em `config/aurorion/npcs_extras.json` (criado vazio), mesmo formato, aplicados
com `/npc recarregar` sem rebuild. NPC com **id novo** é acrescentado; com o **id de um NPC do mod**,
substitui aquele NPC inteiro. `/npc exemplo exportar` grava uma cópia do catálogo do mod em
`config/aurorion/npcs_exemplo.json` para consulta (essa cópia não é carregada).

O `npcs.json` das versões até a 0.4.x é **aposentado** na primeira leitura: vira
`npcs.json.antigo-<data>` e deixa de valer, com aviso no log e em `/npc listar`. Se havia algo
editado à mão nele, copie o NPC para o `npcs_extras.json`.

**Esmeralda não é moeda em Aurorion.** `price_item`/`cost_item` com `minecraft:emerald` é recusado
no carregamento (a oferta ou o serviço some e o aviso aparece em `/npc listar`). Preço é em
óbolos/fragmentos (`_money`); `_item` fica para material de verdade.

Clique direito no NPC abre a tela do ofício, em HTML/CSS do **TesseraUI** (`assets/aurorion_profissoes/ui/npc.css`):
cabeçalho com nome, saldo da carteira e saudação; abas **Loja**, **Serviços** e **Conversar**; o item
real desenhado em cada oferta (com encantamento/poção e tooltip); paginação pela roda do mouse ou
pelo rodapé. A cor de destaque segue o ofício. O cliente só desenha; preço, estoque, saldo, distância
(8 blocos) e regras são conferidos de novo no servidor a cada escolha. O TesseraUI é exigido **só no
cliente**; o servidor nunca carrega classe dele.

### Preços: Economia do Ato 2

O exemplo segue o *Manual Operacional V3* da economia:

- **Serviços** custam cerca de **2,5×** o preço de referência do profissional jogador (regra do “NPC
  de emergência”), e são bloqueados quando há um jogador do ofício por perto (ver Plantão).
- **Itens** seguem a coluna **“NPC vende”** da tabela-base, no lote da tabela (ex.: 512 pedras por
  2 F, 64 ferros por 1 O 2 F). Item sem linha na tabela foi classificado pela tabela de classes.
- O pagamento sai da carteira e **é destruído** (100% burn, como o manual manda para NPC
  emergencial). NPC **não compra** nada.

| Serviço | Referência do jogador | NPC |
|---|---:|---:|
| Curativo / ferimento moderado | 3–8 F | 1 O / 2 O |
| Lesão grave / trauma crítico | 2–6 O | 6 O / 12 O |
| Reparo leve / médio / pesado / quase destruído | 5 F / 1 O / 2 O / 3 O | 1,3 / 2,5 / 5 / 7,5 O |
| Encantamento médio / alto / máximo comum | 2 / 4 / 5–6 O | 5 / 10 / 14 O |
| Fortune, Looting, Silk Touch | 6–8 O | 18 O |
| Concentrar ou prolongar poção | 1,5–2 O | 4 O |
| Etiqueta com nome | — | 2 O |

### Comandos da staff (nível 2)

```text
/npc criar <id>           invoca o NPC na sua posição, olhando para onde você olha
/npc definir <id>         troca o id do NPC mais próximo (até 4 blocos)
/npc skin <textura> [wide|slim]  troca a skin do NPC mais próximo e salva no mundo
/npc skin padrao          volta à skin do ofício (ou à do catálogo, se ele definir uma)
/npc remover              remove o NPC mais próximo (até 4 blocos); /kill também funciona
/npc listar               ids carregados e avisos do arquivo
/npc recarregar           relê o JSON e atualiza nome/skin dos NPCs carregados
/npc estoque <id> repor   repõe o estoque das ofertas limitadas
/npc exemplo exportar     grava o catálogo do mod em config/aurorion/npcs_exemplo.json, só para consulta
```

A entidade guarda o `id` e, opcionalmente, uma skin escolhida in-game. Nome, serviços e loja vêm do
JSON; a skin vem do ofício, embutida no mod. Editar e recarregar muda todos os corpos daquele id, exceto a skin dos corpos
com override. Um JSON que nem é JSON **não** derruba os NPCs já carregados.

### Plantão: o NPC não concorre com jogadores

Se um jogador com a mesma `profession` estiver a até `fallback_radius` blocos do NPC (padrão 64;
`-1` = servidor inteiro; `0` = desliga), os **serviços** ficam bloqueados e a tela diz quem procurar.
A loja continua aberta, a menos que `fallback_blocks_trades` seja `true`. Mercadores
(`"profession": "mercador"`) nunca bloqueiam.

### Formato

```json
{
  "npcs": [
    {
      "id": "medico_principal",
      "display_name": "Médico do Vilarejo",
      "title": "Casa de Cura",
      "profession": "medico",
      "skin": "meupack:textures/entity/npc/medico.png",
      "slim_skin": false,
      "greeting": "Sente-se. Deixe-me ver esses ferimentos.",
      "dialogue": ["Falas mostradas em Conversar."],
      "adm_dialogue": "",
      "fallback_radius": 64,
      "fallback_blocks_trades": false,
      "services": [
        {
          "name": "Cura Completa",
          "description": "Trata todas as partes do corpo.",
          "action_type": "command",
          "command": "bodydamage heal {player} all 100",
          "cost_item": "minecraft:emerald",
          "cost_amount": 5,
          "cost_money": "2,5",
          "cooldown_seconds": 60
        }
      ],
      "trades": [
        {
          "id": "espada",
          "item_id": "minecraft:diamond_sword",
          "components": "[enchantments={levels:{\"minecraft:sharpness\":2}}]",
          "nbt_data": "{marca:1b}",
          "amount": 1,
          "price_item": "minecraft:emerald",
          "price_amount": 10,
          "price_money": "5",
          "max_stock": 5,
          "restock": "daily"
        }
      ]
    }
  ]
}
```

| Campo | Observação |
|---|---|
| `profession` | `medico`, `ferreiro`, `cozinheiro`, `arcanista`, `corretor`, `mercador`/`nenhuma`. Aceita `doctor`, `blacksmith`, `chef`, `arcanist`, `alquimista`, `merchant`. |
| `skin` | **Opcional.** Sem ele, o NPC usa a skin embutida do ofício (`NpcSkins`): médico → enfermeira, ferreiro → ferreira (ambas de braço fino), cozinheiro → chef, arcanista → arcanista, mercador/corretor → mercador. Só preencha para trocar: textura 64×64 de resource pack; caminho inexistente aparece como textura faltando. `slim_skin` só vale junto com `skin`. |
| `action_type` | `command`, `heal`, `repair`, `finish_food`, `enchant`, `potion_strength`, `potion_duration`, `name_tag`. Sem o campo: `command` se houver comando. |
| `tier` | Só em `heal` e `repair`: a faixa que o serviço atende — `leve`, `medio`/`moderado`, `pesado`/`grave`, `critico`/`quase_destruido` (ou 1–4). Sem o campo: qualquer faixa. |
| `enchantment` / `level` | Só em `enchant`: id do encantamento e nível aplicado. Mending é recusado; nível acima de `specialistEnchantmentMaxLevel` também. |
| `command` / `commands` | Um texto ou lista (até 8); `/` inicial opcional. Rodam em qualquer tipo de ação. |
| `cost_*` / `price_*` | `_item` (+ `_amount`, padrão 1) e/ou `_money` em óbolos da Economia (`"12"`, `"2,5"`). Nada: gratuito. `price_item_id` também é aceito. |
| `max_stock` | Ou `stock_limit`. `-1` (padrão) = infinito. Estoque do NPC, compartilhado entre jogadores. |
| `restock` | `restart` (padrão, a cada reinício), `daily` (virada do dia do relógio do servidor), `never`. |
| `components` | Mesma sintaxe do `/give` 1.21.1, entre colchetes. |
| `nbt_data` | SNBT (texto ou objeto) gravado em `minecraft:custom_data`. |
| `id` da oferta | Opcional, mas recomendado: sem ele o estoque é identificado pela posição e reordenar a lista o zera. |

**Ações nativas**

- `heal`: vida cheia e, com o LSO, todas as partes do corpo tratadas (inclusive lesão grave).
  Recusa se o jogador já está saudável — ninguém paga por nada. Com `tier`, só atende aquela
  gravidade: **leve** (vida e membros acima de 50%), **moderado** (algo abaixo de 50%, sem lesão
  grave), **grave** (uma lesão grave do LSO ou membro abaixo de 25%), **crítico** (duas lesões graves,
  ou uma com a vida em 25% ou menos). A tela diz qual faixa é a do paciente.
- `repair`: durabilidade total do equipamento na mão principal, encantamentos preservados. Com
  `tier`, só atende aquele desgaste: até 25%, 50%, 75% ou acima.
- `finish_food`: o mesmo acabamento do cozinheiro (Quality Food), com as mesmas exigências de lote.
- `enchant`: aplica `enchantment` no nível `level` ao item da mão principal, com as regras da
  inscrição do arcanista (compatível com o item e com os encantamentos que ele já tem). Livro comum
  vira livro encantado. Não rebaixa: se o item já tem o nível, recusa.
- `potion_strength` / `potion_duration`: o efeito da glowstone/redstone na poção da mão principal,
  pelas receitas reais do jogo, sem precisar do catalisador.
- `name_tag`: a tela mostra um campo de texto; o jogador digita o nome e recebe uma **etiqueta já
  gravada**. Mesmo filtro da bigorna (sem `§`, até 50 caracteres), conferido de novo no servidor;
  o nome sai no log. Enter no campo também grava.

**Comandos** rodam como o servidor (nível 4), posicionados no jogador e com ele como executor:
`@s` e `@p` apontam para quem pagou. Marcadores: `{player}`, `{uuid}`, `{npc}`, `{x}`, `{y}`, `{z}`. Como os comandos rodam com permissão 4, `{player}` só entra cru se o nome for de conta Mojang (`[A-Za-z0-9_]`, até 16); qualquer outro nome (possível em offline-mode, como `@a`) vira o UUID, para não virar seletor.
A saída vai para o log. No tipo `command`, se **nenhum** comando der certo, o pagamento é devolvido.

**Pagamento:** conferido e cobrado no mesmo tick; itens saem de qualquer slot do inventário
(qualquer pilha daquele item conta). Dinheiro sai da carteira da Economia e não vai para ninguém.
Staff em criativo não paga. A compra entra no inventário; o que não couber cai no chão.

**Conversar:** com o ADM instalado e `adm_dialogue` definido (`"namespace:arquivo"`), abre a árvore
de diálogo; senão mostra as falas de `dialogue`.

Limites: 16 serviços, 32 ofertas e 16 falas por NPC. Erros no arquivo aparecem com o caminho
(`npcs[1].trades[0].price_amount`) em `/npc recarregar`, `/npc listar` e no log; só o pedaço
quebrado é ignorado.

## Configuração e extensão

Config nativa em `config/aurorion/profissoes-server.toml`:

| Chave | Padrão |
|---|---:|
| `enabled` | `true` |
| `commonEnchantmentMaxLevel` | 3 |
| `specialistEnchantmentMaxLevel` | 10 |
| `chefQualityLevel` | 3 |
| `severeInjuryThreshold` | 0.35 |
| `firstAidCeiling` | 0.5 |
| `chefFoodDecayMultiplier` | 0.5 |
| `amateurFoodDecayMultiplier` | 2.0 |
| `fixFoodSpoilThaw` | `true` |

`ProfessionApi` consulta o ofício. `ServiceEvent.Validate` permite vetar um atendimento antes
do consumo; `ServiceEvent.Completed` informa sua conclusão com identificador único.
Esses pontos permitem acrescentar orçamento e cobrança na mesma interface. **Economia ainda não
está implementada**: antes de cobrar, será necessário adicionar reserva/reembolso e registro
persistente da transação. Não debitar dinheiro irreversivelmente no evento de validação.

O servidor valida profissão, itens, materiais, distância, estação e XP novamente no aceite.
Tokens pertencem a um jogador, expiram e são consumidos uma vez. Não há varredura por tick
de todos os jogadores, inventários ou entidades; as consultas de estação ocorrem só ao atender,
num raio limitado e sem carregar chunks.

## Build e validação

```powershell
.\gradlew.bat :aurorion-profissoes:build
.\gradlew.bat :aurorion-profissoes:runGameTestServer
# Para testar as pontes, acrescente os caminhos dos JARs reais:
# -PlsoJar="...jar" -PqualityFoodJar="...jar" -PfoodSpoilJar="...jar"
.\gradlew.bat buildAll
```

JAR instalável em `../build/jars-servidor/aurorion_profissoes-neoforge-1.21.1-0.5.0.jar`.
A partir da 0.5.0 o protocolo de rede é o `"3"`: cliente e servidor precisam da mesma versão, e o
cliente precisa do `tesseraui-1.1.jar` (já exigido pelo diário).
JUnit cobre persistência, primeira ajuda e sessões. GameTests usam jogadores simulados,
menus vanilla e os mods reais para conferir aceite, repetição, troca de item, distância,
materiais/XP, Mending, ferimentos, poções e produção/conservação de comida.
Os testes dos adaptadores só executam quando seus mods estão presentes.

**Limites da validação:** não houve teste visual com dois clientes nem teste do modpack completo
com jogadores reais. Máquinas/receitas especiais de outros mods que não passam pelas APIs acima
precisam de adaptadores próprios; este módulo não intercepta toda alteração de durabilidade,
encantamentos, receitas ou poções de qualquer mod.

## Ideias a partir do pack instalado

- **Farmer's Delight, Croptopia e Farm & Charm:** encomendas de refeições, cardápios e ingredientes
  regionais para o cozinheiro; Quality Food já oferece caminhos compartilhados de produção.
- **HerbalBrews + LSO:** receitas de medicamentos e insumos para o médico, além do medkit inicial.
- **Aquaculture:** pescaria como fornecimento de ingredientes e encomendas culinárias.
- **Create:** produção comum em escala abastecendo oficinas e cozinhas; finalização premium continua
  vinculada a um profissional identificável.
- **Iron's Spellbooks:** serviços de pergaminhos e inscrições, após definir os limites com o arcanista.
- **Bountiful:** contratos de fornecimento e atendimento; depois, conectar à cobrança pela UI.

Estas são propostas de próximas integrações, não mecânicas adicionais já implementadas.
