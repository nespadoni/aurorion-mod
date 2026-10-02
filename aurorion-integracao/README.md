# Aurorion Integração

Leva ao site, no instante em que acontecem, os marcos do mundo. Eles aparecem na **Crônica da
temporada** da linha do tempo (`/wiki/linha-do-tempo`).

| Fato | De onde sai | No site |
|---|---|---|
| Morte confirmada | este mod (`death/DeathFacts`) | público: personagem, causa, dimensão, vidas restantes |
| Queda no Limbo, saída (resgate, porta, staff) | `aurorion-limbo`, `AuditLog.record` | público |
| Fim definitivo | `aurorion-limbo`, prazo vencido | público, em destaque |
| Portal abriu/fechou | `aurorion-portais`, `TransitAnnouncer` | público (só com `enforce` ligado) |
| Troca de casa | `aurorion-ethereal`, `HouseManager.bind` | **só a equipe**, até alguém publicar |

Só o servidor precisa deste jar. Sem ele, os outros mods continuam iguais: o `GameFacts` do core vira
no-op.

## Configurar

`config/aurorion/integracao-startup.toml` (criado no primeiro boot):

```toml
[integracao]
habilitado = true
url = "https://aurorionstudios.cloud/api/v1/integration/v1"
token = "<o mesmo valor de GAME_API_TOKEN no backend>"
spoolMaxMiB = 8
```

- O token **não é** o do bot. No backend, defina `GAME_API_TOKEN` (32+ caracteres) e reinicie.
- Mudar o arquivo exige reiniciar o servidor do jogo. É `STARTUP` de propósito: config `SERVER` é
  enviada a todo cliente que conecta, e o token iria junto.
- `url` é a **base** da integração; os caminhos (`/events/batch`, `/link/claim`, `/diary/...`) são
  montados a partir dela. O endereço antigo terminado em `/events/batch` continua aceito.
- Na mesma rede Docker do backend, `http://backend:8080/...` também serve. HTTP para um host externo
  gera aviso no log, porque o token trafegaria aberto.

## `/vincular`

Liga a conta do site (Discord) ao perfil Minecraft. O código nasce no site (Perfil → Diário →
"Vincular minha conta Minecraft"), vale 10 minutos e é de uso único; o backend limita tentativas por
perfil. O comando só repassa código + perfil da sessão, de forma assíncrona. O `aurorion-diario`
usa o mesmo cliente do site (`FactBridge.site()`), sem repetir credencial.

## Como sai sem pesar

`/aurorion diagnostico` (Essentials, OP nível 2/console) mostra filas, prazo de reenvio, estados de
disco e chamadas HTTP. Respostas do site são limitadas a 256 KiB durante a leitura. Confira
[os limites e as validações](../docs/OTIMIZACOES-OPERACIONAIS-2026-10-02.md).

- Nada roda por tick. A morte é capturada no próprio evento e confirmada por **uma** tarefa agendada
  logo após o despacho (para respeitar totem e PlayerRevive). Os outros fatos saem do funil que cada
  mod já tinha.
- A thread do servidor só enfileira. Disco e rede ficam na thread `aurorion-integracao`.
- Cada fato é gravado em `<mundo>/aurorion_integracao/pendentes.jsonl` antes do envio e só sai de lá
  quando o site responde. Site fora do ar ou servidor reiniciado: o fato espera e é reenviado. O site
  reconhece reenvios pelo `event_id` e não duplica nada.

## O que validar ao compilar (outra máquina)

- `./gradlew :aurorion-integracao:test :aurorion-limbo:test` — `OutboxTest`, `IngestClientTest`,
  `LimboFactsTest`.
- Servidor dedicado com core, vidas, limbo, portais, ethereal e integração. Conferir:
  - morte comum, morte por outro jogador, morte com totem (não deve sair) e com PlayerRevive;
  - queda no Limbo e resgate; abertura e fechamento de uma linha de portal;
  - `/casa` atribuindo casa: a entrada chega ao site como "só equipe";
  - backend desligado durante as mortes, depois ligado: os fatos chegam uma vez só.
- Cliente conectando **não** recebe `integracao-startup.toml` (só configs `SERVER` são sincronizadas).
