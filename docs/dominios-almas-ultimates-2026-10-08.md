# Magias de alma, expansões e efeitos de ultimates — 0.6.0

Implementação no `aurorion-magia`. O Santuário atinge somente entidades, conforme a escolha do Neto.
A compilação nesta máquina foi autorizada pelo Neto após a implementação, substituindo a
restrição anterior do `AGENTS.md`. Os jars ficam em `build/jars-servidor`; o pack instalado
e o servidor de produção não foram alterados.

## Conteúdo

| Magia | ID completo | Comportamento no nível 1 |
| --- | --- | --- |
| Reino das Almas | `aurorion_magia:reino_almas` | Duelo entre dois jogadores em outra dimensão por 30 s; alcance 24; conjurador causa +30% e recebe -20% de dano |
| Vazio Infinito | `aurorion_magia:vazio_infinito` | Expansão cósmica de raio 24 por 20 s; sobrecarga mental paralisa inimigos elegíveis; eles continuam vulneráveis a dano |
| Santuário Malevolente | `aurorion_magia:santuario_malevolente` | Expansão aberta de raio 200 por 30 s; alterna Desmantelar e Destrinchar a cada 0,5 s; sem dano a blocos |

As três são proibidas, sem craft/loot e concedidas individualmente pela staff. O comando de todas
as escolas continua seguindo a regra das proibidas: não concede essas três automaticamente.
Reconjurar encerra o respectivo domínio. Só pode existir uma expansão de Vazio/Santuário por
conjurador; morte, desconexão, mudança de dimensão, incapacidade de conjurar ou saída do raio
também encerram a expansão. O efeito mental residual expira em até 8 ticks após o último pulso.
O marcador de expansão e o efeito do duelo sincronizam a reconjuração instantânea com o cliente.
Esconjurar, leite e `/effect clear` no dono encerram a expansão; o duelo não é curado por leite,
mas Esconjurar e `/effect clear` em qualquer participante encerram e devolvem os dois.

No Vazio, não se anda, pula, ataca, usa item/bloco, fala ou conjura. O servidor segura posição e
rotação, interrompe a conjuração atual e impede criaturas de escolher novos alvos. NPCs
invulneráveis, criativo/espectador e os imunes a deslocamento continuam protegidos. A redoma e
o cenário cósmico são geometria visual; não substituem o terreno por blocos de outro cenário.

O Santuário usa uma **esfera** de alcance e atinge todos os alvos elegíveis carregados dentro dela,
sem teto que deixaria alguns imunes por lotação. Destrinchar acrescenta 3,5% da vida máxima ao dano
base, limitado a 1,75× o dano base. Armadura, escudo e resistência continuam valendo. Os cortes
ignoram apenas a janela de invulnerabilidade entre golpes, para manter a cadência de 0,5 s.
PvP, áreas protegidas e a opção de poupar aliados continuam sendo consultados.

Os níveis 2 e 3 das expansões somam, respectivamente, 20% e 40% do tempo base. O Santuário ganha
também dano por nível. A duração e o raio base ficam em `[dominios]` de `magia-server.toml`:

```toml
[dominios]
duracaoReinoAlmas = 600
raioVazioInfinito = 24
duracaoVazioInfinito = 400
raioSantuarioMalevolente = 200
duracaoSantuarioMalevolente = 600
```

Tempos em ticks: 20 ticks = 1 s. Cooldown, raridade e permissões seguem a integração existente
com o Iron's/Restrictions. As correções anteriores e o multiplicador global de dano/duração
permanecem no código; as novas durações acima são valores finais, configurados diretamente.

## Passivas

| Passiva | ID | Efeito |
| --- | --- | --- |
| Colheita de Almas | `aurorion_magia:colheita_almas` | Abate atribuído ao portador restaura 4 pontos de saúde e 20 de mana; intervalo de 3 s; aliados não contam |
| Domínio Refinado | `aurorion_magia:dominio_refinado` | +25% de duração no Reino e nas expansões; +25% no raio das expansões, com tetos de 50 para o Vazio e 200 para o Santuário |

Não têm interruptor nem custo periódico. São do personagem e usam os comandos/pergaminhos já
existentes. Reset de personagem limpa as concessões e o marcador de intervalo da colheita.

## Genki Dama e Lux

- Genki Dama: carga máxima de 30 s; raio visual cresce de 0,75 até 8 blocos, com interpolação entre
  pacotes. O raio da explosão cresce de 3 até 18/24/30 conforme nível e carga, e o dano continua
  crescendo com a carga. O custo por pulso caiu para permitir sustentar a carga. A esfera sai
  da posição onde foi mostrada e converge para a mira, em vez de voar acima de um alvo no chão.
