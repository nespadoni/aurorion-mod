# Ruínas do Limbo — como adicionar uma

Esta pasta guarda os `.nbt` das construções que geram no Limbo. **Nenhum deles vem do código** — você
constrói no jogo com Blocos de Estrutura e salva aqui. A infraestrutura já está pronta; adicionar uma
ruína nova é construir, salvar e acrescentar **uma entrada** num JSON.

## O caminho completo

**1. Construa.** Em criativo, num mundo qualquer. Vale montar já com `stone_bricks` — o processador
`aurorion_limbo:desgaste` racha, cobre de musgo e abre buracos na hora de gerar, então você constrói
a ruína *inteira* e o jogo a quebra sozinho. Construir já quebrado dá um resultado pior e igual em
todo lugar.

**2. Marque o chão.** O bloco mais baixo da estrutura é o que encosta no terreno. O
`project_start_to_heightmap` gruda a peça na superfície, então deixe a base plana.

**3. Salve com o Bloco de Estrutura.**

```
/give @s minecraft:structure_block
```

Modo `SAVE`, nome `aurorion_limbo:ruinas/<seu_nome>`. O arquivo sai em
`<mundo>/generated/aurorion_limbo/structures/ruinas/<seu_nome>.nbt`.

**4. Copie o `.nbt` para cá**, mantendo a subpasta `ruinas/`.

**5. Registre no pool** — `worldgen/template_pool/ruinas.json`:

```json
{
  "weight": 1,
  "element": {
    "element_type": "minecraft:single_pool_element",
    "location": "aurorion_limbo:ruinas/seu_nome",
    "processors": "aurorion_limbo:desgaste",
    "projection": "rigid"
  }
}
```

`weight` é frequência relativa. Uma ruína com peso 3 aparece três vezes mais que uma de peso 1.

**6. `/reload`** e voá. Para conferir sem procurar:

```
/place structure aurorion_limbo:ruina
```

## Os livros

Um baú dentro da ruína puxa de `aurorion_limbo:ruinas/altar_partido` — é onde moram os livros
escritos. No Bloco de Estrutura, use um **Loot Table Block** (ou um baú com
`LootTable: "aurorion_limbo:ruinas/altar_partido"`) para o conteúdo não ficar fixo.

Para uma ruína com história própria, copie o arquivo de loot, troque os livros e aponte o baú dela
para a cópia. O texto é **conteúdo**: editar e dar `/reload` já muda, sem rebuild.

### Sobre escrever os livros

O registro que os três exemplos seguem, e que vale manter: **nunca explique**. Quem escreveu está
dentro da história e não sabe mais que o leitor. Nenhum livro diz o que é o Limbo, quem é o Oráculo
ou por que o prazo existe — cada um deixa cair um pedaço e erra em outro.

O jogador junta. É ele que monta a versão dele, e é por isso que ela gruda.

## Frequência

`worldgen/structure_set/ruinas.json` controla o quanto o Limbo fica povoado:

| Campo | Padrão | O que faz |
|---|---|---|
| `spacing` | `24` | Média de chunks entre uma ruína e a próxima. Menor = mais denso |
| `separation` | `8` | Mínimo garantido. **Sempre menor que `spacing`** |
| `salt` | `20260914` | Muda o sorteio sem mudar densidade. Troque se a distribuição ficar feia |

Com `24/8` dá uma ruína a cada ~400 blocos — perto o bastante para achar caminhando, longe o
bastante para a caminhada da Porta do Esquecido não virar um museu.

⚠️ **Mudar isso não altera chunk já gerado.** Para ver o efeito, gere área nova ou apague a dimensão
do save de teste.
