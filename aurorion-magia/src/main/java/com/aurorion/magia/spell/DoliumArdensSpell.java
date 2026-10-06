package com.aurorion.magia.spell;

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
 * Dolium Ardens — Barril Explosivo. A ultimate do Gragas: um barril voa em arco ate o ponto mirado e
 * explode, ferindo os inimigos no raio e jogando todos para <b>longe do centro</b> — quanto mais perto
 * do estouro, mais longe vao.
 *
 * <p>Nao quebra bloco nem poe fogo: a explosao e de magia, nao de polvora. Aliados nao sao feridos nem
 * empurrados. Chefes levam o dano e ficam no lugar.
 */
public final class DoliumArdensSpell extends AurorionSpell {
    private static final int RANGE = 30;
    private static final int MAX_TARGETS = 24;

    public DoliumArdensSpell() {
        super("dolium_ardens", SchoolRegistry.FIRE_RESOURCE, SpellRarity.EPIC, 3, 35, CastType.INSTANT);
        this.baseSpellPower = 8;
        this.spellPowerPerLevel = 3;
        this.baseManaCost = 60;
        this.manaCostPerLevel = 15;
        this.castTime = 0;
    }

    @Override
    public AnimationHolder getCastStartAnimation() {
        return SpellAnimations.ANIMATION_CHARGED_CAST;
    }

    @Override
    public Optional<SoundEvent> getCastFinishSound() {
        return Optional.of(SoundEvents.WITCH_THROW);
    }

    @Override
    public Vector3f getTargetingColor() {
        return new Vector3f(1.0f, 0.55f, 0.15f);
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, @Nullable LivingEntity caster) {
        return List.of(
                Component.translatable("ui.aurorion_magia.dano", Utils.stringTruncation(getSpellPower(spellLevel, caster), 1)),
                Component.translatable("ui.aurorion_magia.raio", Utils.stringTruncation(radius(spellLevel), 1)),
                Component.translatable("ui.aurorion_magia.alcance", RANGE),
                Component.translatable("ui.aurorion_magia.afasta_do_centro"));
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource castSource, MagicData playerMagicData) {
        if (level instanceof ServerLevel serverLevel) {
            Vec3 at = Hits.aimGround(serverLevel, entity, RANGE);
            Vec3 from = entity.getEyePosition().add(entity.getLookAngle().scale(0.8));
            int ticks = Math.clamp((int) (from.distanceTo(at) * 0.9), 8, 26);
            MagiaProjectileEntity.launch(serverLevel, entity, MagiaProjectileEntity.Shape.DOLIUM, from,
                    MagiaProjectileEntity.arc(from, at, ticks, MagiaProjectileEntity.Shape.DOLIUM),
                    0.9f, getSpellPower(spellLevel, entity), radius(spellLevel), ticks + 30);
        }
        super.onCast(level, spellLevel, entity, castSource, playerMagicData);
    }

    /** O barril estourou. */
    public static void burst(ServerLevel level, LivingEntity caster, Vec3 at, double radius, float damage) {
        for (LivingEntity victim : Hits.around(level, caster, at, radius, MAX_TARGETS, t -> Hits.enemy(caster, t))) {
            DamageSources.ignoreNextKnockback(victim);
            FriendlyFire.applyDamage(victim, damage, MagiaSpells.DOLIUM_ARDENS.get().getDamageSource(caster));
            if (!victim.isAlive() || Displacement.isImmune(victim)) continue;
            Vec3 outward = victim.position().subtract(at).multiply(1, 0, 1);
            double distance = outward.length();
            if (distance < 0.05) {
                outward = new Vec3(Math.cos(victim.getId()), 0, Math.sin(victim.getId()));
                distance = 1;
            }
            double force = 1.4 - 0.8 * Math.min(1, distance / radius);
            launch(victim, outward.scale(force / distance).add(0, 0.55, 0));
            victim.resetFallDistance();
        }
        sound(level, at, SoundEvents.GENERIC_EXPLODE.value(), 1.6f, 0.9f);
        sound(level, at, SoundEvents.WOOD_BREAK, 1.4f, 0.6f);
        MagiaNetwork.sendVisualAt(level, caster, SpellVisualPayload.Kind.DOLIUM_BLAST, 24, at, (float) radius);
    }

    /** 5 blocos no nivel 1, +0,5 por nivel. */
    private static float radius(int spellLevel) {
        return 4.5f + 0.5f * spellLevel;
    }
}
