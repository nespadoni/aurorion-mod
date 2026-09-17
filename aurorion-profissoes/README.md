# Aurorion Profissões

Minecraft 1.21.1 / NeoForge 21.1.248 / Java 21. Primeira fase das mecânicas de ofício,
sem moeda ou cobrança. Instalar **no servidor e nos clientes**, junto com `aurorion-core`.
Os JARs dos mods integrados continuam separados; nenhum é embutido neste módulo.

## Uso pelos jogadores

- **J** abre os serviços do próprio personagem. A tecla pode ser alterada em Controles.
- **Agachar + botão direito em outro jogador** abre os serviços dele.
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

- Todos continuam usando os primeiros socorros e a recuperação normal do LSO em ferimentos leves.
- Quando a saúde de um membro cai para **35% ou menos**, a lesão fica marcada como grave.
- Primeiros socorros estabilizam uma lesão grave até **50%** da saúde do membro.
  Repetir curas não remove a lesão; a marca sobrevive ao salvamento do jogador.
- Outro médico pode tratar o membro pela UI: **1 medkit do LSO** restaura a saúde desse membro
  e remove a lesão. Não há atendimento clínico de si mesmo.
- O consumível é definido pela tag `aurorion_profissoes:medical_supplies`, extensível por datapack.

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

JAR instalável em `../build/jars-servidor/aurorion_profissoes-neoforge-1.21.1-0.1.0.jar`.
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
