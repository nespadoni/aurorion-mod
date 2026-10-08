package com.aurorion.magia.spell;

import com.aurorion.magia.AurorionMagia;
import com.aurorion.magia.compat.LivesMagic;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.SchoolRegistry;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.CastType;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Um ritual aplica uma unica mudanca de vida no fim da conjuracao. */
public abstract class LifeSpell extends AurorionSpell {
    private static final int RANGE = 16;
    private final int delta;

    protected LifeSpell(String id, int delta) {
        super(id, SchoolRegistry.BLOOD_RESOURCE, SpellRarity.LEGENDARY, 1, 30, CastType.LONG, true);
        this.delta = delta;
        baseSpellPower = 1;
        spellPowerPerLevel = 0;
        baseManaCost = 80;
        manaCostPerLevel = 0;
        castTime = 40;
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, @Nullable LivingEntity caster) {
        return List.of(Component.translatable(delta < 0 ? "ui.aurorion_magia.ceifar_vida" : "ui.aurorion_magia.entregar_vida"),
                Component.translatable("ui.aurorion_magia.alcance", RANGE));
    }

    @Override
    public boolean checkPreCastConditions(Level level, int spellLevel, LivingEntity caster, MagicData data) {
        if (level.isClientSide) return true;
        if (!LivesMagic.available()) {
            if (caster instanceof ServerPlayer player)
                player.displayClientMessage(Component.translatable("aurorion_magia.vidas_indisponiveis"), true);
            return false;
        }
        return aim(level, caster, data, RANGE, delta > 0, target -> target instanceof ServerPlayer player
                && (delta > 0 || Hits.hittable(caster, target)) && LivesMagic.canChange(player, delta));
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity caster, CastSource source, MagicData data) {
        if (level instanceof ServerLevel serverLevel && target(serverLevel, caster, data) instanceof ServerPlayer target
                && target.isAlive() && caster.distanceToSqr(target) <= RANGE * RANGE && caster.hasLineOfSight(target)
                && (delta > 0 || Hits.hittable(caster, target) && Hits.enemy(caster, target))
                && LivesMagic.change(target, delta)) {
            target.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 100, 0), caster);
            if (delta < 0) {
                target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 400, 0), caster);
                target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 400, 0), caster);
                target.addEffect(new MobEffectInstance(MobEffects.HUNGER, 400, 0), caster);
            }
            sound(target, delta < 0 ? SoundEvents.WITHER_SPAWN : SoundEvents.TOTEM_USE, 0.8f, 1.0f);
            target.sendSystemMessage(Component.translatable(delta < 0
                    ? "aurorion_magia.vida_ceifada" : "aurorion_magia.vida_entregue", caster.getDisplayName()));
            AurorionMagia.LOGGER.info("Magia {}: {} ajustou uma vida de {} (delta {}).",
                    getSpellId(), caster.getUUID(), target.getUUID(), delta);
        }
        super.onCast(level, spellLevel, caster, source, data);
    }
}
