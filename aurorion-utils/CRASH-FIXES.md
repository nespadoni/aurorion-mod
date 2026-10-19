# Proteções para crashes de cliente

## Atualização preparada em 18/10/2026: Utils 0.2.5 (Copycats+ e Sodium)

Os copycats travam o jogo no **cliente**, não no servidor. Os decorativos não têm ticker; isso foi
conferido no bytecode do Copycats+ 3.0.9 e do Create 6.0.10. O custo está na montagem de malha dos
chunks:
- cada reconstrução recalcula os quads de cada copycat do zero;
- os vizinhos são consultados várias vezes;
- com "Atualizações de chunk = Imediato", o Sodium segura o quadro até terminar.

Esta versão traz só as correções de baixo risco. O cache de quads entre blocos, que daria o ganho
maior, ficou de fora de propósito por risco de textura errada.

- **`MultiStateMaterialGuardMixin`** (`MultiStateCopycatBlockEntity` e `CopycatCogWheelBlockEntity`).
  - O problema: o `getMaterial()` padrão não checa nulo. Quando o bloco é quebrado enquanto a thread
    de malha do Sodium ainda lê o chunk, o cliente cai com "Encountered exception while building chunk
    meshes" (`crash-2026-09-20_00.19.12-client.txt`, `copycat_byte`).
  - A correção: usa a parte padrão; senão, qualquer parte com material; senão, a base de copycat do
    Create.
- **`CopycatClientNotifyMixin`** (`SyncedBlockEntity.notifyUpdate`).
  - O problema: cada leitura de NBT de copycat chamava `notifyUpdate()` também no cliente, a cada
    chunk carregado. No cliente isso só avisa os 6 vizinhos de sinal de comparador, que é trabalho
    inútil ali.
  - A correção: cancela só para BEs de copycat e só no cliente.
- **`ClientHooksSolidLayerMixin`** + **`BlockStateSolidLayerMixin`**.
  - O problema: `isBlockInSolidLayer` criava um `RandomSource` novo a cada face testada, disputado
    pelas threads de malha.
  - A correção: a resposta fica num byte do `BlockState` e é zerada a cada recarga de modelos.
  - Vale para todos os blocos, não só para os copycats.
- **`SodiumFaceHidingOrderMixin`** (`NeoForgeBlockAccess.shouldSkipRender`).
  - O problema: o Sodium calculava primeiro o `hidesNeighborFace` caro do copycat vizinho, e depois
    descartava o resultado para todo bloco sólido.
  - A correção: a ordem do "e" foi invertida; o resultado é idêntico. Usa `require = 0`, então outra
    versão do Sodium só desliga a otimização.

As configs `aurorion_utils.copycats.mixins.json` e `aurorion_utils.sodium.mixins.json` carregam só
quando o Copycats ou o Sodium estão no pack. Precisa chegar aos **clientes** pelo AutoModpack; trocar
só o servidor não muda nada. O jar do Copycats em `mod-servidor-referencia/` é só para compilar.

**Para os jogadores, sem atualizar nada:** *Opções de vídeo → Desempenho → Atualizações de chunk*
em **"Em breve"** em vez de "Imediato". O Sodium deixa de segurar o quadro esperando as reconstruções
perto da câmera, e é isso que vira "travada" perto de construções com muitos copycats.

Validação pendente: compilar; entrar perto de uma construção grande com copycats e comparar o tempo
de quadro; quebrar copycats multiestado (byte, painel) repetidamente sem crash; conferir que nenhuma
face de bloco sumiu ou sobrou encostada em copycat.

## Atualização preparada em 17/10/2026 — Utils 0.2.4

`SimplyMoreCooldownSideMixin` impede `EntityUtils.putAllItemsOnCooldown` de executar
no cliente. O Simply More 1.3.0_alpha converte o mundo de entidades que não são
jogadores em `ServerLevel`, inclusive quando `StunEffect` é aplicado em um dummy
no cliente. A rotina original continua funcionando no servidor. A proteção anterior
da Mimi-Cry permanece ativa.

A versão deve chegar a todos os clientes pelo pack/AutoModpack. Compilação e ensaios
em jogo desta atualização estão pendentes em outra máquina; ver
[roteiro completo](../docs/CORRECOES-MAGIAS-SIMPLYMORE-2026-10-17.md).

## Histórico — 01/10/2026

Os dois relatórios fornecidos são crashes do cliente conectado ao servidor.

- `crash-2026-10-01_18.52.43-client.txt`: Simply More `1.3.0_alpha`,
  `MimicryItem.use`, linha 65. O JAR instalado faz cast direto de `Level` para `ServerLevel`.
  `MimicryClientUseMixin` trata a previsão de uso no cliente sem executar esse cast. No servidor,
  o método original continua conferindo a habilidade e iniciando seu uso. O alvo é opcional:
  sem Simply More, o mixin `@Pseudo` não exige a classe.
- `crash-2026-09-30_22.06.04-client.txt`: `DynamicTexture.lambda$new$0` acessa `pixels`
  nulo na fila de renderização. `DisposedDynamicTextureMixin` ignora a inicialização de uma
  textura já descartada e agenda `close()` na thread de renderização quando chamado de outra
  thread. O relatório não identifica o mod que descartou a imagem; Moonlight aparecer como mixin
  nessa classe não prova que seja o causador. Esta é uma proteção do ciclo de vida da textura.

## Instalação da versão 0.2.2

Atualize `aurorion_utils-neoforge-1.21.1-0.2.2.jar` no servidor/pack e nos clientes via AutoModpack,
substituindo o JAR antigo. A correção precisa carregar no cliente; trocar somente o servidor
não corrige esses crashes. Nenhum JAR instalado foi alterado nesta tarefa.

## Validação pendente

- Conferir a aplicação dos dois mixins no cliente Minecraft 1.21.1.
- Abrir cliente com o pack completo, inclusive Moonlight, e repetir uso/cancelamento da Mimicry,
  com habilidade liberada e bloqueada; confirmar que o servidor valida e aplica os efeitos.
- Repetir conexões/desconexões e carregamento/descarte de texturas; conferir também aparência
  de skins e interfaces. O crash de textura não contém passos de reprodução.
- Iniciar servidor dedicado e cliente sem Simply More para validar o alvo opcional.
- Ao atualizar Simply More ou Minecraft, reconferir os métodos alvo dos mixins.

Build executado e aprovado nesta máquina em 01/10/2026, com autorização explícita
do usuário. O servidor dedicado de validação carregou Utils 0.2.2 e concluiu os
7 GameTests da Trama sem Simply More instalado, confirmando que o alvo ausente
não impede a inicialização. A aplicação dos mixins no cliente e a reprodução
dos crashes com o modpack completo continuam pendentes.
