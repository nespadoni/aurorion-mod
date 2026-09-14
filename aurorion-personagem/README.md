# Aurorion Personagem

Quem é a pessoa, e quem é o personagem.

O Minecraft só conhece uma identidade: o UUID da conta. Este mod acrescenta a segunda — a do
personagem — e é ela que morre quando a morte definitiva do [Limbo](../aurorion-limbo/MORTE-DEFINITIVA.md)
acontece. A conta continua válida, sem banimento, e a mesma pessoa volta com **outro personagem**,
outro nome e nada da história anterior.

## A tela

Quem entra sem personagem nomeado é retido no login e vê dois campos: **nome** e **sobrenome**.
Enquanto não responder, fica em espectador, sem chat, sem comandos (exceto `/personagem`) e sem
viagem entre dimensões. O nome completo passa a ser o nome exibido no jogo — no chat, na tab list e
sobre a cabeça —, usando o mesmo mecanismo do `/fakename`.

Cliente sem o mod entra normalmente: recebe as mesmas perguntas por mensagem e responde em
`/personagem criar <Nome> <Sobrenome>`. A retenção é do servidor, não da tela.

### Regra do nome

Nome e sobrenome, **apenas letras** (acentos valem), de 2 a 24 caracteres cada, até 48 no total.
Espaço, hífen e apóstrofo são aceitos **dentro** de uma parte (`de Verrine`, `D'Arcy`).

Um nome pertence a um personagem só — **inclusive depois que ele morre**. O índice ignora acento e
caixa, então `Álda Verrine` e `alda verrine` são o mesmo nome.

## As duas situações

| Situação | O que acontece |
|---|---|
| Conta sem personagem nomeado, vivo | Só ganha um nome. **Nada é apagado**: inventário, casa, vidas e progressão continuam |
| Conta com personagem morto em definitivo | Troca de identidade: reset completo antes de o personagem novo existir |

A diferença importa para instalar o mod num servidor que já está rodando: a população existente é
nomeada sem perder nada.

## O reset

Só acontece na troca depois da morte. O que é apagado:

- **Vanilla**: inventário, baú do Fim, grade de criação, XP, efeitos, fome, vida, ar, ponto de
  renascimento, avanços, estatísticas, receitas conhecidas, tags de jogador, placar e time.
- **Aurorion**: vidas, casa/veredito/pontos do Etéreo, passes de viagem, nome exibido, registro e
  memória do Limbo, epílogo pendente, estilo de balão de fala e origem de abdução.

Nada de OP, whitelist, banimento ou UUID de autenticação é tocado: isso é da pessoa, não do
personagem.

### Por que outros mods entram sozinhos

Este mod **não importa classe de nenhum outro mod do ecossistema**. Ele dispara
`CharacterResetEvent` (do `aurorion-core`) e cada mod apaga o que é seu, no seu próprio código, ao
lado dos dados que ele mesmo escreveu. Um mod novo que guarde algo por jogador entra no reset
acrescentando um listener — sem tocar aqui.

```java
@SubscribeEvent
public static void onReset(CharacterResetEvent event) {
    MeuData.get(event.server()).clear(event.account());
}
```

Todo handler precisa ser **idempotente**: um reset interrompido é repetido no login seguinte.

### Mods de terceiros

Progressão de mods de fora do repositório **não é apagada automaticamente** — o reset alcança o que
o vanilla guarda e o que os mods Aurorion guardam. Para quests, magia, dinheiro e pesquisas de
outros mods, o caminho é um listener de `CharacterResetEvent` numa camada de compatibilidade, ou um
comando desses mods chamado por função de datapack. Muitos guardam por UUID de conta e vão
sobreviver à troca se ninguém apagar.

## A transação

Trocar de personagem tem um ponto de não-retorno: entre "apagar a história antiga" e "publicar a
identidade nova" existe um instante em que uma queda de energia deixaria a conta sem nenhuma das
duas. Por isso a troca é uma transação com diário:

1. a identidade nova é **reservada** e gravada em disco (com `fsync`) antes de qualquer coisa ser
   apagada;
2. o reset roda — vanilla aqui, mods no `CharacterResetEvent`;
3. só então a identidade é publicada, e o diário gravado de novo.

Cair no meio deixa a reserva no disco e a conta ainda morta — que é exatamente o estado de onde o
próximo login retoma. O que não acontece é a pessoa voltar viva com metade das coisas da vida
anterior.

O diário fica em `data/aurorion_core_characters.dat`, no mundo.

## Comandos

| Comando | Quem usa | O que faz |
|---|---|---|
| `/personagem criar <Nome> <Sobrenome>` | qualquer um, só quando retido | Responde a pergunta do nome |
| `/personagem continuar` | qualquer um, só quando retido | Retoma uma criação interrompida (o nome já está reservado) |
| `/personagem ver [jogador]` | staff para terceiros | Identidade, ID do personagem, datas e reserva pendente |
| `/personagem renomear <jogador> <Nome> <Sobrenome>` | staff | Corrige a grafia; mantém ID, progressão e a marca de morte |
| `/personagem cancelar <jogador>` | staff | Devolve o nome de uma reserva travada. **Não ressuscita ninguém** |

Nomes com espaço vão entre aspas: `/personagem criar Alda "de Verrine"`.

Não existe comando que apenas remova a marca de morte, e isso é deliberado: ele devolveria o
inventário, a casa e a progressão da história anterior — exatamente o que a morte definitiva encerra.

## Configuração

`config/aurorion/personagem-server.toml`. Todo o texto da tela vem daqui: o cliente desenha o que
recebeu no pacote, então trocar uma frase não exige resource pack nem atualizar o mod de ninguém.

| Chave | Padrão | Efeito |
|---|---|---|
| `titulo` | "Quem é você em Aurorion?" | Título da tela |
| `boasVindas` | texto | Para quem nunca teve personagem |
| `recomeco` | texto | Para quem está criando outro depois de morrer |
| `regra` | texto | Linha de regra abaixo dos campos |
| `saudacao` | "%s abriu os olhos…" | Anúncio no chat; `%s` vira o nome completo |
| `primeirasPalavras` | 2 linhas | Mensagens só para quem acabou de nascer |
| `cobrarDeQuemJaJoga` | `true` | `false` pergunta **só** a quem vai criar outro personagem depois da morte |
| `nascerNoSpawn` | `true` | `false` faz o personagem novo acordar onde o anterior morreu |

## Verificação

Os testes de identidade e da transação ficam em `aurorion-core/src/test/.../character` —
`CharacterDataTest` cobre a troca interrompida, a reserva que sobrevive ao reinício e a unicidade do
nome depois da morte; `CharacterNameTest` cobre a regra do nome.

No jogo, com um mundo descartável, vale conferir:

- primeiro login pede nome, e o nome aparece no chat e na tab list;
- fechar a tela na força não devolve o jogo;
- cliente sem o mod recebe as instruções no chat e consegue responder pelo comando;
- dois jogadores não conseguem registrar o mesmo nome, nem o nome de um personagem morto;
- depois da morte definitiva, reconectar abre a criação e o personagem novo nasce sem nada;
- desligar o servidor no meio da criação e voltar retoma pelo `/personagem continuar`.
