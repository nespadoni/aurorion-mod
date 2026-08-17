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

`TimeFormat` é a única exceção à regra das duas cópias: é puro, sem dependências, e formatar duração
é o tipo de coisa que o próximo mod vai querer. Se em seis meses ainda tiver um usuário só, ele volta
para o `aurorion-portais`.

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
config/    DerivedConfig
data/      SavedDataAccess, PlayerMapNbt
datapack/  DatapackRegistry
event/     CoreServerEvents   — o único listener, e existe para os outros não precisarem
level/     SafeSpot
text/      TimeFormat
```

Decisões e por quê: [SDD §3.1 e §3.2](../SDD.md).
