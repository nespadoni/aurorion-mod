package com.aurorion.magia.spell;

import com.aurorion.magia.network.MagiaNetwork;
import com.aurorion.magia.network.SpellVisualPayload;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.SchoolRegistry;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.CastType;
import io.redspace.ironsspellbooks.api.spells.SpellAnimations;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import io.redspace.ironsspellbooks.api.util.AnimationHolder;
import io.redspace.ironsspellbooks.api.util.Utils;
import io.redspace.ironsspellbooks.capabilities.magic.MagicManager;
import io.redspace.ironsspellbooks.damage.DamageSources;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.List;
import java.util.Optional;

/**
 * Messis Uberrima — Colheita Farta. O W do Fiddlesticks: enquanto o botao fica apertado (ate dois
 * segundos), fios de vida saem de todos os inimigos em {@value #RADIUS} blocos ate quem conjura — o
 * dano de cada pulso vira cura. Quem segurar ate o fim colhe: um ultimo golpe que cresce com a vida
 * que ja falta a cada um.
 *
 * <p>Soltar antes do fim encerra a drenagem sem a colheita. Custo: uma busca no raio a cada meio
 * segundo de canalizacao.
 */
public final class MessisUberrimaSpell extends AurorionSpell {
    private static final int RADIUS = 6;
    private static final int MAX_TARGETS = 8;
    private static final int PULSES_PER_SECOND = 20 / MagicManager.CONTINUOUS_CAST_TICK_INTERVAL;
    /** Fracao do dano drenado que volta como vida. */
    private static final float LIFESTEAL = 0.6f;

    public MessisUberrimaSpell() {
        super("messis_uberrima", SchoolRegistry.ELDRITCH_RESOURCE, SpellRarity.EPIC, 5, 20, CastType.CONTINUOUS);
        this.baseSpellPower = 2;
        this.spellPowerPerLevel = 1;
        this.baseManaCost = 8;
        this.manaCostPerLevel = 2;
        this.castTime = 40;
    }

    @Override
    public AnimationHolder getCastStartAnimation() {
        return SpellAnimations.ANIMATION_CONTINUOUS_CAST;
    }

    // Sem getCastFinishAnimation: ver o aviso no DolorCruciatusSpell.

    @Override
    public Optional<SoundEvent> getCastStartSound() {
        return Optional.of(SoundEvents.SOUL_ESCAPE.value());
    }

    @Override
    public Optional<SoundEvent> getCastFinishSound() {
        return Optional.empty();
    }

    @Override
    public Vector3f getTargetingColor() {
        return new Vector3f(0.5f, 0.15f, 0.55f);
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, @Nullable LivingEntity caster) {
        return List.of(
                Component.translatable("ui.aurorion_magia.dano_por_segundo",
                        Utils.stringTruncation(pulse(spellLevel, caster) * PULSES_PER_SECOND, 1)),
                Component.translatable("ui.aurorion_magia.raio", RADIUS),
                Component.translatable("ui.aurorion_magia.colheita",
                        Utils.stringTruncation(getSpellPower(spellLevel, caster), 1)));
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource castSource, MagicData playerMagicData) {
        if (level instanceof ServerLevel serverLevel) drain(serverLevel, entity, pulse(spellLevel, entity), false);
        super.onCast(level, spellLevel, entity, castSource, playerMagicData);
    }

    @Override
    public void onServerCastComplete(Level level, int spellLevel, LivingEntity entity, MagicData playerMagicData,
                                     boolean cancelled) {
        super.onServerCastComplete(level, spellLevel, entity, playerMagicData, cancelled);
        if (!cancelled && level instanceof ServerLevel serverLevel) {
            drain(serverLevel, entity, getSpellPower(spellLevel, entity), true);
            sound(entity, SoundEvents.SOUL_ESCAPE.value(), 2.0f, 0.5f);
            sound(entity, SoundEvents.PHANTOM_DEATH, 1.0f, 0.6f);
        }
    }

    /**
     * Um pulso de drenagem; na colheita ({@code reap}), o dano cresce ate o triplo com a vida que falta
     * a cada alvo.
     */
    private void drain(ServerLevel level, LivingEntity caster, float damage, boolean reap) {
        float drained = 0;
        for (LivingEntity victim : Hits.around(level, caster, caster.position(), RADIUS, MAX_TARGETS, t -> Hits.enemy(caster, t))) {
            float dealt = reap ? damage * (1 + 2 * Execution.missing(victim)) : damage;
            DamageSources.ignoreNextKnockback(victim);
            if (FriendlyFire.applyDamage(victim, dealt, getDamageSource(caster))) drained += dealt;
            MagiaNetwork.sendVisual(caster, victim, SpellVisualPayload.Kind.MESSIS_UBERRIMA,
                    MagicManager.CONTINUOUS_CAST_TICK_INTERVAL + 5);
        }
        if (drained > 0) caster.heal(drained * LIFESTEAL);
    }

    private float pulse(int spellLevel, @Nullable LivingEntity caster) {
        return getSpellPower(spellLevel, caster) * 0.5f;
    }
}
