# Aurorion Mod: análise e ideias de implementação

Data: 02/10/2026. Base: código local em Minecraft 1.21.1, NeoForge 21.1.248 e Java 21.

As quatro otimizações técnicas escolhidas depois desta análise estão documentadas em
[Otimizações operacionais](OTIMIZACOES-OPERACIONAIS-2026-10-02.md), incluindo limites e validações pendentes.

## Escopo e método

Foi feita uma varredura estática de todos os 752 arquivos Java em `src` dos 19 módulos: 671 de produção e 81 de testes/GameTests, somando 74.299 linhas físicas, inclusive comentários e linhas vazias. A análise procurou pontos de rede, permissões, identidade, persistência, eventos, limites, trabalho por tick e integrações. Os principais fluxos foram lidos em detalhe e comparados com os READMEs, `SDD.md` e `ECONOMIA.md`.

Também foram conferidos os registros/configurações de build, os usos dos scripts auxiliares e a sintaxe dos 197 JSON em `src/main/resources`: nenhum erro de sintaxe encontrado. Isso não verifica referências de registros, semântica de datapacks, geometrias ou funcionamento em jogo.

O inventário abrange todos os módulos, mas não representa uma revisão manual linha a linha de todos os arquivos. Bibliotecas/JARs de terceiros, saídas de build, saves, caches e arquivos temporários não foram auditados. Nenhum Gradle, compilação ou teste do mod foi executado, conforme `AGENTS.md`. Nenhuma mecânica foi alterada nesta análise.

## O que já existe e vale aproveitar

O projeto já tem identidade separada de personagem/conta, reset com transação registrada, vários alts, casas configuráveis, cerimônia, mural, cofres, vidas, resgate no Limbo, magias autorizadas pelo servidor, progressão passiva, venda de terrenos, cobrança presencial, profissões, NPCs, anúncios de serviços e integração com o site.

A estrutura de módulos, contratos opcionais no core e conteúdo por datapack favorece novas integrações. Áreas já usam índice espacial; várias rotinas já trabalham por evento; rede e disco da integração já ficam fora da thread principal. As propostas abaixo preservam essas decisões.

Uma diferença relevante: o livro-caixa, contas econômicas tipadas, custódia e fechamento semanal descritos em `ECONOMIA.md` ainda são planejamento. O código atual usa `Wallet`/`WalletData`, com saldos por UUID de perfil, cofres e escrituras. `ServiceManager` ainda apresenta atendimentos sem cobrança integrada. Não se deve tratar o texto da SDD como comprovação de que toda a economia planejada está implementada.

## Ideias prioritárias

Esforço é relativo: baixo, médio ou alto. Não é estimativa de prazo. Cada proposta começa com uma entrega pequena e verificável.

### 1. Contratos de trabalho com pagamento reservado

**Experiência:** contratar um ferreiro, médico ou escolta pelo celular, combinar o valor, reservar o pagamento e liberá-lo após a entrega. Cancelamento e disputa têm regras explícitas. Isso dá utilidade ao dinheiro e conecta os anúncios ao trabalho realizado.

**Base:** `servicos/ServicosManager`, `profissoes/ServiceManager`, `ServiceEvent.Completed`, `economia/Wallet` e o desenho de custódia em `ECONOMIA.md`.

**Primeira entrega:** um atendimento nativo de profissão, com orçamento aceito pelo cliente, reserva no aceite e conclusão validada pelo servidor. Depois expandir para trabalho livre com confirmação das duas partes e intervenção da staff.

**Regras:** operação idempotente, participantes identificados por personagem, devolução após cancelamento/reinício conforme o estado persistido. O app atualmente marca pedidos aceitos como concluídos depois de um dia em `ServicosRules.age`; essa regra não pode virar prova automática de entrega ou disparar pagamento.

**Esforço:** alto. Depende da fundação econômica da ideia 2.

### 2. Livro-caixa, extrato e economia por personagem

**Experiência:** o jogador vê por que ganhou ou perdeu Óbolos; a staff encontra a origem de saldo indevido e mede emissão/queima. NPCs, PIX, cobranças, terrenos e cofres usam a mesma regra.

