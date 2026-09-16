package com.aurorion.essentials.privacy;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Config do lado servidor, gravada em {@code config/aurorion_essentials-privacy.toml}. Controla o
 * que fica visivel so para operadores, para evitar meta-gaming (saber quem esta online, ver
 * conquistas alheias, ou trocar mensagens privadas sem moderacao).
 */
public final class PrivacyConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue HIDE_JOIN_LEAVE_MESSAGES;
    public static final ModConfigSpec.BooleanValue HIDE_ADVANCEMENT_MESSAGES;
    public static final ModConfigSpec.BooleanValue RESTRICT_PRIVATE_MESSAGES;

    public static final ModConfigSpec SPEC;

    static {
        BUILDER.comment(
                "Oculta entradas e saidas de todos os jogadores, inclusive operadores.",
                "Conquistas alheias e mensagens privadas podem ser restritas a operadores."
        ).push("privacy");

        HIDE_JOIN_LEAVE_MESSAGES = BUILDER
                .comment("Esconde \"Fulano entrou/saiu do jogo\" de todos os chats, inclusive de operadores.")
                .define("hideJoinLeaveMessages", true);

        HIDE_ADVANCEMENT_MESSAGES = BUILDER
                .comment("Esconde o anuncio de conquista (\"Fulano completou a conquista...\") do chat de quem nao e OP.")
                .define("hideAdvancementMessages", true);

        RESTRICT_PRIVATE_MESSAGES = BUILDER
                .comment("Restringe /msg, /tell e /w para uso exclusivo de operadores.")
                .define("restrictPrivateMessages", true);

        BUILDER.pop();

        SPEC = BUILDER.build();
    }

    private PrivacyConfig() {
    }
}
