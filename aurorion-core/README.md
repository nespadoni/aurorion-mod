# Aurorion Core

Biblioteca compartilhada do ecossistema. **Não adiciona nada ao jogo**: não registra item, bloco,
comando nem receita, e tem exatamente um listener — que existe para os outros mods não precisarem de
um.

Precisa estar sempre no pack. É a contrapartida assumida da [SDD §3.1](../SDD.md): os mods continuam
podendo ser ligados e desligados um a um, o core não.

## A regra de entrada

**Só entra aqui o que já estava duplicado.** Utilidade que só um mod usa fica no mod.

Isso não é preciosismo — é o que separa uma biblioteca de um depósito. Uma camada compartilhada que
aceita código especulativo vira o lugar onde ninguém sabe o que ainda é usado, e aí ela piora a
manutenção em vez de melhorar. Todo item abaixo tem o número de cópias que existia antes.

Um exemplo do que **não** entrou: um helper para registrar pacotes de rede economizaria ~14 linhas,
mas obrigaria a criar o lambda que referencia classe de cliente durante o registro, que roda também
no servidor dedicado. Hoje esse lambda só nasce depois da checagem de `Dist`. Trocar 14 linhas por
risco de o servidor não subir é péssimo negócio.

## O que tem

| Classe | Cópias antes | O que resolve |
|---|---|---|
| [`data/SavedDataAccess`](src/main/java/com/aurorion/core/data/SavedDataAccess.java) | 4 | Pegar/guardar um `SavedData` do overworld, sem alocar por chamada e sem vazar entre mundos |
| [`data/PlayerMapNbt`](src/main/java/com/aurorion/core/data/PlayerMapNbt.java) | 4 | Ler/escrever `Map<UUID, ?>` em NBT |
| [`level/SafeSpot`](src/main/java/com/aurorion/core/level/SafeSpot.java) | 2 | "Onde dá para colocar um jogador sem matá-lo?" |
| [`datapack/DatapackRegistry`](src/main/java/com/aurorion/core/datapack/DatapackRegistry.java) | 2 | Pasta de JSON de datapack → catálogo consultável |
| [`config/DerivedConfig`](src/main/java/com/aurorion/core/config/DerivedConfig.java) | 2 | Valor caro derivado de config, recalculado sozinho |
| [`text/TimeFormat`](src/main/java/com/aurorion/core/text/TimeFormat.java) | 1 | Duração e data como `Component` traduzível |
| [`client/ShaderPacks`](src/main/java/com/aurorion/core/client/ShaderPacks.java) | 2 | "Tem shader pack ligado agora?" — ponte reflexiva com a API v0 do Iris |
| [`client/ScreenFog`](src/main/java/com/aurorion/core/client/ScreenFog.java) | 2 | Neblina desenhada em espaço de tela, que sobrevive ao pipeline do shader |
| [`compat/TwilightTeleportCompat`](src/main/java/com/aurorion/core/compat/TwilightTeleportCompat.java) | 2 | Teleporte síncrono mesmo com o Twilight Teleport, que adiaria a viagem (Limbo, Reino das Almas, batalha de intenção) |

`TimeFormat` é a única **utilidade** que entrou sem duas cópias: é pura, sem dependências, e formatar
duração é o tipo de coisa que o próximo mod vai querer. Se em seis meses ainda tiver um usuário só,
ela volta para o `aurorion-portais`. (Os contratos entre mods são outra categoria — ver a seção
abaixo; eles não são utilidade e a regra das cópias não se aplica a eles.)


## A outra coisa que mora aqui: contratos entre mods

`character/` e `house/` não deduplicam nada — eles são **perguntas que um mod faz e outro responde**,
sem que os dois se conheçam:

| Contrato | Quem responde | Quem pergunta |
|---|---|---|
| [`character/CharacterGate`](src/main/java/com/aurorion/core/character/CharacterGate.java) | `aurorion-personagem` | `aurorion-limbo` |
| [`house/HouseGate`](src/main/java/com/aurorion/core/house/HouseGate.java), [`HouseChangedEvent`](src/main/java/com/aurorion/core/house/HouseChangedEvent.java) | `aurorion-ethereal` | `aurorion-areas`, `aurorion-essentials` (cor da casa na tab) |
| [`character/CharacterAbilities`](src/main/java/com/aurorion/core/character/CharacterAbilities.java) (0.6.7) | `aurorion-magia` (magias, passivas), `aurorion-profissoes`, `aurorion-trama` — cada um registra um provedor | `aurorion-ficha` (aba Habilidades) |
| [`character/CharacterSheet`](src/main/java/com/aurorion/core/character/CharacterSheet.java) (0.6.8) | `aurorion-ficha` (retrato da ficha do site, em memória e em SavedData) | `aurorion-magia` (teste do lançamento), `aurorion-profissoes` (teste de fabricação) |
| [`character/CharacterSpells`](src/main/java/com/aurorion/core/character/CharacterSpells.java) (0.6.8) | `aurorion-magia` (magias do livro equipado, com o teste) | `aurorion-intencao` (magia como ação do duelo) |
| [`character/CharacterFinale`](src/main/java/com/aurorion/core/character/CharacterFinale.java) (0.6.8) | `aurorion-limbo` (morte definitiva com a cena de fim) | `aurorion-intencao` (duelo até a morte) |
| [`character/SkillTreeGate`](src/main/java/com/aurorion/core/character/SkillTreeGate.java), [`SkillTreeChangedEvent`](src/main/java/com/aurorion/core/character/SkillTreeChangedEvent.java) (0.6.8) | `aurorion-trama` | `aurorion-magia` (Despertar Arcano e especialidade), `aurorion-profissoes` (item que exige nó) |
| [`client/menu/CharacterMenu`](src/main/java/com/aurorion/core/client/menu/CharacterMenu.java) (0.6.9, só cliente) | `aurorion-menu` (o host: o painel no mundo, o gesto, a janela ○/×, o cartão de aviso) | `aurorion-ficha` (Perfil), `aurorion-intencao` (Duelo, Ações, "Desafiar para duelo") — cada um registra seções e interações |

