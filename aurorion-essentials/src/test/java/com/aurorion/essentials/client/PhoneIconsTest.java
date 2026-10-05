package com.aurorion.essentials.client;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PhoneIconsTest {
    @Test
    void everyIconIsASquareOfTheDeclaredSize() {
        for (String[] icon : List.of(PhoneIcons.CAMERA, PhoneIcons.LOCATION, PhoneIcons.CLIP, PhoneIcons.IMPORT)) {
            assertEquals(PhoneIcons.SIZE, icon.length);
            for (String row : icon) {
                assertEquals(PhoneIcons.SIZE, row.length(), row);
                assertTrue(row.matches("[.#]+"), row);
            }
        }
    }

    @Test
    void insideUsesHalfOpenBounds() {
        assertTrue(PhoneIcons.inside(10, 10, 10, 10, 5, 5));
        assertTrue(PhoneIcons.inside(14.9, 14.9, 10, 10, 5, 5));
        assertFalse(PhoneIcons.inside(15, 12, 10, 10, 5, 5));
        assertFalse(PhoneIcons.inside(9.9, 12, 10, 10, 5, 5));
    }
}
