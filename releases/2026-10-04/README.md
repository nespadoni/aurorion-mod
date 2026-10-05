# Release dos mods — 04/10/2026

Entrega de dois módulos: **Aurorion Essentials 0.7.2 → 0.7.4** (telefone MikasRevs, fotos da
galeria e modo streamer) e **Aurorion Áreas 0.6.2 → 0.6.3** (correção do crash do servidor de 04/10 às 17:37).
Os outros 17 módulos não mudaram desde a release de 03/10 e continuam nas mesmas versões, com o
**mesmo SHA-256** — conferido arquivo a arquivo contra `releases/2026-10-03/SHA256SUMS.txt`. As
versões estão em [versions.json](versions.json).

## Áreas 0.6.3 — crash “Exception ticking world”

O servidor caiu com `IllegalArgumentException: Cannot get property age … cropcritters:liverwort`
em `SunFernBlock.randomTick`. Causa, conferida no bytecode dos dois mods: a Sun Fern do **Legendary
Survival Overhaul 2.4.7.2** chama o crescimento vanilla (`CropBlock.randomTick`) e em seguida relê
o bloco e pede a idade dele, sem conferir se ainda é a samambaia. O **Crop Critters 1.5.0** injeta
nesse crescimento e pode trocar a planta por erva daninha (ou degradar o solo até ela quebrar); a
leitura da idade num bloco sem `age` derruba o tick do mundo. A Ice Fern tem o mesmo código.

O `FernTickGuardMixin` (no config `aurorion_areas.lso.mixins.json`, só carregado com o LSO
presente) interrompe o `randomTick` das duas samambaias logo após o crescimento vanilla quando o
bloco já não é mais a samambaia — o que sobra do método é só a chance de virar samambaia dourada.
O Crop Critters continua funcionando como antes. A injeção tem `require = 0`: se uma versão futura
do LSO mudar o método, o servidor sobe sem a trava em vez de não subir, e o
`LsoFernTickContractTest` acusa a mudança no build. Precisa estar **no servidor** (o crash é do lado
servidor); no cliente é inofensivo.

Build com `:aurorion-areas:build` e `AURORION_LSO_JAR` apontando para
`mod-servidor-referencia/legendarysurvivaloverhaul-1.21.1-2.4.7.2.jar` (mesmo SHA-256 do instalado na
instância): 49 testes, todos passaram, nenhum ignorado. Log em
`build/release-2026-10-04/areas-build.log`. O 0.6.2 foi guardado em
`build/jars-arquivados/release-2026-10-04/`.

## Essentials 0.7.4

A 0.7.3 foi gerada mais cedo no mesmo dia e já rodou no cliente de testes; a 0.7.4 a substitui (a
0.7.3 está em `build/jars-arquivados/release-2026-10-04/`). Os dados salvos pela 0.7.3 continuam
válidos.

Build com `:aurorion-essentials:build`, Java 21 (Temurin 21.0.12), Minecraft 1.21.1 e NeoForge
21.1.248, com `AURORION_PHONE_JAR` apontando para `mod-servidor-referencia/mattupolisphone112.jar`
(SHA-256 `2657b87e…16827e`, o mesmo instalado na instância): aprovado. Testes do Essentials: 157,
todos passaram, nenhum ignorado — inclusive os contratos de mixin contra o bytecode real do telefone.
O log do build final (limpo, com os testes rodados de novo) está em
`build/release-2026-10-04/final-build.log`.

Em `build/jars-servidor`: o `aurorion_essentials-neoforge-1.21.1-0.7.2.jar` saiu (guardado em
`build/jars-arquivados/release-2026-10-04/`) e o 0.7.4 entrou no lugar; o resto da pasta ficou
igual (fora o Áreas, acima). O JAR novo foi conferido: id e versão no `neoforge.mods.toml`, versão do manifest, bytecode
Java 21, dependência do Core `[0.6.1,)` e todas as classes citadas nos três configs de mixin. Os
hashes estão em [SHA256SUMS.txt](SHA256SUMS.txt) e na pasta dos JARs.