**Base:** `economia/server/Wallet.java`, `WalletData.java` e `ECONOMIA.md`, que já propõe esse caminho.

**Primeira entrega:** operações de transferência, emissão e queima com ID, motivo, ator e extrato recente. Migrar os saldos existentes com backup e reconciliação; só depois adotar contas `PLAYER:<characterId>`, instituições e custódia. Preservar a separação que os UUIDs dos alts já oferecem e explicitar a regra de nascimento/morte de personagens no mesmo perfil.

**Regras:** repetir operação não repete pagamento; total debitado coincide com total creditado em transferências; nenhum saldo negativo; auditoria não altera saldos; bolsa inicial continua uma vez por personagem. A classificação precisa ser consistente nos NPCs, terrenos e cofre, não apenas no PIX.

**Esforço:** alto. Maior valor como base para os próximos sistemas.

### 3. Governança dos cofres das Casas

**Experiência:** cada Casa escolhe tesoureiro, aprova despesas e acompanha projetos. Um membro pode contribuir sem necessariamente ter permissão para retirar todo o saldo.

**Base:** `ethereal/house/HouseMuralManager.transfer` declara e permite depósitos/saques por qualquer membro; já registra movimentações no mural. Isso é uma política atual, não um bypass de autorização descoberto.

**Primeira entrega:** depósitos livres e saques configuráveis por cargo/limite diário. Opcionalmente exigir aprovação de um segundo membro autorizado para valores maiores. Mostrar destino e motivo no mural.

**Regras:** aprovação vinculada à Casa, quantia e operação; não aprovar o próprio saque; conferir cargos/saldo novamente na execução; mudanças de cargo invalidam propostas; o executor debita uma única vez. Cofre e histórico hoje ficam em estados distintos: avaliar consistência antes de usar o histórico para decisões financeiras.

**Esforço:** médio.

### 4. Aulas e certificados da Academia

**Experiência:** professores abrem aulas presenciais; alunos recebem registro de formação, liberam uma magia específica e acompanham sua trajetória. A staff organiza conteúdo, em vez de executar concessões individuais repetidas.

**Base:** `magia/unlock/SpellAccess`, `SpellUnlockData`, `core/house/HouseGate` e progressão da Trama. Atualmente a concessão oficial é feita pela staff.

**Primeira entrega:** aula cadastrada com professor autorizado, participantes presentes e certificado por personagem. A conclusão ensina uma magia permitida, usando a mesma autorização do servidor já existente.

**Regras:** presença validada pelo servidor; certificado não duplicável; magia proibida mantém autorização específica; professores recebem permissões próprias, sem precisar de OP geral. Não premiar só por ficar parado no local ou enviar mensagens.

**Esforço:** médio a alto.

### 5. Terrenos com moradores, comércio e aluguel

**Experiência:** o corretor já vende o lote; o próximo passo é o proprietário definir moradores e visitantes, abrir uma loja e alugar espaços com regras claras.

**Base:** `economia/server/LandSaleManager`, `land/LandDeed` e `WalletData.deeds`. O módulo de Áreas já resolve regras e exceções, mas não apareceu consumo direto das escrituras por ele.

**Primeira entrega:** consulta visual da escritura, lista de moradores e uma regra pequena de acesso/interação. Em seguida, aluguel por contrato, renovação e revenda. Comissões do corretor devem ser uma decisão explícita: a venda atual retira o valor de circulação e não paga comissão.

**Regras:** lote continua pertencendo ao ID de personagem; definir o destino após morte definitiva; sobreposições continuam recusadas; desativar a ponte territorial não apaga escrituras. Não modificar blocos/chunks a cada tick para representar propriedade.

**Esforço:** médio para consulta/moradores; alto para aluguel e proteção completa.

### 6. Missões e projetos coletivos por Casa

**Experiência:** mural oferece expedição, atendimento comunitário ou projeto aprovado pela staff. Diferentes ofícios participam, e a Casa ganha progresso narrativo e recursos.

