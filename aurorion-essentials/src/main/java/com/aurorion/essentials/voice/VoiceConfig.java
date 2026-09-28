package com.aurorion.essentials.voice;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Config do lado servidor, gravada em {@code config/aurorion/essentials-voice-server.toml}. Regras do
 * Simple Voice Chat que o servidor impoe; sem o Voice Chat instalado, nada aqui tem efeito.
 */
public final class VoiceConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue ONLY_OPEN_GROUPS;
    public static final ModConfigSpec.BooleanValue ALLOW_GROUP_PASSWORDS;
    public static final ModConfigSpec.DoubleValue MAX_SHOUT_DISTANCE;

    public static final ModConfigSpec SPEC;

    static {
        BUILDER.comment("Grupos e alcance de voz do Simple Voice Chat.").push("voice");

        ONLY_OPEN_GROUPS = BUILDER
                .comment("So permite grupos do tipo Aberto: quem esta perto de um membro ouve o que ele fala.",
                        "Grupo Normal ou Isolado e recusado na criacao, com aviso no chat para quem tentou.",
                        "Vale tambem para grupos criados por outros mods; as ligacoes do telefone ja sao abertas.")
                .define("onlyOpenGroups", true);

        ALLOW_GROUP_PASSWORDS = BUILDER
                .comment("false (padrao): grupo com senha tambem e recusado, mesmo sendo Aberto.")
                .define("allowGroupPasswords", false);

        MAX_SHOUT_DISTANCE = BUILDER
                .comment("Maior alcance, em blocos, que o /gritar aceita. Quem esta longe demais para o",
                        "cliente enxergar o jogador (fora da distancia de visao) pode nao ouvir mesmo dentro disto.")
                .defineInRange("maxShoutDistance", 256.0, 8.0, 1024.0);

        BUILDER.pop();

        SPEC = BUILDER.build();
    }

    private VoiceConfig() {
    }
}
