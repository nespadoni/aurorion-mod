package com.aurorion.essentials.compat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.UUID;

/**
 * Regressao do reset de personagem sobre os arquivos do telefone.
 *
 * <p>Nao sobe Minecraft: o {@code RunPhoneResetTests} compila esta classe junto com o
 * {@code MattupolisPhoneCompat} contra stubs e chama o metodo por reflexao.</p>
 */
public class MattupolisPhoneCompatDataTest {
    private static final UUID ACCOUNT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID OTHER = UUID.fromString("22222222-2222-2222-2222-222222222222");

    public static void main(String[] args) throws Exception {
        dailyRowsAreCompacted();
        everyIndexedListIsCompactedAtOnce();
        unindexedKeysSurviveAndStaleRowsDoNot();
        System.out.println("PASS: reset do telefone (3 casos)");
    }

    /** O teto diario de transferencia vive numa lista propria, separada do extrato. */
    private static void dailyRowsAreCompacted() throws Exception {
        Properties bank = new Properties();
        bank.setProperty("daily.count", "2");
        bank.setProperty("daily.0", ACCOUNT + "|2026-09-16|100");
        bank.setProperty("daily.1", OTHER + "|2026-09-16|200");

        Properties actual = reset(bank);
        expect(actual, "daily.count", "1");
        expect(actual, "daily.0", OTHER + "|2026-09-16|200");
    }

    /**
     * O {@code bank_history.properties} real tem tres listas no mesmo arquivo. Compactar so a
     * primeira deixava as outras com o contador mentindo sobre um indice ja apagado.
     */
    private static void everyIndexedListIsCompactedAtOnce() throws Exception {
        Properties bank = new Properties();
        bank.setProperty("entry.count", "3");
        bank.setProperty("entry.0", ACCOUNT + "|tx1|out|Zm9v|MTA=|bm90ZQ==|1700000000000");
        bank.setProperty("entry.1", OTHER + "|tx2|in|YmFy|MjA=|bm90ZQ==|1700000001000");
        bank.setProperty("entry.2", ACCOUNT + "|tx3|out|Zm9v|MzA=|bm90ZQ==|1700000002000");
        bank.setProperty("daily.count", "2");
        bank.setProperty("daily.0", ACCOUNT + "|2026-09-16|100");
        bank.setProperty("daily.1", OTHER + "|2026-09-16|200");
        bank.setProperty("account.count", "2");
        bank.setProperty("account.0", ACCOUNT + "|Rm9v");
        bank.setProperty("account.1", OTHER + "|QmFy");

        Properties actual = reset(bank);
        expect(actual, "entry.count", "1");
        expect(actual, "entry.0", OTHER + "|tx2|in|YmFy|MjA=|bm90ZQ==|1700000001000");
        expect(actual, "daily.count", "1");
        expect(actual, "daily.0", OTHER + "|2026-09-16|200");
        expect(actual, "account.count", "1");
        expect(actual, "account.0", OTHER + "|QmFy");
    }

    /**
     * Chave que nao pertence a lista nenhuma continua no arquivo; linha indexada alem do contador e
     * resto de uma gravacao anterior e nao deve reaparecer depois da compactacao.
     */
    private static void unindexedKeysSurviveAndStaleRowsDoNot() throws Exception {
        Properties bank = new Properties();
        bank.setProperty("entry.count", "1");
        bank.setProperty("entry.0", OTHER + "|tx1|in|YmFy|MTA=|bm90ZQ==|1700000000000");
        bank.setProperty("entry.7", ACCOUNT + "|stale|out|Zm9v|OTk=|bm90ZQ==|1600000000000");
        bank.setProperty("schemaVersion", "3");

        Properties actual = reset(bank);
        expect(actual, "entry.count", "1");
        expect(actual, "entry.0", OTHER + "|tx1|in|YmFy|MTA=|bm90ZQ==|1700000000000");
        expect(actual, "schemaVersion", "3");
        if (actual.getProperty("entry.7") != null) {
            throw new AssertionError("Linha alem do contador deveria sumir: " + actual);
        }
    }

    private static Properties reset(Properties fixture) throws Exception {
        Path file = Files.createTempFile("aurorion-bank-regression-", ".properties");
        try {
            try (var writer = Files.newBufferedWriter(file)) { fixture.store(writer, "Regression fixture"); }
            var method = MattupolisPhoneCompat.class.getDeclaredMethod("removePropertiesForAccount", Path.class, UUID.class);
            method.setAccessible(true);
            method.invoke(null, file, ACCOUNT);
            Properties actual = new Properties();
            try (var reader = Files.newBufferedReader(file)) { actual.load(reader); }
            return actual;
        } finally {
            Files.delete(file);
        }
    }

    private static void expect(Properties actual, String key, String value) {
        if (!value.equals(actual.getProperty(key))) {
            throw new AssertionError(key + " deveria ser '" + value + "', veio '"
                    + actual.getProperty(key) + "' em " + actual);
        }
    }
}
