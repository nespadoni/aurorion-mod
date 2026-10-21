package com.aurorion.essentials.compat;

import com.aurorion.essentials.AurorionEssentials;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.BufferedReader;
import java.lang.reflect.Field;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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
    /**
     * Teto para o {@code .count} lido do arquivo. O valor vem do disco e o laco e por indice: um
     * contador corrompido (ou editado) com dois bilhoes travava a thread do servidor no reset. O
     * telefone grava listas densas de algumas centenas de linhas; um milhao de consultas leva
     * milissegundos.
     */
    static final int MAX_INDEXED = 1_000_000;
    private static final DateTimeFormatter AUDIT_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private MattupolisPhoneCompat() {
    }

    public static void resetCharacterData(MinecraftServer server, UUID account) throws IOException {
        resetCharacterData(server, account, null);
    }

    /**
     * @param accountName o nick, para a marca no log de auditoria do banco; {@code null} usa a UUID
     */
    public static void resetCharacterData(MinecraftServer server, UUID account, String accountName) throws IOException {
        Path dir = phoneDir(server);
        resetNumberDirectory(dir.resolve(PHONE_NUMBER_STORE), account);
        removePropertiesForAccount(dir.resolve(BANK_HISTORY_STORE), account);
        markResetInAudit(dir.resolve("bank_audit.log"), account, accountName);
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
        int count = boundedCount(props.getProperty("entry.count"));
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
        int count = boundedCount(props.getProperty("entry.count"));
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

    /**
     * Tira do arquivo as linhas <b>da conta</b>: as que tem a UUID dela como primeiro campo (o dono da
     * linha, no formato do telefone) ou como chave.
     *
     * <p>Antes bastava a UUID aparecer em qualquer lugar do valor. O extrato guarda a observacao do
     * PIX, texto livre de outro jogador: quem escrevesse a UUID de alguem na observacao fazia a
     * propria linha de extrato — de outra conta — sumir no reset dessa pessoa.
     */
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
            int count = boundedCount(original.getProperty(family + "count"));
            for (int i = 0; i < count; i++) {
                String value = original.getProperty(family + i);
                if (value == null || ownedBy(value, needle)) continue;
                kept.setProperty(family + keptIndex++, value);
            }
            kept.setProperty(family + "count", String.valueOf(keptIndex));
        }

        // Sobrou o que nao pertence a nenhuma lista indexada (e as linhas soltas que passaram do
        // contador, que morrem aqui de proposito: sao restos de uma gravacao anterior).
        for (String key : original.stringPropertyNames()) {
            if (isIndexed(key, families)) continue;
            String value = original.getProperty(key);
            if (!key.contains(needle) && (value == null || !ownedBy(value, needle))) {
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

    /** O primeiro campo ({@code uuid|...}) e a conta dona da linha. */
    static boolean ownedBy(String value, String account) {
        return value.equals(account) || value.startsWith(account + "|");
    }

    /**
     * O log de auditoria do banco nao e apagado: cada linha e uma transferencia entre <b>duas</b>
     * pessoas, e o lado de quem continua vivo (e a investigacao da staff sobre golpe) nao pode sumir
     * porque o outro lado trocou de personagem. Em vez disso o reset deixa uma marca no fim, no
     * formato que a tela de auditoria do telefone ja le ({@code data | de -> para | valor | nota}): o
     * que vem antes dela e do personagem anterior.
     *
     * <p>Append, como o proprio telefone grava: uma linha so, sem reescrever o arquivo.
     */
    static void markResetInAudit(Path file, UUID account, String accountName) throws IOException {
        if (!Files.exists(file)) return;
        String who = accountName == null || accountName.isBlank() ? account.toString() : accountName;
        String line = LocalDateTime.now().format(AUDIT_TIME) + " | " + who + " -> [reset de personagem] | 0 | "
                + "aurorion: personagem anterior encerrado (" + account + ")" + System.lineSeparator();
        Files.writeString(file, line, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
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

    /**
     * Grava num temporario e move por cima. O reset reescreve o arquivo inteiro do banco e o de
     * numeros de <b>todo mundo</b>: um crash no meio da escrita direta deixava o arquivo truncado e o
     * extrato e os numeros do servidor inteiro perdidos. O nome do temporario e proprio — o
     * {@code PhoneNumberSaveQueue} usa {@code .tmp} no mesmo diretorio.
     */
    private static void store(Path file, Properties props, String comment) throws IOException {
        Files.createDirectories(file.getParent());
        Path temporary = file.resolveSibling(file.getFileName() + ".reset-tmp");
        try (BufferedWriter writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
            props.store(writer, comment);
        }
        try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
            channel.force(true);
        }
        try {
            Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static Path phoneDir(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve("mattupolis_phone");
    }

    /** O {@code .count} do arquivo, entre 0 e {@link #MAX_INDEXED}. */
    static int boundedCount(String value) {
        return Math.max(0, Math.min(parseInt(value, 0), MAX_INDEXED));
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
