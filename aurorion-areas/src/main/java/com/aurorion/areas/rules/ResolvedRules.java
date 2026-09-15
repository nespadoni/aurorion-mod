package com.aurorion.areas.rules;

import com.aurorion.areas.region.AreaRegion;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;

/** Destino reutilizavel de consulta: nenhuma lista ou decisao nasce por movimento do jogador. */
public final class ResolvedRules {
    private final boolean[] allowed = new boolean[AreaRule.ALL.length];
    private final AreaRegion[] owners = new AreaRegion[AreaRule.ALL.length + 3];
    /**
     * Padrao da dimensao ja traduzido. {@link AreaRules} e imutavel e {@code AreaData} devolve sempre
     * a mesma instancia ate a staff mudar o fundo, entao o {@code reset} de cada tick vira um
     * {@code arraycopy} em vez de cinco consultas ao mapa de regras.
     */
    @Nullable private AreaRules cachedDefaults;
    private final boolean[] base = new boolean[AreaRule.ALL.length];
    private ResourceLocation baseAmbience;
    private double baseHealth, baseDamage;
    private UUID actor;
    private AreaRegion top;
    private ResourceLocation ambience;
    private double mobHealth, mobDamage;

    public void reset(AreaRules defaults, @Nullable UUID actor) {
        if (defaults != cachedDefaults) {
            cachedDefaults = defaults;
            for (AreaRule rule : AreaRule.ALL) base[rule.ordinal()] = defaults.flag(rule.key()) != Decision.DENY;
            baseAmbience = defaults.ambience() == null ? AreaRules.NO_AMBIENCE : defaults.ambience();
            baseHealth = defaults.mobHealth() < 0 ? 1 : defaults.mobHealth();
            baseDamage = defaults.mobDamage() < 0 ? 1 : defaults.mobDamage();
        }
        this.actor = actor;
        Arrays.fill(owners, null);
        top = null;
        System.arraycopy(base, 0, allowed, 0, base.length);
        ambience = baseAmbience;
        mobHealth = baseHealth;
        mobDamage = baseDamage;
    }
    public void include(AreaRegion region) {
        if (region.beats(top)) top = region;
        Set<String> exceptions = region.exceptionsOf(actor);
        for (AreaRule rule : AreaRule.ALL) {
            Decision value = region.decision(rule.key(), exceptions);
            if (value != Decision.INHERIT && region.beats(owners[rule.ordinal()])) {
                owners[rule.ordinal()] = region;
                allowed[rule.ordinal()] = value == Decision.ALLOW;
            }
        }
        int offset = AreaRule.ALL.length;
        AreaRules rules = region.rules();
        if (rules.ambience() != null && region.beats(owners[offset])) {
            owners[offset] = region; ambience = rules.ambience();
        }
        if (rules.mobHealth() >= 0 && region.beats(owners[offset + 1])) {
            owners[offset + 1] = region; mobHealth = rules.mobHealth();
        }
        if (rules.mobDamage() >= 0 && region.beats(owners[offset + 2])) {
            owners[offset + 2] = region; mobDamage = rules.mobDamage();
        }
    }
    public boolean allows(AreaRule rule) { return allowed[rule.ordinal()]; }
    @Nullable public AreaRegion owner(AreaRule rule) { return owners[rule.ordinal()]; }
    @Nullable public AreaRegion top() { return top; }
    public ResourceLocation ambience() { return ambience; }
    public double mobHealth() { return mobHealth; }
    public double mobDamage() { return mobDamage; }
}
