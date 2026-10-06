package com.aurorion.magia.spell;

import com.aurorion.magia.entity.MagiaProjectileEntity;
import com.aurorion.magia.entity.ShadowEntity;
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
 * Stellae Laminatae — Shuriken Laminado. O Q do Zed: quem conjura e cada Sombra Viva arremessam um
 * shuriken na direcao em que quem conjura esta mirando — as sombras miram o mesmo ponto, entao os
 * shurikens cruzam o alvo. Cada shuriken atravessa todo mundo no caminho e para no primeiro bloco.
 */
public final class StellaeLaminataeSpell extends AurorionSpell {
    private static final int RANGE = 30;
    private static final double SPEED = 1.6;

    public StellaeLaminataeSpell() {
        super("stellae_laminatae", SchoolRegistry.ENDER_RESOURCE, SpellRarity.RARE, 5, 6, CastType.INSTANT);
        this.baseSpellPower = 5;
        this.spellPowerPerLevel = 2;
        this.baseManaCost = 30;
        this.manaCostPerLevel = 3;
        this.castTime = 0;
    }

    @Override
    public AnimationHolder getCastStartAnimation() {
        return SpellAnimations.THROW_SINGLE_ITEM;
    }

    @Override
    public Optional<SoundEvent> getCastFinishSound() {
        return Optional.of(SoundEvents.TRIDENT_THROW.value());
    }

    @Override
    public Vector3f getTargetingColor() {
        return new Vector3f(0.75f, 0.75f, 0.8f);
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, @Nullable LivingEntity caster) {
        return List.of(
                Component.translatable("ui.aurorion_magia.dano", Utils.stringTruncation(getSpellPower(spellLevel, caster), 1)),
                Component.translatable("ui.aurorion_magia.alcance", RANGE),
                Component.translatable("ui.aurorion_magia.atravessa"),
                Component.translatable("ui.aurorion_magia.sai_das_sombras"));
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource castSource, MagicData playerMagicData) {
        if (level instanceof ServerLevel serverLevel) {
            float damage = getSpellPower(spellLevel, entity);
            Vec3 aim = Hits.aimPoint(serverLevel, entity, RANGE);
            Vec3 from = entity.getEyePosition().subtract(0, 0.2, 0);
            throwStar(serverLevel, entity, from, aim.subtract(from), damage);
            for (ShadowEntity shadow : ShadowEntity.of(serverLevel, entity)) {
                Vec3 shadowFrom = shadow.position().add(0, 1.4, 0);
                throwStar(serverLevel, entity, shadowFrom, aim.subtract(shadowFrom), damage);
                sound(shadow, SoundEvents.TRIDENT_THROW.value(), 0.6f, 1.4f);
            }
        }
        super.onCast(level, spellLevel, entity, castSource, playerMagicData);
    }

    private static void throwStar(ServerLevel level, LivingEntity owner, Vec3 from, Vec3 direction, float damage) {
        if (direction.lengthSqr() < 1.0E-4) return;
        MagiaProjectileEntity.launch(level, owner, MagiaProjectileEntity.Shape.STELLA, from,
                direction.normalize().scale(SPEED), 0.5f, damage, 0, (int) (RANGE / SPEED));
    }

    /** Um shuriken cortou alguem. */
    public static void cut(ServerLevel level, LivingEntity owner, LivingEntity victim, float damage, long stamp) {
        FriendlyFire.applyDamage(victim, damage, MagiaSpells.STELLAE_LAMINATAE.get().getDamageSource(owner));
        UmbraVivaSpell.energy(owner, victim, stamp);
    }
}
