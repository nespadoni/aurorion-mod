package com.aurorion.essentials.compat;

import com.aurorion.essentials.AurorionEssentials;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

/**
 * Integra o telefone MikasRevs/Mattupolis sem torna-lo dependencia obrigatoria.
 *
 * <p>O mod grava estado por UUID fora de {@code playerdata}, entao o reset generico do personagem
 * nao consegue limpar telefone, contatos e historico bancario sozinho. Esta classe mexe apenas nas
 * entradas da conta afetada e deixa o resto do servidor intacto.</p>
 *
 * <h2>Por que o nome do personagem nao e gravado aqui</h2>
 *
 * <p>Seria natural trocar o nick pelo nome do personagem no diretorio do telefone e acabou. Nao da:
 * o telefone <b>roteia por nome</b>. {@code ServerboundPhoneTextPacket}, {@code ...CallStartPacket}
 * e {@code ...BankTransferPacket} carregam um {@code targetPlayerName} que o servidor resolve com
 * {@link net.minecraft.server.players.PlayerList#getPlayerByName(String)} — que compara com o nick
 * da conta Mojang, nao com o que a tela mostra. Gravar "Arthur Pendragon" no diretorio faria o
 * cliente mandar esse texto de volta, e ligacao, mensagem e PIX passariam a resolver para
 * {@code null}.</p>
 *
 * <p>Por isso a troca de nome e so de exibicao e acontece no cliente, no ultimo instante antes do
 * desenho: {@code MattupolisPhoneNames} e os mixins {@code Phone*NameMixin}. O campo que o telefone
 * guarda e envia continua sendo o nick real, e o roteamento continua funcionando.</p>
 */
public final class MattupolisPhoneCompat {
    private static final String PHONE_NUMBER_STORE = "phone_numbers.properties";
    private static final String BANK_HISTORY_STORE = "bank_history.properties";
    /** Sufixo que o telefone usa para o tamanho de cada lista indexada gravada num .properties. */
    private static final String COUNT_KEY = ".count";

    private MattupolisPhoneCompat() {
    }

    public static void resetCharacterData(MinecraftServer server, UUID account) throws IOException {
        Path dir = phoneDir(server);
        resetNumberDirectory(dir.resolve(PHONE_NUMBER_STORE), account);
        removePropertiesForAccount(dir.resolve(BANK_HISTORY_STORE), account);
        removeAuditLinesForAccount(dir.resolve("bank_audit.log"), account);
        forgetLoadedRuntimeState();
    }

    /**
     * Tira a conta do diretorio de numeros e aposenta o numero dela. Reserva antes de apagar: se o
     * reset cair entre um passo e outro, a repeticao ainda acha o numero da conta, e a reserva nao
     * duplica.
     */
    private static void resetNumberDirectory(Path numbers, UUID account) throws IOException {
        reserveNumbers(numbers, numbersOf(numbers, account));
        removePropertiesForAccount(numbers, account);
    }

    /**
     * Os numeros que a conta tinha. Linha do diretorio: {@code uuid|base64(nome)|numero}.
     */
    private static List<String> numbersOf(Path file, UUID account) throws IOException {
        List<String> numbers = new ArrayList<>();
        if (!Files.exists(file)) return numbers;
        Properties props = load(file);
        String owner = account.toString();
        int count = parseInt(props.getProperty("entry.count"), 0);
        for (int i = 0; i < count; i++) {
            String value = props.getProperty("entry." + i);
            if (value == null) continue;
            String[] parts = value.split("\\|", -1);
            if (parts.length >= 3 && parts[0].equals(owner) && parts[2].matches("\\d{6}")) numbers.add(parts[2]);
        }
        return numbers;
    }

    /**
     * Aposenta os numeros do personagem anterior: cada um fica no diretorio em nome de uma conta que
     * nao existe, sem nome.
     *
     * <p>Sem isto o personagem novo recebia o <b>mesmo</b> numero: o telefone gera o numero a partir
     * da UUID da conta, tentando sempre o mesmo primeiro, e o reset so o deixava livre. Quem ligasse
     * para o numero do morto caia no personagem novo. Com a reserva, o gerador pula para o proximo, e
     * discar o numero antigo responde "Number owner not found." — o numero morreu com o personagem.
     *
     * <p>Um numero por reset, de um espaco de 900.000: nao falta.
     */
    private static void reserveNumbers(Path file, List<String> numbers) throws IOException {
        if (numbers.isEmpty() || !Files.exists(file)) return;
        Properties props = load(file);
        int count = parseInt(props.getProperty("entry.count"), 0);
        boolean changed = false;
        for (String number : numbers) {
            String reserved = retiredNumberOwner(number) + "||" + number;
            boolean already = false;
            for (int i = 0; i < count && !already; i++) {
                already = reserved.equals(props.getProperty("entry." + i));
            }
            if (already) continue;
            props.setProperty("entry." + count++, reserved);
            changed = true;
        }
        if (!changed) return;
        props.setProperty("entry.count", String.valueOf(count));
        store(file, props, "Aurorion character reset");
    }

