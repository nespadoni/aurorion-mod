package com.aurorion.magia.spell;

import com.aurorion.magia.config.MagiaConfig;
import com.aurorion.magia.entity.MagiaProjectileEntity;
import com.aurorion.magia.network.MagiaNetwork;
import com.aurorion.magia.network.SpellVisualPayload;
import com.aurorion.magia.registry.MagiaSpells;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.SchoolRegistry;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.CastType;
import io.redspace.ironsspellbooks.api.spells.SpellAnimations;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import io.redspace.ironsspellbooks.api.util.AnimationHolder;
import io.redspace.ironsspellbooks.api.util.Utils;
import io.redspace.ironsspellbooks.damage.DamageSources;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.List;
import java.util.Optional;

/**
 * Pyrobolus Infernalis — Bomba Megainfernal. A ultimate do Ziggs: uma bomba enorme arremessada de
 * muito longe (ate {@code alcanceBomba} blocos, 128 por padrao), num arco alto que leva de um a dois
 * segundos para cair. Um alvo vermelho aparece no chao para todo mundo que esta perto de onde ela vai
 * cair — da tempo de correr.
 *
 * <p>Quem esta no miolo da explosao ({@value #CORE} blocos do centro) leva o dano inteiro; dali ate a
 * borda, o dano cai ate 60%. Nao quebra bloco: e a ultimate de um yordle, nao uma bomba de mineracao.
 */
public final class PyrobolusInfernalisSpell extends AurorionSpell {
    private static final double CORE = 2;
    private static final int MAX_TARGETS = 32;

    public PyrobolusInfernalisSpell() {
        super("pyrobolus_infernalis", SchoolRegistry.FIRE_RESOURCE, SpellRarity.LEGENDARY, 3, 75, CastType.LONG);
        this.baseSpellPower = 14;
        this.spellPowerPerLevel = 5;
        this.baseManaCost = 120;
        this.manaCostPerLevel = 30;
        this.castTime = 20;
    }

    @Override
    public AnimationHolder getCastStartAnimation() {
        return SpellAnimations.CHARGE_ANIMATION;
    }

    @Override
    public AnimationHolder getCastFinishAnimation() {
        return SpellAnimations.ANIMATION_CHARGED_CAST;
    }

    @Override
    public Optional<SoundEvent> getCastStartSound() {
        return Optional.of(SoundEvents.TNT_PRIMED);
    }

    @Override
    public Optional<SoundEvent> getCastFinishSound() {
        return Optional.of(SoundEvents.FIRECHARGE_USE);
    }

    @Override
    public Vector3f getTargetingColor() {
        return new Vector3f(1.0f, 0.35f, 0.1f);
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, @Nullable LivingEntity caster) {
        return List.of(
                Component.translatable("ui.aurorion_magia.dano_centro", Utils.stringTruncation(getSpellPower(spellLevel, caster), 1)),
                Component.translatable("ui.aurorion_magia.raio", Utils.stringTruncation(radius(spellLevel), 1)),
                Component.translatable("ui.aurorion_magia.alcance", MagiaConfig.PYROBOLUS_RANGE.get()));
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource castSource, MagicData playerMagicData) {
        if (level instanceof ServerLevel serverLevel) {
            Vec3 at = Hits.aimGround(serverLevel, entity, MagiaConfig.PYROBOLUS_RANGE.get());
            Vec3 from = entity.getEyePosition().add(0, 0.5, 0).add(entity.getLookAngle().scale(0.8));
            int ticks = Math.clamp((int) (from.distanceTo(at) / 3.5), 20, 40);
            float radius = radius(spellLevel);
            MagiaProjectileEntity.launch(serverLevel, entity, MagiaProjectileEntity.Shape.PYROBOLUS, from,
                    MagiaProjectileEntity.arc(from, at, ticks, MagiaProjectileEntity.Shape.PYROBOLUS),
                    1.3f, getSpellPower(spellLevel, entity), radius, ticks + 40);
            MagiaNetwork.sendVisualAt(serverLevel, entity, SpellVisualPayload.Kind.PYROBOLUS_TARGET, ticks + 4, at, radius);
        }
        super.onCast(level, spellLevel, entity, castSource, playerMagicData);
    }

    /** A bomba caiu. */
    public static void blast(ServerLevel level, LivingEntity caster, Vec3 at, double radius, float damage) {
        for (LivingEntity victim : Hits.around(level, caster, at, radius, MAX_TARGETS, t -> Hits.enemy(caster, t))) {
            double distance = Math.sqrt(victim.position().distanceToSqr(at));
            float scale = distance <= CORE ? 1f : (float) (1 - 0.4 * Math.min(1, (distance - CORE) / (radius - CORE)));
            FriendlyFire.applyDamage(victim, damage * scale, MagiaSpells.PYROBOLUS_INFERNALIS.get().getDamageSource(caster));
        }
        sound(level, at, SoundEvents.GENERIC_EXPLODE.value(), 4.0f, 0.6f);
        sound(level, at, SoundEvents.DRAGON_FIREBALL_EXPLODE, 2.0f, 0.8f);
        MagiaNetwork.sendVisualAt(level, caster, SpellVisualPayload.Kind.PYROBOLUS_BLAST, 40, at, (float) radius);
    }

    /** 6 blocos no nivel 1, +0,5 por nivel. */
    private static float radius(int spellLevel) {
        return 5.5f + 0.5f * spellLevel;
    }
}
