# Otimizações operacionais — 02/10/2026

Implementação das quatro prioridades escolhidas após a [análise do mod](ANALISE-IDEIAS-2026-10-02.md).
Plataforma: Minecraft 1.21.1, NeoForge 21.1.248 e Java 21.

## Cobranças e pedidos de ajuda

Os limites abaixo são constantes no código. O relógio dos intervalos é monotônico; tentativas
recusadas não prolongam o prazo. Sair do jogo ou trocar de personagem não apaga os intervalos
da conta principal. Reiniciar o servidor limpa esses limites, que não são persistidos.

| Ação | Intervalo mínimo | Identidade usada |
|---|---|---|
| Pacote de economia, antes de `enqueueWork` | 100 ms por tipo de pacote | UUID do perfil conectado |
| Abrir menu de cobrança | 300 ms | Conta principal |
| Selecionar interação | 250 ms | Conta principal |
| Enviar cobrança | 5 s | Conta principal |
| Responder cobrança | 250 ms | Conta principal |
| Aviso de cobrança recusada pelo intervalo | 2 s | Conta principal |
| `/ajuda <descrição>` | 30 s | Conta principal |
| Aviso de espera do `/ajuda` | 3 s | Conta principal |

Cada participante pode estar em uma cobrança pendente por vez, em qualquer dos dois papéis.
Uma cobrança nova não substitui a aprovação que já está aberta. Pagamento e recusa continuam
consumindo um token de uso único vinculado ao pagador, e a cobrança vence após 30 s.
As verificações de distância, presença, visibilidade e saldo continuam no servidor.
Os pacotes de envio/resposta de venda de terreno também passam pelo limite antes de enfileirar.
Os handlers C2S são registrados com `HandlerThread.NETWORK`; só a admissão roda ali. As ações
passam por `enqueueWork`, que também descarta a sessão desconectada/substituída. O registrar
separado mantém os handlers de cliente na thread principal. A API de agendamento foi conferida na
[documentação oficial de NeoForge 1.21.1](https://docs.neoforged.net/docs/1.21.1/networking/payload/).

O pedido de ajuda tem descrição de até 512 caracteres e remove caracteres de controle antes
de avisar operadores e gravar no log. Pedidos bloqueados não avisam a equipe nem geram um novo
registro de socorro. O limitador compartilhado guarda no máximo 4.096 identidades por instância;
se estiver cheio, reutiliza apenas posições vencidas, sem expulsar uma restrição ativa.

## Respostas HTTP

`SiteApi` e `IngestClient` usam `LimitedResponseBody`: no máximo **256 KiB por resposta**,
contados em bytes durante a leitura, inclusive com transferência chunked e sem `Content-Length`.
O subscriber soma os buffers recebidos antes de copiá-los. Ao exceder o limite, cancela a
assinatura HTTP e devolve uma falha de transporte; não tenta interpretar o conteúdo excessivo.
O limite se aplica ao corpo acumulado pelo mod, não a toda a memória interna do cliente HTTP.

O fluxo existente de reenvio mantém fatos e rascunhos pendentes quando a resposta excede o limite.
Timeouts e a política de não seguir redirecionamentos continuam vigentes. O Discord já descartava
o corpo da resposta e não acumula uma string. Esta alteração não é uma auditoria completa de
uploads ou autorização do backend/site.

## Consultas de nomes

`/personagem alt criar` passa a iniciar uma consulta assíncrona e avisa o dono quando termina.
O executor usa duas threads e uma fila de até 16 tarefas, com um pedido pendente por dono e
intervalo de 30 s. São separados até 16 candidatos localmente livres, examinando no máximo
1.000 variações locais. A consulta ao cache/Mojang e a classificação do perfil offline usam
um snapshot; o cadastro, as permissões e a whitelist são tratados na thread do servidor.

Antes de criar, o callback verifica a sessão original, a permissão, o prazo e possíveis colisões
locais. Logout cancela o pedido; desligar limpa a fila e impede que callbacks antigos cadastrem
personagens em outro mundo. Se o cadastro existir mas o cache/whitelist falhar, o jogador e o
log recebem um aviso específico, sem deixar um pedido pendente preso.

A resolução de nicks offline de `/gritar` e `/deathhistory` também usa duas threads e fila de
até 16 tarefas, separada da fila de disco do histórico. Consultas remotas são limitadas a uma
por segundo por solicitante; identidades locais continuam imediatas. Resultados voltam à thread
do servidor com nova verificação de OP 2 e da sessão. O fallback do histórico para nomes usados
nas mortes continua disponível quando o perfil consultado não é encontrado.

Os dois fluxos têm prazo lógico de 30 s: resultados atrasados não executam a ação. Esse prazo
não garante interromper uma chamada de rede interna do cache vanilla que ignore interrupção.
O número de threads e tarefas continua limitado. A interpretação de perfil inexistente ou offline
preserva o comportamento anterior; não cria uma garantia adicional sobre disponibilidade da Mojang.

## Diagnóstico

Execute **`/aurorion diagnostico`** como OP nível 2 ou pelo console. Jogadores comuns não têm
permissão. Consultas de jogadores têm intervalo de 1 s.

- Integração: estado da configuração, fila de fatos, quantidade e bytes pendentes, capacidade,
  prazo de reenvio, resolvidos/descartados, último resultado, tempo desde a tentativa/confirmação,
  estado recente das operações de disco e chamadas/tarefas HTTP em andamento.
- Diário: total de rascunhos, aptos a reenvio, conflitos/recusas, legibilidade do arquivo,
  gravação em andamento, confirmações aguardando disco, resultado da última gravação,
  idade do rascunho mais antigo e disponibilidade do transporte configurado.

O comando consulta contadores em memória. Não envia requisições, lê arquivos, força gravações
ou altera rascunhos. Não mostra tokens, URLs, textos, autores ou IDs de rascunhos. Essentials
agrega provedores opcionais do core, sem depender diretamente dos módulos Diário/Integração.
Módulos ausentes e integração desativada são identificados no relatório.

“Transporte disponível” indica que existe cliente configurado, não que o backend está saudável.
“Sem falhas registradas” no disco não certifica a durabilidade de cada item. Os contadores de
integração reiniciam com a instância; “resolvidos” inclui aceitos, duplicados e recusas definitivas.

## Validação

Nesta máquina foram feitos apenas revisão de fontes e verificações estáticas. Não executar Gradle,
compilação nem testes aqui, conforme [AGENTS.md](../AGENTS.md). Os testes abaixo foram escritos ou
atualizados, mas **não executados**:

- `ActionCooldownTest`: isolamento, prazo não prorrogado e capacidade sem expulsar limites ativos.
- `ChargeBookTest`: aprovação preservada, tokens por pagador, uso único e expiração.
- `LimitedResponseBodyTest`: UTF-8 dividido, limite exato, cancelamento antes da cópia excessiva
  e soma de buffers sem depender de cabeçalhos.
- `OutboxTest` e `PendingStoreTest`: contadores e ausência de conteúdo privado no diagnóstico.

Em outra máquina, compilar e executar `check` nos módulos `aurorion-core`, `aurorion-economia`,
`aurorion-essentials`, `aurorion-integracao`, `aurorion-diario` e `aurorion-personagem`.
Os módulos devem ser compilados e atualizados juntos, pois usam novos contratos do core.
Conferir também as suites existentes de HTTP, reenvio, persistência e diário.

Validar em servidor dedicado e clientes:

1. Envio normal de cobrança, pagamento, recusa, expiração, participante ocupado e repetição
   de pacotes. Confirmar uma única transferência e ausência de substituição da aprovação.
2. `/ajuda` e cobranças com spam, relog e troca de alt: limites compartilhados pela conta;
   avisos de espera limitados e nova ação permitida após o intervalo.
3. Backend indisponível, resposta inválida e resposta chunked acima de 256 KiB: falha controlada,
   conteúdo pendente preservado e reenvio sem duplicação após normalizar a resposta/reiniciar.
4. Criação de alt com Mojang lenta, candidatos ocupados, duas criações concorrentes, logout,
   perda de OP, whitelist, modo online/offline e desligamento durante a consulta. Conferir que
   os ticks não aguardam a rede e que callbacks inválidos não cadastram personagens.
5. `/gritar` e `/deathhistory` por UUID, personagem, nick online/offline, nome antigo e nome
   inexistente; testar fila cheia, perda de OP e desconexão durante a consulta.
6. Diagnóstico no console/OP 2, bloqueio para jogador comum, módulos ausentes, integração
   desativada, filas com falhas de disco e rascunhos em conflito. Conferir privacidade e que
   consultas não disparam reenvio.