    /** A "conta" dona de um numero aposentado: fixa por numero, e nunca a de um jogador. */
    static UUID retiredNumberOwner(String number) {
        return UUID.nameUUIDFromBytes(("aurorion-retired-phone-number:" + number).getBytes(StandardCharsets.UTF_8));
    }

    private static void removePropertiesForAccount(Path file, UUID account) throws IOException {
        if (!Files.exists(file)) return;

        String needle = account.toString();
        Properties original = load(file);
        Properties kept = new Properties();

        List<String> families = new ArrayList<>();
        for (String key : original.stringPropertyNames()) {
            if (key.endsWith(COUNT_KEY) && key.length() > COUNT_KEY.length()) {
                families.add(key.substring(0, key.length() - "count".length()));
            }
        }

        for (String family : families) {
            int keptIndex = 0;
            int count = parseInt(original.getProperty(family + "count"), 0);
            for (int i = 0; i < count; i++) {
                String value = original.getProperty(family + i);
                if (value == null || value.contains(needle)) continue;
                kept.setProperty(family + keptIndex++, value);
            }
            kept.setProperty(family + "count", String.valueOf(keptIndex));
        }

        // Sobrou o que nao pertence a nenhuma lista indexada (e as linhas soltas que passaram do
        // contador, que morrem aqui de proposito: sao restos de uma gravacao anterior).
        for (String key : original.stringPropertyNames()) {
            if (isIndexed(key, families)) continue;
            String value = original.getProperty(key);
            if (!key.contains(needle) && (value == null || !value.contains(needle))) {
                kept.setProperty(key, value);
            }
        }

        store(file, kept, "Aurorion character reset");
    }

    /** Verdadeiro para {@code <familia>.count} e para {@code <familia>.<numero>}. */
    private static boolean isIndexed(String key, List<String> families) {
        for (String family : families) {
            if (!key.startsWith(family)) continue;
            String suffix = key.substring(family.length());
            if (suffix.equals("count")) return true;
            if (suffix.isEmpty()) continue;
            boolean digits = true;
            for (int i = 0; i < suffix.length(); i++) {
                if (!Character.isDigit(suffix.charAt(i))) digits = false;
            }
            if (digits) return true;
        }
        return false;
    }

    private static void removeAuditLinesForAccount(Path file, UUID account) throws IOException {
        if (!Files.exists(file)) return;

        String needle = account.toString();
        List<String> kept = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.contains(needle)) kept.add(line);
            }
        }

        try (BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            for (String line : kept) {
                writer.write(line);
                writer.newLine();
            }
        }
    }

    /** Faz o telefone reler numeros e banco do disco na proxima vez que precisar deles. */
    public static void forgetLoadedRuntimeState() {
        resetLoadedWorldKey("com.mattupolis.phone.server.contacts.PhoneNumberServerStore");
        resetLoadedWorldKey("com.mattupolis.phone.server.bank.PhoneBankServerStore");
    }

    private static void resetLoadedWorldKey(String className) {
        try {
            Class<?> store = Class.forName(className);
            Field loadedWorldKey = store.getDeclaredField("loadedWorldKey");
            loadedWorldKey.setAccessible(true);
            loadedWorldKey.set(null, "");
        } catch (ClassNotFoundException ignored) {
            // O telefone e opcional.
        } catch (ReflectiveOperationException e) {
            AurorionEssentials.LOGGER.warn("Nao foi possivel invalidar cache de {}.", className, e);
        }
    }

    private static Properties load(Path file) throws IOException {
        Properties props = new Properties();
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            props.load(reader);
        }
        return props;
    }

    private static void store(Path file, Properties props, String comment) throws IOException {
        Files.createDirectories(file.getParent());
        try (BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            props.store(writer, comment);
        }
    }

    private static Path phoneDir(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve("mattupolis_phone");
    }

    private static int parseInt(String value, int fallback) {
        if (value == null || value.isBlank()) return fallback;
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

}
