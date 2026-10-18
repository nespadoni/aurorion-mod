# Correções de magias e Simply More — 17/10/2026

Código preparado na branch `fix/correcoes-magias-simplymore-2026-10-17`, baseada em
`3f20213` da `release/2026-10-05` (release de 08/10). A `master` aberta inicialmente
não continha Ceifar Vida e Entregar Vida.

**Estado: alterações de código e revisão estática concluídas. Compilação, aplicação
dos mixins e testes em jogo pendentes em outra máquina, conforme `AGENTS.md`.**
Não há JAR novo compilado nem alteração aplicada ao servidor de produção.

## Módulos para atualizar juntos depois da validação

| Módulo | Versão preparada | Motivo |
|---|---|---|
| Aurorion Magia | 0.6.1 | Cooldown do barril, transferência de vidas e magias entre casas |
| Aurorion Vidas | 0.2.5 | API de transferência de uma vida entre dois jogadores |
| Aurorion Utils | 0.2.4 | Proteção de lado no cooldown do Simply More; inclui a proteção anterior da Mimi-Cry |
| Aurorion Profissões | 0.5.2 | Mesa de ferraria somente para ferreiros; revalidação contra duplicação |
| Aurorion Essentials | 0.8.0 | Grupos no Mensagens, Gram salvo no servidor, otimizações (ver README do módulo) |
| Aurorion Core | 0.6.5 | `SavedDataAccess#flush` por arquivo |
| Aurorion Personagem | 0.3.2 | Flush só dos dados da transação; exige Core 0.6.5 |
| Aeonita 0.3.3, Áreas 0.6.4, Ethereal 0.3.5, Limbo 0.4.7, Mundos 0.1.3, Serviços 0.1.3, Talk 0.1.3, Trama 0.1.2 | — | Otimizações de [AUDITORIA-OTIMIZACAO-2026-10-18.md](AUDITORIA-OTIMIZACAO-2026-10-18.md) |

Magia e Utils precisam carregar também nos clientes. A proteção de Simply More
deve chegar a **todos os clientes próximos do dummy**, pois o erro ocorre no mundo
local de cada observador. Distribuir os três módulos juntos evita instalações
misturadas. O Magia declara Vidas 0.2.5 ou superior como dependência opcional do
servidor: sem Vidas, o restante das magias funciona e os rituais de vida são recusados.

## Alterações

### Barril Explosivo — Dolium Ardens

O código da release já declarava 35 segundos. Isso não prova qual configuração
zerou a recarga no servidor; os arquivos de configuração em produção não foram
fornecidos. A correção trata dois caminhos de recarga zerada e uma falha de escopo:

- `getSpellCooldown()` usa 35 s quando o valor do JSON/datapack existente resulta
  em zero ou negativo. Valores positivos continuam configuráveis.
- O evento de cooldown restaura a recarga base se um multiplicador de arma ou
  atributo zerar o resultado final. Reduções que produzam duração positiva continuam valendo.
- A exceção de cooldown ao ligar Tempus/Vínculo agora pertence à **magia e ao tick**,
  além do jogador. Ela não pode cancelar a recarga do barril conjurado no mesmo tick.
  A marca é removida ao desconectar e ao encerrar o servidor.

O fluxo normal do Iron's continua sendo usado. Pergaminhos consumíveis, comandos
e criativo têm regras próprias do Iron's; validar o problema com livro/arma em
survival. O uso de pergaminho não recebe recarga pelo Iron's.

### Ceifar Vida e Entregar Vida

`LifeSpell` chamava `LivesMagic.change(target, delta)`: apenas o alvo era alterado.
Agora chama uma transferência entre dois UUIDs, validada no início e no final do ritual.

- Ceifar: alvo perde uma vida de RP e conjurador recebe uma.
- Entregar: conjurador perde uma vida de RP e alvo recebe uma.
- Doador sem vidas, recebedor no limite, mesmo jogador ou personagem com morte
  definitiva impedem a operação inteira. Não há débito sem crédito.
