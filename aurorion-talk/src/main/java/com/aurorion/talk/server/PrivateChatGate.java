package com.aurorion.talk.server;

import com.aurorion.core.config.DerivedConfig;
import com.aurorion.talk.config.TalkServerConfig;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Este jogador pode mandar este comando de mensagem privada?
 *
 * <p>Toda a regra do sussurro esta aqui, e nao no listener, por um motivo pratico: o listener so
 * sabe ligar coisas do NeoForge (evento, jogador, cancelamento) e nao da para testar sem um
 * servidor; isto aqui e config e texto, e um teste comum cobre a parte que erraria em silencio —
 * {@code /W}, {@code /w} e {@code "/w "} digitados no TOML sao o mesmo comando.
 */
public final class PrivateChatGate {
    /**
     * A lista do TOML vira {@code Set} uma vez por edicao do arquivo, nao uma vez por comando.
     *
     * <p>Comando de jogador nao e caminho quente — mas o {@link DerivedConfig} tambem evita o cache
     * que envelhece: editar a lista com o servidor no ar vale na proxima leitura, sem listener de
     * config para alguem esquecer de ligar.
     */
    private static final DerivedConfig<List<? extends String>, Set<String>> COMMANDS =
            new DerivedConfig<>(PrivateChatGate::rawCommands, PrivateChatGate::parse);

    private PrivateChatGate() {
    }

    /**
     * Metodo, e nao {@code TalkServerConfig.PRIVATE_MESSAGE_COMMANDS::get} direto no campo: a
     * referencia ao campo leria a config na carga <b>desta</b> classe, e o teste de
     * {@link #parse} passaria a depender de um spec construido. Mesma razao do
     * {@code WorldCatalog}.
     */
    private static List<? extends String> rawCommands() {
        return TalkServerConfig.PRIVATE_MESSAGE_COMMANDS.get();
    }

    /**
     * @param rootLiteral a primeira palavra do comando, como o jogador digitou. Em {@code /w Ana oi}
     *                    e {@code "w"} — o apelido, nao o {@code msg} para onde o vanilla redireciona;
     *                    por isso a lista de config cita os tres nomes.
     */
    public static boolean denies(ServerPlayer player, String rootLiteral) {
        if (!TalkServerConfig.BLOCK_PRIVATE_MESSAGES.get()) return false;
        if (player.hasPermissions(TalkServerConfig.BYPASS_PERMISSION_LEVEL.get())) return false;

        return COMMANDS.get().contains(normalize(rootLiteral));
    }

    /** Visivel para teste: e a unica regra desta classe. */
    static Set<String> parse(List<? extends String> raw) {
        Set<String> roots = new HashSet<>(raw.size());

        for (String entry : raw) {
            if (entry == null) continue;

            String root = normalize(entry);
            if (!root.isEmpty()) roots.add(root);
        }

        return roots;
    }

    /**
     * A barra nao faz parte do nome do comando, mas e assim que a pessoa que edita o TOML pensa
     * nele. Aceitar {@code "/w"} e {@code "w"} evita uma lista que nao barra nada sem dizer por que.
     */
    static String normalize(String literal) {
        String trimmed = literal.trim();
        int start = 0;
        while (start < trimmed.length() && trimmed.charAt(start) == '/') start++;

        return trimmed.substring(start).toLowerCase(Locale.ROOT);
    }
}
