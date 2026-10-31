package com.aurorion.intencao.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/** {@code config/aurorion/intencao-server.toml}. */
public final class IntencaoConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.IntValue PERMISSION_LEVEL;
    public static final ModConfigSpec.IntValue TIMEOUT_MINUTES;

    public static final ModConfigSpec.BooleanValue DUEL_ENABLED, DEATH_ALLOWED, FLEE_KILLS;
    public static final ModConfigSpec.IntValue CHALLENGE_RANGE, CHALLENGE_SECONDS, FIELD_RADIUS, WARN_RADIUS,
            TURN_SECONDS, COUNTDOWN_SECONDS, REACTION_MS, REACTION_MS_PER_POINT, REACTION_MS_MAX,
            PERFECT_BONUS, TIMING_BONUS, GUARD_FATIGUE, XP_WIN, XP_LOSS, MAX_ROUNDS, CHALLENGE_COOLDOWN, XP_PAIR_MINUTES;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        PERMISSION_LEVEL = builder
                .comment("Nivel de permissao para conduzir uma batalha (/intencao).")
                .defineInRange("permissionLevel", 2, 0, 4);
        TIMEOUT_MINUTES = builder
                .comment("Rede de seguranca: uma batalha esquecida aberta se desfaz sozinha (empate) depois disto.",
                        "Uma sessao de mesa longa cabe com folga no padrao.")
                .defineInRange("timeoutMinutes", 180, 10, 24 * 60);

        builder.comment("Duelo com a ficha (/duelo): combate por turnos feito pelo jogo, com a vida, PE, defesa e",
                "pericias da ficha do site. Ninguem leva dano de verdade no Minecraft; quem zera a vida da ficha num",
                "duelo ate a morte perde o personagem (a cena de fim do Limbo, sem passar por ele).").push("duelo");
        DUEL_ENABLED = builder.comment("Liga o /duelo.").define("habilitado", true);
        DEATH_ALLOWED = builder.comment("Permite o duelo ate a morte (limite 0). Exige o aurorion-limbo no pack.")
                .define("permiteMorte", true);
        FLEE_KILLS = builder.comment("Num duelo ate a morte, sair do jogo no meio conta como derrota com morte.",
                "Desligado (padrao), conta so como derrota: queda de conexao e reinicio do servidor nao matam ninguem.")
                .define("fugaMata", false);
        CHALLENGE_RANGE = builder.comment("Distancia maxima (blocos) para desafiar alguem.").defineInRange("alcanceDesafio", 16, 2, 64);
        CHALLENGE_SECONDS = builder.comment("Quanto tempo o desafio espera resposta.").defineInRange("segundosDesafio", 60, 10, 600);
        FIELD_RADIUS = builder.comment("Raio do campo de nao fuga em volta do ponto medio dos duelistas.")
                .defineInRange("raioCampo", 12, 4, 48);
        WARN_RADIUS = builder.comment("Quem estiver ate esta distancia e avisado do duelo (comeco e fim).")
                .defineInRange("raioAviso", 48, 8, 256);
        TURN_SECONDS = builder.comment("Tempo para escolher a acao no seu turno; passou, a pessoa se defende.")
                .defineInRange("segundosTurno", 30, 5, 180);
        COUNTDOWN_SECONDS = builder.comment("Contagem antes do primeiro turno.").defineInRange("segundosContagem", 5, 0, 30);
        REACTION_MS = builder.comment("Janela base da reacao cronometrada (esquiva/aparo), em milissegundos para cada lado do golpe.")
                .defineInRange("reacaoMs", 180, 50, 1000);
        REACTION_MS_PER_POINT = builder.comment("Milissegundos a mais na janela por ponto de bonus de Acrobacia (esquiva) ou",
                "Corpo a corpo (aparo).").defineInRange("reacaoMsPorPonto", 15, 0, 100);
        REACTION_MS_MAX = builder.comment("Teto da janela.").defineInRange("reacaoMsMaximo", 450, 50, 2000);
        PERFECT_BONUS = builder.comment("A reacao e um teste: d20 + Corpo a corpo (aparo) ou Acrobacia (esquiva) contra o",
                "total do ataque (a CD). Clicar no instante perfeito soma isto ao teste.").defineInRange("bonusTempoPerfeito", 5, 0, 20);
        TIMING_BONUS = builder.comment("Clicar dentro da janela (sem ser perfeito) soma isto. Fora dela nao ha teste: o golpe entra.")
                .defineInRange("bonusTempo", 2, 0, 20);
        MAX_ROUNDS = builder.comment("Rodadas no maximo; passou, o duelo termina empatado (ninguem fica em duelo para sempre).")
                .defineInRange("rodadasMaximas", 30, 5, 200);
        CHALLENGE_COOLDOWN = builder.comment("Segundos entre dois desafios do mesmo jogador.").defineInRange("segundosEntreDesafios", 10, 0, 600);
        XP_PAIR_MINUTES = builder.comment("A mesma dupla so ganha XP de duelo de novo depois de tantos minutos.")
                .defineInRange("minutosXpMesmaDupla", 30, 0, 1440);
        XP_WIN = builder.comment("Experiencia (a do vanilla) para quem vence um duelo. 0 desliga.").defineInRange("xpVitoria", 50, 0, 10000);
        XP_LOSS = builder.comment("Experiencia para quem perde (lutou ate o fim). 0 desliga.").defineInRange("xpDerrota", 15, 0, 10000);
        GUARD_FATIGUE = builder.comment("Cada defesa bem-sucedida seguida tira isto da proxima (a guarda cansa). Volta a",
                "zero quando o golpe entra ou quando a pessoa usa Defender no turno dela.").defineInRange("guardaCansada", 2, 0, 10);
        builder.pop();
        SPEC = builder.build();
    }

    private IntencaoConfig() {
    }
}
