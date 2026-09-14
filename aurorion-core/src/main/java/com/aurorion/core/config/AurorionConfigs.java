package com.aurorion.core.config;

import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.IConfigSpec;
import net.neoforged.fml.config.ModConfig;
import org.jetbrains.annotations.Nullable;

/**
 * Registra config do ecossistema numa pasta so: {@code config/aurorion/}.
 *
 * <h2>Por que</h2>
 *
 * <p>Com dez mods, o {@code config/} de um servidor de 289 mods ganhava dez arquivos
 * {@code aurorion_*.toml} espalhados entre os dos outros. Juntando numa pasta, achar a config do
 * Aurorion deixa de ser uma busca — e fica obvio o que e nosso e o que nao e.
 *
 * <h2>O nome encurta</h2>
 *
 * <p>{@code config/aurorion_limbo-server.toml} vira {@code config/aurorion/limbo-server.toml}. O
 * prefixo {@code aurorion_} sai porque a pasta ja diz isso; repetir seria
 * {@code aurorion/aurorion_limbo-server.toml}.
 *
 * <h2>Por que isto e do core</h2>
 *
 * <p>Onze chamadas de {@code registerConfig} tomavam a mesma decisao de nome, e nenhuma sabia da
 * outra. Se cada mod montasse o caminho a mao, bastaria um esquecer a pasta para a convencao virar
 * mentira. E o criterio estreito do core aplicado direito: <b>ja estava duplicado</b>.
 *
 * <p>O NeoForge cria o diretorio do arquivo sozinho ({@code ConfigTracker} chama
 * {@code createDirectories} no {@code getParent()} do caminho), entao nao ha nada a preparar.
 */
public final class AurorionConfigs {
    /** A pasta, dentro de {@code config/}. */
    public static final String FOLDER = "aurorion";

    private static final String PREFIX = "aurorion_";

    private AurorionConfigs() {
    }

    /** O caso comum: um arquivo por tipo, por mod. */
    public static void register(ModContainer container, ModConfig.Type type, IConfigSpec spec) {
        register(container, type, spec, null);
    }

    /**
     * Para quando um mod tem mais de uma config do mesmo tipo.
     *
     * @param variant sufixo que separa os arquivos, como {@code "privacy"} no
     *                {@code aurorion-essentials}. {@code null} para o arquivo principal.
     */
    public static void register(ModContainer container, ModConfig.Type type, IConfigSpec spec,
                                @Nullable String variant) {
        container.registerConfig(type, spec, fileName(container.getModId(), type, variant));
    }

    /**
     * Visivel para teste: e a unica regra desta classe, e a que quebraria em silencio.
     *
     * <p>Um mod fora do padrao de nome (sem o prefixo) mantem o id inteiro, em vez de ficar sem nome.
     */
    public static String fileName(String modId, ModConfig.Type type, @Nullable String variant) {
        String shortName = modId.startsWith(PREFIX) ? modId.substring(PREFIX.length()) : modId;
        String suffix = variant == null || variant.isBlank() ? "" : "-" + variant;

        return FOLDER + "/" + shortName + suffix + "-" + type.extension() + ".toml";
    }
}
