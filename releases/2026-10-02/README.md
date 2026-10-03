# Release dos mods — 02/10/2026

Os quatro repositorios Git do workspace foram atualizados com `git pull --ff-only`:
Aurorion Mod, Bot, Frontend e Backend. Todos ja estavam atualizados nos respectivos
branches. Esta entrega compila os 19 modulos do Aurorion Mod, com incremento de
uma versao patch por modulo. As versoes estao em [versions.json](versions.json).

Build completo com `buildAll --rerun-tasks`, Java 21, Minecraft 1.21.1 e NeoForge
21.1.248: aprovado, com 129 tarefas executadas. Os relatorios registram 374 testes,
dos quais 350 passaram e 24 foram ignorados, sem falhas ou erros.

Os arquivos instalaveis estao em `build/jars-servidor`: 19 JARs Aurorion novos e
o C2ME remendado existente. As 19 versoes antigas foram excluidas dessa pasta.
Os JARs foram gerados em staging e conferidos antes da substituicao: integridade
ZIP, identificador e versao no `neoforge.mods.toml`, versao do manifest, bytecode
compativel com Java 21 e SHA-256. O C2ME coincide com o remendo em vendor-backups.
Os hashes da entrega estao em [SHA256SUMS.txt](SHA256SUMS.txt) e tambem na pasta
dos JARs.

O `launch.json` local do VS Code estava corrompido e impedia a configuracao do
Gradle. Foi preservado em `build/release-2026-10-02/launch.corrompido.json`;
o arquivo regenerado foi validado como JSON. O log do build esta em
`build/release-2026-10-02/build.log`.

Esta entrega nao foi instalada no servidor ou no CurseForge e nao foi publicada
no remoto. O build nao comprova a resolucao da divergencia de registro
`create:item_vault` nem do crash de renderizacao do Immersive Furniture.
Ainda precisam ser validados o carregamento com o modpack completo e a conexao
cliente/servidor. GameTests e uma sessao do jogo nao foram executados nesta entrega.
