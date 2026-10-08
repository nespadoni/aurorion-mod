package com.aurorion.magia.spell;

import com.aurorion.magia.AurorionMagia;
import com.aurorion.magia.config.MagiaConfig;
import com.aurorion.magia.network.MagiaNetwork;
import com.aurorion.magia.network.SpellVisualPayload;
import com.aurorion.magia.registry.MagiaEffects;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.SchoolRegistry;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.CastType;
import io.redspace.ironsspellbooks.api.spells.SpellAnimations;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import io.redspace.ironsspellbooks.api.util.AnimationHolder;
import io.redspace.ironsspellbooks.api.util.Utils;
import io.redspace.ironsspellbooks.damage.DamageSources;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.List;
import java.util.Optional;

/**
 * Iustitia Demaciae — Justica Demaciana. <b>Magia proibida.</b> A ultimate do Garen, e so sai com uma
 * <b>espada na mao</b>.
 *
 * <p>Quem conjura ergue a espada enquanto concentra e a desce num golpe por cima da cabeca — a mesma
 * engine de animacao (playerAnimator) do Better Combat, pelas animacoes de golpe que o Iron's ja traz.
 * No alvo, uma espada dourada gigante cai do ceu.
 *
 * <p>O golpe cresce com a vida que falta ao alvo: base + {@value #MISSING_RATIO} da vida perdida, como
 * dano de magia comum. Abaixo do limiar ({@code limiarJustica}: 25/30/35% por nivel) ele e
 * <b>executado</b>, e totem nao salva — mas estase, inalvejavel e area sem PvP seguram ate a execucao.
 */
public final class IustitiaDemaciaeSpell extends AurorionSpell {
    public static final ResourceKey<DamageType> DAMAGE_TYPE =
            ResourceKey.create(Registries.DAMAGE_TYPE, AurorionMagia.id("iustitia_demaciae"));
    private static final int RANGE = 10;
    private static final float MISSING_RATIO = 0.3f;

    public IustitiaDemaciaeSpell() {
        super("iustitia_demaciae", SchoolRegistry.HOLY_RESOURCE, SpellRarity.LEGENDARY, 3, 60, CastType.LONG, true);
        this.baseSpellPower = 8;
        this.spellPowerPerLevel = 4;
        this.baseManaCost = 80;
        this.manaCostPerLevel = 20;
        this.castTime = 15;
    }

    @Override
    public AnimationHolder getCastStartAnimation() {
        return SpellAnimations.CHARGE_RAISED_HAND;
    }

    @Override
    public AnimationHolder getCastFinishAnimation() {
        return SpellAnimations.OVERHEAD_MELEE_SWING_ANIMATION;
    }

    @Override
    public Optional<SoundEvent> getCastStartSound() {
        return Optional.of(SoundEvents.BEACON_POWER_SELECT);
    }

    @Override
    public Optional<SoundEvent> getCastFinishSound() {
        return Optional.of(SoundEvents.PLAYER_ATTACK_STRONG);
    }

    @Override
    public Vector3f getTargetingColor() {
        return new Vector3f(1.0f, 0.85f, 0.35f);
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, @Nullable LivingEntity caster) {
        return List.of(
                Component.translatable("ui.aurorion_magia.dano", Utils.stringTruncation(damagePower(spellLevel, caster), 1)),
                Component.translatable("ui.aurorion_magia.vida_que_falta", (int) (MISSING_RATIO * 100)),
                Component.translatable("ui.aurorion_magia.execucao", (int) Math.round(threshold(spellLevel) * 100)),
                Component.translatable("ui.aurorion_magia.exige_espada"),
                Component.translatable("ui.aurorion_magia.proibida"));
    }

    @Override
    public boolean checkPreCastConditions(Level level, int spellLevel, LivingEntity entity, MagicData playerMagicData) {
        return WeaponGate.requireSword(entity)
                && aim(level, entity, playerMagicData, RANGE, false, target -> Hits.hittable(entity, target));
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource castSource, MagicData playerMagicData) {
        if (level instanceof ServerLevel serverLevel) {
            LivingEntity target = target(serverLevel, entity, playerMagicData);
            // A mira foi conferida no comeco da concentracao: tres quartos de segundo depois o alvo pode
            // ter entrado em estase, na Poca de Sangue, no criativo ou numa area sem PvP, ou ter fugido.
            // E a espada pode ter saido da mao: sem ela, o golpe nao desce.
            if (target != null && WeaponGate.holdsSword(entity) && Hits.hittable(entity, target)
                    && !target.hasEffect(MagiaEffects.STASIS)
                    && target.distanceToSqr(entity) <= (RANGE + 4) * (RANGE + 4)) {
                judge(serverLevel, entity, target, spellLevel);
            }
        }
        super.onCast(level, spellLevel, entity, castSource, playerMagicData);
    }

    private void judge(ServerLevel level, LivingEntity caster, LivingEntity target, int spellLevel) {
        MagiaNetwork.sendVisual(caster, target, SpellVisualPayload.Kind.IUSTITIA_DEMACIAE, 30);
        sound(target, SoundEvents.LIGHTNING_BOLT_IMPACT, 1.2f, 0.8f);
        sound(target, SoundEvents.ANVIL_LAND, 1.0f, 0.5f);
        if (Execution.below(target, threshold(spellLevel))) {
            Execution.execute(level, caster, target, DAMAGE_TYPE);
            return;
        }
        // Fora da execucao, o golpe e dano de magia comum: so a execucao ignora totem e invulnerabilidade.
        float damage = damagePower(spellLevel, caster) + Execution.missing(target) * target.getMaxHealth() * MISSING_RATIO;
        FriendlyFire.applyDamage(target, damage, getDamageSource(caster));
    }

    private static double threshold(int spellLevel) {
        return MagiaConfig.IUSTITIA_THRESHOLD.get() + 0.05 * (spellLevel - 1);
    }
}
