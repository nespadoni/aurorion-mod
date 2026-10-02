# Aurorion Trama do Eco

Árvore passiva de 271 nós, integrada às cinco Casas do `aurorion-ethereal` pelo
`HouseGate` do core. O mod usa a tela e o grafo do Pufferfish's Skills 0.19.x,
com progressão e cálculo dos efeitos no servidor. Instale os dois mods no
cliente e no servidor; o Ethereal fornece a Casa. Sem Casa reconhecida, a
categoria fica fechada e os efeitos ficam suspensos.

## Progressão automática

Não exige comandos de concessão por jogador. Vincular a Casa abre a categoria,
concede a origem correspondente e disponibiliza cinco pontos iniciais.
Os outros 40 pontos exigem **XP de formação e tempo ativo acumulado**:

- Prática ativa: 10 XP por minuto reconhecido, até 900 XP de prática por dia UTC.
- Primeiro bioma: 120 XP, uma vez por bioma e personagem, até 40 biomas.
  A descoberta é registrada no bioma em que termina um minuto ativo.
- Conquistas selecionadas: 150 XP cada, uma vez por ID e personagem.
  Há 38 conquistas vanilla na lista inicial; receitas e IDs fora da lista não dão XP.
  Conquistas já concluídas são reconhecidas ao receber a Casa ou entrar no servidor.
- XP vanilla, abates, drops, spawners, criação repetida, receitas repetidas e
  saldo/pontuação da Casa **não concedem XP da Trama**.

Cada ponto depois dos cinco iniciais custa 80 XP e 15 minutos ativos adicionais,
com incremento de 20 XP e quatro minutos no custo do ponto seguinte.
Os requisitos são cumulativos. Descobertas ficam guardadas; ter muito XP não
ignora o requisito de tempo, e tempo sozinho não ignora o XP.

| Pontos totais | XP acumulada necessária | Tempo ativo mínimo |
| --- | ---: | ---: |
| 5 | 0 | 0 |
| 10 | 600 | 1h55 |
| 20 | 3.300 | 10h45 |
| 30 | 8.000 | 26h15 |
| 38 | 13.200 | 43h27 |
| 45 | 18.800 | 62h |

O teto diário afeta apenas XP de prática. Tempo ativo continua contando depois
dele, e descobertas não expiram nem exigem um evento irrepetível. A prática é
uma rota renovável até o fim, inclusive para personagens dedicados a construção
e sem acesso a chefes ou determinadas dimensões.

### Reconhecimento de atividade

O servidor verifica uma janela de 60 amostras, uma por segundo. É necessário
mudar a direção da câmera e cumprir uma das condições:

- exploração: visitar pelo menos quatro células de dois blocos e movimentar-se
  em pelo menos dez amostras;
- construção/interação: visitar pelo menos duas células, realizar dois tipos de
  interação e interagir em pelo menos oito amostras.

Teleporte maior que 64 blocos, mudança de dimensão, reconexão, voo criativo e
montarias interrompem a janela. Criativo, espectador, personagem morto ou em
criação/cena adiada não progride. Os minutos incompletos não são persistidos.
Um trilho/corrente de água com câmera parada e cliques estacionários não contam.

Isso é uma heurística de atividade, não uma prova de presença humana: macros que
imitem movimento, câmera e interação podem passar. O teto diário, as curvas de
tempo e a ausência de XP por abate limitam a vantagem mesmo nesse cenário.
Um jogador realmente ativo perto de uma farm pode receber a prática normal;
a quantidade de mobs mortos não aumenta a recompensa.

## Uso pelo jogador

- `/trama`: abre a árvore na interface do Pufferfish.
- `/trama progresso`: mostra pontos, tempo ativo e os dois requisitos que faltam.
- `/trama respec`: devolve todos os pontos gastos e recoloca a origem da Casa.
  Primeiro uso gratuito; seguintes têm intervalo padrão de 24 horas. Todos os
  usos exigem 15 segundos fora de combate. Reconectar não limpa essa restrição.

