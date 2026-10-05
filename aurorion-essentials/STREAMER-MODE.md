# Modo streamer

O modo streamer é uma preferência local do Aurorion Essentials. A pessoa continua com OP e
pode usar comandos enquanto os novos avisos administrativos ficam ocultos no chat dela.
A preferência fica em `config/aurorion/essentials-streamer-client.toml` e é mantida ao reiniciar
o cliente. O padrão é desligado. É preciso instalar a versão atualizada também no cliente.

| Comando | Comportamento |
|---|---|
| `/streamer on` ou `/streamer ativar` | Oculta feedback de comandos do Minecraft/Aurorion que usa chaves `commands.*`, teleporte, mudança de modo de jogo, morte, entrada/saída, conquistas e avisos de staff identificados. |
| `/streamer total` | Oculta também os demais avisos recebidos como mensagens de sistema. Use quando algum mod manda logs como texto simples. Falas de NPC/RP de alguns mods também podem ser ocultas. |
| `/streamer off` ou `/streamer desativar` | Desliga o filtro. |
| `/streamer` ou `/streamer status` | Mostra a preferência atual. |

O modo `on` conserva mensagens literais de NPC/RP e notificações de outros mods que não têm
marcador administrativo conhecido. O modo `total` conserva chat assinado e os envelopes de fala
do Minecraft (`chat.type.text`, `/say`, `/me`, chat de time e sussurros), incluindo o caminho de
fala do Aurorion Talk/No Chat Reports. Texto simples recebido como mensagem de sistema não tem
um indicador confiável de quem falou; nesse caso o modo `total` oculta a mensagem.

Avisos privados `[admin]` do histórico de mortes, `[Áreas]` do Aurorion Areas, `/ajuda` e o alerta
de staff do Protetor Arcano ficam ocultos no modo `on`, inclusive os dados de conta e localização.
O feedback de `/streamer` é local e continua aparecendo em ambos os modos.

Ative antes da live e use **F3+D** para limpar mensagens antigas que já estavam no histórico do
chat. O filtro age nas mensagens que chegam depois da ativação. A barra de ação, a tela de morte,
toasts e as permissões seguem o comportamento normal. As mensagens dos demais jogadores e o
registro do servidor não são alterados; desligar o filtro não recupera mensagens ocultadas.

## Validação em outra máquina

As regras do repositório proíbem compilar ou executar Minecraft/Gradle nesta máquina de edição.
Foram escritos `StreamerChatFilterTest` (regras, envelopes de fala, RP literal, aviso de morte
de staff, actionbar e desligamento) e `StreamerMixinTargetTest` (assinatura exata de
`ChatListener#handleSystemMessage`). A execução permanece pendente na máquina de build.

No pack completo, validar:

1. `on`, `/tp`, `/gamemode creative`, `/gamemode survival`, comando inválido e morte de outro jogador
   não mostram feedback administrativo, mesmo com `sendCommandFeedback` e `logAdminCommands`
   ligados. Outro OP sem o modo streamer recebe as mensagens como antes.
2. Falas de jogadores, `/me`, sussurros autorizados e balões do Talk continuam funcionando em
   `on` e `total`; texto RP/NPC de outros mods permanece em `on`.
3. `total` oculta uma notificação literal de outro mod; `/streamer status` e `off` continuam visíveis.
4. Relogar e reiniciar o cliente conserva o modo selecionado. Trocar de modo não altera OP,
   gamerules nem logs do servidor. A configuração é da instalação do cliente, não da conta.
5. Servidor dedicado inicia sem carregar classes de cliente; o mixin deve ficar apenas no array
   `client` de `aurorion_essentials.mixins.json`.
