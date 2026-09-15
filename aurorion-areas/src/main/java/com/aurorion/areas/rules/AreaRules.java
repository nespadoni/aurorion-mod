package com.aurorion.areas.rules;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import java.util.HashMap;
import java.util.Map;

public record AreaRules(Map<String, Decision> flags, @Nullable ResourceLocation ambience,
                        double mobHealth, double mobDamage) {
    public static final ResourceLocation NO_AMBIENCE = ResourceLocation.parse("aurorion_areas:none");
    public static final AreaRules INHERIT = new AreaRules(Map.of(), null, -1, -1);

    public AreaRules {
        flags = Map.copyOf(flags);
        if (flags.size() > 64) throw new IllegalArgumentException("Limite de 64 regras por area.");
        for (var entry : flags.entrySet()) {
            validateKey(entry.getKey());
            if (entry.getValue() == null) throw new IllegalArgumentException("Regra sem decisao.");
        }
        if (!validMultiplier(mobHealth) || !validMultiplier(mobDamage)) {
            throw new IllegalArgumentException("Multiplicadores: -1 para herdar, ou entre 0.1 e 20.");
        }
    }
    private static boolean validMultiplier(double value) { return value == -1 || Double.isFinite(value) && value >= .1 && value <= 20; }
    public static void validateKey(String key) {
        if (!key.matches("[a-z][a-z0-9_:.-]{0,79}")) throw new IllegalArgumentException("Nome de regra invalido.");
    }
    public Decision flag(String key) { return flags.getOrDefault(key, Decision.INHERIT); }
    public AreaRules withFlag(String key, Decision decision) {
        validateKey(key);
        Map<String, Decision> changed = new HashMap<>(flags);
        if (decision == Decision.INHERIT) changed.remove(key); else changed.put(key, decision);
        return new AreaRules(changed, ambience, mobHealth, mobDamage);
    }
    public AreaRules withAmbience(@Nullable ResourceLocation id) { return new AreaRules(flags, id, mobHealth, mobDamage); }
    public AreaRules withStrength(boolean health, double value) {
        return new AreaRules(flags, ambience, health ? value : mobHealth, health ? mobDamage : value);
    }
}
