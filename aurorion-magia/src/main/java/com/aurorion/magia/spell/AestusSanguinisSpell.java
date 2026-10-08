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
import io.redspace.ironsspellbooks.damage.DamageSources;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.List;
import java.util.Optional;

/**
 * Aestus Sanguinis — Mare de Sangue. O E do Vladimir: segurando a conjuracao, quem conjura paga vida a
 * cada meio segundo para encher uma reserva de sangue (ate um segundo e meio). Ao soltar, a reserva
 * estoura em volta dele: o dano cresce com a carga, e com a carga cheia quem foi atingido fica lento.
 *
 * <p>Como no jogo, a mare e bloqueada: so quem esta a vista de quem conjurou e atingido — parede e
 * coluna protegem.
 */
public final class AestusSanguinisSpell extends AurorionSpell {
    private static final int RADIUS = 8;
    private static final int MAX_TARGETS = 16;
    /** Vida paga por pulso, em fracao da vida maxima. */
    private static final float HEALTH_PER_PULSE = 0.04f;

    public AestusSanguinisSpell() {
        super("aestus_sanguinis", SchoolRegistry.BLOOD_RESOURCE, SpellRarity.EPIC, 5, 12, CastType.CONTINUOUS);
        this.baseSpellPower = 6;
        this.spellPowerPerLevel = 2;
        this.baseManaCost = 5;
        this.manaCostPerLevel = 1;
        this.castTime = 60;
    }

    @Override
    public AnimationHolder getCastStartAnimation() {
        return SpellAnimations.ANIMATION_CONTINUOUS_CAST_ONE_HANDED;
    }

    // Sem getCastFinishAnimation: ver o aviso no DolorCruciatusSpell.

    @Override
    public Optional<SoundEvent> getCastStartSound() {
        return Optional.of(SoundEvents.WARDEN_HEARTBEAT);
    }

    @Override
    public Optional<SoundEvent> getCastFinishSound() {
        return Optional.empty();
    }

    @Override
    public Vector3f getTargetingColor() {
        return new Vector3f(0.7f, 0.0f, 0.1f);
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, @Nullable LivingEntity caster) {
        return List.of(
                Component.translatable("ui.aurorion_magia.dano_maximo",
                        Utils.stringTruncation(damagePower(spellLevel, caster) * 1.5f, 1)),
                Component.translatable("ui.aurorion_magia.raio", RADIUS),
                Component.translatable("ui.aurorion_magia.custo_vida_carga", (int) (HEALTH_PER_PULSE * 100)),
                Component.translatable("ui.aurorion_magia.bloqueada_paredes"));
    }

    /** Cada pulso da carga: o sangue sai de quem conjura. */
    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource castSource, MagicData playerMagicData) {
        if (level instanceof ServerLevel && playerMagicData != null && playerMagicData.isCasting()) {
            float cost = entity.getMaxHealth() * HEALTH_PER_PULSE;
            if (entity.getHealth() > cost + 1) entity.setHealth(entity.getHealth() - cost);
            MagiaNetwork.sendVisual(entity, entity, SpellVisualPayload.Kind.AESTUS_CHARGE, 15, entity.position(),
                    charge(playerMagicData));
        }
        super.onCast(level, spellLevel, entity, castSource, playerMagicData);
    }

    @Override
    public void onServerCastComplete(Level level, int spellLevel, LivingEntity entity, MagicData playerMagicData,
                                     boolean cancelled) {
        float charge = playerMagicData == null ? 1 : charge(playerMagicData);
        super.onServerCastComplete(level, spellLevel, entity, playerMagicData, cancelled);
        if (!(level instanceof ServerLevel serverLevel)) return;
        MagiaNetwork.sendVisual(entity, entity, SpellVisualPayload.Kind.AESTUS_CHARGE, 0, entity.position(), 0);
        // Interrompida (estase, silencio, morte): o sangue se perde, nao estoura.
        if (charge < 0.05f || castBlocked(entity)) return;

        float damage = damagePower(spellLevel, entity) * (0.5f + charge);
        for (LivingEntity victim : Hits.around(serverLevel, entity, entity.position(), RADIUS, MAX_TARGETS,
                t -> Hits.enemy(entity, t) && entity.hasLineOfSight(t))) {
            FriendlyFire.applyDamage(victim, damage, getDamageSource(entity));
            if (charge >= 0.95f && victim.isAlive()) {
                victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, 3, false, false, true), entity);
            }
        }
        sound(entity, SoundEvents.WARDEN_SONIC_BOOM, 1.0f, 1.4f);
        sound(entity, SoundEvents.GENERIC_SPLASH, 1.4f, 0.5f);
        MagiaNetwork.sendVisualAt(serverLevel, entity, SpellVisualPayload.Kind.AESTUS_BURST, 16, entity.position(), RADIUS);
    }

    private static float charge(MagicData data) {
        int duration = Math.max(1, data.getCastDuration());
        return Mth.clamp((duration - data.getCastDurationRemaining()) / (float) duration, 0, 1);
    }
}
