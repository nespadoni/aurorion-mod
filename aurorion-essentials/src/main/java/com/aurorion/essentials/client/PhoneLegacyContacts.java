package com.aurorion.essentials.client;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

/** Recupera contatos da raiz antiga sem trocar as preferencias dos contatos mais novos. */
final class PhoneLegacyContacts {
    private PhoneLegacyContacts() {}

    static void merge(Path legacyFile, Path currentFile) throws IOException {
        Properties current = read(currentFile);
        Map<String, String> contacts = contacts(current);
        int currentCount = contacts.size();
        contacts(read(legacyFile)).forEach(contacts::putIfAbsent);
        if (contacts.size() == currentCount) return;
        current.keySet().removeIf(key -> key.toString().startsWith("contact."));
        int index = 0;
        for (String contact : contacts.values()) current.setProperty("contact." + index++, contact);
        current.setProperty("contact.count", Integer.toString(index));
        Path temporary = Files.createTempFile(currentFile.getParent(), "contacts-recovery-", ".tmp");
        try {
            try (var output = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                current.store(output, "Mattupolis Phone Contacts - recuperacao Aurorion");
            }
            try {
                Files.move(temporary, currentFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, currentFile, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
        // O arquivo legado continua intacto como copia de recuperacao, e o marker impede reimportar.
    }

    private static Properties read(Path file) throws IOException {
        Properties properties = new Properties();
        try (var input = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            properties.load(input);
        } catch (IllegalArgumentException invalid) {
            throw new IOException("Agenda invalida: " + file, invalid);
        }
        return properties;
    }

    private static Map<String, String> contacts(Properties properties) throws IOException {
        try {
            int count = Integer.parseInt(properties.getProperty("contact.count", ""));
            if (count < 0 || count > 10_000) throw new IllegalArgumentException("Quantidade de contatos invalida");
            Map<String, String> contacts = new LinkedHashMap<>();
            for (int i = 0; i < count; i++) {
                String row = properties.getProperty("contact." + i);
                if (row == null) throw new IllegalArgumentException("Contato ausente");
                String[] fields = row.split("\\|", -1);
                if (fields.length < 5) throw new IllegalArgumentException("Contato incompleto");
                String nick = new String(Base64.getDecoder().decode(fields[0]), StandardCharsets.UTF_8).trim();
                if (nick.isEmpty()) throw new IllegalArgumentException("Contato sem nome");
                contacts.putIfAbsent(nick.toLowerCase(Locale.ROOT), row);
            }
            return contacts;
        } catch (IllegalArgumentException invalid) {
            throw new IOException("Agenda antiga ou atual invalida; ambas preservadas", invalid);
        }
    }
}