**Base:** mural, placares, NPCs configuráveis, regras territoriais e eventos de profissão. Não foi encontrado um gerenciador próprio completo de missões no código Aurorion examinado; outros mods do pack podem já oferecer parte dessa função.

**Primeira entrega:** três tipos de objetivo por evento: atendimento validado, chegada a uma região e entrega de uma lista de itens. Objetivos de construção podem usar aprovação da staff, evitando varredura contínua do mundo.

**Regras:** ID estável da missão e versão de conteúdo; uma recompensa por conclusão; cotas; evitar farm por troca de personagens ou repetição entre a mesma dupla. Conclusão por fato do servidor, nunca por pacote do cliente afirmando que concluiu.

**Esforço:** alto. Começar por conteúdo simples, antes de uma árvore de missões extensa.

### 7. Progressão da Trama também para RP presencial

**Experiência:** médicos, professores e outros personagens envolvidos em cenas presenciais progridem mesmo quando não estão explorando ou construindo.

**Base:** `trama/progression/ActivityWindow` reconhece movimento, câmera e interação. Apenas conversa estacionária não satisfaz os critérios. Já existem limites diários e progressão por personagem.

**Primeira entrega:** registrar tempo de formação em aulas concluídas e atendimentos reais, com teto próprio. Decidir explicitamente se esse tempo também atende o requisito de minutos ativos; conceder apenas XP pode não resolver a limitação atual.

**Regras:** manter proteção contra AFK; não conceder pontos por número de mensagens ou PIX; limitar repetição da mesma dupla; não recompensar personagens da mesma conta entre si. Eventos válidos complementam a heurística, sem torná-la uma prova de presença humana.

**Esforço:** médio, após aulas/contratos.

### 8. Chat de RP com entrega local pelo servidor

**Experiência:** fala normal chega a quem está perto; sussurro presencial tem raio menor; grito tem raio maior. `/me`, `/do` e dados públicos enriquecem cenas e podem aparecer em balões próprios.

**Base:** `talk/client/IncomingChat` cria o balão após receber a mensagem e pode ocultá-la no HUD. `TalkServerEvents` restringe comandos privados, mas não implementa distribuição espacial da fala.

**Primeira entrega:** confirmar se algum outro mod do pack já controla o chat local; se não, estabelecer destinatários no servidor, por dimensão e distância, respeitando o protocolo de chat da versão. Ocultar a mensagem no cliente não garante que pessoas distantes deixaram de recebê-la.

**Regras:** validar modalidade e raio no servidor; tratar assinaturas do chat e alts; registrar apenas o necessário para moderação; canais da staff têm autorização própria. Evitar mensagens globais duplicadas e compatibilizar com voz/celular.

**Esforço:** médio, com teste obrigatório em servidor dedicado e dois clientes.

### 9. Seleção visual e ficha dos personagens

**Experiência:** `/personagem` abre uma lista com nome exibido, Casa, profissão e estado de cada personagem próprio. Escolher um deles realiza a troca já existente. A ficha oferece atalhos para diário e progressão.

**Base:** `personagem/alt/AltLogin.switchCharacter`, `core/character/AltData.forOwner`, dados de personagem e fakename. A troca já grava a escolha e reconecta; não precisa substituir essa estratégia.

**Primeira entrega:** seletor somente de leitura seguido da ação de troca. Separar nome cadastrado e nome exibido, para mudança de fakename não modificar titularidade, saldo ou histórico.

**Regras:** servidor monta a lista a partir da conta autenticada; cliente não escolhe outro dono; personagem morto tem estado claro; não revelar o dono de alts a outros jogadores. Preservar fallback por comando para clientes sem a interface.

**Esforço:** baixo a médio.

### 10. Diário com sugestões de acontecimentos

**Experiência:** após uma aula, expedição ou resgate, o personagem recebe uma sugestão de entrada: título, data e participantes permitidos. Ele escreve o relato e decide publicar.

**Base:** `core/integration/GameFacts`, fila da Integração e o Diário compartilhado com o site.

