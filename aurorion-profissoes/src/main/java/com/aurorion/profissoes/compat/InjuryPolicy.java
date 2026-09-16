package com.aurorion.profissoes.compat;

public final class InjuryPolicy {
    private InjuryPolicy() {}
    public static boolean severe(float damage, float max, double threshold) {
        return max > 0 && damage >= max * (1 - threshold);
    }
    public static float firstAid(float healing, float damage, float max, double ceiling) {
        return Math.max(0, Math.min(healing, damage - (float)(max * (1 - ceiling))));
    }
}
