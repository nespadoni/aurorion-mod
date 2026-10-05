package com.aurorion.essentials.client;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

class PhoneSocialNamesTest {
    private static final Function<String, String> NAMES;

    static {
        Map<String, String> names = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        names.put("Steve_42", "Arthur Pendragon");
        names.put("Alex", "Morgana");
        NAMES = names::get;
    }

    @Test
    void currentFakeNameComesFromTheAccountEvenWhenTheStoredDisplayNameIsOld() {
        assertEquals("Arthur Pendragon", PhoneSocialNames.author("@steve_42", "Nome antigo", NAMES));
        assertEquals("Arthur Pendragon", PhoneSocialNames.author(" STEVE_42 ", "Steve_42", NAMES));
    }

    @Test
    void anotherAccountsMatchingDisplayNameCannotOverrideTheRealAuthor() {
        assertEquals("Arthur Pendragon", PhoneSocialNames.author("@steve_42", "Alex", NAMES));
    }

    @Test
    void unknownAndBuiltInAuthorsKeepTheirOriginalDisplayName() {
        assertEquals("Mattupolis News", PhoneSocialNames.author("@city_news", "Mattupolis News", NAMES));
        assertEquals("Autor desconhecido", PhoneSocialNames.author(null, "Autor desconhecido", NAMES));
    }

    @Test
    void initialSkipsAHandlePrefixAndKeepsACompleteUnicodeCharacter() {
        assertEquals("M", PhoneSocialNames.initial(" @Morgana "));
        assertEquals("\uD83D\uDC51", PhoneSocialNames.initial("\uD83D\uDC51 Rei"));
        assertEquals("?", PhoneSocialNames.initial(null));
        assertEquals("?", PhoneSocialNames.initial(" "));
    }
}
