# Aurorion Areas

Áreas narrativas administradas pela staff para Minecraft 1.21.1 / NeoForge 21.1.248.
Depende de `aurorion-core`. Módulo incluído no monorepo e descoberto automaticamente por `aurorion-runs`.

**Estado:** código escrito e revisado estaticamente. Build, testes e Minecraft **não foram executados**, conforme solicitado para esta máquina de desenvolvimento. Há testes escritos em `src/test`; a validação com o modpack real ainda é necessária.

## O que está implementado

- Círculos exatos e polígonos simples, inclusive côncavos; união de partes e subtração de recortes.
- Altura própria por forma. Uma sala pode ocupar apenas um andar.
- Prioridade por regra, com herança nas sobreposições.
- Voo, magia, spawn hostil, dano hostil e PvP controlados no servidor.
- Exceções por personagem, regra e área.
- Vida e dano de monstros definidos no nascimento, sem acumular multiplicadores ao recarregar chunks.
- Ambientes por datapack: sons individuais, escuridão, cegueira curta, sombra periférica, neblina e ataques invisíveis.
- Comandos de edição, inspeção e visualização exclusivos de staff (permissão 2).
- API pública e regras com nomes próprios para futuras profissões.

Nenhuma área é criada em coordenadas arbitrárias ao instalar. Fora das áreas, tudo começa permitido, sem ambiente e sem multiplicadores adicionais. `/area mundo` define a regra de fundo **da dimensão onde o comando é executado**.

## Escola circular, sala de treino e vila

Posicione-se no centro da escola e use um raio adequado ao mapa:

```mcfunction
/area circulo 120
/area altura -64 320
/area criar escola
/area prioridade escola 10
/area perfil escola aurorion_areas:escola
/area nome escola Escola de Aurorion
/area visualizar escola
```

O círculo usa seu X/Z como centro. Para bloquear voo também acima do teto de construção, escolha um teto maior, por exemplo `/area altura -2048 2048` **antes de criar a área**. Os limites de Y são inclusivos.

Para uma sala irregular, inicie uma seleção e caminhe pelos cantos do contorno em ordem:

```mcfunction
/area selecao
/area ponto
```

Repita `/area ponto` em cada canto (mínimo 3; não repita o primeiro para fechar).
Também é possível informar coordenadas absolutas: `/area ponto 100 250`.
Depois, usando as alturas reais da sala:

```mcfunction
/area altura 70 76
/area visualizar
/area criar sala_treino
/area prioridade sala_treino 20
/area perfil sala_treino aurorion_areas:treino
```

O perfil de treino **só libera magia**. Voo, monstros e PvP continuam seguindo as regras da escola nas posições onde as duas áreas se sobrepõem. Criar uma área interna não a vincula à escola: a herança depende da posição e da prioridade.

Crie a vila da mesma forma e aplique `aurorion_areas:vila`.

### Relevo e formas complexas

| Comando | Uso |
|---|---|
| `/area selecao` | Inicia um polígono novo nesta dimensão |
| `/area ponto [x z]` | Adiciona um vértice ao contorno |
| `/area desfazer` | Remove o último vértice da seleção |
| `/area circulo <raio>` | Inicia uma seleção circular no seu X/Z |
| `/area altura <min> <max>` | Altera as alturas da seleção atual |
| `/area criar <id>` | Cria área usando a seleção |
| `/area adicionar <id>` | Acrescenta a seleção à união de formas da área |
| `/area recortar <id>` | Subtrai a seleção de todas as partes da área |
| `/area remover_forma <id> parte\|recorte <índice>` | Corrige uma forma já salva; índice começa em 1 |
| `/area visualizar [id]` | Mostra a seleção ou uma área por 30 segundos |
| `/area ver <id>` | Lista formas, alturas, regras e exceções |
| `/area listar` | Lista áreas de todas as dimensões |
| `/area remover <id>` | Remove a definição da área |

A seleção permanece disponível depois de criar/adicionar/recortar. `altura` altera a seleção; não edita retroativamente uma forma salva. Para substituir uma forma, adicione a nova e remova a antiga pelo índice.

Recortes pertencem à mesma área e retiram **todas** as regras dessa área naquela posição. Para liberar só magia, use outra área com prioridade maior. Um recorte não remove a proteção de outra área sobreposta.

