package com.aurorion.essentials.privacy;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Config do lado servidor, gravada em {@code config/aurorion/essentials-privacy.toml}. Controla
 * quem ve os avisos automaticos do jogo, para evitar meta-gaming (saber quem esta online, ver
 * conquistas alheias, acompanhar mortes) e para manter o chat limpo num servidor de 80 pessoas.
 *
 * <p><b>Mudanca de formato:</b> os antigos {@code hideJoinLeaveMessages} e
 * {@code hideAdvancementMessages} eram booleanos e viraram {@link Visibility}, porque "esconder"
 * tinha dois significados diferentes na pratica — sumir para todo mundo, ou sumir so para quem nao
 * modera. O NeoForge nao migra valor de chave que mudou de tipo: ao subir a primeira vez ele
 * reescreve o arquivo com os padroes abaixo e registra a correcao no log. Os padroes ja sao o
 * comportamento desejado, entao so precisa reeditar quem tinha mudado os valores de proposito.
 */
public final class PrivacyConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.EnumValue<Visibility> JOIN_LEAVE_MESSAGES;
    public static final ModConfigSpec.EnumValue<Visibility> ADVANCEMENT_MESSAGES;
    public static final ModConfigSpec.EnumValue<Visibility> DEATH_MESSAGES;
    public static final ModConfigSpec.BooleanValue RESTRICT_PRIVATE_MESSAGES;

    public static final ModConfigSpec SPEC;

    static {
        BUILDER.comment(
                "Quem ve cada aviso automatico do jogo.",
                "EVERYONE = todo mundo (padrao do Minecraft) | ADMINS = so OP nivel 2+ | NOBODY = ninguem."
        ).push("privacy");

        JOIN_LEAVE_MESSAGES = BUILDER
                .comment("\"Fulano entrou/saiu do jogo\". NOBODY esconde inclusive de operadores.")
                .defineEnum("joinLeaveMessages", Visibility.NOBODY);

        ADVANCEMENT_MESSAGES = BUILDER
                .comment("\"Fulano completou a conquista...\". Nao afeta o aviso no canto da tela de quem",
                        "desbloqueou: esse e so dele e continua aparecendo.")
                .defineEnum("advancementMessages", Visibility.NOBODY);

        DEATH_MESSAGES = BUILDER
                .comment("\"Fulano foi morto por...\". ADMINS mantem o registro para quem modera sem",
                        "encher o chat de todo mundo. Quem morreu continua vendo a causa na tela de morte,",
                        "seja qual for o valor aqui.")
                .defineEnum("deathMessages", Visibility.ADMINS);

        RESTRICT_PRIVATE_MESSAGES = BUILDER
                .comment("Restringe /msg, /tell e /w para uso exclusivo de operadores.")
                .define("restrictPrivateMessages", true);

        BUILDER.pop();

        SPEC = BUILDER.build();
    }

    private PrivacyConfig() {
    }
}
