# Economia do Ato 2 — arquitetura de implementação

Este documento transforma as regras da documentação do Ato 2 em um plano técnico para o mod
`aurorion-economia`. A moeda já é sempre armazenada como um número inteiro de **fragmentos**:
`1 Óbolo = 10 fragmentos`. Saldo negativo não existe.

## 1. Princípio central: toda mudança passa pelo livro-caixa

O saldo não deve mais ser alterado diretamente. Uma única API de servidor, `EconomyService`,
confere e grava uma operação atômica no livro-caixa. Cada operação recebe um UUID único; repetir o
mesmo UUID devolve o resultado anterior sem cobrar ou pagar duas vezes.

Cada lançamento registra:

- sequência, UUID da operação, instante e personagem responsável;
- tipo: `TRANSFER`, `ISSUE` (emissão) ou `BURN` (queima);
- conta de origem, conta de destino e valor em fragmentos;
- motivo estruturado (`PROFESSION_SERVICE`, `HOUSE_TAX`, `MARKET_FEE`, etc.);
- metadados pequenos, como atendimento, contrato, propriedade ou semana de referência.

Transferência preserva a oferta monetária. Emissão aumenta a oferta; queima diminui. Essa
classificação permite auditar a inflação sem reconstruir toda a história do mundo.

## 2. Contas

As contas usam uma chave tipada, em vez de UUID solto:

- `PLAYER:<characterId>` — carteira do personagem, sem herança automática entre personagens;
- `ACADEMY` e `GREYMOR` — tesouros institucionais;
- `HOUSE:<houseId>` — cofre de cada Casa;
- `ESCROW:<purposeId>` — custódia temporária para atendimento, contrato ou mercado.

O personagem, e não a conta Minecraft, é a identidade econômica. A bolsa inicial de 4 Óbolos é
concedida uma única vez por `characterId`, com operação idempotente registrada como emissão.

## 3. Persistência e auditoria

Um único `EconomyData` no `SavedData` do mundo guarda:

- saldos das contas;
- oferta total calculada incrementalmente;
- sequência do livro-caixa;
- personagens que já receberam a bolsa inicial;
- estado do fechamento semanal;
- UUIDs recentes de operações e os 50 lançamentos mais recentes para a UI.

O histórico completo é escrito também em JSONL, somente para auditoria administrativa. O arquivo
não é fonte de verdade e sua falha não pode corromper o saldo. A gravação do `SavedData` continua
atômica no tick do servidor. Nenhuma consulta de disco ocorre ao abrir uma tela ou transferir.

## 4. Fechamento semanal

O fechamento roda uma vez por semana, por padrão segunda-feira às 05:00 no fuso do servidor. A
chave `ano-semana` torna a rotina idempotente: reiniciar o servidor não repete pagamento, imposto
ou emissão.

Um personagem é ativo quando alcança 3 pontos em pelo menos duas categorias entre evento,
contrato, turno, missão e projeto. Os produtores desses eventos apenas acrescentam pontos; não há
varredura por jogador a cada tick.

Valores em fragmentos para `A` personagens ativos:

```text
alvoEmCirculacao = A * 150       # 15 Óbolos por ativo
limiteCrescimento = A * 5        # 5 fragmentos por ativo/semana
limiteAbsoluto = A * 20          # 2 Óbolos por ativo/semana
deficit = max(0, alvoEmCirculacao - dinheiroAtivo)
crescimento = min(deficit, limiteCrescimento)
emissao = min(queimaDaSemana + crescimento, limiteAbsoluto)
```

A emissão entra na Academia. O fechamento segue uma ordem fixa: consolidar atividade, medir
oferta, calcular emissão, distribuir verbas, cobrar manutenção, pagar dividendos e publicar o
resumo. Quando faltar saldo institucional, pagamentos divisíveis usam rateio proporcional em
fragmentos; o resto é distribuído em ordem determinística de UUID.

## 5. Impostos, Casas e propriedades

- Renda oficial retém 10% e envia o imposto ao cofre da Casa do personagem.
- Pagamento direto e venda no mercado não recebem esse imposto de Casa.
- Metade do imposto semanal da Casa vira dividendo entre membros elegíveis, em partes iguais, com
  teto de 2 Óbolos por pessoa. O restante permanece no cofre.
- Cada cofre tem limite por upgrade (100/250/500/1.000 Óbolos). Quando estiver cheio, o imposto
  excedente não é cobrado e permanece com o jogador; reduzir o nível nunca destrói saldo.
- Propriedade referencia o ID de uma área do `aurorion-areas`, sua zona e área calculada quando o
  cadastro muda. Nunca percorre os blocos da região para cobrar manutenção.
