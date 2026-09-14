# Morte definitiva e epílogo

Quando as 48 horas de servidor ativo acabam, o personagem é marcado como morto no servidor.
O tempo continua passando com o jogador offline; o tempo com o servidor desligado continua
pausado, conforme a regra anterior do Limbo.

## Sequência padrão

| Momento em relação ao vencimento | Apresentação |
| --- | --- |
| 0 até 25 s | Personagem encerrado, controles bloqueados; música começa junto com o fechamento dos olhos e a câmera sobe 32 blocos localmente |
| 25 até 40 s | Fundo preto e a frase de despedida |
| 40 até 180 s | Epílogo original em rolagem, inspirado na apresentação dos créditos |
| 180 até 240 s | “Você está morto.”; a música perde volume nos primeiros 10 s |
| 240 s | Servidor desconecta o jogador |

A câmera permanece acima do ponto da morte e o fundo continua preto após o fechamento.
A sequência não envia pedido de respawn. O Cinematic Respawn é opcional e fica suspenso durante
esta cena, inclusive se o jogador ainda estiver na animação de uma morte comum.

O texto inicial começa com:

> A vida é um sopro.
> Eu deixo de existir aqui...
> mas houve um instante em que o mundo me chamou pelo nome.

Os parágrafos completos ficam em `FinaleConfig.java` como valores iniciais da configuração.
São texto original, sem copiar o poema ou os créditos do Minecraft.

## Configuração

Arquivo gerado pelo NeoForge: `config/aurorion/limbo-finale-server.toml`.

| Chave | Padrão | Efeito |
| --- | --- | --- |
| `subidaEmSegundos` | 25 | Subida e fechamento dos olhos |
| `fraseEmSegundos` | 15 | Frase central |
| `creditosEmSegundos` | 140 | Duração da rolagem completa |
| `frase` | Texto acima | Texto central, com quebras de linha |
| `paragrafos` | 13 parágrafos | Texto em rolagem; até 128 parágrafos de 512 caracteres |
| `musica` | `aurorion_limbo:finale` | ID do evento de som |

O minuto após “Você está morto.” é fixo. Cada morte salva uma cópia dos tempos e textos;
editar a configuração afeta as próximas sequências, sem cortar o minuto final de uma já iniciada.

A faixa fornecida para desenvolvimento está em
`src/main/resources/assets/aurorion_limbo/sounds/finale.ogg`. É Ogg Vorbis estéreo,
48 kHz, com aproximadamente 132,67 s. Ela é reproduzida em streaming e repetida para cobrir
a cena completa, respeitando o volume de música do jogador. É possível substituí-la com
um resource pack no mesmo caminho, ou trocar o evento em `musica`.

## Conta e personagem

- **UUID da conta:** identidade de autenticação do Minecraft.
- **UUID do personagem:** identidade desta história, criada e persistida pelo Core.
- **Estado do personagem:** vivo ou morto; o registro contém criação e momento da morte.

Os dados ficam em `data/aurorion_core_characters.dat`, no mundo. Não há escrita na banlist.
Morrer não altera o UUID de autenticação e conceder vidas não gera uma nova identidade.

O Limbo guarda epílogos pendentes em `data/aurorion_limbo_finales.dat`, incluindo o ID do
personagem, o texto e o progresso. Enquanto assiste, o jogador fica em espectador, sem movimento,
ações de inventário, comandos ou viagem entre dimensões. A câmera que sobe existe somente no cliente.

Se o prazo vence offline, o personagem morre mesmo assim e assiste ao epílogo no próximo login.
Se desconectar no meio, a próxima conexão retoma o progresso salvo. Ao terminar,
o registro do epílogo é removido, e novas conexões são recusadas no login com a explicação de que
a conta precisa de outro personagem. Não é possível reiniciar o prazo ao reconectar.

O cliente sem o canal cinematográfico recebe o texto por mensagem; as mesmas restrições e
a desconexão continuam sendo decididas pelo servidor. A apresentação completa exige o mod no cliente.

Registros antigos do Limbo cujo prazo já estava vencido também passam a representar morte
definitiva na primeira varredura ou no login. Ajustes de prazo e resgates são aceitos somente
antes do vencimento. `/limbo prazo <jogador> 0` encerra o personagem imediatamente.

## Próxima etapa: criação de personagem

O fluxo de criação de outro personagem **ainda não está implementado**. Ele deverá:

1. Conferir que a conta está sem personagem vivo.
2. Arquivar a identidade encerrada e preparar um novo UUID de personagem.
3. Limpar inventários normal e Ender, XP, efeitos, spawn, avanços e estatísticas, além de
   vidas, casa, nome de RP, permissões de viagem, histórico de personagem e progressão dos mods do pack.
4. Instalar o estado inicial e liberar o novo personagem somente depois de todo o reset terminar.

A conta e as permissões de moderação continuam vinculadas ao UUID de autenticação.
Progressão de personagem deve ser vinculada ao ID de personagem ou participar do reset.
A versão atual não oferece um comando que apenas remova a marca de morto: isso permitiria
voltar com o inventário, a casa e a progressão da história anterior.

## Verificação posterior

A prévia para staff é `/limbo finale previa`: música e fechamento dos olhos começam juntos,
sem marcar morte nem desconectar. Durante a cena, Esc encerra a prévia.

Para validar a regra definitiva, use um personagem e mundo descartáveis. Pontos a conferir:

- resgate antes do prazo permite continuar e não inicia música nem epílogo;
- vencer online e offline encerra o personagem;
- reconectar durante a rolagem retoma o progresso;
- mudar a configuração durante uma cena não corta sua duração;
- “Você está morto.” permanece por um minuto antes da desconexão;
- conceder vidas, alterar gamemode ou tentar resgate após a morte não libera o personagem;
- tela, câmera e áudio com o modpack completo, shaders e diferentes escalas de GUI.

Os testes JUnit ficam em `aurorion-core/src/test/.../character` e
`aurorion-limbo/src/test/.../finale` e `.../network`. O GameTest existente foi atualizado
para a regra terminal e para manter a cobertura de resgate antes do prazo.

Nesta implementação foi feita verificação de sintaxe Java e estrutura dos recursos. A compilação
Gradle ficou bloqueada no download de `com.mojang:logging:1.2.7`; por isso JUnit e GameTest
não foram executados nesta sessão. Nenhum cliente ou servidor de gameplay foi iniciado.

Referência de API: [payloads do NeoForge 1.21.1](https://docs.neoforged.net/docs/1.21.1/networking/).
