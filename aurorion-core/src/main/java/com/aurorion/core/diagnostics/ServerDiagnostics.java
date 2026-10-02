package com.aurorion.core.diagnostics;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/** Optional queue providers. Queries are server-thread only, read-only and must not perform IO. */
public final class ServerDiagnostics {
    private static final Map<String, Supplier<List<String>>> PROVIDERS = new ConcurrentHashMap<>();
    private ServerDiagnostics() { }

    public static void register(String section, Supplier<List<String>> provider) {
        PROVIDERS.put(section, provider);
    }

    public static void unregister(String section) {
        PROVIDERS.remove(section);
    }

    public static Map<String, List<String>> snapshot() {
        var report = new TreeMap<String, List<String>>();
        PROVIDERS.forEach((section, provider) -> {
            try {
                report.put(section, List.copyOf(provider.get()));
            } catch (RuntimeException unavailable) {
                report.put(section, List.of("Diagnóstico indisponível neste momento."));
            }
        });
        return report;
    }
}
