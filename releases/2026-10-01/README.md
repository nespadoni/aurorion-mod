# Release dos mods — 01/10/2026

Build completo do ecossistema Aurorion para Minecraft 1.21.1, NeoForge 21.1.248
e Java 21. Os JARs instaláveis estão em `build/jars-servidor`, com uma versão
por mod. As versões substituídas foram preservadas em
`build/jars-arquivados/release-2026-10-01`.

| Mod atualizado | Versão | Alteração |
| --- | --- | --- |
| Diário | 0.1.2 | Corrige posicionamento, espaçamento e editor da tela; adiciona `/diario teste` com dados locais temporários, sem enviar ao site. |
| Core | 0.6.3 | Cadastro e persistência de múltiplos personagens alternativos, preservando o cadastro antigo. |
| Personagem | 0.3.0 | Criação, seleção e remoção de alts específicos; exige Core >= 0.6.3. |
| Utils | 0.2.2 | Proteções no cliente para uso de Mimicry e descarte de texturas. |
| Trama | 0.1.0 | Release anterior incluída no conjunto: árvore integrada às Casas com progressão automática. |

Todos os 19 mods Aurorion foram compilados com `buildAll`. A Integração permanece
em 0.2.1 e foi recompilada com as correções já versionadas. Mods cujo código não
mudou mantiveram suas versões. O C2ME remendado existente foi preservado.

Os ajustes dos runs de desenvolvimento registram TesseraUI como mod de cliente
e o copiam para cada run; o servidor dedicado não recebe essa biblioteca.

## Validação

- `buildAll`: aprovado, incluindo 368 testes unitários, sem falhas ou erros.
- Servidor dedicado de teste: Diário 0.1.2, Utils 0.2.2, Integração 0.2.1,
  Core 0.6.3, Trama, Ethereal e Iron's carregados, sem TesseraUI. Os 7 GameTests
  da Trama passaram. Simply More ausente não impediu o carregamento de Utils.
- Metadados de versão, bytecode Java 21, conteúdo dos JARs, mixins de Utils e
  comando local do Diário conferidos. Nenhuma biblioteca foi embutida nos JARs.
- Checksums de todos os artefatos em [SHA256SUMS.txt](SHA256SUMS.txt);
  versões completas em [versions.json](versions.json).

A tela do Diário, os mixins no cliente com Simply More/Moonlight e os fluxos de
personagem em contas premium ainda precisam de validação manual com o modpack.
O teste dedicado não equivale a homologação do servidor completo de produção.

## Instalação

Pare o servidor, faça backup e substitua os JARs dos mods atualizados. Mantenha
uma versão por mod. Instale também nos clientes os mods que fornecem telas e
as correções de Utils; o Diário exige TesseraUI >= 1.1 no cliente. Atualize Core
e Personagem juntos, pois Personagem 0.3.0 exige a API de Core 0.6.3.

Os JARs antigos não devem voltar para `mods` junto dos novos. Os dados de alts
antigos são migrados na leitura, preservando UUIDs; após salvar no novo formato,
um rollback de Core/Personagem deve usar também o backup anterior do mundo.
O versionamento desta entrega é local; não houve publicação no remoto.
