package com.aurorion.essentials.client;

import java.util.function.Function;

/** Display rules for social records; the original usernames remain the routing/storage keys. */
final class PhoneSocialNames {
    private PhoneSocialNames() {}

    static String author(String username, String storedDisplayName, Function<String, String> lookup) {
        String current = lookup.apply(accountKey(username));
        return current != null ? current : storedDisplayName;
    }

    static String accountKey(String username) {
        if (username == null) return "";
        String key = username.trim();
        return key.startsWith("@") ? key.substring(1) : key;
    }

    static String initial(String shown) {
        String name = accountKey(shown);
        if (name.isEmpty()) return "?";
        return name.substring(0, name.offsetByCodePoints(0, 1));
    }
}
