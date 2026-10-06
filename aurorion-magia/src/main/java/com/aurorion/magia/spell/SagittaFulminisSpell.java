package com.aurorion.magia.spell;

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
 * Sagitta Fulminis — Flecha de Choque. O choque do Sova: encanta a proxima flecha do arco, e onde ela
 * bater — chao, parede ou alguem — descarrega eletricidade em {@value #RADIUS} blocos. O dano e o da
 * descarga, alem do da propria flecha. Exige arco (ou besta) na mao. Ver {@link SovaArrows}.
 */
public final class SagittaFulminisSpell extends AurorionSpell {
    private static final float RADIUS = 3;
    private static final int MAX_TARGETS = 12;

    public SagittaFulminisSpell() {
        super("sagitta_fulminis", SchoolRegistry.LIGHTNING_RESOURCE, SpellRarity.RARE, 5, 15, CastType.INSTANT);
        this.baseSpellPower = 6;
        this.spellPowerPerLevel = 2;
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
        return Optional.of(SoundEvents.BEACON_POWER_SELECT);
    }

    @Override
    public Vector3f getTargetingColor() {
        return new Vector3f(0.3f, 0.7f, 1.0f);
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, @Nullable LivingEntity caster) {
        return List.of(
                Component.translatable("ui.aurorion_magia.dano", Utils.stringTruncation(getSpellPower(spellLevel, caster), 1)),
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
        if (level instanceof ServerLevel) SovaArrows.imbue(entity, SovaArrows.SHOCK, getSpellPower(spellLevel, entity), RADIUS);
        super.onCast(level, spellLevel, entity, castSource, playerMagicData);
    }

    /** A flecha encantada bateu. */
    public static void discharge(ServerLevel level, LivingEntity caster, Vec3 at, double radius, float damage) {
        for (LivingEntity victim : Hits.around(level, caster, at, radius, MAX_TARGETS, t -> Hits.enemy(caster, t))) {
            FriendlyFire.applyDamage(victim, damage, MagiaSpells.SAGITTA_FULMINIS.get().getDamageSource(caster));
        }
        sound(level, at, SoundEvents.LIGHTNING_BOLT_IMPACT, 1.4f, 1.4f);
        MagiaNetwork.sendVisualAt(level, caster, SpellVisualPayload.Kind.SAGITTA_SHOCK, 14, at, (float) radius);
    }
}
