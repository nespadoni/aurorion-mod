package com.aurorion.magia.spell;

import com.aurorion.magia.entity.SpellZoneEntity;
import com.aurorion.magia.network.MagiaNetwork;
import com.aurorion.magia.network.SpellVisualPayload;
import com.aurorion.magia.registry.MagiaEffects;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.SchoolRegistry;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.CastType;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import io.redspace.ironsspellbooks.api.util.Utils;
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
 * Lacus Sanguinis — Poca de Sangue. O W do Vladimir: paga {@value #COST_PERCENT}% da vida atual e
 * mergulha numa poca de sangue por dois segundos.
 *
 * <p>Na poca, quem conjurou fica <b>inalvejavel</b> — invisivel, nenhum dano o alcanca, nenhuma mira o
 * escolhe e nenhuma criatura o persegue — mas tambem nao ataca. Quem pisar na poca anda muito devagar
 * e e drenado a cada meio segundo; metade do que a poca drena volta como vida.
 *
 * <p>A poca e uma {@link SpellZoneEntity} na forma {@code POCA}, que segue quem mergulhou.
 */
public final class LacusSanguinisSpell extends AurorionSpell {
    private static final int COST_PERCENT = 20;
    private static final int DURATION = 40;
    private static final float RADIUS = 3;

    public LacusSanguinisSpell() {
        super("lacus_sanguinis", SchoolRegistry.BLOOD_RESOURCE, SpellRarity.EPIC, 5, 25, CastType.INSTANT);
        this.baseSpellPower = 2;
        this.spellPowerPerLevel = 1;
        this.baseManaCost = 40;
        this.manaCostPerLevel = 5;
        this.castTime = 0;
    }

    @Override
    public Optional<SoundEvent> getCastFinishSound() {
        return Optional.of(SoundEvents.GENERIC_SPLASH);
    }

    @Override
    public Vector3f getTargetingColor() {
        return new Vector3f(0.55f, 0.0f, 0.08f);
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, @Nullable LivingEntity caster) {
        return List.of(
                Component.translatable("ui.aurorion_magia.custo_vida", COST_PERCENT),
                Component.translatable("ui.aurorion_magia.inalvejavel", Utils.timeFromTicks(DURATION, 1)),
                Component.translatable("ui.aurorion_magia.dano_por_segundo",
                        Utils.stringTruncation(getSpellPower(spellLevel, caster), 1)),
                Component.translatable("ui.aurorion_magia.raio", Utils.stringTruncation(RADIUS, 1)));
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource castSource, MagicData playerMagicData) {
        if (level instanceof ServerLevel serverLevel) dive(serverLevel, entity, spellLevel);
        super.onCast(level, spellLevel, entity, castSource, playerMagicData);
    }

    private void dive(ServerLevel level, LivingEntity caster, int spellLevel) {
        // Paga com a propria vida, e nao com dano: armadura e escudo nao abatem o preco.
        caster.setHealth(Math.max(1, caster.getHealth() * (1 - COST_PERCENT / 100f)));
        caster.addEffect(new MobEffectInstance(MagiaEffects.UNTARGETABLE, DURATION, 0, false, false, true));
        caster.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, DURATION, 0, false, false, false));
        caster.stopUsingItem();
        // Dois pulsos por segundo: metade do poder em cada um.
        SpellZoneEntity.create(level, caster, SpellZoneEntity.Shape.POCA, caster.position(), RADIUS, 1.5f, DURATION,
                getSpellPower(spellLevel, caster) * 0.5f, Vec3.ZERO);
        sound(caster, SoundEvents.WARDEN_HEARTBEAT, 1.6f, 0.7f);
        MagiaNetwork.sendVisual(caster, caster, SpellVisualPayload.Kind.LACUS_SANGUINIS, DURATION, caster.position(), RADIUS);
    }
}
