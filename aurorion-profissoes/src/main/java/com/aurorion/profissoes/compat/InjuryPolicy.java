package com.aurorion.profissoes.compat;

public final class InjuryPolicy {
    private InjuryPolicy() {}
    public static boolean severe(float damage, float max, double threshold) {
        return Float.isFinite(damage) && Float.isFinite(max) && max > 0
                && damage >= max * (1 - threshold);
    }
    public static float firstAid(float healing, float damage, float max, double ceiling) {
        if (!Float.isFinite(healing) || !Float.isFinite(damage) || !Float.isFinite(max)) return 0;
        return Math.max(0, Math.min(healing, damage - (float)(max * (1 - ceiling))));
    }
    /** Save corrompido nao pode contaminar o proximo golpe. */
    public static float cleanDamage(float damage, float max) {
        if (!Float.isFinite(damage) || !Float.isFinite(max) || max < 0) return 0;
        return Math.max(0, Math.min(damage, max));
    }
}
