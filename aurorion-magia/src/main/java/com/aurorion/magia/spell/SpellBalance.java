package com.aurorion.magia.spell;

/** Balanceamento autoral: dobra dano e tempo de efeito, sem alterar recarga ou tempo de preparo. */
public final class SpellBalance {
    private SpellBalance() { }

    public static float damage(float value) {
        return Float.isFinite(value) && value > 0 ? Math.min(value, Float.MAX_VALUE / 2) * 2 : 0;
    }

    public static int duration(int ticks) {
        return ticks < 0 ? ticks : (int) Math.min((long) ticks * 2, Integer.MAX_VALUE);
    }
}