A alternativa seria um mod importar o outro — o que torna um obrigatório para o outro, contra a
[SDD §3](../SDD.md) — ou alcançá-lo por reflexão, como se faz com mod de terceiro. Entre dois mods
nossos, um contrato tipado e conferido pelo compilador ganha das duas opções.

O desenho do menu, [`client/menu/MenuUi`](src/main/java/com/aurorion/core/client/menu/MenuUi.java)
(0.6.9), também mora aqui: formas lisas em alta resolução (`textures/gui/menu/`, geradas por
`tools/generate_ui.py`), a fonte Rajdhani (`aurorion_core:menu`, OFL) e a paleta marfim, violeta e dourado.
Ficha, intenção e o host desenham com ele, e não com três cópias. Junto vêm
[`compat/EmotecraftBridge`](src/main/java/com/aurorion/core/compat/EmotecraftBridge.java) (toca um emote
do Emotecraft por reflexão, sem exigir o mod) e `client/IrisShadowPass` (pular o passe de sombra do Iris).

[`character/SheetCheck`](src/main/java/com/aurorion/core/character/SheetCheck.java) (0.6.8) é a
regra do teste da ficha (d20 + bônus contra CD, crítico, falha crítica, vantagem). Entrou como
utilidade porque magia e profissões aplicam a mesma regra — duas cópias de uma regra de jogo são
duas chances de o servidor ter dois "d20" diferentes.

**Para a batalha de intenção com ficha (futuro):** o `Stats` já carrega os derivados da ficha
(`vida_max`, `pe_max`, `stamina_max`, `defesa`, `rd`, `iniciativa`, `fortitude`), e o
`SheetCheck.roll` aceita um modificador situacional. Turno, reação e combo devem partir desses dois
pontos, não de um contrato novo.

O teste de entrada continua sendo o mesmo: **o que acontece se quem usar esquecer da parte dele?**
Se ninguém chamar `HouseGate.provide`, `installed()` responde `false` e quem pergunta trata isso como
"não há sistema de casas" — e avisa no log. O que **não** pode acontecer é o silêncio ambíguo: se o
gate respondesse só `null`, "não há casas" e "esta pessoa não tem casa" seriam a mesma resposta, e o
`aurorion-areas` muraria o mapa inteiro por um jar faltando.

## O princípio que guia o resto

**Uma abstração que exige disciplina de quem chama não é uma abstração.**

Duas classes daqui nasceram errado e foram corrigidas por esse critério:

- **`SavedDataAccess`** pedia que cada mod chamasse `invalidate()` num listener de
  `ServerStoppedEvent`. Dois dos quatro mods não tinham esse listener e passaram a precisar de um —
  ou seja, o bug mudou de lugar em vez de sumir. Agora são duas travas e nenhuma depende de quem usa:
  o core zera todos os caches ao parar o servidor, **e** o cache guarda de qual servidor veio, então
  o dado nunca cruza de um mundo para outro mesmo que o evento falhe.

- **`DerivedConfig`** substituiu um cache que dependia de um listener de `ModConfigEvent` — uma
  classe inteira no `aurorion-portais` só para isso, que foi apagada. Agora o gatilho é o próprio
  dado: guarda-se o valor cru junto com o convertido e compara-se. Não há evento para esquecer, nem
  ordem de inicialização para acertar.

Quando for adicionar algo aqui, a pergunta é: *o que acontece se quem usar esquecer de fazer a parte
dele?* Se a resposta for "quebra silenciosamente", o desenho está errado.

## Notas de uso

**`SafeSpot` pode gerar chunk.** Ler um bloco de um chunk não carregado o *gera*, na thread do
servidor. Para respawn é inofensivo (o chunk da morte está carregado), mas uma varredura larga num
lugar nunca visitado trava o tick. Por isso a varredura é por chunk (pega uma vez por coluna, em vez
de uma busca por bloco), o raio é pequeno, e quem chama **grava o resultado** para não repetir.

**`DatapackRegistry` separa registro de listener.** O vanilla cria um listener novo a cada
`/reload`, então o catálogo não pode morar dentro dele. O registro é permanente (campo estático do
mod) e `listener()` devolve um descartável que só escreve de volta nele.

**Nada aqui é thread-safe por acidente.** `SavedDataAccess` e `DerivedConfig` são para a thread do
servidor. `DatapackRegistry` publica snapshots imutáveis em campos `volatile`, então a leitura é
segura de qualquer lugar — a escrita continua sendo só no reload, na thread do servidor.

## Estrutura

```
character/ CharacterGate, CharacterData, ... — contrato do criador de personagens;
           CharacterAbilities — o que o personagem tem no jogo, de vários mods, para a ficha
compat/    TwilightTeleportCompat
config/    DerivedConfig
data/      SavedDataAccess, PlayerMapNbt
datapack/  DatapackRegistry
event/     CoreServerEvents   — o único listener, e existe para os outros não precisarem
house/     HouseGate          — contrato do sistema de casas
level/     SafeSpot
text/      TimeFormat
```

Decisões e por quê: [SDD §3.1 e §3.2](../SDD.md).