- Corrigido o cliente que renovava a duração do visual, mas descartava o novo tamanho da esfera.
- Esfera carregada e projétil: quatro camadas de luz, três anéis rúnicos inclinados e arcos
  elétricos. Impacto: três ondas de choque, coroa elétrica, clarão, explosão e trovão.
- Lux: selo e concentração na mão antes do disparo, indicação fina da direção, feixe com quatro
  camadas, três filamentos helicoidais, clarão e arcos na ponta. O feixe visual dura 1,5 s;
  o dano ocorre uma vez, preservando Iluminação e o balanceamento anterior.
- Tremor curto perto do impacto respeita `cameraShake` e a escala de efeitos de distorção.
  Raios são geometria de luz; não invocam relâmpagos que incendiam blocos ou causam dano adicional.

## Retorno, persistência e custos

O Reino usa a dimensão `aurorion_magia:reino_almas`, com gerador vazio. Arenas de 33×33 blocos
ficam separadas por 1.024 blocos, para uma expansão não alcançar outro duelo. Piso e paredes são
de bedrock. A arena é preparada na conjuração, somente nessa dimensão; não copia construções.
Não admite Possessão durante o duelo, evitando misturar dois sistemas de controle/retorno.

As ancoragens de origem, parceiro e identificador do duelo ficam em
`data/aurorion_magia_reino_almas.dat`. Login e respawn recuperam participantes pendentes;
em uma morte, o corpo é devolvido antes de Vidas/Limbo registrarem o evento, sem curar/reviver.
Se outro mod vetar o retorno, a ancoragem fica preservada e há nova tentativa enquanto o jogador
estiver no Reino sem o efeito do duelo. Trancas de outros mods não são ignoradas.
Se a origem deixou de existir, o fallback é o spawn do Overworld. NBT inválido não gera teleporte
com coordenadas não finitas. Arenas ocupadas não são reutilizadas; lotação recusa a magia.

A ponte opcional do Twilight Teleport segue a API já utilizada pelo Limbo para fazer a viagem
sincronamente. Ela evita o teleporte adiado de um participante depois que o duelo já foi desfeito.
Não cria dependência obrigatória nova. Os ícones reutilizam a identidade visual existente do módulo.

As expansões reutilizam `zona_de_magia`, sem novo tipo de entidade. Ela não é salva com o chunk;
login limpa estado residual. Somente zonas ativas pulsam: Vazio a cada 4 ticks; Santuário a cada
10. A busca lê entidades carregadas, não percorre blocos nem força carregamento no raio de 200.
O tracking da zona chega a 16 chunks para os clientes próximos da borda. Partículas respeitam a
opção do vanilla e a distância visual; estrelas usam posições pré-calculadas, e a quantidade
de geometria/cortes ambientais é fixa, independente do volume do raio.

Os modificadores de movimento/pulo/gravidade do Vazio entraram na limpeza de atributos órfãos.
Sua âncora também é limpa ao terminar o efeito e na recuperação. Silêncios sobrepostos não
liberam a voz uns dos outros. Os passes de tinta e luz continuam sequenciais, evitando o crash
`Not building!` causado por escrever em um consumidor depois de trocar o buffer.
A expiração do Reino agenda o retorno fora da iteração de efeitos; a expansão não tenta remover
novamente o marcador que já está sendo removido. Coordenadas fora dos limites e rotações não
finitas têm fallback; falhas na procura de espaço ou no teleporte preservam a ancoragem.

## Conceder para ensaio

```mcfunction
/aurorion spells unlock spell aurorion_magia:reino_almas Jogador
/aurorion spells unlock spell aurorion_magia:vazio_infinito Jogador
/aurorion spells unlock spell aurorion_magia:santuario_malevolente Jogador
/aurorion passivas conceder aurorion_magia:colheita_almas Jogador
/aurorion passivas conceder aurorion_magia:dominio_refinado Jogador
```

Os pergaminhos estão na aba criativa Aurorion — Magias, usando o registro existente.
Servidor e clientes precisam do mesmo `aurorion_magia 0.6.0`, protocolo 9. O novo jar foi gerado
em `build/jars-servidor`. No diretório de pack informado havia `aurorion_magia 0.3.3`;
ele ainda não foi substituído. Manifesto e hashes: `releases/2026-10-08`.

## Verificações feitas nesta máquina

