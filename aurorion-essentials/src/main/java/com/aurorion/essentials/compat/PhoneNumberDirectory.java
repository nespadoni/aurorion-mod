package com.aurorion.essentials.compat;

import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * As regras do diretorio de numeros do telefone Mattupolis, copiadas do
 * {@code PhoneNumberServerStore} (1.3.4) para que o {@code PhoneNumberStoreMixin} possa decidir sem
 * chamar o codigo original.
 *
 * <h2>Por que isto existe</h2>
 *
 * <p>O crash de 21:58:33 foi o Watchdog matando o servidor com a thread principal parada dentro de
 * {@code PhoneNumberServerStore.save()}, abrindo {@code phone_numbers.properties} para escrita. O
 * arquivo e pequeno; o problema e quantas vezes ele e reescrito. A cada login o telefone chama
 * {@code syncToPlayer} para cada jogador online, e cada {@code syncToPlayer} chama
 * {@code ensureNumberFor} duas vezes por jogador online — e {@code ensureNumberFor} grava o arquivo
 * inteiro <b>mesmo quando nada mudou</b>. Sao 2·N² gravacoes sincronas por login: 882 com 21 pessoas,
 * 12.800 com 80. Basta o disco do container demorar um pouco num {@code open()} para o tick travar.
 *
 * <p>Classe pura de proposito: nao conhece servidor nem disco, entao da para testar sem subir o jogo.
 */
public final class PhoneNumberDirectory {
    /** O telefone corta nomes em 16 caracteres, o limite de nick da Mojang. */
    private static final int MAX_NAME = 16;
    private static final int NUMBER_DIGITS = 6;

    private PhoneNumberDirectory() {
    }

    /**
     * O numero que {@code ensureNumberFor} devolveria sem mudar nada, ou {@code null} quando a chamada
     * original precisa rodar (numero novo, nome trocado, numero tomado por outra conta).
     *
     * <p>Quando devolve um numero, o original teria feito exatamente isto: regravado o mesmo nome, o
     * mesmo dono do numero e salvo o arquivo com o mesmo conteudo. Pular e so economia.
     */
    @Nullable
    public static String unchangedNumber(Map<UUID, String> uuidToNumber, Map<String, UUID> numberToUuid,
                                         Map<UUID, String> uuidToName, UUID account, String profileName) {
        String number = uuidToNumber.get(account);
        if (number == null || cleanNumber(number).length() != NUMBER_DIGITS) return null;
        if (!account.equals(numberToUuid.get(number))) return null;
        if (!cleanName(profileName).equals(uuidToName.get(account))) return null;
        return number;
    }

    /**
     * As linhas {@code entry.N} do arquivo, na mesma codificacao do telefone
     * ({@code uuid|base64(nome)|numero}). Ordenadas por conta, para que duas fotos do mesmo conteudo
     * sejam iguais e a gravacao repetida possa ser descartada.
     */
    public static List<String> entries(Map<UUID, String> uuidToNumber, Map<UUID, String> uuidToName) {
        List<Map.Entry<UUID, String>> sorted = new ArrayList<>(uuidToNumber.entrySet());
        sorted.sort(Map.Entry.comparingByKey());
        List<String> lines = new ArrayList<>(sorted.size());
        for (Map.Entry<UUID, String> entry : sorted) {
            String number = cleanNumber(entry.getValue());
            if (number.length() != NUMBER_DIGITS) continue;
            String name = uuidToName.getOrDefault(entry.getKey(), "");
            lines.add(entry.getKey() + "|" + encode(name) + "|" + number);
        }
        return lines;
    }

    /** Igual ao {@code cleanPlayerName} do telefone. */
    public static String cleanName(@Nullable String input) {
        if (input == null) return "";
        String cleaned = input.trim();
        return cleaned.length() > MAX_NAME ? cleaned.substring(0, MAX_NAME) : cleaned;
    }

    /** Igual ao {@code cleanPhoneNumber} do telefone: so digitos, no maximo seis. */
    public static String cleanNumber(@Nullable String input) {
        if (input == null) return "";
        StringBuilder digits = new StringBuilder(NUMBER_DIGITS);
        for (int i = 0; i < input.length() && digits.length() < NUMBER_DIGITS; i++) {
            char c = input.charAt(i);
            if (c >= '0' && c <= '9') digits.append(c);
        }
        return digits.toString();
    }

    private static String encode(@Nullable String raw) {
        return Base64.getEncoder().encodeToString((raw == null ? "" : raw).getBytes(StandardCharsets.UTF_8));
    }
}
