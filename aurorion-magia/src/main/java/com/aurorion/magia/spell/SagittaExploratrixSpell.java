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
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.List;
import java.util.Optional;

/**
 * Sagitta Exploratrix — Flecha de Reconhecimento. O reconhecimento do Sova: encanta a proxima flecha do
 * arco. Onde ela cravar, ela pulsa {@value #PULSES} vezes, uma por segundo, e cada pulso revela os
 * inimigos a vista dela: eles brilham atraves das paredes por alguns segundos.
 *
 * <p>So revela quem a flecha "ve" — parede entre a flecha e a pessoa esconde, como no jogo. Exige arco
 * (ou besta) na mao. Ver {@link SovaArrows}.
 */
public final class SagittaExploratrixSpell extends AurorionSpell {
    public static final int PULSE_TICKS = 20;
    public static final int PULSES = 2;
    private static final float RADIUS = 15;
    private static final int MAX_TARGETS = 24;

    public SagittaExploratrixSpell() {
        super("sagitta_exploratrix", SchoolRegistry.LIGHTNING_RESOURCE, SpellRarity.RARE, 3, 30, CastType.INSTANT);
        this.baseSpellPower = 1;
        this.spellPowerPerLevel = 0;
        this.baseManaCost = 30;
        this.manaCostPerLevel = 5;
        this.castTime = 0;
    }

    @Override
    public AnimationHolder getCastStartAnimation() {
        return SpellAnimations.BOW_CHARGE_ANIMATION;
    }

    @Override
    public Optional<SoundEvent> getCastFinishSound() {
        return Optional.of(SoundEvents.AMETHYST_BLOCK_RESONATE);
    }

    @Override
    public Vector3f getTargetingColor() {
        return new Vector3f(0.45f, 0.8f, 1.0f);
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, @Nullable LivingEntity caster) {
        return List.of(
                Component.translatable("ui.aurorion_magia.revela", Utils.timeFromTicks(revealTicks(spellLevel), 1)),
                Component.translatable("ui.aurorion_magia.raio", (int) RADIUS),
                Component.translatable("ui.aurorion_magia.proxima_flecha"),
                Component.translatable("ui.aurorion_magia.exige_arco"));
    }

    @Override
    public boolean checkPreCastConditions(Level level, int spellLevel, LivingEntity entity, MagicData playerMagicData) {
        return WeaponGate.requireBow(entity);
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource castSource, MagicData playerMagicData) {
        if (level instanceof ServerLevel) SovaArrows.imbue(entity, SovaArrows.RECON, revealTicks(spellLevel), RADIUS);
        super.onCast(level, spellLevel, entity, castSource, playerMagicData);
    }

    /** Um pulso: revela quem a flecha consegue ver. */
    public static void pulse(ServerLevel level, LivingEntity caster, Vec3 at, double radius, int revealTicks) {
        Vec3 eye = at.add(0, 0.3, 0);
        for (LivingEntity victim : Hits.around(level, caster, at, radius, MAX_TARGETS, t -> Hits.enemy(caster, t))) {
            Vec3 body = victim.position().add(0, victim.getBbHeight() * 0.6, 0);
            if (level.clip(new ClipContext(eye, body, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, victim))
                    .getType() != HitResult.Type.MISS) continue;
            victim.addEffect(new MobEffectInstance(MobEffects.GLOWING, revealTicks, 0, false, false, true), caster);
        }
        sound(level, at, SoundEvents.SCULK_CLICKING, 1.4f, 1.6f);
        MagiaNetwork.sendVisualAt(level, caster, SpellVisualPayload.Kind.SAGITTA_PULSE, 20, at, (float) radius);
    }

    /** Base: 4 s no nivel 1, +1 s por nivel. SpellBalance dobra este tempo. */
    private static int revealTicks(int spellLevel) {
        return SpellBalance.duration(60 + 20 * spellLevel);
    }
}
