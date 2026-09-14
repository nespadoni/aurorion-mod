package com.aurorion.core.character;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/** Display text is never an identifier. Formatting codes and control characters are not names. */
public record CharacterName(String firstName, String lastName) {
    public static final int PART_LIMIT = 24;
    public static final int FULL_LIMIT = 48;
    private static final Pattern PART = Pattern.compile("[\\p{L}\\p{M}]+(?:[ '\u2019-][\\p{L}\\p{M}]+)*");
    public CharacterName {
        firstName = normalize(firstName);
        lastName = normalize(lastName);
        if (!validPart(firstName) || !validPart(lastName) || firstName.length() + lastName.length() + 1 > FULL_LIMIT)
            throw new IllegalArgumentException("Informe nome e sobrenome com letras, ate 48 caracteres no total.");
    }
    private static String normalize(String value) {
        return Normalizer.normalize(value.strip(), Normalizer.Form.NFC).replaceAll(" +", " ");
    }
    private static boolean validPart(String value) {
        return value.length() >= 2 && value.length() <= PART_LIMIT && PART.matcher(value).matches();
    }
    public String fullName() { return firstName + " " + lastName; }
    public String key() { return key(fullName()); }
    public static String key(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT);
    }
}
