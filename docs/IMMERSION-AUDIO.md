# Imersão sonora do Limbo e das magias

Três efeitos selecionados no Epidemic Sound em 23/09/2026: ar suave (`Soft Airy`), voz
invertida (`Ghostly Voice Reversed 02`) e sino de vento (`Koshi Terra Chime ... 01`).
IDs e processamento em [audio-sources.json](audio-sources.json). Seleção pelos metadados;
avaliação auditiva final pendente em jogo.

Downloads WAV autorizados pelo MCP, não previews. Conversão para Vorbis mono 44,1 kHz,
filtro 90–6500 Hz, alvo -24 LUFS e fades; sino reduzido a 7 s. Assets e subtítulos em cada mod,
sem dependência entre Limbo e Magia. São assets de terceiros do Epidemic Sound; a licença
do código do repositório não os relicencia.

| Momento | Feedback | Limite |
|---|---|---|
| Chegada, travessia e saída do Limbo | Ar suave | Uma vez, só o dono |
| Porta revelada, resgate e vínculo | Sino discreto | Uma camada adicional por vez |
| Janela de saída solitária | Sussurro baixo | Debounce de repetição |
| Aproximação da Porta | Sino a menos de 10 blocos | Rearma fora de 20 blocos |
| Assombração já sorteada | Sussurro em 1/3 dos casos sonoros | Raridade anterior preservada |
| Imperium/Aspectus | Voz invertida ocasional | Intervalos de 16–28 s; fade ao terminar |
| Fim dos efeitos de controle/dor | Sopro de liberação | Uma transição, sem pacote novo |
| Cruciatus/Aspectus simultâneos | Batimento prioritário | Sem duas pulsações sobrepostas |

Trilha do bioma, encerramento e biblioteca ambiental anterior continuam registrados. Sons
vanilla das magias permanecem com volume/espaçamento ajustados para evitar caudas empilhadas.
Assombrações e ruídos psicológicos vão apenas ao jogador afetado. Nenhum tick novo de servidor:
Limbo reutiliza avisos/varredura; magias leem efeitos já sincronizados. Sons novos param ao
desconectar/trocar de mundo, e a cena de encerramento interrompe a camada adicional do Limbo.

Volume nos controles vanilla: Ambiente para Limbo, Jogadores para magias; os antigos ruídos
de monstros mantêm Hostil. Não muda dano, duração, resgate ou vidas.

## Conexão e reprodução

MCP global do Codex: `epidemic-sound`, URL
`https://www.epidemicsound.com/a/mcp-service/mcp`,
`bearer_token_env_var = "EPIDEMIC_SOUND_API_KEY"`. Chave na variável de ambiente do usuário
Windows, fora deste repo. Reabrir o Codex permite ao novo processo herdar a variável.
Conexão JSON-RPC, consulta de ferramentas, buscas e downloads foram realizados nesta sessão.

`tools/epidemic-mcp.ps1` lê a variável do processo/usuário e não imprime o cabeçalho.
`tools/convert-epidemic-audio.py` reproduz a conversão de `build/epidemic/{air,whisper,chime}.wav`
usando `imageio-ffmpeg` em `build/audio-tools`. WAVs e URLs temporárias ficam em `build/`,
ignorado pelo Git. O jogo não acessa Epidemic nem precisa da chave.

Referência: [MCP oficial do Epidemic](https://developers.epidemicsound.com/docs/mcp/).

## Validar na outra máquina

Compilar Limbo/Magia e iniciar servidor dedicado: registros, subtítulos e isolamento de classes
de cliente. Ouvir cues com fones e ajustar níveis relativos a música/voz. Com dois jogadores,
só o afetado deve ouvir a assombração. Conferir porta, resgate, reconexão, dimensão, pausa,
reload de resources e encerramento. Empilhar/remover magias, morrer e desconectar: sem
pulsação duplicada, sussurro persistente ou pacotes por tick.

Não houve compilação, testes automatizados ou execução de Minecraft nesta máquina.