Inspeção estática concluída: 55 registros de magia com ícones e guias, quatro passivas,
384 chaves equivalentes nos dois idiomas, placeholders de tradução, JSONs e dimensões dos PNGs.
Ordinais antigos dos visuais de rede e das formas de zona preservados; delimitadores dos 33
arquivos Java da implementação inicial e `git diff --check` conferidos. O `buildAll` depois
foi aprovado com Java 21; os relatórios da suíte existente contêm 462 testes, zero falhas,
zero erros e 35 ignorados. Foram corrigidos um import ausente em Olhar Cativo e o teste que
ainda tratava duração negativa do silêncio como expirada, cobrindo agora a liberação explícita.
Profissões passou nos 12 GameTests com LSO 2.4.7.2, incluindo o membro curado que não recai.
O smoke da Magia em servidor dedicado passou nos registros de magias/efeitos/tipo de dano,
no tipo da dimensão e na decodificação do gerador do Reino pelo codec real. GameTestServer
cria apenas seus mundos vanilla; a entrada e o retorno do duelo continuam no checklist.
Essas verificações não substituem a validação de desempenho/visual e dos duelos com dois clientes.

## Validação em jogo pendente

- [x] Compilar todos os módulos e executar a suíte existente, incluindo contratos de assets/idiomas.
- [ ] Iniciar servidor e dois clientes com os jars atualizados para os ensaios abaixo.
- [ ] Confirmar carregamento da dimensão após reinício de um mundo existente; nenhum erro de codec
      de gerador/dimension type/damage type; os três pergaminhos e os três efeitos sem textura ausente.
- [ ] Duelo com dois clientes: entrada simultânea, bônus de dano, retorno por recast e por prazo;
      logout de cada lado, restart durante o duelo, morte de cada lado, totem e respawn. Conferir
      posição, inventário, espólio do Limbo e uma única cobrança de vida de RP. Se morte for cancelada
      por PlayerRevive/outro mod, ninguém deve perder vida nem ficar preso.
- [ ] Duas arenas simultâneas: Shrine em uma não afeta a outra. Testar cerca, voo/levitação,
      pérola/chorus, entrada por comando da staff, origem removida e origem ocupada por blocos.
- [ ] Testar Twilight Teleport 1.0.1 e trancas do Portais: rejeitar entrada/retorno artificialmente;
      confirmar ausência de viagem adiada, ancoragem preservada e recuperação ao liberar a tranca.
- [ ] Vincular/encarcerar alguém durante o duelo, usar Tempus e Vazio, encerrar: âncoras da arena
      não reaparecem no mundo de origem; nenhum modificador de movimento/gravidade fica órfão.
- [ ] Vazio: paralisia imediata no instante de abertura; item, bloco, ataque, teclado, mouse, chat,
      voz e conjuração bloqueados; criatura não ataca. Encerrar, deslogar ou matar o conjurador
      libera todos; sobrepor dois domínios não libera prematuramente quem ainda está no outro.
- [ ] Reconjuração instantânea com dois clientes; Esconjurar, leite e `/effect clear` no dono das
      expansões. Esconjurar/limpeza administrativa em cada participante do Reino; expiração natural
      sem erro de iteração de efeitos, retorno duplicado ou efeito/estado residual.
- [ ] Santuário: cortes nas distâncias 1/100/199 e nenhum corte fora de 200; fuga possível;
      mais de 48 alvos elegíveis atingidos; aliados/PvP/áreas respeitados; blocos intactos.
- [ ] Passivas: concessão, pergaminho, revogação, relog e reset; cooldown da colheita sem farm
      de aliados; conferir mana sincronizada e modificadores de duração/raio com cada nível.
- [ ] Genki com 1/5/15/30 s: crescimento visível para conjurador e observador, dano/alcance
      proporcionais e mira funcionando no chão. Soltar antes de 1 s, silenciar, paralisar,
      morrer e cancelar por comando: nada de projétil duplicado nem animação de braços presa.
- [ ] Lux em livro, grimório e pergaminho: carga/cancelamento, disparo, Iluminação e efeito
      de impacto; som sem repetição contínua e tremor desligável.
- [ ] FPS/TPS com várias expansões e grupo numeroso; partículas Todas/Reduzidas/Mínimas,
      câmera longe do centro mas dentro do Santuário, Sodium/Iris com e sem shaders.
- [ ] Regressão: Body Damage curado não volta quebrado ao dano seguinte; vidas/NBT após relog;
      Umbra Viva e Dolium em livro/grimório sem desconexão/crash/animação presa; Ceifar Vida,
      Entregar Vida e Esconjurar; silêncios persistentes e `/aurorion spells unlock todos`.
