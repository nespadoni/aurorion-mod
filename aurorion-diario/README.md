# Aurorion Diário

`/diario` abre o diário do personagem dentro do jogo — o **mesmo** diário do site
(Perfil → Diário). Escreva aqui ou lá; tudo fica privado até você publicar.

## Para o jogador

1. No site, Perfil → Diário → **Vincular minha conta Minecraft**. Aparece um código.
2. No jogo: `/vincular CODIGO`. Pronto, a conta está ligada.
3. `/diario` abre a lista de entradas do seu personagem atual e o editor.

A tela escreve com uma marcação curta (a barra de ferramentas insere por você):

| Você escreve | Vira |
|---|---|
| `## Título` / `### Subtítulo` | título |
| `> texto` | citação |
| `- item` | lista |
| `---` | separador |
| `**negrito**` / `*itálico*` | negrito / itálico |

Imagens se colocam pelo site. No jogo elas aparecem como `[[imagem:12|legenda]]` e são
preservadas quando você salva — não apague a linha se quiser manter a imagem.

O texto salva sozinho 2 s depois que você para de digitar. **Publicar** envia a versão exata que
está na tela; o resto continua privado. Se o site e o jogo editarem a mesma entrada ao mesmo
tempo, a tela mostra o conflito e você escolhe qual texto fica (o outro continua no histórico do
site).

## Como funciona

- **Servidor** (`server/DiaryServer`): fala com o site pelo `SiteApi` do `aurorion-integracao`,
  com a credencial do servidor; o perfil e o personagem vêm da sessão do jogador, nunca da tela.
  Nada roda por tick: cada ação é uma chamada assíncrona, e a resposta volta para a thread do
  servidor antes de tocar no jogador.
- **Site fora do ar**: a gravação fica em `<mundo>/aurorion_diario/pendentes.json`
  (`PendingStore`) e uma rotina numa thread própria reenvia a cada 30 s. A tela só mostra "guardado
  no servidor" depois que o arquivo foi gravado. Reabrir a entrada continua a mesma sessão de edição
  (a cópia não é consumida ao abrir) e adianta o reenvio. Se o site recusar a cópia por conflito, ela
  vira um ponto "conflito" no histórico do site e, no jogo, a tela mostra as duas versões. Publicar
  espera a sincronização.
- **Cliente** (`client/DiaryScreen`): visual em HTML/CSS do **TesseraUI**
  (`assets/aurorion_diario/ui/diario.css`) e o campo de texto nativo do Minecraft para escrever.
  As classes de cliente só carregam depois do teste de `Dist`.
- **Formato**: `markup/DiaryMarkup` converte a marcação ↔ documento de blocos (o mesmo do site e do
  backend). Entrada com mais de 10 mil caracteres abre só para leitura no jogo — o pacote cliente →
  servidor do 1.21.1 tem teto de 32 KiB, e cortar o texto perderia conteúdo.

## Dependências

- `aurorion-integracao` ≥ 0.2.0 (dos dois lados — no singleplayer o servidor integrado roda no
  cliente).
- **TesseraUI 1.1** no cliente (`mod-servidor-referencia/tesseraui-1.1.jar`; LGPL-3.0). O servidor
  não precisa dele. **O jar precisa estar versionado no repositório** para o build da outra máquina.

## O que validar ao compilar (outra máquina)

- `./gradlew :aurorion-diario:test` — `DiaryMarkupTest`, `PendingStoreTest`.
- `runClient` + `runServer` com integração configurada para um backend de teste:
  - `/vincular` com código do site; `/diario` sem vínculo mostra a orientação;
  - tela: cantos, cores e fontes do CSS (ajustar `diario.css` com o hot reload do Tessera se
    preciso), lista paginada, campos de título/data, barra de ferramentas, prévia com rolagem;
  - autosave (status "Salvo no site"), editar no site e depois no jogo → conflito → "Manter a minha"
    e "Usar a do site";
  - derrubar o backend enquanto escreve → "guardado no servidor"; fechar e reabrir a entrada com o
    site ainda fora (o texto continua lá); religar → sincroniza e avisa no chat;
  - com o site fora, escrever no jogo; editar a mesma entrada no site; religar → aviso de conflito
    no chat; reabrir no jogo → tela de conflito; "Usar a do site" → reabrir não mostra mais a cópia;
  - publicar/retirar e conferir no perfil público do site;
  - cliente sem o mod → `/diario` manda o link do site; servidor dedicado sobe sem carregar classe do
    Tessera.
- Conferir se o `runServer` do `aurorion-runs` aceita o TesseraUI no classpath (é mod de cliente).
