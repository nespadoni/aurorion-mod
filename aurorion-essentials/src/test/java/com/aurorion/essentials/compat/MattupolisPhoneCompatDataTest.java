package com.aurorion.essentials.compat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.UUID;

public class MattupolisPhoneCompatDataTest {
    public static void main(String[] args) throws Exception {
        UUID account = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID other = UUID.fromString("22222222-2222-2222-2222-222222222222");
        Path file = Files.createTempFile("aurorion-bank-regression-", ".properties");
        try {
            Properties bank = new Properties();
            bank.setProperty("daily.count", "2");
            bank.setProperty("daily.0", account + "|2026-09-16|100");
            bank.setProperty("daily.1", other + "|2026-09-16|200");
            try (var writer = Files.newBufferedWriter(file)) { bank.store(writer, "Regression fixture"); }
            var reset = MattupolisPhoneCompat.class.getDeclaredMethod("removePropertiesForAccount", Path.class, UUID.class);
            reset.setAccessible(true);
            reset.invoke(null, file, account);
            Properties actual = new Properties();
            try (var reader = Files.newBufferedReader(file)) { actual.load(reader); }
            if (!"1".equals(actual.getProperty("daily.count"))
                    || !(other + "|2026-09-16|200").equals(actual.getProperty("daily.0"))) {
                throw new AssertionError("Daily rows must be compacted: " + actual);
            }
        } finally {
            Files.delete(file);
        }
    }
}
