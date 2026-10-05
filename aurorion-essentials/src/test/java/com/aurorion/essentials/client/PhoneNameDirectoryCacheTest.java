package com.aurorion.essentials.client;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PhoneNameDirectoryCacheTest {
    @Test
    void loginDirectorySurvivesTheFirstRenderOnTheSameConnection() {
        PhoneNameDirectoryCache directory = new PhoneNameDirectoryCache();
        Object connection = new Object();
        assertTrue(directory.bind(connection));
        directory.apply(true, Map.of("Steve_42", "Arthur Pendragon"));

        assertFalse(directory.bind(connection));
        assertEquals("Arthur Pendragon", directory.get("steve_42"));
    }

    @Test
    void aNewConnectionCannotDisplayThePreviousServersNames() {
        PhoneNameDirectoryCache directory = new PhoneNameDirectoryCache();
        directory.bind(new Object());
        directory.apply(true, Map.of("Steve_42", "Arthur Pendragon"));

        assertTrue(directory.bind(new Object()));
        assertNull(directory.get("Steve_42"));
    }

    @Test
    void updatesAndRemovalsUseCaseInsensitiveAccountKeys() {
        PhoneNameDirectoryCache directory = new PhoneNameDirectoryCache();
        directory.bind(new Object());
        directory.apply(true, Map.of("Steve_42", "Arthur", "Alex", "Morgana"));
        directory.apply(false, Map.of("steve_42", "Arthur Pendragon"));
        assertEquals("Arthur Pendragon", directory.get("STEVE_42"));
        assertEquals("Morgana", directory.get("alex"));

        directory.apply(false, Map.of("STEVE_42", ""));
        assertNull(directory.get("Steve_42"));
        assertEquals("Morgana", directory.get("Alex"));
    }

    @Test
    void aFullLoginSnapshotReplacesEarlierEntries() {
        PhoneNameDirectoryCache directory = new PhoneNameDirectoryCache();
        directory.bind(new Object());
        directory.apply(true, Map.of("Steve_42", "Arthur"));
        directory.apply(true, Map.of("Alex", "Morgana"));
        assertNull(directory.get("Steve_42"));
        assertEquals(Map.of("Alex", "Morgana"), directory.entries());
    }
}
