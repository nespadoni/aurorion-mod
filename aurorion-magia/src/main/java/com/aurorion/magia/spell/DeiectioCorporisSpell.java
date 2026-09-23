package com.aurorion.magia.spell;

import com.aurorion.magia.network.MagiaNetwork;
import com.aurorion.magia.network.SpellVisualPayload;
import com.aurorion.magia.registry.MagiaEffects;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.SchoolRegistry;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.CastType;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import io.redspace.ironsspellbooks.api.util.Utils;
import io.redspace.ironsspellbooks.damage.DamageSources;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.List;

/**
 * Deiectio Corporis — Queda Forcada. Manda o alvo para o chao.
 *
 * <ul>
 *   <li><b>No ar</b> (pulando, levitando, de elytra, voando por habilidade de sobrevivencia, sendo
 *       carregado pela Mao do Algoz): tira a elytra, a levitacao e o voo, e crava o alvo para baixo.
 *       A altura vira dano de queda, pelo vanilla — de bem alto, mata.</li>
 *   <li><b>No chao</b>: um impacto pequeno e {@code abatido} (sem pular) por alguns segundos.</li>
 * </ul>
 *
 * <p>E o contra natural de varias outras magias de movimento — inclusive das nossas.
 */
public final class DeiectioCorporisSpell extends AurorionSpell {
    private static final int RANGE = 24;

    public DeiectioCorporisSpell() {
        super("deiectio_corporis", SchoolRegistry.ENDER_RESOURCE, SpellRarity.UNCOMMON, 5, 14, CastType.INSTANT);
        this.baseSpellPower = 2;
        this.spellPowerPerLevel = 1;
        this.baseManaCost = 30;
        this.manaCostPerLevel = 5;
        this.castTime = 0;
    }

    @Override
    public Vector3f getTargetingColor() {
        return new Vector3f(0.5f, 0.35f, 0.75f);
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, @Nullable LivingEntity caster) {
        return List.of(
                Component.translatable("ui.aurorion_magia.dano_impacto",
                        Utils.stringTruncation(getSpellPower(spellLevel, caster), 1)),
                Component.translatable("ui.aurorion_magia.sem_pulo", Utils.timeFromTicks(groundedTicks(spellLevel), 1)));
    }

    @Override
    public boolean checkPreCastConditions(Level level, int spellLevel, LivingEntity entity, MagicData playerMagicData) {
        return aim(level, entity, playerMagicData, RANGE, false, target -> !Displacement.isImmune(target));
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource castSource, MagicData playerMagicData) {
        if (level instanceof ServerLevel serverLevel) {
            LivingEntity target = target(serverLevel, entity, playerMagicData);
            if (target != null) slam(entity, target, spellLevel);
        }
        super.onCast(level, spellLevel, entity, castSource, playerMagicData);
    }

    private void slam(LivingEntity caster, LivingEntity target, int spellLevel) {
        if (isAirborne(target)) {
            target.removeEffect(MobEffects.LEVITATION);
            target.removeEffect(MobEffects.SLOW_FALLING);
            if (target instanceof ServerPlayer player) {
                player.stopFallFlying();
                if (!player.isCreative() && !player.isSpectator() && player.getAbilities().flying) {
                    player.getAbilities().flying = false;
                    player.onUpdateAbilities();
                }
            }
            Vec3 motion = motionOf(target);
            launch(target, new Vec3(motion.x * 0.2, -(2.2 + 0.3 * spellLevel), motion.z * 0.2));
            sound(target, SoundEvents.PHANTOM_SWOOP, 1.2f, 0.6f);
        } else {
            DamageSources.applyDamage(target, getSpellPower(spellLevel, caster), getDamageSource(caster));
            target.addEffect(new MobEffectInstance(MagiaEffects.GROUNDED, groundedTicks(spellLevel), 0,
                    false, false, true), caster);
            launch(target, new Vec3(0, -0.6, 0));
            sound(target, SoundEvents.ANVIL_LAND, 0.6f, 0.5f);
        }
        MagiaNetwork.sendVisual(caster, target, SpellVisualPayload.Kind.DEIECTIO, 40);
    }

    private static boolean isAirborne(LivingEntity target) {
        return !target.onGround() || target.isFallFlying() || target.hasEffect(MobEffects.LEVITATION)
                || target instanceof Player player && player.getAbilities().flying;
    }

    /** 1,5 s no nivel 1, +0,5 s por nivel. */
    private static int groundedTicks(int spellLevel) {
        return 30 + 10 * (spellLevel - 1);
    }
}
