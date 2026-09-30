package com.aurorion.essentials.client;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
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
