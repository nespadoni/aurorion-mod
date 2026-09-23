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
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.List;
import java.util.Optional;

/**
 * Manus Carnificis — Mao do Algoz. Telecinese controlavel: segure o botao, conduza o alvo com a mira,
 * solte para arremessar.
 *
 * <p>Canalizacao de ate 5 s. O movimento roda em {@link #onServerCastTick} — todo tick, mas so para
 * quem esta canalizando esta magia e so sobre UM alvo; e o que a propria telecinese do Iron's faz. A
 * fisica esta em {@link Telekinesis}.
 */
public final class ManusCarnificisSpell extends AurorionSpell {
    private static final int RANGE = 16;
    private static final double DROP_RANGE_SQR = (RANGE * 1.5) * (RANGE * 1.5);

    public ManusCarnificisSpell() {
        super("manus_carnificis", SchoolRegistry.EVOCATION_RESOURCE, SpellRarity.EPIC, 5, 25, CastType.CONTINUOUS);
        this.baseSpellPower = 1;
        this.spellPowerPerLevel = 1;
        this.baseManaCost = 5;
        this.manaCostPerLevel = 1;
        this.castTime = 100;
    }

    @Override
    public AnimationHolder getCastStartAnimation() {
        return SpellAnimations.ANIMATION_CONTINUOUS_CAST;
    }

    @Override
    public AnimationHolder getCastFinishAnimation() {
        return AnimationHolder.pass();
    }

    @Override
    public Optional<SoundEvent> getCastStartSound() {
        return Optional.of(SoundEvents.EVOKER_PREPARE_SUMMON);
    }

    @Override
    public Optional<SoundEvent> getCastFinishSound() {
        return Optional.empty();
    }

    @Override
    public Vector3f getTargetingColor() {
        return new Vector3f(0.72f, 0.62f, 1.0f);
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, @Nullable LivingEntity caster) {
        return List.of(
                Component.translatable("ui.aurorion_magia.duracao", Utils.timeFromTicks(castTime, 1)),
                Component.translatable("ui.aurorion_magia.alcance", RANGE));
    }

    @Override
    public boolean checkPreCastConditions(Level level, int spellLevel, LivingEntity entity, MagicData playerMagicData) {
        return aim(level, entity, playerMagicData, RANGE, false,
                target -> !Displacement.isImmune(target) && !(target instanceof Player p && (p.isCreative() || p.isSpectator())));
    }

    @Override
    public void onServerCastTick(Level level, int spellLevel, LivingEntity entity, MagicData playerMagicData) {
        super.onServerCastTick(level, spellLevel, entity, playerMagicData);
        if (!(level instanceof ServerLevel serverLevel)) return;
        LivingEntity target = target(serverLevel, entity, playerMagicData);
        if (target != null && entity.distanceToSqr(target) <= DROP_RANGE_SQR) Telekinesis.hold(entity, target);
    }

    /** A cada pulso de canalizacao, so o visual; o movimento e do tick. */
    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource castSource, MagicData playerMagicData) {
        if (level instanceof ServerLevel serverLevel) {
            LivingEntity target = target(serverLevel, entity, playerMagicData);
            if (target != null) {
                MagiaNetwork.sendVisual(entity, target, SpellVisualPayload.Kind.MANUS_GRIP,
                        MagicManager.CONTINUOUS_CAST_TICK_INTERVAL + 5);
                sound(target, SoundEvents.BEACON_AMBIENT, 0.5f, 1.8f);
            }
        }
        super.onCast(level, spellLevel, entity, castSource, playerMagicData);
    }

    @Override
    public void onServerCastComplete(Level level, int spellLevel, LivingEntity entity, MagicData playerMagicData,
                                     boolean cancelled) {
        if (level instanceof ServerLevel serverLevel) {
            LivingEntity target = target(serverLevel, entity, playerMagicData);
            if (target != null) {
                Telekinesis.release(entity, target, spellLevel);
                sound(target, SoundEvents.PLAYER_ATTACK_SWEEP, 1.0f, 0.6f);
            }
        }
        super.onServerCastComplete(level, spellLevel, entity, playerMagicData, cancelled);
    }
}
