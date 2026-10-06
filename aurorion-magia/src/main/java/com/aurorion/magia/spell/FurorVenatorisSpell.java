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
import io.redspace.ironsspellbooks.capabilities.magic.RecastInstance;
import io.redspace.ironsspellbooks.damage.DamageSources;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.List;
import java.util.Optional;

/**
 * Furor Venatoris — Furia do Cacador. A ultimate do Sova: tres disparos de energia, cada um uma
 * conjuracao, em ate {@value #WINDOW_TICKS} ticks. Cada disparo sai do arco em linha reta por
 * {@value #RANGE} blocos e <b>atravessa paredes</b>, ferindo e revelando (Brilho) todos no caminho.
 *
 * <p>Usa a reconjuracao do Iron's: a recarga so comeca depois do terceiro disparo, ou quando o prazo
 * acaba. Exige arco (ou besta) na mao.
 */
public final class FurorVenatorisSpell extends AurorionSpell {
    private static final int RANGE = 60;
    private static final int SHOTS = 3;
    private static final int WINDOW_TICKS = 120;
    private static final double WIDTH = 0.7;
    private static final int MAX_TARGETS = 16;
    private static final int REVEAL_TICKS = 80;

    public FurorVenatorisSpell() {
        super("furor_venatoris", SchoolRegistry.LIGHTNING_RESOURCE, SpellRarity.LEGENDARY, 3, 80, CastType.INSTANT);
        this.baseSpellPower = 10;
        this.spellPowerPerLevel = 4;
        this.baseManaCost = 120;
        this.manaCostPerLevel = 25;
        this.castTime = 0;
    }

    @Override
    public AnimationHolder getCastStartAnimation() {
        return SpellAnimations.BOW_CHARGE_ANIMATION;
    }

    @Override
    public Optional<SoundEvent> getCastFinishSound() {
        return Optional.of(SoundEvents.CROSSBOW_SHOOT);
    }

    @Override
    public Vector3f getTargetingColor() {
        return new Vector3f(0.35f, 0.75f, 1.0f);
    }

    @Override
    public int getRecastCount(int spellLevel, @Nullable LivingEntity entity) {
        return SHOTS;
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, @Nullable LivingEntity caster) {
        return List.of(
                Component.translatable("ui.aurorion_magia.dano", Utils.stringTruncation(getSpellPower(spellLevel, caster), 1)),
                Component.translatable("ui.aurorion_magia.disparos", SHOTS),
                Component.translatable("ui.aurorion_magia.alcance", RANGE),
                Component.translatable("ui.aurorion_magia.atravessa_paredes"),
                Component.translatable("ui.aurorion_magia.exige_arco"));
    }

    @Override
    public boolean checkPreCastConditions(Level level, int spellLevel, LivingEntity entity, MagicData playerMagicData) {
        return WeaponGate.requireBow(entity);
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource castSource, MagicData playerMagicData) {
        if (level instanceof ServerLevel serverLevel) {
            if (playerMagicData != null && entity instanceof ServerPlayer
                    && !playerMagicData.getPlayerRecasts().hasRecastForSpell(getSpellId())) {
                playerMagicData.getPlayerRecasts().addRecast(new RecastInstance(getSpellId(), spellLevel, SHOTS,
                        WINDOW_TICKS, castSource, null), playerMagicData);
            }
            shoot(serverLevel, entity, getSpellPower(spellLevel, entity));
        }
        super.onCast(level, spellLevel, entity, castSource, playerMagicData);
    }

    private void shoot(ServerLevel level, LivingEntity caster, float damage) {
        Vec3 from = caster.getEyePosition().subtract(0, 0.2, 0);
        Vec3 end = Hits.beamEnd(level, caster, from, caster.getLookAngle(), RANGE, true);
        for (LivingEntity victim : Hits.along(level, caster, from, end, WIDTH, MAX_TARGETS, t -> Hits.enemy(caster, t))) {
            FriendlyFire.applyDamage(victim, damage, getDamageSource(caster));
            if (victim.isAlive()) {
                victim.addEffect(new MobEffectInstance(MobEffects.GLOWING, REVEAL_TICKS, 0, false, false, true), caster);
            }
        }
        sound(caster, SoundEvents.TRIDENT_RIPTIDE_3.value(), 1.2f, 1.6f);
        sound(caster, SoundEvents.BEACON_DEACTIVATE, 1.0f, 2.0f);
        MagiaNetwork.sendVisual(caster, caster, SpellVisualPayload.Kind.FUROR_VENATORIS, 14, end, (float) WIDTH);
    }
}