Os comandos são de consulta/escolha do próprio jogador. A distribuição é
automática; a staff não precisa executar comandos de pontos para cada pessoa.

## Casas e personagens

As origens são `I00`, `S00`, `N00`, `V00` e `A00`. Nenhuma é uma raiz clicável;
o servidor desbloqueia somente a Casa atual. As pontes deixam acessar clusters
de outras Casas, com oito nós entre as extremidades e sem adquirir outra origem.
As origens só têm conexões de saída. Princípios de uma mesma Casa são exclusivos;
os cinco Princípios centrais também são exclusivos entre si.

Trocar a Casa redefine a distribuição da build, preservando a formação e os
pontos conquistados. Remover a Casa suspende a categoria. Morte comum preserva
a árvore; substituir o personagem pelo fluxo do core cria formação nova e
apaga a categoria antiga, inclusive quando a substituição foi feita offline.
Outras categorias do Pufferfish não são apagadas.

A fonte de pontos `aurorion_trama:formacao` é escrita com um total absoluto, não
incrementada no login. Recompensas inéditas, tempo, XP, Casa de origem e respec
ficam no SavedData `world/data/aurorion_trama_progress.dat`. Respec, morte comum,
reload e reconexão não reapresentam conquistas como novas.

## Efeitos e balanceamento

Ratings numéricos são somados quando a build muda. Conversões usam os ratings
originais limitados, sem realimentação. Um único cálculo aplica Princípios,
condições e limites, com modificadores transitórios e IDs estáveis por atributo.
Nenhum nó cria sua própria tarefa, busca em área, raycast ou partículas por tick.
Estados são avaliados uma vez por segundo, com atualizações imediatas nas
transições de sprint e nos eventos de dano relevantes.

- MOV estático: até 4,2%; Horizonte Livre permite 6%; absoluto com condições: 7%.
- APS: até 6% estático; absoluto 8%.
- MEL/RNG/MAG: até 9% em ratings básicos; absoluto 13% por família.
  Corpo Oco permite 12% em ratings MAG e 16% com condições.
- VIT: até 8% em ratings; absoluto 10%; Corpo Oco limita a 3%.
- ARM: até 1,8; Âncora Absoluta permite 3. TGH até 1,5; KBR até 12 p.p.
- MR: até 12% incluindo reações; resistência ambiental até 8%.
- Alcance adicional: bloco até 0,30; entidade até 0,10.
- Queda até 25%, Horizonte Livre até 20%; demais ratings têm os caps do catálogo.

Dano mágico tem precedência sobre projéteis. Tipos desconhecidos não recebem
bônus ofensivo. As tags em `data/aurorion_trama/tags/` são o ponto de extensão:

- `damage_type/magic`: magia vanilla e os nove tipos diretos de magia do Iron's
  encontrados no jar de referência; entradas modded opcionais.
- `damage_type/physical`: ataques e projéteis físicos vanilla.
- `damage_type/environment`: fogo, explosão, afogamento e magia ambiental vanilla.
- `mob_effect/debuffs`: efeitos negativos que habilitam Alvo Debilitado.
- `entity_type/speed_projectiles`: somente flechas vanilla por padrão.
  PSPD é aplicado uma vez no início da vida do projétil, com marcador persistente.

NO4 altera velocidade de mineração somente com ferramenta adequada e respeita
o teto BBR de 10%, sem modificar drops. Efeitos de recuperação e de resistência
compartilham timers por personagem online; dano causado também interrompe a
recuperação fora de combate.

## Configuração e geração

Valores em `aurorion/trama-server.toml`, no diretório de serverconfig do mundo
(ou defaultconfigs para novos mundos). Inclui ritmo, prática diária, biomas,
lista exata de conquistas e intervalo de respec. Alterar o ritmo não retira pontos
já conquistados. O teto de gasto da categoria permanece 45.

Para objetivos de missão/disciplinas, acrescente seus IDs de advancements à
lista `progression.advancements`. O mod reconhece conclusão automaticamente;
não basta inserir um nome de missão: ela deve conceder um advancement real.

