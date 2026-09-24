package com.aurorion.essentials.fakename;

import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;
import java.util.UUID;

/**
 * Achar a conta a partir do nome que as pessoas usam.
 *
 * <p>Num servidor de RP quase ninguem e chamado pelo nick da Mojang: o nome que a staff ouve, le no
 * chat e ve sobre a cabeca e o nome falso. Procurar por conta ou por UUID e o caminho errado — a staff
 * nao sabe o UUID de ninguem, e muitas vezes nem o nick real.
 *
 * <p>A comparacao e a que uma pessoa faria de cabeca: <b>sem as cores</b> (o que esta em disco e o
 * texto cru com os codigos {@code &}), sem diferenca de maiuscula, e sem os espacos das pontas. Dois
 * nomes que o olho le igual sao iguais aqui.
 *
 * <p>Classe pura de proposito: recebe o mapa pronto e nao conhece servidor, disco nem comando. E o que
 * permite testar a regra de comparacao sem subir o jogo.
 */
public final class FakeNameLookup {
    private FakeNameLookup() {
    }

    /** A forma comparavel de um nome: sem cor, sem caixa, sem espaco sobrando. */
    public static String key(String name) {
        return LegacyColorCodes.parse(name).getString().trim().toLowerCase(Locale.ROOT);
    }

    /**
     * A conta de quem usa este nome falso.
     *
     * @param rawNames conta -> texto cru do nome falso, como fica em disco
     * @return a conta, ou {@code null} se ninguem usa esse nome. Com mais de uma conta no mesmo nome
     *         (so acontece com dado antigo: o comando recusa colisao entre quem esta online), vence a
     *         menor UUID, para que a resposta seja sempre a mesma.
     */
    @Nullable
    public static UUID find(Map<UUID, String> rawNames, String query) {
        String wanted = key(query);
        if (wanted.isEmpty()) return null;
        UUID found = null;
        for (Map.Entry<UUID, String> entry : rawNames.entrySet()) {
            if (!key(entry.getValue()).equals(wanted)) continue;
            if (found == null || entry.getKey().compareTo(found) < 0) found = entry.getKey();
        }
        return found;
    }

    /** Os nomes falsos legiveis (sem cor), em ordem, para sugerir no Tab. */
    public static TreeSet<String> plainNames(Map<UUID, String> rawNames) {
        TreeSet<String> names = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (String raw : rawNames.values()) {
            String plain = LegacyColorCodes.parse(raw).getString().trim();
            if (!plain.isEmpty()) names.add(plain);
        }
        return names;
    }
}
