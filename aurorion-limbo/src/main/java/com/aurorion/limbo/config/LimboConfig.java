package com.aurorion.limbo.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Config do lado servidor, em {@code config/aurorion_limbo-server.toml}.
 *
 * <p>Aqui mora <b>regra</b>: quanto tempo, quantos blocos, quem avisa quem. Coordenada nenhuma mora
 * aqui — o ponto de chegada do Limbo continua sendo do {@code aurorion_vidas}
 * ({@code /vidas exilio aqui}), pelo mesmo motivo de sempre: config que guarda coordenada nao
 * acompanha o que foi construido e nao some quando o mundo e trocado.
 */
public final class LimboConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.IntValue DEADLINE_HOURS;
    public static final ModConfigSpec.IntValue LIVES_ON_RESCUE;

    public static final ModConfigSpec.IntValue DOOR_WINDOW_HOURS;
    public static final ModConfigSpec.IntValue DOOR_WALK_MIN;
    public static final ModConfigSpec.IntValue DOOR_WALK_MAX;
    public static final ModConfigSpec.ConfigValue<String> DOOR_FRAME_BLOCK;
    public static final ModConfigSpec.IntValue LEASH_RADIUS;
    public static final ModConfigSpec.IntValue SPAWN_SCATTER;

    public static final ModConfigSpec.IntValue RESCUE_LIFE_COST;
    public static final ModConfigSpec.IntValue RESCUE_MIN_LIVES;
    public static final ModConfigSpec.IntValue PASSAGE_MINUTES;
    public static final ModConfigSpec.IntValue BOND_COUNT;
    public static final ModConfigSpec.ConfigValue<String> ORACLE_TAG;
    public static final ModConfigSpec.ConfigValue<String> ORACLE_DIALOGUE;

    public static final ModConfigSpec.BooleanValue ANNOUNCE_FALL;
    public static final ModConfigSpec.BooleanValue ANNOUNCE_NAMES;

    public static final ModConfigSpec.ConfigValue<String> WEBHOOK_URL;
    public static final ModConfigSpec.BooleanValue AUDIT_TO_LOG;

    public static final ModConfigSpec SPEC;

    static {
        BUILDER.comment("O prazo do exilio.").push("prazo");

        DEADLINE_HOURS = BUILDER
                .comment(
                        "Horas de RELOGIO REAL que alguem tem no Limbo antes do prazo vencer.",
                        "Real, e nao tempo de jogo: quem resgata sao as outras pessoas, e elas vivem no fuso",
                        "delas. O exilado estar offline nao diminui a chance dele — so atrapalha a propria",
                        "caminhada, que e assunto da secao 'porta'.",
                        "48h e o minimo que atravessa um dia de trabalho ou de escola de todo mundo envolvido.",
                        "Com 24h, quem cai na terca de manha depende da casa inteira estar online na terca a",
                        "noite, e a mecanica passa a punir horario de vida em vez de jogo.",
                        "Tempo com o servidor desligado NAO conta — ver LimboManager#tick."
                )
                .defineInRange("horasDePrazo", 48, 1, 24 * 14);

        LIVES_ON_RESCUE = BUILDER
                .comment(
                        "Quantas vidas a pessoa tem ao ser resgatada por alguem.",
                        "Tem que ser maior que o da Porta do Esquecido, senao ninguem prefere ser resgatado."
                )
                .defineInRange("vidasAoSerResgatado", 2, 1, 20);

        BUILDER.pop();
        BUILDER.comment(
                "A Porta do Esquecido: a saida de quem ninguem foi buscar.",
                "Ela so existe para que nenhum jogador fique refem da boa vontade alheia. Uma casa rival",
                "nao pode matar alguem em definitivo simplesmente nao aparecendo."
        ).push("porta");

        DOOR_WINDOW_HOURS = BUILDER
                .comment(
                        "Nas ultimas N horas do prazo a Porta passa a poder aparecer.",
                        "Antes disso ela nao existe, e e isso que da tempo para o resgate ser tentado: se a",
                        "Porta valesse desde o primeiro minuto, ninguem esperaria por ninguem."
                )
                .defineInRange("janelaEmHoras", 5, 1, 24);

        DOOR_WALK_MIN = BUILDER
                .comment(
                        "Distancia minima, em blocos, a caminhar dentro da janela antes da Porta aparecer.",
                        "A distancia exata e sorteada UMA VEZ entre o minimo e o maximo, quando a janela abre,",
                        "e fica guardada. Do lado de dentro isso e indistinguivel de 'uma certa chance a cada",
                        "tanto', que era a ideia original — mas do lado do servidor tem tres vantagens:",
                        "nao roda sorteio por tick, a caminhada nunca e desperdicada por azar, e o jogador que",
                        "andou o combinado SEMPRE encontra a saida.",
                        "A distancia vem da estatistica que o vanilla ja mantem (andar + correr + agachado), entao",
                        "medir isso nao custa um listener de tick."
                )
                .defineInRange("blocosMinimo", 1000, 1, 100_000);

        DOOR_WALK_MAX = BUILDER
                .comment("Teto do sorteio. Se ficar abaixo do minimo, o minimo vence.")
                .defineInRange("blocosMaximo", 2000, 1, 100_000);

        DOOR_FRAME_BLOCK = BUILDER
                .comment("Bloco da moldura da Porta quando ela aparece. So aparencia; a saida e a proximidade.")
                .define("blocoDaMoldura", "minecraft:crying_obsidian");

        LEASH_RADIUS = BUILDER
                .comment(
                        "Ate onde o exilado pode se afastar da ancora do exilio antes de ser empurrado de volta.",
                        "E a BORDA do Limbo — nao ha world border de verdade, o empurrao e esta config.",
                        "",
                        "O valor nasceu 300 quando o Limbo era uma arena plana e vazia e a coleira existia para o",
                        "resgate: uma busca de 15 minutos precisava de area pequena. Duas coisas mudaram desde",
                        "entao e o numero teve que mudar junto:",
                        "  - as ruinas geram a cada ~400 blocos, entao com 300 ninguem encontrava nenhuma;",
                        "  - o resgatador chega PERTO do exilado, nao num ponto fixo, entao a busca ja nao",
                        "    depende de o Limbo ser pequeno.",
                        "",
                        "2000 da uma area para sobreviver, explorar e achar ruinas, e ainda e uma borda: o Limbo",
                        "tem fim, e bater nele lembra que o lugar e uma jaula.",
                        "Quando a janela da Porta do Esquecido abre, a coleira cai sozinha — e o momento em que o",
                        "Limbo deixa de ser sala de espera e vira um lugar para vagar procurando saida.",
                        "Zero desliga a borda e o Limbo vira infinito."
                )
                .defineInRange("raioDaColeira", 2000, 0, 100_000);

        SPAWN_SCATTER = BUILDER
                .comment(
                        "Raio em que o ponto de acordar e sorteado, em volta da ancora do exilio.",
                        "Vale para a chegada E para cada morte dentro do Limbo: ninguem acorda duas vezes no",
                        "mesmo lugar. Morrer passa a custar territorio em vez de rebobinar a caminhada.",
                        "Sempre na SUPERFICIE — nunca dentro de caverna, nunca em cima de copa de arvore.",
                        "Mantenha bem abaixo do raioDaColeira, ou alguem acorda ja fora da borda.",
                        "Zero faz todo mundo acordar na ancora, como era antes."
                )
                .defineInRange("raioDeDispersao", 400, 0, 100_000);

        BUILDER.pop();
        BUILDER.comment(
                "O resgate: o Oraculo, a passagem e o Vinculo de Alma.",
                "Ir buscar alguem custa uma vida de quem vai. E o que transforma resgate em decisao",
                "em vez de logistica — e fecha de graca o abuso de conta alt, que nao tem vida sobrando."
        ).push("resgate");

        RESCUE_LIFE_COST = BUILDER
                .comment(
                        "Quantas vidas quem abre a passagem paga.",
                        "Zero desliga o custo e transforma o resgate em rotina. Foi discutido e rejeitado:",
                        "sem preco, a Porta do Esquecido perde o sentido, porque nunca faltaria quem fosse."
                )
                .defineInRange("custoEmVidas", 1, 0, 20);

        RESCUE_MIN_LIVES = BUILDER
                .comment(
                        "Vidas minimas para poder abrir a passagem.",
                        "Tem que ser MAIOR que o custo: quem ficaria com zero ao pagar seria exilado no ato,",
                        "e o servidor ganharia dois exilados em vez de zero."
                )
                .defineInRange("vidasMinimas", 2, 1, 20);

        PASSAGE_MINUTES = BUILDER
                .comment(
                        "Quantos minutos a passagem fica aberta.",
                        "Fechou, quem esta dentro continua dentro ate achar o exilado ou ser trazido de volta."
                )
                .defineInRange("minutosDaPassagem", 15, 1, 120);

        BOND_COUNT = BUILDER
                .comment("Quantos Vinculos de Alma o resgatador leva ao atravessar. Um sobrando cobre perder um.")
                .defineInRange("vinculosPorResgate", 2, 1, 16);

        ORACLE_TAG = BUILDER
                .comment(
                        "Tag de entidade que transforma um mob em Oraculo.",
                        "Nao registramos entidade propria de proposito: um esqueleto com esta tag ja serve, e",
                        "qualquer mob do modpack pode virar Oraculo sem uma linha de codigo. Mesma ideia da tag",
                        "de altar do aurorion_ethereal — o acoplamento e um dado, nunca uma classe.",
                        "",
                        "Para criar um:",
                        "  /summon minecraft:skeleton ~ ~ ~ {Tags:[\"aurorion_oraculo\"],CustomName:'\"O Oraculo\"',",
                        "   NoAI:1b,Silent:1b,PersistenceRequired:1b,Invulnerable:1b}"
                )
                .define("tagDoOraculo", "aurorion_oraculo");

        ORACLE_DIALOGUE = BUILDER
                .comment(
                        "Arquivo de dialogo do ADM que o Oraculo abre, sem a extensao .json.",
                        "Vazio (ou ADM ausente) faz o Oraculo abrir a lista de exilados direto — o resgate",
                        "funciona igual, so perde a conversa.",
                        "",
                        "O dialogo e CONTEUDO: mora em config/adm-dialogues/dialogues/ ou num datapack, e a",
                        "staff reescreve a fala sem rebuild. O que vem do codigo sao as condicoes que ele pode",
                        "consultar:",
                        "  {\"type\": \"aurorion_limbo:exilados\", \"min\": 1}  ha alguem no Limbo",
                        "  {\"type\": \"aurorion_limbo:pode_pagar\"}          tem vida para a passagem",
                        "  {\"type\": \"aurorion_limbo:no_limbo\"}            quem fala esta exilado",
                        "Uma escolha do dialogo abre a lista rodando: commands: [\"limbo oraculo\"]"
                )
                .define("dialogoDoOraculo", "oraculo_do_limbo");

        BUILDER.pop();
        BUILDER.comment("O que o servidor conta, e para quem.").push("avisos");

        ANNOUNCE_FALL = BUILDER
                .comment("Anuncia para o servidor inteiro quando alguem cai no Limbo.")
                .define("anunciarQueda", true);

        ANNOUNCE_NAMES = BUILDER
                .comment(
                        "Se o anuncio publico diz o NOME de quem caiu.",
                        "Falso (padrao) e a versao com RP: o servidor sabe que um vinculo se rompeu, nao de quem.",
                        "Descobrir o nome passa a ser assunto da casa da pessoa e do Oraculo. Com 80 jogadores o",
                        "sumico de alguem passa despercebido sozinho, entao alguma pista precisa existir — mas",
                        "gritar o nome mata a investigacao inteira.",
                        "Verdadeiro e mais simples e mais direto, se o servidor nao quiser esse RP."
                )
                .define("anunciarNomes", false);

        BUILDER.pop();
        BUILDER.comment(
                "Registro para a staff.",
                "Tudo que acontece no Limbo vira uma linha em <mundo>/aurorion_limbo/auditoria.jsonl,",
                "sempre, independente destas opcoes. O arquivo e a fonte da verdade; o que esta aqui e",
                "so como ele chega ate voce."
        ).push("auditoria");

        WEBHOOK_URL = BUILDER
                .comment(
                        "Webhook (Discord ou outro) que recebe um POST a cada evento do Limbo. Vazio desliga.",
                        "",
                        "POR QUE WEBHOOK E NAO RCON: RCON e de mao unica e a mao e a de fora — um cliente se",
                        "conecta no servidor e manda comando. O servidor nao abre conexao RCON com ninguem, entao",
                        "'avisar o Discord na hora que alguem saiu' nao e uma coisa que RCON saiba fazer.",
                        "",
                        "As duas metades, entao:",
                        "  - EMPURRAR o evento na hora  -> este webhook.",
                        "  - PUXAR o estado quando quiser -> seu bot roda /limbo relatorio por RCON. A saida e",
                        "    texto estavel em chave=valor, feita para ser parseada.",
                        "Da para usar so uma das duas. Um bot que ja fala RCON e que so precisa de um painel",
                        "'quem esta no Limbo agora' nao precisa de webhook nenhum.",
                        "",
                        "O envio e assincrono e descartavel: nunca bloqueia o tick, e um webhook fora do ar vira",
                        "aviso no log, nunca lag no servidor."
                )
                .define("webhookUrl", "");

        AUDIT_TO_LOG = BUILDER
                .comment("Repete cada evento da auditoria no log do servidor, para quem prefere ler pelo console.")
                .define("tambemNoLog", true);

        BUILDER.pop();

        SPEC = BUILDER.build();
    }

    private LimboConfig() {
    }

    /** O sorteio da Porta so faz sentido com o teto acima do piso; a config nao garante isso sozinha. */
    public static int walkMax() {
        return Math.max(DOOR_WALK_MIN.get(), DOOR_WALK_MAX.get());
    }

    /**
     * Vidas minimas reais para abrir a passagem.
     *
     * <p>A config deixa configurar minimo e custo separados, e nada impede alguem escrever minimo 1 com
     * custo 1. Isso exilaria o resgatador no momento em que ele paga — dois exilados no lugar de zero,
     * e o segundo sem ninguem para ir buscar. O piso e sempre custo + 1.
     */
    public static int minLivesToRescue() {
        return Math.max(RESCUE_MIN_LIVES.get(), RESCUE_LIFE_COST.get() + 1);
    }
}