**Primeira entrega:** uma sugestão por acontecimento relevante, guardada no backend como privada, com link para editar. Reutilizar ID do evento para impedir sugestões duplicadas nos reenvios. Essa expansão também exige alterações no backend/site.

**Regras:** nenhuma publicação automática; ocultar informação de staff, coordenadas e identidades secretas; fatos históricos preservam o nome no momento do acontecimento. Decidir a diferença entre nome cadastrado e fakename: `GameFacts.subject` ainda usa o nome cadastrado, enquanto o Diário captura o nome exibido.

**Esforço:** médio.

### 11. Rumores do Oráculo e expedições de resgate

**Experiência:** procurar o Oráculo revela pistas graduais de quem caiu no Limbo; companheiros da Casa podem receber pistas diferentes. Isso transforma busca e resgate em atividade coletiva.

**Base:** `limbo/rescue/RescueManager.listExiles` atualmente lista todos gratuitamente e documenta que a economia de informação ainda não foi construída. A rotação diária do Oráculo e o resgate já existem; não precisam ser recriados.

**Primeira entrega:** informação por níveis, com acesso definido por Casa/vínculo e pistas escritas. Cobrança opcional só após o livro-caixa; não bloquear toda tentativa de resgate atrás de pagamento.

**Regras:** garantir que a pessoa consiga obter informação suficiente antes do prazo; não revelar conta real, alt ou coordenadas por acidente; configurar publicidade dos exilados como decisão narrativa.

**Esforço:** médio.

### 12. Painel da staff e chamados persistentes

**Experiência:** `/ajuda` gera um chamado com número, responsável e estado; a equipe consegue retomá-lo depois de reiniciar. Um painel mostra fila do site, rascunhos pendentes e problemas de configuração.

**Base:** `essentials/command/AjudaCommand` já avisa OPs e registra no log, mas não mantém uma fila de atendimento. `integracao/outbox/Outbox.backlog` já fornece uma métrica útil; histórico de mortes tem recibos e reservas próprios.

**Primeira entrega:** chamados abertos/assumidos/encerrados, com limite por jogador; comando administrativo de diagnóstico com contadores e última falha, sem token ou texto privado de diário.

**Regras:** acesso por permissão de moderação; coordenadas e nome real ficam restritos; retenção definida; ação administrativa tem ator e motivo; diagnóstico não executa correções destrutivas.

**Esforço:** médio. Pode começar antes da nova economia.

## Melhorias técnicas a priorizar

| Ponto observado | Implementação sugerida | Como validar |
| --- | --- | --- |
| `ChargeManager.open` tem intervalo de 300 ms; `select` e `submit` não têm limitação equivalente | Limitar todas as ações, preservar uma cobrança pendente e evitar reabrir repetidamente a tela do alvo | Envio repetido não satura fila, tela ou notificações; aceite válido continua único |
| `/ajuda` distribui e registra cada chamada sem intervalo próprio | Limite por conta e limite de chamados simultâneos, mantendo mensagem útil de retorno | Spam não inunda OPs/log; pedido legítimo e atendimento permanecem acessíveis |
| Cooldowns de vários handlers rodam depois de `enqueueWork` | Avaliar orçamento de admissão por conexão antes de ocupar a thread do servidor, usando estado seguro para threads | Carga de pacotes inválidos permanece limitada sem desconectar jogadores normais |
| `SiteApi` usa `BodyHandlers.ofString`; só depois `parse` confere 256 KiB. `IngestClient` também materializa resposta inteira | Limitar bytes durante leitura HTTP, além de timeout e limite do parser; testar respostas excessivas | Endpoint devolvendo corpo grande é interrompido sem reter tudo na memória |
| `AltLogin.profileNameTaken` pode consultar a Mojang sincronicamente; o laço permite até mil candidatos | Resolver nomes fora da thread principal, limitar tentativas e revalidar propriedade/conexão antes de criar | Falha/lentidão de rede não para ticks; duas criações concorrentes não colidem |
| Luz dinâmica percorre entidades renderizáveis e faz alterações locais de luz | Opções por distância, número de fontes e frequência; começar medindo no cliente | Comparar frame time em cena com muitas entidades; não afirmar ganho sem medição |
| `AreaCast.victims` ordena todos os candidatos antes de cortar para o máximo | Medir aglomerações; se relevante, selecionar os mais próximos com coleção limitada | Mesmos alvos/resultados, menos alocações/ordenação quando há muitas entidades |
| Não há `.github` de CI neste checkout; vários fluxos dependem de compatibilidade com mods externos | Build, testes existentes e GameTests em máquina adequada; manifesto de JARs/versões e matriz de compatibilidade | Servidor dedicado sobe com o pack completo e combinações opcionais documentadas |

