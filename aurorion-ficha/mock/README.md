# Mock local do site (aurorion-ficha)

Um site Aurorion falso, em memória, para testar a ficha no jogo (e o `/vincular`, o diário e o envio
de eventos da integração) num mundo singleplayer ou num servidor local, sem o backend real.
Fica fora de `src/`: não entra no jar.

- `mock_site.py`: Python 3.12, só biblioteca padrão. Responde às rotas do jogo com as regras, os
  códigos, os status HTTP e o JSON de `internal/rpg/game.go` / `game_handler.go`.
- `iniciar-mock.bat`: com duplo clique, sobe o mock em `http://127.0.0.1:8787` e abre o painel.
- `mock_state.json`: estado salvo (criado sozinho; **Resetar tudo** no painel apaga). Use
  `--sem-salvar` para não ler nem gravar esse arquivo.

## Subir o mock

Duplo clique em `iniciar-mock.bat`, ou no Git Bash:

```bash
cd ~/Documents/TI/Mods/aurorion-mod/aurorion-ficha/mock && python -X utf8 mock_site.py --abrir
```

O painel abre em <http://127.0.0.1:8787/>. Opções: `--porta 8787`, `--sem-salvar`, `--estado arquivo.json`.
Ctrl+C para parar.

## Configurar o mod

Nos runs de desenvolvimento já está pronto: `aurorion-runs/run/client/config/aurorion/integracao-startup.toml`
e `aurorion-runs/run/server/config/aurorion/integracao-startup.toml` apontam para o mock, com o
token `"mock-local"`. No servidor de dev, o `Dev1` é OP; como toda conta do mock já nasce staff, ele
é mestre assim que vincular.

Fora dos runs (outra instância ou outro servidor), edite `config/aurorion/integracao-startup.toml`:

```toml
[integracao]
	habilitado = true
	url = "http://127.0.0.1:8787/api/v1/integration/v1"
	token = "mock-local"
```

O token pode ser qualquer valor não vazio. O painel deixa exigir um valor exato, para testar token
errado. Essa config é STARTUP: reinicie o jogo/servidor depois de mudar.

Singleplayer: crie o mundo com **cheats ligados**. OP (permissão ≥ 2) mais staff no site = mestre.
Tire o cargo no painel para testar um jogador comum.
Para jogar de alt, use `/personagem alt` (e `/personagem trocar` para voltar).

A tecla da ficha é **'** (acento grave, a tecla ao lado do 1), ou use `/ficha`.

## Roteiro de teste

1. **Conta não vinculada.** Com o mock no ar, entre no mundo e aperte ' (ou `/ficha`). A ficha vem
   com o passo a passo de vínculo (`not_linked`). Digite `/vincular ABCD-EFGH`: por padrão vale
   qualquer código com 6+ letras/números. Com **só o código fixo** no painel, só vale o código que
   aparece no topo (`AURO-2026`). Seis tentativas erradas seguidas dão `too_many_attempts`. O
   primeiro vínculo vai para "Você (mock)"; os seguintes (outro jogador num servidor local) ganham
   uma conta nova, com fichas próprias.
2. **Ficha completa e testes.** Aperte ' de novo: aparece Kaelen Rubraluz (Martírio, 22 perícias,
   técnicas, lore). Role perícias com e sem CD e com "secreto". O painel mostra cada rolagem
   (dados, natural, total, CD, crítico no 20, falha crítica no 1). No painel, abra **Recursos e
   condições** do perfil, marque *Medo* e salve: o próximo teste sai com desvantagem e a tela
   aberta recebe "a ficha mudou no site" (a `version` muda).
3. **Ficha antiga.** Em *Contas do site*, escolha **Antiga**: sem construção e sem aviso de
   aprovação (`has_build=false`, `pending_review=false`). Saia e entre de novo: 5–8 s depois do
   login a conta principal chama `/rpg/sheet` e o mod avisa que a ficha é antiga (a chamada aparece
   no log do painel). Testes de perícia/atributo voltam `no_build`; a rolagem livre
   (`/ficha dados 2d6`) funciona.
4. **Aprovação chegando com a tela aberta.** Escolha **Aguardando aprovação**: a aba Vínculo avisa
   "aguardando aprovação no site", e no login não aparece o aviso de ficha antiga. Com a ficha
   aberta no jogo, clique em **Aprovar construção agora**: ao atualizar, a ficha passa a ter
   atributos e perícias.
5. **Alt sem ficha → NPC.** `/personagem alt`: no login o mod avisa que o personagem não tem ficha
   (`needs_binding`). Abra a ficha na aba Vínculo e escolha *Bram Ferrolho* (NPC seu) ou um NPC
   livre (marcado como só staff: vincular liga o NPC a você). A rolagem do NPC volta `private`: quem
   está perto não vê o bônus. *Sombra de Ilmar* não tem construção (`no_build`). No painel,
   **alt → outra conta** testa `foreign`. Liberar o NPC em *NPCs* deixa o vínculo `stale`.
6. **Sem ficha.** **Sem ficha** na conta principal dá `no_sheet` com a lista de candidatos.
7. **Mesa do mestre.** Na conta principal (OP), abra a mesa (`/ficha mestre` ou a tecla que você
   configurou) e peça aos jogadores próximos:
   - um teste com CD escondida e vantagem;
   - dados (ex.: `2d6+3` com motivo e CD). O pedido de dados chega ao site como rolagem livre
     (`kind: "free"`, sem vantagem nem mestre). O site devolve dados e total sem CD, e o mod compara
     `total ≥ CD` sozinho.

   Responda no alt ou no outro cliente. Use "ver ficha" (`/peek`). **Tirar staff** faz o site
   recusar o mestre (`gm_required`). Vantagem sem mestre também volta `gm_required`.
8. **Site fora, lento, busy.** *Disponível*: **503** (o site responde `unavailable`), **Sem
   resposta** (a conexão cai: falha de transporte) e **Desativada** (`integration_disabled`).
   *Latência* de 4 s mostra os estados de espera; acima de 12 s o mod desiste. **Forçar busy na
   próxima** devolve `busy` na próxima rolagem (o pedido do mestre volta a "aguardando").
   Repetir a mesma rolagem (mesmo `command_id`) devolve `replayed`, com o mesmo resultado.

O log do painel mostra cada chamada (rota, HTTP, código, ms, perfil) e cada rolagem. O diário
(`/diario`) e os eventos (`/events/batch`) também são aceitos, com um diário mínimo em memória.

## O que o mock não reproduz

- Encontros e mesas do site: nada de `character_in_other_encounter` (o `busy` vem do botão ou do
  limite de 240 rolagens/min). Bônus de efeitos de encontro e o 1d6 Esotérico não entram.
- O código do `/vincular` não é gerado no site: qualquer código com 6+ caracteres (ou o fixo) vale.
  No backend o código tem exatamente 8 caracteres e expira em 10 min.
- Fichas e NPCs são os de exemplo; o orçamento da construção não é validado. O diário é só um stub
  coerente (sem revisões, sem limites).