O catálogo revisado fica em `tools/catalog.json`. O gerador padrão Python,
`python aurorion-trama/tools/generate_tree.py`, produz dados já versionáveis no
jar; a compilação não requer Python nem o PDF original. IDs `VX1..VX5` e
`AX1..AX5` distinguem viagem dos clusters VT e AT, cujos IDs colidiam no PDF.
Cinco Fios centrais omitidos na extração inicial tiveram seus ratings recuperados.
O layout usa ícones e fundo vanilla provisórios, com cores por Casa.

Dependência de compilação/runtime: jar original do Pufferfish 0.19.0, copiado
da instância fornecida para `mod-servidor-referencia/`. Pode ser sobrescrito com
`-PpuffishSkillsJar=C:/caminho/arquivo.jar`. A dependência não é embutida no jar.
API consultada no [código oficial do Pufferfish](https://github.com/pufmat/skillsmod/tree/1.21/Common/src/main/java/net/puffish/skillsmod/api).

## Release 0.1.0 e validação

Compilação e testes executados nesta máquina com autorização explícita do usuário
em 01/10/2026, usando Java 21 e NeoForge 21.1.248. A versão é inicial; o ritmo
de progressão e os ícones vanilla continuam sujeitos a ajuste com jogadores reais.

```powershell
.\gradlew.bat :aurorion-trama:test :aurorion-trama:build
.\gradlew.bat :aurorion-trama:runGameTestServer -PtramaTestMagicPack=true
```

- 11 testes unitários aprovados: curvas cumulativas, limite de 45 pontos,
  XP/tempo independentes, janelas AFK/transporte/teleporte/construção e caps.
- 7 GameTests aprovados em servidor dedicado descartável com Pufferfish 0.19.0,
  Ethereal e Iron's 3.16.3: leitura dos 271 nós, cinco origens, concessão sem
  duplicação, descobertas deduplicadas, respec/reconexão/bloqueio em combate,
  substituição online/offline, modificadores sem empilhamento, gravação e leitura
  NBT, prática ativa versus AFK e teto diário.
- Os eventos reais de dano reconheceram magia vanilla e os nove tipos diretos do
  Iron's, sem empilhar bônus de projétil/corpo a corpo. Abates concederam zero XP.
- Os jogadores de teste usam conexões simuladas; não houve sessão visual de
  cliente, ensaio do modpack completo, carga de 90 jogadores ou validação manual
  de todos os Princípios com equipamentos de terceiros.

O JAR instalável fica em
`build/jars-servidor/aurorion_trama-neoforge-1.21.1-0.1.0.jar`.
O mod de GameTests, suas estruturas e bibliotecas não são incluídos nesse JAR.

## Instalação em servidor existente

1. Pare o servidor e faça backup do mundo e configurações, incluindo os dados
   do Pufferfish e `world/data/aurorion_trama_progress.dat` após a primeira execução.
2. Instale o JAR da Trama **no servidor e nos clientes**, com Minecraft 1.21.1,
   Java 21, Aurorion Core >= 0.6.2 e Pufferfish's Skills 0.19.x (testado: 0.19.0).
   Para as Casas, use Aurorion Ethereal >= 0.3.3. Essas dependências são separadas;
   mantenha apenas uma versão de cada mod no diretório `mods`.
3. Na primeira entrada, a Casa existente abre a árvore com cinco pontos.
   Conquistas selecionadas anteriores dão XP uma única vez; tempo ativo começa
   do zero. A staff não precisa distribuir pontos nem migrar cada jogador.
4. Confira a tela `/trama` com um cliente e a configuração gerada em
   `world/serverconfig/aurorion/trama-server.toml`. Antes de liberar a todos,
   valide em uma cópia do mundo com o mesmo modpack de produção: navegação,
   compra de nós/pontes, efeitos com equipamentos, PvP e recuperação.

Para suspender os efeitos e a progressão, configure `enabled=false` no início de
`aurorion/trama-server.toml` e reinicie o servidor. Os dados são preservados para
a reativação. A aparência final, ícones próprios e telemetria ficam para uma
iteração posterior.
