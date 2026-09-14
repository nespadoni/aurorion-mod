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
| Conta com personagem morto em definitivo | Troca de identidade: apagamento total antes de o personagem novo existir |

A diferença importa para instalar o mod num servidor que já está rodando: a população existente é
nomeada sem perder nada.

## Morrer não dá direito a recomeçar

Quem morre em definitivo **não** entra e cria outro personagem sozinho. A conta continua válida e
sem banimento, mas a tela de criação só aparece depois de `/personagem liberar <jogador>`.

Sem isso, a morte definitiva viraria um contratempo de dois minutos — morre, cria outro, segue. A
liberação vale por **uma** história: publicar a identidade nova a consome, e a próxima morte precisa
de outra conversa com a staff.

Enquanto não estiver liberada, a conta é recusada no login com a explicação, configurável em
`semAutorizacao`.

## O apagamento

Só acontece na troca depois da morte, e é literalmente **apagar a playerdata**:

- `playerdata/<uuid>.dat`, mais o `.dat_old` e o `.dat_new` que o vanilla deixa para trás;
- `stats/<uuid>.json`;
- `advancements/<uuid>.json`;
- e o que cada mod Aurorion guarda fora desses arquivos, via `CharacterResetEvent`: vidas,
  casa e pontos do Etéreo, passes de viagem, nome exibido, registro e memória do Limbo, rito
  pendente, estilo de balão e origem de abdução.

### Por que apagar arquivo em vez de zerar campo

A primeira versão limpava item por item: inventário, XP, avanços, estatísticas, e um trecho para
cada mod do ecossistema. Isso funcionava para o que **conhecíamos** — e deixava passar todo o resto.
Num modpack pesado, a maior parte da progressão de um jogador não está em lugar nenhum que este mod
possa listar: está dentro do próprio `playerdata/<uuid>.dat`, em NBT persistente e em data
attachments que cada mod grava do seu jeito.

Apagar o arquivo inverte o padrão: passa a ser "não sobrevive", e o que precisa sobreviver é que tem
de ser dito em voz alta.

Nada de OP, whitelist, banimento ou UUID de autenticação é tocado: isso é da pessoa, não do
personagem.

### Por que a pessoa é desconectada

Com o dono online, o arquivo no disco é uma **cópia velha**: o `ServerPlayer` em memória é a verdade
e reescreve o arquivo no logout. Apagar naquele momento devolveria tudo alguns segundos depois.

Então a troca não acontece no clique:

1. o clique **reserva** a identidade e grava o diário com `fsync`;
2. a conta é desconectada com a explicação (`apagando`, na config);
3. fora do jogo, a varredura apaga os arquivos e dispara `CharacterResetEvent`;
4. só então a identidade é publicada, e o diário gravado de novo;
5. no login seguinte o personagem novo é apresentado — nome exibido, saudação, spawn do mundo.

Fica mais lento e mais cerimonioso que um reset instantâneo, e essa é a ideia: começar outra vida
custa uma autorização e uma reconexão, não um clique.

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

O evento é disparado com o dono **offline**, então `event.player()` pode ser `null`. Todo handler
precisa ser **idempotente**: uma troca interrompida é repetida na varredura seguinte.

### Mods de terceiros

Agora a maior parte é alcançada de graça: tudo que um mod guarda no NBT do jogador ou num data
attachment vai junto com o arquivo. O que **não** vai é o que o mod guarda fora dele — `SavedData`
próprio, arquivo por jogador numa pasta do mod, tabela num banco. Para esses o caminho continua
sendo um listener de `CharacterResetEvent` numa camada de compatibilidade.

## A transação

Trocar de personagem tem um ponto de não-retorno: entre "apagar a história antiga" e "publicar a
identidade nova" existe um instante em que uma queda de energia deixaria a conta sem nenhuma das
duas. Por isso a troca é uma transação com diário, na ordem descrita acima.

Cair em qualquer ponto deixa a reserva no disco e a conta ainda morta — que é exatamente o estado de
onde a varredura seguinte retoma. O que não acontece é a pessoa voltar viva com metade das coisas da
vida anterior.

O diário fica em `data/aurorion_core_characters.dat`, no mundo, junto com a lista de contas
liberadas pela staff e de identidades publicadas que ainda não foram apresentadas ao dono.

## Comandos

| Comando | Quem usa | O que faz |
|---|---|---|
| `/personagem criar <Nome> <Sobrenome>` | qualquer um, só quando retido | Responde a pergunta do nome |
| `/personagem liberar <jogador>` | staff | Libera **uma** criação para quem morreu em definitivo |
| `/personagem revogar <jogador>` | staff | Cancela uma liberação ainda não usada |
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
| `semAutorizacao` | texto | Tela de desconexão de quem morreu e ainda não foi liberado |
| `apagando` | texto | Tela de desconexão entre reservar o nome e nascer; `%s` vira o nome escolhido |
| `cobrarDeQuemJaJoga` | `true` | `false` pergunta **só** a quem vai criar outro personagem depois da morte |

## Verificação

Os testes de identidade e da transação ficam em `aurorion-core/src/test/.../character` —
`CharacterDataTest` cobre a troca interrompida, a reserva que sobrevive ao reinício e a unicidade do
nome depois da morte; `CharacterNameTest` cobre a regra do nome.

No jogo, com um mundo descartável, vale conferir:

- primeiro login pede nome, e o nome aparece no chat e na tab list;
- fechar a tela na força não devolve o jogo;
- cliente sem o mod recebe as instruções no chat e consegue responder pelo comando;
- dois jogadores não conseguem registrar o mesmo nome, nem o nome de um personagem morto;
- depois da morte definitiva, reconectar **sem liberação** é recusado com a explicação;
- com `/personagem liberar`, a tela aparece, e confirmar desconecta com o aviso de apagamento;
- reconectando, o personagem novo nasce sem inventário, sem avanços e sem estatísticas — e vale
  conferir também o que os mods do pack guardavam para aquela conta;
- desligar o servidor entre a reserva e o nascimento e voltar: a varredura termina a troca sozinha.
