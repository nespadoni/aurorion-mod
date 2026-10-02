# Proteções para crashes de cliente — 01/10/2026

Os dois relatórios fornecidos são crashes do cliente conectado ao servidor.

- `crash-2026-10-01_18.52.43-client.txt`: Simply More `1.3.0_alpha`,
  `MimicryItem.use`, linha 65. O JAR instalado faz cast direto de `Level` para `ServerLevel`.
  `MimicryClientUseMixin` trata a previsão de uso no cliente sem executar esse cast. No servidor,
  o método original continua conferindo a habilidade e iniciando seu uso. O alvo é opcional:
  sem Simply More, o mixin `@Pseudo` não exige a classe.
- `crash-2026-09-30_22.06.04-client.txt`: `DynamicTexture.lambda$new$0` acessa `pixels`
  nulo na fila de renderização. `DisposedDynamicTextureMixin` ignora a inicialização de uma
  textura já descartada e agenda `close()` na thread de renderização quando chamado de outra
  thread. O relatório não identifica o mod que descartou a imagem; Moonlight aparecer como mixin
  nessa classe não prova que seja o causador. Esta é uma proteção do ciclo de vida da textura.

## Instalação da versão 0.2.2

Atualize `aurorion_utils-neoforge-1.21.1-0.2.2.jar` no servidor/pack e nos clientes via AutoModpack,
substituindo o JAR antigo. A correção precisa carregar no cliente; trocar somente o servidor
não corrige esses crashes. Nenhum JAR instalado foi alterado nesta tarefa.

## Validação pendente

- Conferir a aplicação dos dois mixins no cliente Minecraft 1.21.1.
- Abrir cliente com o pack completo, inclusive Moonlight, e repetir uso/cancelamento da Mimicry,
  com habilidade liberada e bloqueada; confirmar que o servidor valida e aplica os efeitos.
- Repetir conexões/desconexões e carregamento/descarte de texturas; conferir também aparência
  de skins e interfaces. O crash de textura não contém passos de reprodução.
- Iniciar servidor dedicado e cliente sem Simply More para validar o alvo opcional.
- Ao atualizar Simply More ou Minecraft, reconferir os métodos alvo dos mixins.

Build executado e aprovado nesta máquina em 01/10/2026, com autorização explícita
do usuário. O servidor dedicado de validação carregou Utils 0.2.2 e concluiu os
7 GameTests da Trama sem Simply More instalado, confirmando que o alvo ausente
não impede a inicialização. A aplicação dos mixins no cliente e a reprodução
dos crashes com o modpack completo continuam pendentes.
