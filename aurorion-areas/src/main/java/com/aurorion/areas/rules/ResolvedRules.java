package com.aurorion.areas.rules;

import com.aurorion.areas.region.AreaRegion;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import java.util.Arrays;
import java.util.UUID;

/** Destino reutilizavel de consulta: nenhuma lista ou decisao nasce por movimento do jogador. */
public final class ResolvedRules {
    private final boolean[] allowed = new boolean[AreaRule.ALL.length];
    private final AreaRegion[] owners = new AreaRegion[AreaRule.ALL.length + 3];
    private UUID actor;
    private AreaRegion top;
    private ResourceLocation ambience;
    private double mobHealth, mobDamage;

    public void reset(AreaRules defaults, @Nullable UUID actor) {
        this.actor = actor;
        Arrays.fill(owners, null);
        top = null;
        for (AreaRule rule : AreaRule.ALL) allowed[rule.ordinal()] = defaults.flag(rule.key()) != Decision.DENY;
        ambience = defaults.ambience() == null ? AreaRules.NO_AMBIENCE : defaults.ambience();
        mobHealth = defaults.mobHealth() < 0 ? 1 : defaults.mobHealth();
        mobDamage = defaults.mobDamage() < 0 ? 1 : defaults.mobDamage();
    }
    public void include(AreaRegion region) {
        if (region.beats(top)) top = region;
        for (AreaRule rule : AreaRule.ALL) {
            Decision value = region.decision(rule.key(), actor);
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
