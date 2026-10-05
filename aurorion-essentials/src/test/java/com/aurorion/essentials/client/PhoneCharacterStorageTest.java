package com.aurorion.essentials.client;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A pasta do telefone por personagem, sem subir o jogo. */
class PhoneCharacterStorageTest {
    private static final UUID MAIN = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @TempDir
    Path game;

    @Test
    void filesInThePhoneRootMoveIntoTheCharacterFolder() {
        Path dir = PhoneCharacterStorage.characterDir(game, MAIN);
        Path notes = game.resolve("mattupolis_phone").resolve("notes.properties");
        assertEquals(dir.resolve("notes.properties"), PhoneCharacterStorage.redirect(notes, dir));
    }

    /** O layout da tela inicial e montado com "mattupolis_phone/home_layout.properties" num resolve so. */
    @Test
    void aSlashInsideTheResolvedNameStillCounts() {
        Path dir = PhoneCharacterStorage.characterDir(game, MAIN);
        Path layout = game.resolve("mattupolis_phone/home_layout.properties");
        assertEquals(dir.resolve("home_layout.properties"), PhoneCharacterStorage.redirect(layout, dir));
    }

    @Test
    void pathsOutsideThePhoneRootAndOutsideAWorldAreUntouched() {
        Path dir = PhoneCharacterStorage.characterDir(game, MAIN);
        Path screenshot = game.resolve("screenshots").resolve("a.png");
        assertSame(screenshot, PhoneCharacterStorage.redirect(screenshot, dir));

        Path notes = game.resolve("mattupolis_phone").resolve("notes.properties");
        assertSame(notes, PhoneCharacterStorage.redirect(notes, null));
    }

    @Test
    void offlineMainAccountIsRecognizedWithoutClaimingAnAlt() {
        String nick = "MainAccount";
        UUID online = UUID.fromString("00000000-0000-0000-0000-000000000002");
        UUID offline = UUID.nameUUIDFromBytes(("OfflinePlayer:" + nick).getBytes(StandardCharsets.UTF_8));
        assertTrue(PhoneCharacterStorage.isMainProfile(online, nick, online, nick));
        assertTrue(PhoneCharacterStorage.isMainProfile(offline, nick, online, nick));
        assertFalse(PhoneCharacterStorage.isMainProfile(MAIN, "AltAccount", online, nick));
        assertFalse(PhoneCharacterStorage.isMainProfile(MAIN, nick, online, nick));
    }

    @Test
    void migrationNeverOverwritesTheCurrentCharacterOrTouchesAnotherCharacter() throws IOException {
        Path root = Files.createDirectories(game.resolve(PhoneCharacterStorage.ROOT));
        Path main = Files.createDirectories(PhoneCharacterStorage.characterDir(game, MAIN));
        Path alt = Files.createDirectories(PhoneCharacterStorage.characterDir(game, UUID.randomUUID()));
        Files.writeString(root.resolve("notes.properties"), "legacy notes");
        Files.writeString(main.resolve("notes.properties"), "current notes");
        Files.writeString(alt.resolve("contacts.properties"), "alt contacts");

        PhoneCharacterStorage.migrateLegacy(game, main);

        assertEquals("current notes", Files.readString(main.resolve("notes.properties")));
        assertEquals("alt contacts", Files.readString(alt.resolve("contacts.properties")));
        assertEquals("legacy notes", Files.readString(root.resolve("notes.properties")));
    }

    @Test
    void legacyFilesMoveToTheMainAccountOnce() throws IOException {
        Path root = Files.createDirectories(game.resolve("mattupolis_phone"));
        Files.writeString(root.resolve("notes.properties"), "note.count=1");
        Files.createDirectories(root.resolve("camera"));
        Files.writeString(root.resolve("camera").resolve("foto.png"), "png");

        Path dir = PhoneCharacterStorage.characterDir(game, MAIN);
        PhoneCharacterStorage.migrateLegacy(game, dir);

        assertEquals("note.count=1", Files.readString(dir.resolve("notes.properties")));
        assertTrue(Files.exists(dir.resolve("camera").resolve("foto.png")));
        assertFalse(Files.exists(root.resolve("notes.properties")));
        assertTrue(Files.exists(dir.resolve(PhoneCharacterStorage.MIGRATED_MARKER)));

        // Um arquivo que aparecer na raiz depois nao e mais arrastado: a migracao ja aconteceu.
        Files.writeString(root.resolve("wallpaper.properties"), "home=x");
        PhoneCharacterStorage.migrateLegacy(game, dir);
        assertFalse(Files.exists(dir.resolve("wallpaper.properties")));
    }

    @Test
    void anUnreadableLegacyAgendaDoesNotBlockTheRestOfTheMigration() throws IOException {
        Path root = Files.createDirectories(game.resolve(PhoneCharacterStorage.ROOT));
        Path dir = Files.createDirectories(PhoneCharacterStorage.characterDir(game, MAIN));
        Files.writeString(root.resolve("contacts.properties"), "contact.count=");
        Files.writeString(dir.resolve("contacts.properties"), "contact.count=0");
        Files.writeString(root.resolve("notes.properties"), "note.count=1");

        PhoneCharacterStorage.migrateLegacy(game, dir);

        assertEquals("note.count=1", Files.readString(dir.resolve("notes.properties")));
        assertTrue(Files.exists(dir.resolve(PhoneCharacterStorage.MIGRATED_MARKER)));
        assertEquals("contact.count=", Files.readString(root.resolve("contacts.properties")));
    }

    @Test
    void aSecondMainFolderDoesNotTakeWhatTheFirstMigrationLeftInTheRoot() throws IOException {
        Path root = Files.createDirectories(game.resolve(PhoneCharacterStorage.ROOT));
        Path online = Files.createDirectories(PhoneCharacterStorage.characterDir(game, MAIN));
        Files.writeString(online.resolve(PhoneCharacterStorage.MIGRATED_MARKER), "ok");
        Files.writeString(root.resolve("contacts.properties"), "copia de recuperacao");
        Path offline = PhoneCharacterStorage.characterDir(game, UUID.randomUUID());

        PhoneCharacterStorage.migrateLegacy(game, offline);

        assertFalse(Files.exists(offline.resolve("contacts.properties")));
        assertTrue(Files.exists(root.resolve("contacts.properties")));
        assertTrue(Files.exists(offline.resolve(PhoneCharacterStorage.MIGRATED_MARKER)));
    }

    @Test
    void wipeKeepsOnlyTheListedFiles() throws IOException {
        Path dir = Files.createDirectories(PhoneCharacterStorage.characterDir(game, MAIN));
        Files.writeString(dir.resolve("notes.properties"), "x");
        Files.writeString(dir.resolve("aurorion_retired_contacts.txt"), "id");
        Files.createDirectories(dir.resolve("message_photos"));
        Files.writeString(dir.resolve("message_photos").resolve("a.jpg"), "jpg");

        PhoneCharacterStorage.wipe(dir, Set.of("aurorion_retired_contacts.txt"));

        assertFalse(Files.exists(dir.resolve("notes.properties")));
        assertFalse(Files.exists(dir.resolve("message_photos")));
        assertTrue(Files.exists(dir.resolve("aurorion_retired_contacts.txt")));
    }
}
