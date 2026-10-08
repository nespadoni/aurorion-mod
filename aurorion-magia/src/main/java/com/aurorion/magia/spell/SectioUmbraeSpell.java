package com.aurorion.magia.spell;

import com.aurorion.magia.entity.ShadowEntity;
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
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
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
 * Sectio Umbrae — Corte Sombrio. O E do Zed: quem conjura e cada Sombra Viva giram as laminas ao mesmo
 * tempo, ferindo todo inimigo a {@value #RADIUS} blocos de cada um. Cada inimigo toma o dano uma vez
 * so, mesmo que esteja no alcance de varios cortes; quem foi cortado por uma <b>sombra</b> fica lento.
 */
public final class SectioUmbraeSpell extends AurorionSpell {
    private static final double RADIUS = 3.5;
    private static final int MAX_TARGETS = 12;
    private static final int SLOW_TICKS = 80;

    public SectioUmbraeSpell() {
        super("sectio_umbrae", SchoolRegistry.ENDER_RESOURCE, SpellRarity.RARE, 5, 5, CastType.INSTANT);
        this.baseSpellPower = 4;
        this.spellPowerPerLevel = 2;
        this.baseManaCost = 25;
        this.manaCostPerLevel = 3;
        this.castTime = 0;
    }

    @Override
    public AnimationHolder getCastStartAnimation() {
        return SpellAnimations.ONE_HANDED_HORIZONTAL_SWING_ANIMATION;
    }

    @Override
    public Optional<SoundEvent> getCastFinishSound() {
        return Optional.of(SoundEvents.PLAYER_ATTACK_SWEEP);
    }

    @Override
    public Vector3f getTargetingColor() {
        return new Vector3f(0.6f, 0.05f, 0.1f);
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, @Nullable LivingEntity caster) {
        return List.of(
                Component.translatable("ui.aurorion_magia.dano", Utils.stringTruncation(damagePower(spellLevel, caster), 1)),
                Component.translatable("ui.aurorion_magia.raio", Utils.stringTruncation(RADIUS, 1)),
                Component.translatable("ui.aurorion_magia.sai_das_sombras"),
                Component.translatable("ui.aurorion_magia.sombra_lentidao"));
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource castSource, MagicData playerMagicData) {
        if (level instanceof ServerLevel serverLevel) {
            long stamp = serverLevel.getGameTime();
            float damage = damagePower(spellLevel, entity);
            IntSet struck = new IntOpenHashSet();
            slash(serverLevel, entity, entity.position(), damage, false, stamp, struck);
            for (ShadowEntity shadow : ShadowEntity.of(serverLevel, entity)) {
                slash(serverLevel, entity, shadow.position(), damage, true, stamp, struck);
                sound(shadow, SoundEvents.PLAYER_ATTACK_SWEEP, 0.7f, 0.8f);
            }
        }
        super.onCast(level, spellLevel, entity, castSource, playerMagicData);
    }

    private void slash(ServerLevel level, LivingEntity caster, Vec3 center, float damage, boolean fromShadow, long stamp,
                       IntSet struck) {
        for (LivingEntity victim : Hits.around(level, caster, center.add(0, 0.5, 0), RADIUS, MAX_TARGETS, t -> Hits.enemy(caster, t))) {
            if (struck.add(victim.getId())) {
                FriendlyFire.applyDamage(victim, damage, getDamageSource(caster));
            }
            UmbraVivaSpell.energy(caster, victim, stamp);
            if (fromShadow && victim.isAlive()) {
                victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, SLOW_TICKS, 1, false, false, true), caster);
            }
        }
        MagiaNetwork.sendVisualAt(level, caster, SpellVisualPayload.Kind.SECTIO_UMBRAE, 10, center, (float) RADIUS);
    }
}
