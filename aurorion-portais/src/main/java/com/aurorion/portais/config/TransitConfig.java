package com.aurorion.portais.config;

import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

/**
 * Config do lado servidor, gravada em {@code config/aurorion_portais-server.toml}.
 *
 * <p>A fronteira com o datapack e a mesma do resto do ecossistema: <b>aqui fica regra de servidor,
 * la fica conteudo</b>. Que dimensoes sao livres e como o servidor reage a uma viagem barrada e
 * regra; nome da linha, dia da partida e duracao da janela sao conteudo, e moram em
 * {@code data/<ns>/aurorion/linhas/*.json}.
 */
public final class TransitConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue ENFORCE;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> FREE_DIMENSIONS;
    public static final ModConfigSpec.BooleanValue LOCK_UNSCHEDULED_DIMENSIONS;

    public static final ModConfigSpec.IntValue BYPASS_PERMISSION_LEVEL;
    public static final ModConfigSpec.BooleanValue BYPASS_CREATIVE;
    public static final ModConfigSpec.BooleanValue BLOCK_PORTALS_EARLY;
    public static final ModConfigSpec.BooleanValue KEEP_PLAYERS_ON_DEATH;

    public static final ModConfigSpec.BooleanValue ANNOUNCE_IN_CHAT;
    public static final ModConfigSpec.IntValue COUNTDOWN_SECONDS;
    public static final ModConfigSpec.IntValue CHECK_INTERVAL_TICKS;
    public static final ModConfigSpec.IntValue DENY_MESSAGE_COOLDOWN_SECONDS;
    public static final ModConfigSpec.BooleanValue LOG_DENIALS;

    public static final ModConfigSpec SPEC;

    static {
        BUILDER.comment(
                "Controle de acesso as dimensoes do Aurorion.",
                "As linhas (nome, dimensoes, horarios, estacoes) NAO ficam aqui: elas sao datapack, em",
                "data/<namespace>/aurorion/linhas/*.json. Editar la e dar /reload aplica sem reiniciar."
        ).push("transito");

        ENFORCE = BUILDER
                .comment(
                        "Chave mestra. Falso libera todas as dimensoes imediatamente, sem apagar linha nem",
                        "passe — e o botao de emergencia para quando algo do modpack conflitar no meio do",
                        "horario de pico, sem precisar reeditar datapack as pressas."
                )
                .define("enforce", true);

        FREE_DIMENSIONS = BUILDER
                .comment(
                        "Dimensoes que NUNCA sao controladas: da para entrar e sair delas a qualquer hora.",
                        "Todo o resto e tratado como controlado — inclusive dimensao de mod que ainda nem foi",
                        "instalada. E deliberado que a lista seja de excecoes e nao de alvos: uma dimensao nova",
                        "que apareca no modpack ja nasce trancada em vez de nascer aberta sem ninguem notar."
                )
                .defineList("freeDimensions",
                        List.of("minecraft:overworld"),
                        () -> "minecraft:overworld",
                        entry -> entry instanceof String);

        LOCK_UNSCHEDULED_DIMENSIONS = BUILDER
                .comment(
                        "O que fazer com dimensao controlada que nenhuma linha atende.",
                        "Verdadeiro (padrao): trancada permanentemente — so passe ou staff entra.",
                        "Falso: liberada. Util enquanto voce ainda esta escrevendo as linhas do modpack, para",
                        "so o que ja tem horario definido ficar sob controle."
                )
                .define("lockUnscheduledDimensions", true);

        BUILDER.pop();
        BUILDER.comment("Quem escapa das regras, e como o servidor evita trabalho inutil.").push("excecoes");

        BYPASS_PERMISSION_LEVEL = BUILDER
                .comment("Nivel de permissao que ignora os portoes. 4 = so dono, 2 = staff comum, 5 = ninguem.")
                .defineInRange("bypassPermissionLevel", 2, 0, 5);

        BYPASS_CREATIVE = BUILDER
                .comment("Jogador em criativo ou espectador ignora os portoes, independente de permissao.")
                .define("bypassCreative", true);

        BLOCK_PORTALS_EARLY = BUILDER
                .comment(
                        "Barra o portal na entrada, antes de o servidor procurar/criar o portal de destino.",
                        "Vale bastante: sem isso, cada jogador que encosta num portal do Nether fechado faz o",
                        "servidor varrer o destino atras de um portal existente e, nao achando, ESCAVAR um novo",
                        "— lixo no mundo e trabalho pesado, multiplicado pela populacao online.",
                        "Desligue apenas se algum mod usar portal para viagem dentro da mesma dimensao: a",
                        "checagem so conhece a origem, entao nesse caso raro ela erra para o lado de barrar.",
                        "Mesmo desligada, a viagem continua bloqueada — so fica mais cara."
                )
                .define("blockPortalsEarly", true);

        KEEP_PLAYERS_ON_DEATH = BUILDER
                .comment(
                        "Morrer dentro de uma dimensao controlada faz renascer dentro dela, e nao no spawn do",
                        "mundo. E o que impede a morte de virar bilhete de volta gratuito para quem perdeu o",
                        "trem. Se a linha declarar uma estacao de desembarque ('arrival') o respawn e nela;",
                        "senao, num ponto seguro perto de onde o jogador morreu.",
                        "Cama ou ancora dentro da propria dimensao continuam tendo prioridade, como no vanilla."
                )
                .define("keepPlayersOnDeath", true);

        BUILDER.pop();
        BUILDER.comment("Avisos, ritmo de verificacao e diagnostico.").push("avisos");

        ANNOUNCE_IN_CHAT = BUILDER
                .comment(
                        "Anuncia no chat as chamadas de embarque, a abertura e o fechamento das linhas.",
                        "Desligar deixa so a contagem regressiva na actionbar de quem esta dentro."
                )
                .define("announceInChat", true);

        COUNTDOWN_SECONDS = BUILDER
                .comment(
                        "Segundos finais de janela em que quem esta DENTRO da dimensao recebe contagem",
                        "regressiva na actionbar. So esses jogadores recebem, e so nesses segundos. 0 desliga."
                )
                .defineInRange("countdownSeconds", 60, 0, 3600);

        CHECK_INTERVAL_TICKS = BUILDER
                .comment(
                        "De quantos em quantos ticks o relogio compara a hora atual com a proxima transicao.",
                        "20 = 1x por segundo, que e a precisao de um relogio de estacao e ja e barato: o custo",
                        "de um tick de espera e uma leitura de relogio e duas comparacoes de long por linha."
                )
                .defineInRange("checkIntervalTicks", 20, 1, 200);

        DENY_MESSAGE_COOLDOWN_SECONDS = BUILDER
                .comment(
                        "Intervalo minimo entre dois avisos de 'portal fechado' para o mesmo jogador.",
                        "Sem isso, quem fica parado dentro do portal recebe a mensagem a cada tick."
                )
                .defineInRange("denyMessageCooldownSeconds", 5, 1, 300);

        LOG_DENIALS = BUILDER
                .comment(
                        "Escreve no log do servidor toda viagem barrada, com origem e destino.",
                        "Ligue ao instalar um mod novo: e assim que voce descobre que ele usa uma dimensao",
                        "propria que deveria estar em freeDimensions."
                )
                .define("logDenials", false);

        BUILDER.pop();

        SPEC = BUILDER.build();
    }

    private TransitConfig() {
    }
}
