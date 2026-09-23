package com.aurorion.magia.spell;

import com.aurorion.magia.network.MagiaNetwork;
import com.aurorion.magia.network.SpellVisualPayload;
import com.aurorion.magia.registry.MagiaEffects;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.SchoolRegistry;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.CastType;
import io.redspace.ironsspellbooks.api.spells.ICastData;
import io.redspace.ironsspellbooks.api.spells.SpellAnimations;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import io.redspace.ironsspellbooks.api.util.AnimationHolder;
import io.redspace.ironsspellbooks.api.util.Utils;
import io.redspace.ironsspellbooks.capabilities.magic.MagicManager;
import io.redspace.ironsspellbooks.damage.DamageSources;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Dolor Universus — Tormento Coletivo. <b>Magia proibida.</b> O Cruciatus em area.
 *
 * <p>Todos em volta sao erguidos do chao e ficam suspensos, paralisados de dor (o mesmo
 * {@code cruciatus}: sem andar, sem pular, camera tremendo), enquanto a canalizacao dura. A cada meio
 * segundo, todos levam o dano do pulso ao mesmo tempo. Soltar o botao solta todo mundo.
 *
 * <table>
 *   <caption>Por nivel</caption>
 *   <tr><th>Nivel</th><th>Raio</th><th>Altura</th><th>Canalizacao</th><th>Dano/s por alvo</th></tr>
 *   <tr><td>1</td><td>5</td><td>1,5</td><td>3 s</td><td>2</td></tr>
 *   <tr><td>3</td><td>9</td><td>2,1</td><td>5 s</td><td>4</td></tr>
 *   <tr><td>5</td><td>13</td><td>2,7</td><td>7 s</td><td>6</td></tr>
 * </table>
 *
 * <h2>Custo</h2>
 *
 * <p>Os alvos sao escolhidos UMA vez, no inicio (busca espacial no raio, teto de {@value #MAX_TARGETS}),
 * e guardados na conjuracao. Durante a canalizacao, cada tick so empurra esses alvos de volta ao
 * ponto de suspensao — o mesmo que a Mao do Algoz faz com um. Um pacote de visual por pulso, nao um
 * por alvo.
 */
public final class DolorUniversusSpell extends AurorionSpell {
    private static final int MAX_TARGETS = 24;
    private static final double SPRING = 0.3;
    private static final double MAX_LIFT_SPEED = 0.8;
    private static final int EFFECT_TICKS = MagicManager.CONTINUOUS_CAST_TICK_INTERVAL + 5;
    private static final int PULSES_PER_SECOND = 20 / MagicManager.CONTINUOUS_CAST_TICK_INTERVAL;

    public DolorUniversusSpell() {
        super("dolor_universus", SchoolRegistry.BLOOD_RESOURCE, SpellRarity.LEGENDARY, 5, 90, CastType.CONTINUOUS, true);
        this.baseSpellPower = 2;
        this.spellPowerPerLevel = 1;
        this.baseManaCost = 15;
        this.manaCostPerLevel = 5;
        this.castTime = 60;
    }

    /** Canalizacao cresce com o nivel: 3 s, +1 s por nivel. */
    @Override
    public int getCastTime(int spellLevel) {
        return 60 + 20 * (spellLevel - 1);
    }

    @Override
    public AnimationHolder getCastStartAnimation() {
        return SpellAnimations.ANIMATION_CONTINUOUS_OVERHEAD;
    }

    @Override
    public AnimationHolder getCastFinishAnimation() {
        return AnimationHolder.pass();
    }

    @Override
    public Optional<SoundEvent> getCastStartSound() {
        return Optional.of(SoundEvents.EVOKER_PREPARE_ATTACK);
    }

    @Override
    public Optional<SoundEvent> getCastFinishSound() {
        return Optional.empty();
    }

    @Override
    public Vector3f getTargetingColor() {
        return new Vector3f(0.7f, 0.03f, 0.08f);
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, @Nullable LivingEntity caster) {
        return List.of(Component.translatable("ui.aurorion_magia.raio", radius(spellLevel)),
                Component.translatable("ui.aurorion_magia.dano_por_segundo",
                        Utils.stringTruncation(pulseDamage(spellLevel, caster) * PULSES_PER_SECOND, 1)),
                Component.translatable("ui.aurorion_magia.proibida"));
    }

    @Override
    public boolean checkPreCastConditions(Level level, int spellLevel, LivingEntity entity, MagicData playerMagicData) {
        if (!(level instanceof ServerLevel serverLevel)) return true;
        if (playerMagicData == null) return false;
        Suspended suspended = gather(serverLevel, entity, spellLevel);
        if (suspended.holds.isEmpty()) {
            if (entity instanceof ServerPlayer player) {
                player.displayClientMessage(Component.translatable("aurorion_magia.ninguem_em_volta"), true);
            }
            return false;
        }
        playerMagicData.setAdditionalCastData(suspended);
        return true;
    }

    /** Todo tick da canalizacao: cada alvo e puxado de volta ao seu ponto no ar. */
    @Override
    public void onServerCastTick(Level level, int spellLevel, LivingEntity entity, MagicData playerMagicData) {
        super.onServerCastTick(level, spellLevel, entity, playerMagicData);
        if (!(level instanceof ServerLevel serverLevel) || playerMagicData == null
                || !(playerMagicData.getAdditionalCastData() instanceof Suspended suspended)) return;
        for (Hold hold : suspended.holds) {
            LivingEntity target = hold.resolve(serverLevel);
            if (target == null) continue;
            Vec3 pull = hold.hover.subtract(target.position()).scale(SPRING);
            if (pull.length() > MAX_LIFT_SPEED) pull = pull.normalize().scale(MAX_LIFT_SPEED);
            launch(target, pull);
            target.resetFallDistance();
        }
    }

    /** A cada pulso: dano em todos ao mesmo tempo, paralisia renovada e um pacote de visual. */
    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource castSource, MagicData playerMagicData) {
        if (level instanceof ServerLevel serverLevel && playerMagicData != null
                && playerMagicData.getAdditionalCastData() instanceof Suspended suspended) {
            float damage = pulseDamage(spellLevel, entity);
            for (Hold hold : suspended.holds) {
                LivingEntity target = hold.resolve(serverLevel);
                if (target == null) continue;
                DamageSources.ignoreNextKnockback(target);
                DamageSources.applyDamage(target, damage, getDamageSource(entity));
                target.addEffect(new MobEffectInstance(MagiaEffects.CRUCIATUS, EFFECT_TICKS, 0, false, false, true), entity);
            }
            sound(entity, SoundEvents.WARDEN_HEARTBEAT, 2.0f, 0.6f);
            MagiaNetwork.sendVisual(entity, entity, SpellVisualPayload.Kind.DOLOR_UNIVERSUS, EFFECT_TICKS,
                    entity.position(), radius(spellLevel));
        }
        super.onCast(level, spellLevel, entity, castSource, playerMagicData);
    }

    private static Suspended gather(ServerLevel level, LivingEntity caster, int spellLevel) {
        int radius = radius(spellLevel);
        double radiusSqr = radius * radius;
        double lift = 1.5 + 0.3 * (spellLevel - 1);
        List<LivingEntity> victims = level.getEntitiesOfClass(LivingEntity.class, caster.getBoundingBox().inflate(radius),
                target -> target != caster && target.isAlive() && !target.isSpectator() && !untouchable(target)
                        && !(target instanceof Player player && player.isCreative())
                        && !Displacement.isImmune(target)
                        && target.distanceToSqr(caster) <= radiusSqr);
        victims.sort(Comparator.comparingDouble(target -> target.distanceToSqr(caster)));

        List<Hold> holds = new ArrayList<>();
        for (LivingEntity victim : victims.subList(0, Math.min(MAX_TARGETS, victims.size()))) {
            holds.add(new Hold(victim.getUUID(), victim.position().add(0, lift, 0)));
        }
        return new Suspended(holds);
    }

    private float pulseDamage(int spellLevel, @Nullable LivingEntity caster) {
        return getSpellPower(spellLevel, caster) * 0.5f;
    }

    /** 5 blocos no nivel 1, +2 por nivel (13 no 5). */
    private static int radius(int spellLevel) {
        return 5 + 2 * (spellLevel - 1);
    }

    /** Quem esta suspenso nesta canalizacao, e onde. */
    private record Suspended(List<Hold> holds) implements ICastData {
        @Override
        public void reset() {
        }
    }

    private record Hold(UUID id, Vec3 hover) {
        /** Morto, sumido ou arrancado para longe (teleporte) sai da suspensao. */
        @Nullable
        LivingEntity resolve(ServerLevel level) {
            return level.getEntity(id) instanceof LivingEntity living && living.isAlive()
                    && living.position().distanceToSqr(hover) < 64 ? living : null;
        }
    }
}