Esses pontos descrevem o código observado e melhorias propostas. Não houve teste de exploração, benchmark ou confirmação de ataque no servidor publicado. Não equivalem a uma declaração de que o pack está inseguro ou livre de falhas.

## Cobertura por módulo

| Módulo | Java produção / testes | Principal oportunidade |
| --- | ---: | --- |
| `aurorion-core` | 30 / 5 | Contratos pequenos para aulas/conclusões e identidade estável; adicionar só o que tem uso real compartilhado |
| `aurorion-personagem` | 18 / 0 | Seletor visual e criação de alt sem consulta de rede bloqueando ticks |
| `aurorion-essentials` | 84 / 17 | Chamados persistentes, admissão contra spam e diagnóstico da staff |
| `aurorion-talk` | 35 / 3 | Entrega local de fala, ações de RP e acessibilidade dos balões |
| `aurorion-aeonita` | 13 / 1 | Orçamento de luz/efeitos; usar os itens existentes como recompensas, sem recriar catálogo |
| `aurorion-ethereal` | 57 / 7 | Governança, projetos e despesas aprovadas das Casas |
| `aurorion-areas` | 35 / 7 | Ponte com terrenos, salas de aula e regiões de missão, mantendo índice espacial |
| `aurorion-profissoes` | 59 / 9 | Atendimento pago, certificados e contribuição comprovada para projetos |
| `aurorion-economia` | 37 / 5 | Livro-caixa, extrato, custódia e migração explícita para contas por personagem |
| `aurorion-servicos` | 27 / 2 | Contratos, confirmação real de entrega e histórico de trabalho |
| `aurorion-magia` | 92 / 3 | Aulas com concessões verificadas e permissões específicas de professor |
| `aurorion-trama` | 14 / 4 | Formação presencial além da heurística de movimento |
| `aurorion-portais` | 21 / 2 | Expedições com bilhetes usando `TransitPass`, horários e consumo já existentes |
| `aurorion-mundos` | 16 / 3 | Destinos de expedições e diagnóstico de terreno pré-gerado, sem geração inesperada |
| `aurorion-vidas` | 15 / 0 | Matriz de testes de morte/ressurreição/respawn com os outros mods |
| `aurorion-limbo` | 67 / 9 | Rumores do Oráculo e expedições; resgate e rotação já implementados |
| `aurorion-utils` | 29 / 0 | Cenas guiadas pela staff com cancelamento/retorno e recuperação após reinício |
| `aurorion-integracao` | 10 / 2 | Métricas da fila, respostas HTTP limitadas e novos tipos de fato acordados com o backend |
| `aurorion-diario` | 12 / 2 | Sugestões privadas de relato e ficha integrada de personagem |

`aurorion-runs` é o agregador de execução, sem classes Java próprias. Zero arquivos de teste em um módulo não significa ausência total de cobertura: contratos de identidade, por exemplo, são testados no core, e integrações podem ser exercitadas por GameTests de outro módulo.

## Ordem sugerida

1. Melhorias pequenas: limitar spam de cobranças/ajuda, diagnosticar filas, implementar seletor de personagens e esclarecer a documentação econômica.
2. Fundação: livro-caixa e migração de saldos, testes de repetição/reinício e permissões do cofre.
3. Rotina de RP: contrato de um atendimento, aula de uma magia e reconhecimento de formação presencial.
4. Conteúdo amplo: projetos de Casa, aluguel, expedições, rumores e sugestões privadas de diário.

