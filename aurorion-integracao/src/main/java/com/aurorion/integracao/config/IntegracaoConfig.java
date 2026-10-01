package com.aurorion.integracao.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Para onde os fatos do jogo vao, e com que credencial.
 *
 * <p>Registrada como {@code STARTUP} ({@code config/aurorion/integracao-startup.toml}) e
 * <b>nunca</b> como {@code SERVER}: o NeoForge envia o arquivo inteiro de cada config SERVER a todo
 * cliente que conecta ({@code ConfigSync}), e o token iria junto para a maquina de cada jogador.
 * STARTUP fica no servidor. A contrapartida: mudar o valor pede reiniciar o servidor.
 *
 * <p>E config de implantacao, ajustada uma vez por servidor — nao regra de jogo nem algo que a staff
 * mexa no dia a dia, por isso nao tem comando nem tela.
 */
public final class IntegracaoConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue ENABLED;
    public static final ModConfigSpec.ConfigValue<String> URL;
    public static final ModConfigSpec.ConfigValue<String> TOKEN;
    public static final ModConfigSpec.IntValue SPOOL_MAX_MIB;

    public static final ModConfigSpec SPEC;

    static {
        BUILDER.comment("Envio de acontecimentos do jogo (mortes, Limbo, portais, casas) ao site.").push("integracao");

        ENABLED = BUILDER
                .comment("Liga o envio. Desligado, os fatos nem sao montados: custo zero no jogo.")
                .define("habilitado", false);

        URL = BUILDER
                .comment(
                        "Endereco base da integracao no backend. Ex.:",
                        "https://aurorionstudios.cloud/api/v1/integration/v1",
                        "Na mesma rede Docker do backend, pode ser http://backend:8080/api/v1/integration/v1.")
                .define("url", "");

        TOKEN = BUILDER
                .comment(
                        "Credencial do servidor do jogo: o mesmo valor de GAME_API_TOKEN no backend.",
                        "Nao e o token do bot. Nunca compartilhe este arquivo.")
                .define("token", "");

        SPOOL_MAX_MIB = BUILDER
                .comment(
                        "Teto do arquivo de pendencias (<mundo>/aurorion_integracao/pendentes.jsonl) enquanto o",
                        "site estiver fora do ar. Um fato tem poucas centenas de bytes: 8 MiB guardam dezenas de",
                        "milhares. Acima disso, fatos novos sao descartados com aviso no log.")
                .defineInRange("spoolMaxMiB", 8, 1, 64);

        BUILDER.pop();
        SPEC = BUILDER.build();
    }

    private IntegracaoConfig() {
    }
}