- Os dois saldos são escritos na thread do servidor antes da sincronização de HUD
  e da atualização do placar. A segunda tentativa no mesmo tick lê os saldos atuais.
- Alcance, linha de visão, liberação pela staff e PvP de Ceifar continuam conferidos.
  Os efeitos visuais/status e mensagens de sucesso só ocorrem após a transferência.
- Mensagens para o conjurador e descrições em português/inglês explicam a transferência.

O limite `maxLives` continua valendo. Se quem ceifa já está no máximo, ninguém perde
vida. Doar/ceifar a última vida pode deixar o doador em zero; o sistema existente
passa a reconhecê-lo como exilado. A transferência não força morte nem teleporte;
validar login/respawn e o fluxo do Limbo nesse caso.

### Magias em jogadores da mesma casa/time

A implementação anterior trocava o atacante por uma fonte sem causador. Para
magias externas, cancelava o evento original e reaplicava o dano; isso perdia a
resposta de acerto original e não alcançava filtros de colisão anteriores ao evento.

Agora a fonte de dano original é preservada:

- `SpellTeamCheckMixin` libera `DamageSources.isFriendlyFireBetween` do Iron's
  quando `aliados.magiasIgnoramTime = true` e o PvP permite. Isso também alcança
  projéteis que consultam essa função para decidir colisão.
- `ServerPlayerSpellDamageMixin` modifica apenas o resultado da checagem de time
  durante dano de magia (`SpellDamageSource` ou tipo autoral `aurorion_magia`).
- Autoria, resistência, efeitos de acerto, lifesteal, abates e cancelamentos de dano
  seguem pelo caminho original, com um único evento de dano.
- Espadas/flechas comuns seguem a regra normal de fogo amigo. Servidor com PvP
  desligado e proteção de área continuam sendo respeitados.

A chave existente deve estar em `true` no servidor. Addons que usem filtros
próprios de time, fora da API do Iron's, precisam de ensaio específico.

### Simply More — Cindergorge, atordoamento e Mimi-Cry

Foi inspecionado o JAR local `simplymore-forge-1.3.0_alpha.jar`:

- `StunEffect.applyEffectTick` chama `EntityUtils.putAllItemsOnCooldown`.
- Para entidades que não são `Player`, essa rotina converte diretamente `Level`
  para `ServerLevel`. Um dummy com o efeito rodando no cliente alcança o cast inválido.
- `SimplyMoreCooldownSideMixin` interrompe essa rotina apenas no cliente. No servidor,
  o cooldown original continua sendo aplicado. Isso cobre armas que usam a mesma
  rotina de atordoamento; a frigideira deve ser incluída no ensaio.
- A proteção anterior `MimicryClientUseMixin` permanece registrada. Ela evita o cast
  direto de `MimicryItem.use` no cliente e deixa a validação/efeito no servidor.
- Os alvos são `@Pseudo`: Simply More ausente não deve impedir a inicialização.

O erro de cast está no próprio código inspecionado. O relato não demonstra que
Spell Engine, Pufferfish, Create ou Malum sejam os causadores. Não foi assumido que
alpha5 corrige esses pontos; trocar a versão de Simply More exige reconferir os
métodos alvo e reproduzir o caso com o pack completo.

### Mesa de ferraria — somente ferreiro e duplicação (18/10)

- `SmithingMenuMixin` (Profissões) apaga o resultado e recusa a retirada para quem não é
  ferreiro; staff em criativo com permissão 2 continua liberada, como na bigorna. O clique na
  mesa também é recusado com a mensagem de ferraria. O upgrade de ferraria do Sophisticated
  Backpacks herda `SmithingMenu` sem sobrescrever esses métodos e fica coberto.
- Duplicação: a causa exata não foi reproduzida. O shift-click do resultado repete
  `quickMoveStack` em loop sem consultar `mayPickup` de novo. Polymorph e as mochilas mantêm
  a receita selecionada, e um resultado antigo podia continuar no slot depois de os insumos
  acabarem. Agora cada iteração revalida a receita com os insumos atuais.
