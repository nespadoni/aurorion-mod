# Aurorion Ato 2

Mecânicas **exclusivas do Ato 2**. Tudo aqui tem prazo de validade: quando o ato acabar, este jar
sai do modpack e não leva junto nenhum item, bloco ou receita — o conteúdo mora no
[aurorion-aeonita](../aurorion-aeonita/), que fica.

Por isso não existe um único `DeferredRegister` neste mod. Registrar conteúdo aqui significaria que
desligar o Ato 2 apagaria coisa do inventário dos jogadores.

Primeira feature: **escolha de casa**.

## Escolha de casa

O jogador clica com o botão direito num **Altar de Seleção**, escolhe uma casa numa tela e confirma.
A escolha é definitiva por padrão — só a staff desfaz.

As casas **não estão em código**: cada uma é um JSON de datapack. Adicionar, remover, renomear,
recolorir ou botar limite de membros é editar arquivo e dar `/reload`.

```json
// data/<namespace>/aurorion/houses/solar.json
{
  "name": "Casa Solar",
  "description": "Os que carregam a luz para fora.",
  "color": "#FFF305",
  "icon": "aurorion_aeonita:aeonita_ingot_yellow",
  "capacity": 0,
  "order": 1
}
```

| campo | obrigatório | o que faz |
|---|---|---|
| `name` | sim | texto (string simples ou componente JSON) |
| `description` | não | aparece no card da tela, quebrado em linhas automaticamente |
| `color` | não | `"#RRGGBB"`; tinge a borda do card e o nome no chat. Padrão branco |
| `icon` | não | id de item desenhado no card. Item inexistente vira papel, não crash |
| `capacity` | não | `0` (padrão) = sem limite. Acima disso a casa lota e some das opções |
| `order` | não | ordem na tela; empate desempata pelo id |

As três casas que vêm no jar (`solar`, `abissal`, `ignea`) são **exemplos** amarrados às três cores
de Aeonita. Sobrescreva com um datapack ou edite direto — é para isso que elas são dado.

### Que bloco é um altar

Qualquer bloco na tag `aurorion_ato2:house_altars`. O jar já inclui o
`aurorion_aeonita:selection_altar` nela, com `"required": false` — se o mod de conteúdo não estiver
instalado, a tag simplesmente fica vazia em vez de quebrar o datapack.

Isso é o acoplamento inteiro entre os dois mods: **uma tag, nenhum import**. Consequências práticas:
dá para desligar um dos dois sem o outro quebrar, dá para promover qualquer bloco a altar por
datapack (inclusive um bloco de outro mod do modpack), e o Ato 3 pode reaproveitar o mesmo altar.

## Uso

```
/casa                             -> mostra a sua casa (qualquer jogador)

/casa listar                      -> todas as casas e quantos membros cada uma tem   (nivel 2)
/casa ver <jogador>               -> a casa de alguem, mesmo offline                 (nivel 2)
/casa definir <jogador> <casa>    -> atribui, ignorando ritual, trava e lotacao      (nivel 2)
/casa limpar <jogador>            -> tira da casa; ele pode escolher de novo         (nivel 2)
```

O jogador comum só tem `/casa` (consulta). Quem escolhe é o altar — o ritual seria decorativo se
desse para pular ele digitando um comando.

`ver`, `definir` e `limpar` aceitam jogador offline: a casa é gravada por UUID, não por entidade.

## Configuração

`config/aurorion_ato2-server.toml`:

```toml
[houses]
allowRechoose = false   # true deixa o jogador voltar ao altar e trocar de casa
announceInChat = true   # anuncia a escolha no chat do servidor
```

O que **não** é config é tão proposital quanto o que é: nome, cor, ícone e lotação de casa são
datapack. Config é para regra de servidor; conteúdo é para dado.

## Arquitetura

