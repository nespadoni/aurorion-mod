# Release dos mods — 08/10/2026

Build de todos os 19 módulos Aurorion, com Java 21, Minecraft 1.21.1 e NeoForge 21.1.248.
Os 19 jars instaláveis e o C2ME remendado ficam em `build/jars-servidor`, com uma versão
por mod. Versões completas em [versions.json](versions.json), hashes em
[SHA256SUMS.txt](SHA256SUMS.txt) e validação dos artefatos em [verification.json](verification.json).

## Versões e conteúdo

- **Magia 0.6.0:** inclui as correções de Umbra Viva/Dolium, dano e duração dobrados,
  controles persistentes, Ceifar Vida, Entregar Vida, Esconjurar e desbloqueio de todas
  as escolas. Acrescenta Reino das Almas, Vazio Infinito, Santuário Malevolente,
  duas passivas e efeitos ampliados de Lux/Genki Dama.
- **Profissões 0.5.1:** tratamento de Body Damage limpa os marcadores críticos e
  corrige NBT inválido, evitando recaída máxima após um membro ser curado.
- Os demais módulos foram incluídos com suas versões correntes, sem incrementos artificiais.
  Essentials está em 0.7.6 e Limbo em 0.4.6; o manifesto lista os 19 módulos.

Detalhes e configurações: [correções de Body Damage e magia](../../docs/correcoes-magia-bodydamage-2026-10-08.md)
e [domínios, passivas e ultimates](../../docs/dominios-almas-ultimates-2026-10-08.md).

## Validação

- `buildAll`: aprovado. Foram corrigidos o import ausente de `MagiaEffects` em Olhar
  Cativo e o teste antigo que considerava silêncio negativo expirado. Há cobertura
  para duração ilimitada com liberação explícita.
- Relatórios unitários do ecossistema: **462 testes**, **zero falhas/erros** e
  **35 ignorados**. Gradle reutilizou tarefas e resultados dos módulos sem mudanças.
- Profissões com **LSO 2.4.7.2 real**: **12 GameTests aprovados**, incluindo o membro
  tratado que não volta ao dano máximo depois de receber outro golpe.
- Smoke da Magia em servidor dedicado: registro das três magias e três efeitos,
  dimensão decodificada pelo `LevelStem.CODEC` com os registros reais,
  `dimension_type` e tipo de dano do Santuário. O GameTest usa os mundos vanilla
  de teste, portanto não valida o duelo ou o retorno entre dimensões.
- Jars conferidos por CRC, versão interna/manifesto e bytecode compatível com Java 21.
  C2ME comparado com o backup remendado; SHA-256 calculado para os 20 jars.

Logs e scripts locais em `build/release-2026-10-08/`: `buildAll-aprovado.log`,
`profissoes-gametest.log`, `magia-smoke-aprovado.log` e `verify_release.py`.
O ensaio de registros é separado dos testes com jogadores sintéticos: o Iron's
exige negociação de payloads que os jogadores artificiais de Profissões não realizam.

## Instalação e ensaio com clientes

Magia **0.6.0 deve estar no servidor e em todos os clientes**: o protocolo é 9 e há
registros novos. Atualizar Profissões para 0.5.1 onde instalado. Substituir versões
anteriores, mantendo apenas um jar por mod.

Os jars antigos de Magia/Profissões foram arquivados em
`build/jars-arquivados/release-2026-10-08-substituidos/`; antes do build também foi feita
uma cópia completa da saída anterior em `build/jars-arquivados/release-2026-10-08-202752/`.
Nenhum jar foi instalado no CurseForge, no AutoModpack ou no servidor de produção.

Ainda faltam os ensaios com dois clientes: duelo/retorno/morte/relog com Vidas/Limbo,
Umbra e Dolium em grimório, controles/voz, corte do Santuário apenas em entidades,
efeitos de Lux/Genki e FPS/TPS com Sodium/Iris. O checklist está na documentação dos domínios.