- Atrasos e perdão administrativo são estados e operações auditáveis, nunca edição silenciosa de
  saldo.

## 6. Profissões e pagamentos entre jogadores

O fluxo universal já começa no menu configurável **Shift+G**: o jogador mira outro jogador, escolhe
**Fazer cobrança**, informa o valor e o alvo recebe a decisão de pagar ou recusar. Não exige
profissão. Alcance, mira, linha de visão, valor, prazo e token são sempre revalidados no servidor.

A abertura dos serviços do `aurorion-profissoes` já é outro consumidor do mesmo menu; os gatilhos
antigos por J e agachar+interagir foram removidos. A etapa financeira do atendimento ainda precisa
da custódia: ao aceitar uma proposta de serviço, a economia reservará o valor numa conta `ESCROW`
ligada ao UUID do atendimento. O evento
`ServiceEvent.Completed` libera o valor ao profissional. Cancelamento, expiração, logout ou falha
de validação devolvem a reserva ao cliente.

Antes dessa integração, `aurorion-profissoes` precisa expor um evento terminal de cancelamento.
Isso impede dinheiro preso e evita cobrar antes do resultado. Toda cobrança aparece na mesma tela
de confirmação; jogadores não dependem de comandos.

O preço de médico, ferreiro, cozinheiro e arcanista é livre. Um atendimento emergencial por NPC
pode existir como piso de disponibilidade, com preço alto e queima integral, preservando a demanda
por jogadores.

## 7. Contratos, projetos e mercado

- Contratos só ficam disponíveis depois de reservar o orçamento total em custódia. Quantidade,
  prazo e orçamento são limites persistidos e validados no servidor.
- Projetos consomem recursos reais e registram o vínculo entre entrega e recompensa.
- O mercado usa custódia de item e dinheiro; conclui entrega e pagamento na mesma transação. Taxas
  do mercado são lançamentos de queima separados, para aparecerem no relatório.

## 8. UI

O Shift+G é a entrada rápida para interações presenciais; o celular continua sendo a entrada para
carteira e operações remotas. A evolução prevista é:

1. menu presencial, cobrança, aceite e comprovante;
2. carteira com saldo e extrato curto;
3. propostas específicas de profissão dentro do mesmo menu;
4. painel da Casa e fechamento semanal;
5. contratos, projetos, propriedades e mercado;
6. painel administrativo com oferta, emissão, queima e operações de correção auditadas.

Comandos ficam restritos a diagnóstico e administração. Toda regra é validada novamente no
servidor; a UI nunca envia saldo final nem decide autorização.

## 9. Plano de entrega

### Fase 1 — fundação contábil

- `AccountKey`, `LedgerEntry`, `EconomyOperation` e `EconomyService`;
- migração dos saldos atuais para contas de personagem;
- emissão, queima, transferência, idempotência e oferta total;
- bolsa inicial de 4 Óbolos uma vez por personagem;
- extrato curto e auditoria JSONL.

### Fase 2 — carteira e pagamento

- adaptar o app do celular ao novo serviço;
- tela de confirmação e comprovante;
- manter `/saldo` e `/pagar` apenas durante a migração, depois removê-los do fluxo comum.

### Fase 3 — profissões

- reserva, conclusão e cancelamento de atendimento;
- recibo visível aos dois jogadores;
- emissão dos pontos de atividade pelo serviço concluído.

### Fase 4 — ciclo semanal e Casas

- atividade, tesouros, imposto oficial, dividendos, limites e relatório semanal.

### Fase 5 — contratos e projetos

- orçamento reservado, entrega verificável e recompensa transacional.

### Fase 6 — propriedades e mercado

- manutenção por área/zona, inadimplência, custódia de itens e taxas de mercado.

## 10. Orçamento de desempenho

- zero trabalho econômico por tick de jogador;
- fechamento semanal por listas de contas/atividade, nunca por blocos ou chunks;
- saldos e índices em memória; disco somente quando `SavedData` fica sujo e no log de auditoria;
- no máximo uma sessão de negociação por jogador, com expiração por fila de prazo;
- pacotes de rede limitados à abertura/ação da UI, sem sincronização contínua;
- consultas de área por ID e dimensões persistidas, sem varredura do território 2000×2000.

## 11. Decisões de produto ainda abertas

1. **Morte definitiva:** recomendação inicial é mover propriedade e contratos para um espólio por
   prazo curto; depois, leilão ou retorno à instituição definida.
2. **Lojas:** decidir se pertencem ao personagem, à Casa ou a uma instituição. A conta tipada já
   aceita qualquer uma das três opções.
3. **Perdão de dívida/manutenção:** permitir apenas por ação administrativa auditada, com motivo.