Os previews mostram contornos na altura onde você pediu a visualização, limitada às alturas de cada forma. Azul indica partes; vermelho indica recortes. São amostras limitadas, não um desenho de cada bloco; consulte os limites verticais com `ver`. Nenhum bloco do mundo é modificado.

Limites: 512 áreas; 32 formas por área (partes + recortes); 128 vértices por polígono; raio de 0,5 a 100.000; alturas de -2048 a 2048. Polígonos cruzados e degenerados são rejeitados.

## Prioridades, regras e exceções

| Comando | Efeito |
|---|---|
| `/area prioridade <id> <n>` | Maior número prevalece; faixa -10000 a 10000 |
| `/area perfil <id> <perfil>` | Substitui o conjunto de regras pelo preset |
| `/area regra <id> <regra> permitir\|negar\|herdar` | Altera apenas uma regra |
| `/area ambiente <id> <perfil>\|nenhum\|herdar` | Define, remove ou herda ambiente |
| `/area monstros <id> vida\|dano <fator>` | Define fator de 0,1 a 20, ou -1 para herdar |
| `/area excecao <id> <jogador> <regra> true\|false` | Concede ou remove autorização individual |
| `/area ativar <id> true\|false` | Liga/desliga a área preservando sua definição |
| `/area nome <id> <nome>` | Define o nome descritivo |
| `/area mundo <perfil>\|herdar` | Define/limpa o padrão da dimensão atual |
| `/area aqui [jogador]` | Mostra regras efetivas, área de origem e sobreposições |

Regras nativas: `voo`, `magia`, `monstros`, `dano_monstros`, `pvp`.
`monstros negar` impede spawn; `dano_monstros negar` impede dano causado por hostis. PvP considera as posições de atacante e vítima.

A decisão é independente para cada regra: vence a área de maior prioridade que tenha valor explícito. Em empate, vence o id alfabeticamente menor. `herdar` remove o valor local; não significa `permitir`. Ambiente, multiplicador de vida e multiplicador de dano também resolvem separadamente.

Para permitir que um personagem voe na escola:

```mcfunction
/area excecao escola NomeDaConta voo true
/area aqui NomeDaConta
```

Remova com `false`. O nome é o da conta Minecraft (também aceita seletores de jogadores); o dado usa UUID. A autorização permite usar a habilidade que o personagem já tem, sem conceder voo. Ela tem a prioridade daquela área: uma sala de prioridade maior com voo explicitamente negado requer sua própria exceção. O reset de personagem do Aurorion remove suas autorizações, incluindo resets recuperados após falha.

NPCs e outras entidades não recebem restrições de voo/magia. Fake players também têm bypass. Para NPCs que usam tipos classificados como monstros, há a tag de tipos `aurorion_areas:exempt_entities` e a tag individual de entidade `aurorion_areas_npc`. A tag individual deve estar definida **antes do spawn** quando a área impede monstros (por exemplo, no NBT de invocação do NPC).

Espectadores e, por padrão, staff em criativo ignoram restrições pessoais e ambientes. `/area aqui` mostra esse bypass. Para avaliar a experiência real, consulte um jogador de sobrevivência ou desative `creativeStaffBypass` pela configuração do servidor.

## Floresta Negra e exterior

Crie o contorno da floresta com a seleção e aplique:

```mcfunction
/area criar floresta_negra
/area prioridade floresta_negra 10
/area perfil floresta_negra aurorion_areas:floresta_negra
```

Para tornar os monstros mais fortes em toda a dimensão fora das áreas seguras:

```mcfunction
/area mundo aurorion_areas:exterior
```

| Preset | Comportamento |
|---|---|
| `escola`, `vila`, `seguro` | Nega voo, magia, spawn hostil, dano hostil e PvP; sem ambiente; fatores 1 |
| `treino` | Permite apenas magia; restante herdado |
| `exterior` | Permite as cinco regras; vida ×1,5, dano ×1,25; sem ambiente |
| `floresta_negra` | Ambiente da floresta; vida ×2, dano ×1,5; demais regras herdadas |

Todos os ids acima usam o prefixo `aurorion_areas:`.

A floresta inclui:

- Passos e vocalizações/sons inquietantes do Minecraft, a cada 7–16 segundos, em posições próximas de cada jogador.
- Darkness de 5 segundos, cegueira de 2 segundos e sombra periférica, a cada 20–38 segundos.
- Neblina com alcance de 26 blocos e transição gradual.
- Ataque invisível de 1 ponto de dano (meio coração antes das reduções), a cada 35–65 segundos.
- Ataques não letais por padrão: o dano final desse tipo é limitado para deixar pelo menos 1 ponto de vida.

Os três relógios começam na entrada e não reiniciam a cada passo. Sons são privados: não há monstros falsos nem transmissão para todos os jogadores. Ao sair, novos eventos param e a imagem desaparece gradualmente. Efeitos de poção já aplicados terminam pelo seu prazo curto; não removemos poções de outras fontes.

`lethal: true` no ambiente seleciona outro tipo de dano, que pode matar e acionar o sistema de vidas. Não letal limita o golpe ambiental; não torna o jogador imune a outros perigos.

### Conteúdo por datapack

Presets: `data/<namespace>/aurorion/area_rules/<id>.json`.
Ambientes: `data/<namespace>/aurorion/area_ambience/<id>.json`.

Exemplo de preset:

```json
{
  "flags": { "voo": "deny", "magia": "allow" },
  "ambience": "meupack:bosque",
  "mob_health": 2,
  "mob_damage": 1.5
}
```

Chaves ausentes herdam. Flags usam `allow/deny/inherit` no JSON. `aurorion_areas:none` cancela explicitamente ambiente herdado.

Use o [ambiente de exemplo](src/main/resources/data/aurorion_areas/aurorion/area_ambience/floresta_negra.json) como ponto de partida. Sons aceitam ids registrados de qualquer mod. Para vozes/sussurros gravados, um resource pack pode substituir os áudios de ids utilizados, ou uma integração pode registrar novos eventos. O módulo já funciona com sons vanilla, mas não inclui gravações de fala inéditas.

Limites de ambiente: intervalos 1–3600 segundos; até 32 sons; volume 0,01–2; pitch 0,5–2; distância dos sons 2–16; darkness até 30 segundos; cegueira até 15; dano por golpe até 10; neblina até 256 blocos. Dano 0, lista de sons vazia e durações 0 desligam seus respectivos eventos.

`/reload` atualiza os ambientes ativos. Presets de regras são **copiados** ao aplicar `/area perfil`: editar o JSON do preset exige reaplicá-lo às áreas ou ao mundo. O conteúdo de um ambiente, por outro lado, é consultado pelo id e atualizado na recarga. JSON inválido é registrado no log e ignorado pelo catálogo; verifique o log de recarga ao editar.

## Integrações e limites atuais