```
house/       House (record + Codec do JSON + StreamCodec da rede), HouseCatalog (reload listener),
             HouseData (SavedData por UUID), HouseManager (as regras), HouseOption (casa + lotacao),
             PendingSelections (quem tem tela aberta e em qual altar)
network/     Ato2Network (registro), OpenHouseSelectionPayload, ChooseHousePayload,
             HouseChoiceResultPayload
client/      Ato2ClientNetwork, gui/HouseSelectionScreen, gui/HouseCardWidget
command/     HouseCommand
event/       Ato2ServerEvents (reload listener, clique no altar, logout)
config/      HouseConfig
Ato2Tags     a tag de altares
```

### Decisões

- **Zero trabalho por tick.** A escolha é um evento raro — uma vez por jogador, na vida do
  personagem. O custo total da feature é um evento de clique, um pacote de ida e um de volta. Não há
  ticker, timer nem varredura de jogadores em lugar nenhum ([SDD §7](../SDD.md), diretriz 1).
- **O prazo da tela é um `long` comparado na hora, não uma contagem regressiva.** `PendingSelections`
  guarda o game time de expiração; uma entrada vencida não custa nada até alguém tentar usá-la, e
  some no logout.
- **Nada trafega no login.** O catálogo inteiro vai junto com o pacote que abre a tela — O(casas), e
  só quando alguém clica num altar. Isso é o que permite as casas serem datapack: o cliente não
  precisa ter os JSONs nem dar `/reload` junto, e não tem como ficar dessincronizado
  ([SDD §7](../SDD.md), diretriz 3).
- **`ChooseHousePayload` carrega só um id, e esse id passa por três validações no servidor**: existe
  no catálogo? o jogador estava mesmo diante de um altar, na mesma dimensão, a menos de 8 blocos,
  dentro do prazo? a casa ainda cabe mais gente? Entrada de rede é hostil até ser validada
  ([SDD §7](../SDD.md), diretriz 5).
- **A permissão de escolher é gasta antes de qualquer outra checagem.** Uma tela aberta vale
  exatamente uma tentativa, certa ou errada — do contrário daria para varrer ids até achar uma casa
  com vaga.
- **A lotação é reconferida na confirmação, não só na abertura.** Entre abrir a tela e confirmar,
  outra pessoa pode ter pego a última vaga. É por isso que existe o `HouseChoiceResultPayload`:
  fechar a tela no clique e torcer deixaria quem perdeu a corrida achando que entrou.
- **Casa que sumiu do datapack não apaga o dado do jogador.** O id continua gravado e as leituras
  tratam "casa desconhecida" explicitamente. Alguém editando JSON com o servidor ligado não pode
  custar o histórico de ninguém.
- **A tela quebra texto uma vez, na abertura, nunca por frame.** Mesma lição do `aurorion-talk`
  ([SDD §4.2](../SDD.md)): `Font#split` e os `ItemStack` dos ícones são montados no construtor do
  card.
- **Escolha em duas etapas (clicar no card, depois confirmar).** A escolha é definitiva por padrão;
  um clique solitário num card é perto demais de um clique errado.
- **`@OnlyIn(Dist.CLIENT)` numa classe separada para a ponta cliente da rede.** `Ato2Network` só
  toca em `Ato2ClientNetwork` depois do teste de dist, então o servidor dedicado nunca chega a
  resolver uma classe que importa `Minecraft`.

## Status

Compila contra NeoForge 21.1.248 / MC 1.21.1. **Ainda não testado em jogo** — o que conferir na
primeira execução:

- Se `data/<ns>/aurorion/houses/*.json` é mesmo varrido pelo `SimpleJsonResourceReloadListener` com
  diretório composto (o prefixo `aurorion/` existe para não colidir com outro mod do modpack que
  também tenha "houses").
- Se cancelar o `RightClickBlock` no altar não conflita com nada do modpack que também escute esse
  evento.
- Layout da tela com mais de 3 casas (a grade quebra em linhas, mas nunca foi vista com 6+).

```bash
./gradlew :aurorion-ato2:runClient
./gradlew :aurorion-ato2:build
```
