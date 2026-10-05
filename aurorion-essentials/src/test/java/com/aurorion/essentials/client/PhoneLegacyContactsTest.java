package com.aurorion.essentials.client;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Properties;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PhoneLegacyContactsTest {
    @TempDir Path game;

    @Test
    void existingMainAgendaGetsOnlyMissingLegacyContactsAndKeepsCurrentFlags() throws Exception {
        Path root = Files.createDirectories(game.resolve(PhoneCharacterStorage.ROOT));
        Path main = Files.createDirectories(PhoneCharacterStorage.characterDir(game, UUID.randomUUID()));
        Path alt = Files.createDirectories(PhoneCharacterStorage.characterDir(game, UUID.randomUUID()));
        String current = contact("OtherAccount", "100|true|false|true");
        String duplicate = contact("otheraccount", "200|false|true|false");
        String missing = contact("MissingAccount", "300|false|false|false");
        write(root.resolve("contacts.properties"), duplicate, missing);
        write(main.resolve("contacts.properties"), current);
        write(alt.resolve("contacts.properties"), contact("AltContact", "400|false|false|false"));
        byte[] originalLegacy = Files.readAllBytes(root.resolve("contacts.properties"));
        byte[] originalAlt = Files.readAllBytes(alt.resolve("contacts.properties"));

        PhoneCharacterStorage.migrateLegacy(game, main);

        Properties merged = read(main.resolve("contacts.properties"));
        assertEquals("2", merged.getProperty("contact.count"));
        assertEquals(current, merged.getProperty("contact.0"));
        assertEquals(missing, merged.getProperty("contact.1"));
        assertArrayEquals(originalLegacy, Files.readAllBytes(root.resolve("contacts.properties")));
        assertArrayEquals(originalAlt, Files.readAllBytes(alt.resolve("contacts.properties")));
        assertTrue(Files.exists(main.resolve(PhoneCharacterStorage.MIGRATED_MARKER)));
    }

    @Test
    void alreadyResetCharactersNeverImportContactsFromTheOldRoot() throws Exception {
        Path root = Files.createDirectories(game.resolve(PhoneCharacterStorage.ROOT));
        Path main = Files.createDirectories(PhoneCharacterStorage.characterDir(game, UUID.randomUUID()));
        write(root.resolve("contacts.properties"), contact("DeadCharacter", "1|false|false|false"));
        write(main.resolve("contacts.properties"), contact("NewCharacter", "2|false|false|false"));
        Files.writeString(main.resolve("aurorion_retired_contacts.txt"), UUID.randomUUID().toString());
        byte[] current = Files.readAllBytes(main.resolve("contacts.properties"));

        PhoneCharacterStorage.migrateLegacy(game, main);

        assertArrayEquals(current, Files.readAllBytes(main.resolve("contacts.properties")));
        assertTrue(Files.exists(root.resolve("contacts.properties")));
    }

    @Test
    void invalidAgendaNeverOverwritesAValidCurrentAgenda() throws Exception {
        Path legacy = game.resolve("old.properties");
        Path current = game.resolve("current.properties");
        Files.writeString(legacy, "contact.count=1\ncontact.0=incomplete");
        write(current, contact("CurrentContact", "2|false|false|false"));
        byte[] previous = Files.readAllBytes(current);
        assertThrows(IOException.class, () -> PhoneLegacyContacts.merge(legacy, current));
        assertArrayEquals(previous, Files.readAllBytes(current));
    }

    private static String contact(String name, String flags) {
        return Base64.getEncoder().encodeToString(name.getBytes(StandardCharsets.UTF_8)) + "|" + flags;
    }

    private static void write(Path file, String... contacts) throws IOException {
        Properties properties = new Properties();
        properties.setProperty("contact.count", Integer.toString(contacts.length));
        for (int i = 0; i < contacts.length; i++) properties.setProperty("contact." + i, contacts[i]);
        try (var output = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) { properties.store(output, "test"); }
    }

    private static Properties read(Path file) throws IOException {
        Properties properties = new Properties();
        try (var input = Files.newBufferedReader(file, StandardCharsets.UTF_8)) { properties.load(input); }
        return properties;
    }
}
