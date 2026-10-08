# Correcoes de magia e Body Damage — 08/10/2026

Codigo preparado para `aurorion-magia` e `aurorion-profissoes` 0.5.1. Nao foi compilado nem executado nesta maquina, conforme AGENTS.md. As alteracoes preexistentes de aurorion-essentials foram preservadas.

## Ferimentos

- BodyPart protege dano, maxima do membro e cura contra NaN/infinito/valores negativos, inclusive na leitura e escrita do NBT. O dano infinito recebido em combate e limitado a maxima finita do membro.
- Uma cura completa por API/comando tambem remove o marcador AurorionCritical e a cura pendente. Um marcador antigo com dano zero nao volta do save.
- Primeiros socorros continuam estabilizando lesao grave; o atendimento medico continua removendo a lesao.
- Foi escrito o GameTest `realLsoHealedLimbDoesNotRelapse` (ainda nao executado).

## Umbra Viva e Barril Explosivo

- Umbra fornece `getEmptyCastData()` com MultiTargetEntityCastData, exigido pela leitura de pacotes/NBT de RecastInstance. A ausencia era uma causa concreta de erro na decodificacao no cliente. Sem log do incidente, nao se pode excluir outras causas.
- A troca de lugar valida que a sombra pertence ao conjurador.
- Dolium Ardens usa animacao finita ao terminar a conjuracao instantanea, sem abrir a pose de carga que prendia o braco.

## Balanceamento e RP

- Dano das magias autorais ofensivas e duracao dos efeitos/canalizacoes dobrados. As magias sem dano continuam sem dano. Execucoes letais continuam letais, sem multiplicar Float.MAX_VALUE.
- Recarga, preparo e velocidade de projeteis nao foram dobrados. Magias que alternam controle nao iniciam recarga ao prender, para permitir libertar; ao libertar, pagam a recarga normal.
- O dano usa poder de magia, independente do dano da espada. Drenagem de vida acompanha o dano aumentado.
- `config/aurorion/magia-server.toml`, secao `[roleplay]`:
  - `controlesAteReconjurar`: ids das magias cujo efeito dura ate reconjurar no alvo, leite, Esconjurar ou remocao de efeito. Lista vazia usa as duracoes finitas dobradas.
  - `controlesSilenciam`: ids de controle que bloqueiam voz, texto e conjuracao. Lista vazia deixa a voz livre nos controles; Vox sempre silencia.
  - Ambas as listas incluem por padrao: `vox_interdicta`, `mutatio_ferae`, `genua_flecte`, `imperium_mentis`, `aspectus_captus`, `mundus_vacuus`, `ferrum_ligatum`, `carcer_aquae`.
  - `raioEsconjurar`: 8 blocos por padrao; intervalo 1–32.
- Voz e calculada a partir dos efeitos ativos, inclusive apos login. Remover um controle nao libera a voz enquanto outro silencio continua ativo. Voz nao depende de ler entidades na thread de audio.
- Vox e Olhar Cativo em area alternam o estado dos alvos no raio; reconjurar em um alvo individual tambem o liberta.

## Novas magias

| ID | Comportamento |
| --- | --- |
| `aurorion_magia:ceifar_vida` | Retira exatamente uma vida de RP, levita por 5 s e aplica cegueira, fraqueza e fome por 20 s. |
| `aurorion_magia:entregar_vida` | Concede exatamente uma vida de RP e levita por 5 s. Nao retira uma vida do conjurador. |
| `aurorion_magia:esconjurar` | Remove buffs e debuffs de todos os vivos no raio, incluindo aliados e conjurador, pelos eventos normais de remocao. |

As duas magias de vida sao proibidas (sem craft/loot/concessao por escola), concedidas individualmente pela staff pelo fluxo `/aurorion spells`. Usam a API de LivesManager, com save, limite e HUD iguais a `/vidas dar` e `/vidas tirar`. Nao matam o corpo nem disparam uma segunda perda de vida. Ceifar em zero e Entregar no limite sao recusadas. Morto definitivo nao pode ser ressuscitado por estas magias. Sem aurorion-vidas, o ritual e recusado.

Esconjurar remove os status presentes; fontes ainda ativas (passivas, zonas, beacons, etc.) podem reaplica-los. Nao desativa permanentemente habilidades/passivas do personagem.

## Validacao na maquina de build/servidor

1. Compilar aurorion-magia e aurorion-profissoes com as versoes de referencia dos mods; executar a suite existente e o novo GameTest.
2. Instalar o mesmo aurorion-magia 0.5.1 no servidor e em todos os clientes. Atualizar aurorion-profissoes onde instalado.
3. Ferir gravemente um membro; tratar via medico, comando e cura magica; relogar; causar 1 ponto de dano. Confirmar que nao volta ao dano maximo. Repetir com save contendo NaN/infinito e marcador critico com dano zero.
4. Umbra: primeira conjuracao, troca, expiracao, duas sombras, relogin com recast salvo e troca de dimensao. Verificar logs de cliente e servidor e ausencia de erro de decodificacao.
5. Dolium: testar com mao, pergaminho e grimorio; conferir pose normal apos o arremesso e apos interrupcao.
6. Comparar dano e duracao com 0.5.0, em alvo sem armadura, em cada nivel relevante. Testar de mao vazia e com espadas diferentes. Conferir drenagem de vida, marcas atrasadas, bigorna e dano continuo.
7. Cada controle: aplicar, esperar alem do prazo antigo, reconjurar, relogar, remover via leite e Esconjurar. Conferir modelo de guaxinim para quem chega depois e fala/chat/microfone/conjuracao. Sobrepor dois controles e remover so um. Repetir com listas de RP vazias.
8. Ceifar/Entregar: alvo com uma vida, zero vidas, limite cheio e personagem morto definitivo. Conferir uma unica mudanca no HUD/save, sem perda dupla no ritual. Uma morte posterior deve consumir apenas a vida normal daquela morte. Interromper ritual e sair do alcance: nao deve alterar vidas.
9. Esconjurar: buffs e debuffs vanilla e do Aurorion, varios alvos, borda do raio, caster e aliados. Conferir liberação de corrente, bolha, emote, possessao e microfone.
