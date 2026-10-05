package com.aurorion.essentials.streamer;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Config CLIENT, persistida em config/aurorion/essentials-streamer-client.toml. */
public final class StreamerConfig {
    public static final ModConfigSpec.EnumValue<StreamerMode> MODE;
    public static final ModConfigSpec SPEC;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.comment("Modo streamer so neste cliente; nao altera permissoes nem logs do servidor.").push("streamer");
        MODE = builder.comment(
                        "OFF = desativado; OPERATIONS = /streamer on; ALL_SYSTEM = /streamer total.",
                        "OPERATIONS oculta feedback vanilla de comandos, morte, entrada/saida, conquistas",
                        "e avisos administrativos identificados do Aurorion, preservando RP de outros mods.",
                        "ALL_SYSTEM oculta outros avisos de sistema no chat; preserva os envelopes vanilla",
                        "de fala, /me, /say, time e sussurro. RP/NPC enviado como texto literal pode sumir.",
                        "A barra de acao, tela de morte e toasts nao fazem parte do filtro de chat.")
                .defineEnum("mode", StreamerMode.OFF);
        builder.pop();
        SPEC = builder.build();
    }

    private StreamerConfig() { }
}
