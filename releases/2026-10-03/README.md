# Release dos mods — 03/10/2026

Entrega de um módulo: **Aurorion Profissões 0.4.4 → 0.5.0** (NPCs de ofício refeitos). Os outros
18 módulos não mudaram desde a release de 02/10 e continuam nas mesmas versões, com o **mesmo
SHA-256** — conferido arquivo a arquivo contra `releases/2026-10-02/SHA256SUMS.txt`. As versões
estão em [versions.json](versions.json).

Build completo com `buildAll`, Java 21 (Temurin 21.0.12), Minecraft 1.21.1 e NeoForge 21.1.248:
aprovado, 129 tarefas (9 executadas, 120 em dia). Testes do Profissões: 34, dos quais 28 passaram e
6 foram ignorados (contratos de Quality Food e FoodSpoil, que só rodam com os jars desses mods),
sem falhas. O log está em `build/release-2026-10-03/build.log`.

Em `build/jars-servidor`: o `aurorion_profissoes-neoforge-1.21.1-0.4.4.jar` foi excluído e o 0.5.0
entrou no lugar; o resto da pasta ficou igual. O JAR novo foi conferido: integridade ZIP, id e
versão no `neoforge.mods.toml`, versão do manifest, bytecode Java 21, skins, CSS e catálogo
embutidos, e a dependência do TesseraUI declarada só para o cliente (o mod não é embutido). Os
hashes estão em [SHA256SUMS.txt](SHA256SUMS.txt) e na pasta dos JARs.

## O que muda no Profissões 0.5.0

- **Catálogo de NPCs dentro do mod**, com preços da Economia do Ato 2 (manual V3): serviços ~2,5×
  o profissional jogador, itens pela coluna “NPC vende”, pagamento em óbolos e 100% destruído.
  Esmeralda deixou de ser aceita como moeda.
- Enfermeira, ferreira, chef, arcanista e mercador com **skins embutidas** por ofício.
- Novos serviços: cura e reparo por faixa de gravidade/desgaste, encantar item, concentrar e
  prolongar poção, e **etiqueta com nome** digitado pelo jogador (ferreira).
- Arcanista com livros encantados e poções fortes/longas, com estoque diário.
- Tela dos NPCs em HTML/CSS do **TesseraUI**, com saldo da carteira, abas e item real em cada oferta.
- Ajustes do servidor em `config/aurorion/npcs_extras.json`. O `npcs.json` antigo é aposentado
  sozinho na primeira subida (`npcs.json.antigo-<data>`). `/npc exemplo exportar` grava uma cópia
  de consulta do catálogo.

## Atenção na instalação

- **Cliente e servidor precisam da 0.5.0 juntos**: o protocolo de rede do Profissões mudou
  (`"3"`). Cliente antigo não conecta em servidor novo, e vice-versa.
- O **cliente precisa do `tesseraui-1.1.jar`** (já exigido pelo Diário). O servidor não precisa.
- Na pasta de mods sincronizada do cliente de testes havia `aurorion_mundos` 0.1.0 e 0.1.2, e
  `aurorion_portais` 0.1.0 e 0.1.3, lado a lado. Ao instalar, conferir que só a versão nova de cada
  mod fica na pasta.

Esta entrega não foi instalada no servidor ou no CurseForge e não foi publicada no remoto.
Não foram executados GameTests nem uma sessão com o modpack completo; ainda precisam ser validados
no jogo: a tela dos NPCs (alinhamento, campo da etiqueta, tooltips), a aposentadoria do `npcs.json`
antigo, as faixas de cura com o LSO, encantar/aprimorar poção e a conexão cliente/servidor.
