package com.aurorion.core.rate;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Bounded, thread-safe cooldown. Callers supply a monotonic clock; logout does not reset it. */
public final class ActionCooldown {
    private final long intervalMillis;
    private final int capacity;
    private final Map<UUID, Long> deadlines = new HashMap<>();

    public ActionCooldown(long intervalMillis) {
        this(intervalMillis, 4096);
    }

    public ActionCooldown(long intervalMillis, int capacity) {
        if (intervalMillis <= 0 || capacity <= 0) throw new IllegalArgumentException("Invalid cooldown limits");
        this.intervalMillis = intervalMillis;
        this.capacity = capacity;
    }

    public synchronized boolean acquire(UUID actor, long now) {
        Long deadline = deadlines.get(actor);
        if (deadline != null && deadline > now) return false;
        if (deadline == null && deadlines.size() >= capacity) {
            deadlines.values().removeIf(value -> value <= now);
            if (deadlines.size() >= capacity) return false;
        }
        deadlines.put(actor, now + intervalMillis);
        return true;
    }

    public synchronized long remainingMillis(UUID actor, long now) {
        return Math.max(0L, deadlines.getOrDefault(actor, now) - now);
    }

    public synchronized void clear() {
        deadlines.clear();
    }
}
