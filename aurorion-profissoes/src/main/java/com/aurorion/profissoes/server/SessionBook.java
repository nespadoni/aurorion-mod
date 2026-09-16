package com.aurorion.profissoes.server;

import java.util.*;

/** Uma entrada por dono conectado. Consultas invalidas nao consomem a entrada valida de outro pedido. */
public final class SessionBook<T> {
    public record Entry<T>(UUID token, long deadline, T value) {}
    private final Map<UUID, Entry<T>> entries = new HashMap<>();
    public UUID put(UUID owner, T value, long now) {
        UUID token = UUID.randomUUID(); entries.put(owner, new Entry<>(token, now + 30_000, value)); return token;
    }
    public T take(UUID owner, UUID token, long now) {
        var entry = entries.get(owner);
        if (entry == null || !entry.token.equals(token)) return null;
        entries.remove(owner);
        return now >= entry.deadline ? null : entry.value;
    }
    public boolean busy(UUID owner, long now) {
        var entry = entries.get(owner);
        if (entry != null && now >= entry.deadline) { entries.remove(owner); return false; }
        return entry != null;
    }
    public Entry<T> peek(UUID owner, long now) { return busy(owner, now) ? entries.get(owner) : null; }
    public void removeIf(java.util.function.BiPredicate<UUID, T> predicate) {
        entries.entrySet().removeIf(e -> predicate.test(e.getKey(), e.getValue().value));
    }
    public void clear() { entries.clear(); }
}