Para um primeiro pacote com efeito visível no jogo, escolher **seletor de personagens + governança do cofre + uma aula registrada**. Contratos financeiros entram depois da fundação econômica.

## Validação em outra máquina

Antes de publicar implementações, compilar os módulos afetados e executar testes existentes na máquina autorizada. Os testes prioritários são:

- Servidor dedicado com dois clientes: aceitar/recusar, distância, morte, logout, troca de item, troca de personagem e cliente sem canal opcional.
- Repetição de pacote, recibo e reinício: nenhum pagamento, certificado, recompensa ou aprovação em dobro.
- Conta com vários personagens: saldo, profissão, magias, diário, escritura e permissões não passam para o personagem errado; nascimento/morte seguem a regra definida.
- Morte/ressurreição: vidas, Limbo, histórico, PlayerRevive e Cinematic Respawn concordam sobre a morte confirmada.
- Site indisponível e resposta HTTP excessiva: integração mantém limites e rascunhos; servidor continua responsivo.
- Cena com muitos jogadores/mobs/luzes: medir frame time, tempo de tick, filas e memória antes e depois das otimizações.

## Fontes locais mais relevantes

- [Arquitetura do projeto](../SDD.md) e [plano econômico](../aurorion-economia/ECONOMIA.md).
- [Carteira atual](../aurorion-economia/src/main/java/com/aurorion/economia/server/Wallet.java), [estado econômico](../aurorion-economia/src/main/java/com/aurorion/economia/server/WalletData.java) e [cobrança](../aurorion-economia/src/main/java/com/aurorion/economia/server/ChargeManager.java).
- [Mural/cofre](../aurorion-ethereal/src/main/java/com/aurorion/ethereal/house/HouseMuralManager.java) e [venda de terrenos](../aurorion-economia/src/main/java/com/aurorion/economia/server/LandSaleManager.java).
- [Atendimento](../aurorion-profissoes/src/main/java/com/aurorion/profissoes/server/ServiceManager.java), [app Serviços](../aurorion-servicos/src/main/java/com/aurorion/servicos/server/ServicosManager.java) e [estados/expiração](../aurorion-servicos/src/main/java/com/aurorion/servicos/data/ServicosRules.java).
- [Autorização de magia](../aurorion-magia/src/main/java/com/aurorion/magia/unlock/SpellAccess.java), [atividade da Trama](../aurorion-trama/src/main/java/com/aurorion/trama/progression/ActivityWindow.java) e [progressão](../aurorion-trama/src/main/java/com/aurorion/trama/server/TramaRuntime.java).
- [Troca de personagem](../aurorion-personagem/src/main/java/com/aurorion/personagem/alt/AltLogin.java), [balões recebidos](../aurorion-talk/src/main/java/com/aurorion/talk/client/IncomingChat.java) e [comandos privados](../aurorion-talk/src/main/java/com/aurorion/talk/server/TalkServerEvents.java).
- [Resgate/lista do Oráculo](../aurorion-limbo/src/main/java/com/aurorion/limbo/rescue/RescueManager.java), [passes](../aurorion-portais/src/main/java/com/aurorion/portais/pass/TransitPass.java) e [políticas territoriais](../aurorion-areas/src/main/java/com/aurorion/areas/api/AreaApi.java).
- [Fatos públicos](../aurorion-core/src/main/java/com/aurorion/core/integration/GameFacts.java), [HTTP assíncrono](../aurorion-integracao/src/main/java/com/aurorion/integracao/outbox/SiteApi.java), [fila durável](../aurorion-integracao/src/main/java/com/aurorion/integracao/outbox/Outbox.java) e [pedidos de ajuda](../aurorion-essentials/src/main/java/com/aurorion/essentials/command/AjudaCommand.java).
- [Luz dinâmica](../aurorion-aeonita/src/main/java/com/aurorion/aeonita/client/DynamicLightHandler.java) e [seleção de alvos](../aurorion-magia/src/main/java/com/aurorion/magia/spell/AreaCast.java).