- **Vanilla:** fiscaliza `abilities.flying` e elytra no servidor; oferece aterrissagem curta ao interromper voo. Não retira `mayfly` nem restaura habilidades antigas de outro mod.
- **Iron’s Spells:** ponte opcional com `SpellPreCastEvent` cancelável e cancelamento de conjuração em andamento na entrada de uma restrição. Também classifica `irons_spellbooks:angel_wing` como voo e remove o efeito `irons_spellbooks:angel_wings` de jogadores em área proibida. Fontes conferidas no [evento oficial](https://github.com/iron431/irons-spells-n-spellbooks/blob/1.21/src/main/java/io/redspace/ironsspellbooks/api/events/SpellPreCastEvent.java), [Utils](https://github.com/iron431/irons-spells-n-spellbooks/blob/1.21/src/main/java/io/redspace/ironsspellbooks/api/util/Utils.java) e [registro de efeitos](https://github.com/iron431/irons-spells-n-spellbooks/blob/1.21/src/main/java/io/redspace/ironsspellbooks/registries/MobEffectRegistry.java). API incompatível é indicada no log. A versão instalada precisa de validação no pack.
- **Create, addons, jetpacks e vassouras:** não existe bloqueio universal confiável para qualquer implementação de movimento. Itens de uso, efeitos e montarias conhecidos podem ser classificados pelas tags abaixo. Equipamentos passivos, teclas próprias e veículos com movimentação própria precisam consultar a API/integrar seu mecanismo específico quando a lista do pack estiver disponível.
- **Magia:** bloqueia a conjuração local via integração. Não apaga projéteis/feitiços já lançados nem interpreta todos os tipos de dano mágico de terceiros.
- **Zona segura:** bloqueia novos hostis pelos eventos de spawn/entrada, dano hostil e aquisição de alvo protegido. Não apaga mobs já salvos nem vasculha chunks. Mobs carregados de disco, inclusive os pré-gerados pelo worldgen, são preservados; alvos já adquiridos podem continuar sendo perseguidos, mas o dano hostil é vetado.
- **Força:** vale para novos monstros. Mobs existentes não são recalculados ao atravessar fronteiras ou mudar um preset. O multiplicador de dano acompanha a entidade causadora, incluindo projéteis com dono reconhecido.
- **NPCs:** continuam com suas mecânicas; classifique NPCs de tipo hostil pelas exceções de entidade antes do spawn.
- **Cliente:** regras, sons e poções são do servidor; neblina e sombra periférica precisam deste módulo no cliente. O payload é opcional e só é enviado a quem negociou o canal. Neblina de água/lava e neblina já mais densa são preservadas.

Tags para datapack (pastas singulares do Minecraft 1.21):

| Caminho dentro de `data/aurorion_areas/tags/` | Efeito |
|---|---|
| `item/flight_items.json` | Bloqueia uso de itens classificados como voo |
| `item/magic_items.json` | Bloqueia uso de itens classificados como magia |
| `entity_type/flight_mounts.json` | Impede montar e desmonta jogadores de veículos classificados |
| `mob_effect/flight_effects.json` | Remove efeitos explicitamente classificados como voo |
| `entity_type/hostiles.json` | Acrescenta tipos à classificação hostil |
| `entity_type/exempt_entities.json` | Isenta tipos da classificação hostil (NPCs, por exemplo) |

Os eventos de uso não desativam automaticamente armaduras ou acessórios passivos. Entradas de mods opcionais devem usar `{"id":"mod:objeto","required":false}`.

A configuração nativa é registrada em `config/aurorion/areas-server.toml`: `enabled`, `creativeStaffBypass` e `flightSpells` (ids Iron classificados como voo). Desligar o sistema suspende a aplicação de regras/eventos; modificadores de vida já atribuídos a mobs continuam persistidos.

### API para profissões e outros mods

A API é para a thread do servidor. Exemplos:

```java
AreaApi.allows(player, AreaRule.FLIGHT);
AreaApi.allows(player, AreaRule.MAGIC);
AreaApi.allows(player, "profissoes:coletar");
AreaApi.allowsAt(level, x, y, z, player.getUUID(), "profissoes:coletar");
```

As consultas com jogador consideram bypass. `allowsAt` é a política da posição, incluindo exceções explícitas, sem bypass criativo.

A staff pode cadastrar a regra futura agora:

```mcfunction
/area regra escola "profissoes:coletar" negar
```

As aspas permitem `:` no argumento. A regra só passa a afetar uma profissão quando o código daquela profissão a consulta.

## Persistência e desempenho

Geometria, regras de mundo e exceções ficam no SavedData `aurorion_areas_regions` do overworld, separadas por dimensão. O conteúdo JSON é armazenado como bytes UTF-8 em NBT para não depender do limite de strings NBT. Salvamento segue o autosave do mundo; seleções e previews são temporários.

Índice espacial de caixas por dimensão (BVH), reconstruído apenas ao editar. Não há índice proporcional ao tamanho da área nem carregamento de chunks. Cada jogador reutiliza sua resolução de regras; a geometria só é consultada novamente quando a posição ou o cadastro muda. Sons/ataques usam relógios por jogador, e não buscas de entidades.

A prévia emite no máximo 96 partículas privadas a cada 10 ticks por administrador, por 30 segundos. Estado visual de tamanho fixo é enviado na mudança e nos pulsos. Nenhuma lista de áreas ou posições de outros jogadores é sincronizada ao cliente.

## Validação no ambiente do pack

Os testes escritos cobrem concavidade, bordas, alturas, recortes, rejeição de contornos inválidos, precedência, herança, exceções, reset de personagem, dimensões, serialização e comparação do índice com resolução exaustiva.

Quando o código for levado ao ambiente de execução, conferir escola/sala/exterior, entrada voando de elytra, personagem autorizado, NPC voando, Iron's Spells com conjuração em andamento, spawn natural/ovo/spawner, proteção contra projéteis hostis, entrada/saída da floresta, reload e reconexão. Confirmar os efeitos visuais com os shaders e os demais mods de neblina do pack.
