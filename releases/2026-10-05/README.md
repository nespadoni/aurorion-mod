# Release dos mods — 05/10/2026

Entrega de dois módulos: **Aurorion Magia 0.4.3 → 0.5.0** (25 magias novas e magia entre aliados)
e **Aurorion Essentials 0.7.4 → 0.7.5** (fotos recebidas no telefone). Os outros 17 módulos e o C2ME
remendado não mudaram desde a release de 04/10 e continuam com o **mesmo SHA-256**, conferido
arquivo a arquivo contra `releases/2026-10-04/SHA256SUMS.txt`. As versões estão em
[versions.json](versions.json) e os hashes em [SHA256SUMS.txt](SHA256SUMS.txt) e na pasta dos JARs.

## Magia 0.5.0

Detalhes de cada magia, números e custos no [README do módulo](../../aurorion-magia/README.md)
(seções "Magias de kit" e "Magias proibidas ⛔").

### O que entra

- **25 magias**, cada uma com o nome conhecido em português e a invocação em latim (o id):
  - **ultimates:** Centelha Final `lux_finalis`, Têmpera do Destino `temperies_fati`, Puxão Biônico
    `manus_rapax`, Campo Estático `campus_staticus`, Barril Explosivo `dolium_ardens`, Bomba
    Megainfernal `pyrobolus_infernalis`, Tempestade de Corvos `procella_corvorum` e Colheita Farta
    `messis_uberrima`;
  - **avulsas:** Bigorna Celeste `incus_caelestis` e Capricho `mutatio_ferae` (vira guaxinim do Alex's Mobs);
  - **kit de sangue:** Transfusão `transfusio_sanguinis`, Poça de Sangue `lacus_sanguinis`, Maré de
    Sangue `aestus_sanguinis` e Hemopraga `pestis_sanguinea`;
  - **kit de sombra:** Shuriken Laminado `stellae_laminatae`, Sombra Viva `umbra_viva`, Corte
    Sombrio `sectio_umbrae` e Marca Fatal `signum_mortis`;
  - **kit de arco** (exige arco ou besta na mão): Flecha de Reconhecimento `sagitta_exploratrix`,
    Flecha de Choque `sagitta_fulminis` e Fúria do Caçador `furor_venatoris`;
  - **proibidas** (sem craft, sem loot, só `/aurorion spells unlock spell`): Esfera Espiritual
    `sphaera_spiritus`, Possessão `possessio_corporis`, Justiça Demaciana `iustitia_demaciae`
    (exige espada) e Morte Vinda das Profundezas `mors_ex_profundis`.
- **Duas entidades novas**, as duas `noSave`: `aurorion_magia:projetil_de_magia` (uma só para
  barril, bomba, bigorna, shuriken, esfera, Têmpera e garra, com a forma num byte sincronizado) e
  `aurorion_magia:sombra_viva`. Doze efeitos novos (estase, inalvejável, possuído, guaxinim, marcas...).
- **Três tipos de dano** (`iustitia_demaciae`, `mors_ex_profundis`, `incus_caelestis`), nas tags
  `minecraft:bypasses_*` correspondentes.
- **Config nova** em `config/aurorion/magia-server.toml`:
  - `[aliados] magiasIgnoramTime` (padrão `true`);
  - `[destruicao]`: `esferaQuebraBlocos` (`true`), `limiarJustica` (0.25), `limiarProfundezas`
    (0.20) e `alcanceBomba` (128).

### Magia entre aliados

Com `magiasIgnoramTime` ligado, nenhuma magia poupa o próprio time — nem as da Aurorion, nem as do
Iron's. Antes, além dos filtros de alvo, duas travas recusavam o dano: o Iron's
(`DamageSources.applyDamage` → `isFriendlyFireBetween`) e o vanilla (`ServerPlayer.hurt` →
`canHarmPlayer`, com o fogo amigo do time desligado). Entre aliados, o dano agora sai com quem
conjurou só como entidade direta, sem causador (`FriendlyFire`); as magias do Iron's passam pelo
mesmo caminho via `SpellDamageEvent`. A mensagem de morte continua nomeando quem conjurou, mas matar
um aliado com magia não conta abate. Área sem PvP (servidor ou `aurorion-areas`) continua protegendo.
Desligado, tudo volta a poupar o time como antes.

### Revisão de segurança e desempenho

Antes da entrega, as magias novas passaram por uma revisão de bugs, abuso e custo. O que foi
corrigido:

- execução só mata se o golpe entrou (o `kill()` de reserva não passa mais por área sem PvP,
  estase ou inalvejável);
- puxão, silêncio, guaxinim, possessão e Marca Fatal respeitam a regra `pvp` do servidor e do
  `aurorion-areas` na posição da vítima;
- estase e inalvejável só deixam passar `/kill` e o vazio do mundo;
- a Possessão confere o alvo de novo no fim da concentração e devolve quem possuía na dimensão de
  origem;
- a Esfera e a Maré de Sangue interrompidas não disparam;
- a Marca Fatal não teleporta através de parede;
- guaxinim e possessão aparecem também para quem chega perto depois;
- a bigorna tira 30 de chefe, e não 1000;
- a cratera da Esfera vai até força 6 (era 11) e segue `blockExplosionDropDecay`;
- a mira longa da Bomba não carrega chunk.

### Validação

`buildAll` com Java 21, Minecraft 1.21.1 e NeoForge 21.1.248: aprovado. O log está em
`build/release-2026-10-05/buildAll.log`. São 461 testes no ecossistema: nenhuma falha e 35 ignorados,
como antes. A Magia tem 16 testes, entre eles o contrato de ícone e tradução de cada uma das 49
magias.

O módulo também subiu em servidor dedicado de desenvolvimento (modo gametest, sem mundo, para não
aceitar a EULA): as magias, os efeitos e as entidades registraram sem erro.

**Não foi feita sessão de jogo.** Ficam para validar em jogo, com dois clientes:

- Possessão: andar e falar pelo outro, e o balão do `aurorion-talk`;
- guaxinim;
- Bigorna parada e andando;
- cratera da Esfera perto do spawn e em área protegida;
- magia em membro do mesmo time.

## Essentials 0.7.5

Fotos enviadas pelo Mensagens, pelos DMs do Gram e o avatar e capa do MattuTweet chegam em JPEG, e o
telefone as abria com o leitor só-PNG do Minecraft ("Photo could not be loaded"). O
`PhonePhotoFormatMixin` troca, nesses três lugares, a leitura pela do `PhonePhotoDecoder`, que lê
PNG e JPEG. A galeria local, que só tem PNG, fica como está. A injeção tem `require = 0`.

A diferença para o 0.7.4 é essa e só essa: duas classes novas e o config de mixin do telefone,
conferidos dentro dos dois JARs. A correção já constava no comunicado de 04/10, mas ficou fora da
release de 04/10. Os dados salvos pela 0.7.4 continuam válidos.

## Atenção na instalação

- **Magia 0.5.0 em cliente e servidor.** A versão do protocolo subiu (7 → 8) e há registros novos
  (entidades e efeitos). Cliente com a 0.4.3 não entra. Publicar pela origem do AutoModpack.
- **Essentials 0.7.5 em cliente e servidor.** A correção é do cliente; o servidor recebe o mesmo
  JAR para o pack não divergir.
- Em `build/jars-servidor`, saíram `aurorion_magia-…-0.4.3.jar` e `aurorion_essentials-…-0.7.4.jar`
  (guardados em `build/jars-arquivados/release-2026-10-05/`). Conferir que só uma versão de cada mod
  fica na pasta de mods do servidor.
- **Capricho:** pede o Alex's Mobs para o guaxinim. Sem ele, o alvo vira raposa.
- **Dependências:** a Magia continua exigindo Iron's Spells 3.16.3, irons_lib, Curios e
  PlayerAnimator, todos já no pack.

O comunicado para os jogadores está no [CHANGELOG](../../CHANGELOG.md). Nada foi publicado no remoto.