- Mods do pack que também alteram `SmithingMenu`: Citadel, Create, KnightLib, Let's Do
  Furniture e Polymorph. Nenhum sobrescreve `quickMoveStack`.
- Observação: `citadel-1.21.1-2.7.6.jar` e `citadel-up-0.2.0.jar` declaram o mesmo modId
  `citadel`. Só um é carregado; remover o que não for necessário.

## Compilação e validação em outra máquina

Levar esta árvore de fontes, JDK 21 e os JARs de referência exigidos pelo
`aurorion-magia/build.gradle` em `mod-servidor-referencia` (ou informar as propriedades
de caminho documentadas no próprio build). Então executar, **em outra máquina**:

```powershell
.\gradlew.bat :aurorion-vidas:build :aurorion-utils:build :aurorion-magia:build :aurorion-profissoes:build
```

Os JARs instaláveis serão gerados em `build/jars-servidor`. A pasta de trabalho desta
entrega não contém os JARs de referência ignorados pelo Git; copiá-los da instalação
de desenvolvimento/servidor antes da compilação.

Ensaios necessários em uma cópia do servidor com dois clientes:

1. Inicializar servidor dedicado e cliente com Iron's 3.16.3. Confirmar a aplicação
   de `SpellTeamCheckMixin` e `ServerPlayerSpellDamageMixin`, inclusive as chamadas
   `canHarmPlayer` dentro de `ServerPlayer.hurt`. Repetir com Simply More ausente.
2. Em survival, livro e arma imbuída: usar o barril repetidamente; conferir 35 s sem
   redução de recarga. Repetir com config/datapack em zero, cooldown final zerado
   por multiplicador e redução positiva. Conferir Tempus/Vínculo ao ligar/desligar.
3. Com A=2 e B=3 vidas, Ceifar de A em B deve resultar A=3/B=2. Reiniciar o servidor
   e conferir os dois saldos. Entregar de A em B deve resultar A=2/B=3.
4. Testar doador em zero, recebedor no máximo, alvo no limite durante os 2 s do ritual,
   morte definitiva, tentativa no próprio jogador, alvo fora de alcance e duas
   transferências sucessivas. Em caso inválido, os dois saldos devem permanecer iguais.
5. Testar a última vida e conferir o estado de exílio no HUD, login/respawn e Limbo;
   testar também restaurar uma vida de jogador exilado que ainda possa ser resgatado.
6. Colocar A/B no mesmo time/casa com `friendlyFire false` e a chave de magia em `true`.
   Conferir magia direta, projétil do Iron's, área, controle, lifesteal e autoria do
   abate. Conferir que espadas/flechas comuns seguem bloqueadas. Repetir com a chave
   em `false`, PvP global desligado e área sem PvP; não pode haver dano indevido.
7. Com o pack completo e dois observadores, usar Cindergorge e The Pan em dummy,
   criatura e jogador. Conferir stun/cooldown no servidor e ausência do cast em ambos
   os clientes. Usar/cancelar/trocar formas de Mimi-Cry, inclusive durante cooldown.
8. Mesa de ferraria: não ferreiro não abre nem retira resultado; ferreiro aprimora diamante →
   netherite com clique normal e com shift-click, conferindo que sai exatamente 1 item e que
   template, base e adição são consumidos. Repetir com várias pilhas de insumos (shift-click
   em loop), com Polymorph trocando a receita e no upgrade de ferraria da mochila.

## Referências de API

O comportamento do cooldown e das checagens foi conferido nas assinaturas locais
do Iron's 3.16.3, no código de Minecraft/NeoForge já disponível no cache local e no
[código oficial do Iron's](https://github.com/iron431/irons-spells-n-spellbooks/tree/1.21/src/main/java/io/redspace/ironsspellbooks).
A validação em runtime dos novos mixins continua pendente.