### O que muda

Detalhes técnicos em [docs/TELEFONE-STREAMER-2026-10-04.md](../../docs/TELEFONE-STREAMER-2026-10-04.md).

- **Contatos e conversas salvos** por personagem: histórico do app Mensagens e dos DMs do Gram,
  com fotos e estado de leitura, sobrevivem a relogar, reiniciar o jogo e trocar de personagem.
  Ficam no computador de quem joga, em `mattupolis_phone/personagens/<UUID>/`.
- **Fakename no Gram e no MattuTweet**: autores de posts, tweets, citações e comentários mostram
  sempre o fakename, inclusive de jogadores offline. Ações (perfil, seguir, excluir, DM) continuam
  usando a conta real por baixo.
- **Fotos da galeria**: no Mensagens, os botões “Phot”/“Loc” viraram ícones (câmera e
  localização) e entrou um **clipe** que abre a galeria para enviar a foto. No Gram, o “+” de novo
  post pergunta **Câmera ou Galeria**. Stories, foto de perfil, MattuTweet (avatar/banner) e
  Marketplace já tinham galeria no telefone original. O MattuTweet não tem tweet com imagem: o
  pacote de postagem do telefone leva só texto, então isso exigiria um sistema novo de imagens.
- **Importar imagem do PC**: botão no topo da galeria (abre a janela de escolher arquivo do Windows)
  ou arrastar PNG/JPG para a janela do jogo com a galeria aberta. `/celular importar <caminho>`
  continua funcionando. Até 10 MB / 16 MP, reduzido para no máximo 2048 px.
- **Modo streamer** (por cliente, sem depender de OP): `/streamer on` esconde avisos de comando,
  teleporte, troca de modo de jogo, morte, entrada/saída e conquistas; `/streamer total` esconde
  também os demais avisos de sistema; `/streamer off` desliga. A escolha fica salva no cliente.

## Atenção na instalação

- **Áreas 0.6.3** (o que impede o crash de se repetir) já está no servidor. Não muda protocolo.

- **Cliente e servidor** devem receber o 0.7.4: o fakename de autores offline vem do servidor;
  histórico, importação e streamer rodam no cliente.
- O Essentials exige **Core 0.6.1 ou posterior**; o pack do servidor já tem o Core 0.6.4. Publicar
  pela origem do AutoModpack para a troca não ser revertida na próxima sincronização.
- Conferir que só uma versão de cada mod fica na pasta de mods. O pack do servidor
  (`automodpack-content.json` de `198.1.195.142-25588`) lista duas versões de 15 módulos: aeonita
  0.3.0, areas 0.6.0, core 0.5.0, economia 0.3.0, essentials 0.5.0, ethereal 0.3.0, limbo 0.4.2,
  magia 0.4.0, mundos 0.1.0, personagem 0.2.0, portais 0.1.0, profissoes 0.4.1, talk 0.1.0,
  utils 0.2.0 e vidas 0.2.1, ao lado das atuais. O NeoForge escolhe a mais nova, mas as antigas
  devem sair da pasta do servidor.

Situação no servidor (conferida pelo pack sincronizado): o Áreas 0.6.3 já está instalado, com o
mesmo SHA-256 deste build, e o Essentials instalado é o 0.7.3. Para atualizar, basta trocar o
Essentials 0.7.3 pelo 0.7.4. Também entraram no pack Waystones 21.1.46, Kaleidoscope Tavern 1.2.0,
Productive Bees 13.14.0 (com o ProductiveLib embutido) e Mystical Agriculture 8.0.28; Balm e
Cucumber, que eles exigem, já estão no pack. O comunicado para os jogadores está no
[CHANGELOG](../../CHANGELOG.md). Nada foi publicado no remoto.
Não foram executados GameTests nem uma sessão com o modpack completo; o roteiro de validação no jogo
está na seção “Validação em jogo” do documento técnico.
