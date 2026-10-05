package com.aurorion.essentials.client;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

/** Client-thread directory scoped to the connection that supplied it. No Minecraft dependency. */
final class PhoneNameDirectoryCache {
    private final Map<String, String> entries = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    private final Map<String, String> view = Collections.unmodifiableMap(entries);
    private Object connection;
    private int version;

    /** Bind before accepting a login packet, as well as before the first render. */
    boolean bind(Object current) {
        if (connection == current) return false;
        connection = current;
        entries.clear();
        version++;
        return true;
    }

    void apply(boolean replace, Map<String, String> changes) {
        if (replace) entries.clear();
        changes.forEach((nick, name) -> {
            if (name == null || name.isBlank()) entries.remove(nick);
            else entries.put(nick, name);
        });
        version++;
    }

    String get(String nick) {
        return entries.get(nick);
    }

    Map<String, String> entries() {
        return view;
    }

    int version() {
        return version;
    }
}
