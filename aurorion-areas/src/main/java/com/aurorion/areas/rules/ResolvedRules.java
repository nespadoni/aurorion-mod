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
    /**
     * Cinco regras + ambiente + vida + dano + casa dona. Um vetor so para o {@code reset} continuar
     * sendo um {@code Arrays.fill} unico por tick, em vez de um campo por assunto.
     */
    private final AreaRegion[] owners = new AreaRegion[AreaRule.ALL.length + 4];
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
    @Nullable private AreaRegion houseArea;

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
        houseArea = null;
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
        // A dona da casa resolve como o ambiente: entre areas de casa sobrepostas, vale a de maior
        // prioridade. Assim uma sala interna pode trocar a dona de um trecho da casa maior.
        if (region.house() != null && region.beats(owners[offset + 3])) {
            owners[offset + 3] = region; houseArea = region;
        }
    }
    public boolean allows(AreaRule rule) { return allowed[rule.ordinal()]; }
    @Nullable public AreaRegion owner(AreaRule rule) { return owners[rule.ordinal()]; }
    @Nullable public AreaRegion top() { return top; }
    public ResourceLocation ambience() { return ambience; }
    /**
     * A area de casa que vale nesta posicao, ou {@code null} se nenhuma casa e dona daqui.
     *
     * <p>Sai da mesma resolucao que o tick do jogador ja fez: a barreira de casa nao percorre a
     * arvore nem testa poligono de novo (SDD §2, nada novo por jogador por tick).
     */
    @Nullable public AreaRegion houseArea() { return houseArea; }
    /**
     * A area que definiu o ambiente desta posicao, ou {@code null} quando ele veio do padrao da
     * dimensao. Serve para o aviso a staff dizer <em>onde</em> a pessoa entrou com o nome que a
     * propria staff deu ao lugar, em vez do id do ambiente.
     */
    @Nullable public AreaRegion ambienceArea() { return owners[AreaRule.ALL.length]; }
    public double mobHealth() { return mobHealth; }
    public double mobDamage() { return mobDamage; }
}
