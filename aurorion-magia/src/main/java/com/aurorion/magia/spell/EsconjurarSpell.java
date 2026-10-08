package com.aurorion.magia.spell;

import com.aurorion.magia.config.MagiaConfig;
import com.aurorion.magia.effect.EffectCleanup;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.SchoolRegistry;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.CastType;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/** Dissipa buffs e debuffs no raio, passando pelos eventos normais de limpeza de cada mod. */
public final class EsconjurarSpell extends AurorionSpell {
    public EsconjurarSpell() {
        super("esconjurar", SchoolRegistry.HOLY_RESOURCE, SpellRarity.EPIC, 1, 20, CastType.INSTANT);
        baseSpellPower = 1;
        spellPowerPerLevel = 0;
        baseManaCost = 60;
        manaCostPerLevel = 0;
        castTime = 0;
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, @Nullable LivingEntity caster) {
        return List.of(Component.translatable("ui.aurorion_magia.esconjurar"),
                Component.translatable("ui.aurorion_magia.raio", MagiaConfig.DISPEL_RADIUS.get()));
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity caster, CastSource source, MagicData data) {
        if (level instanceof ServerLevel serverLevel) {
            int radius = MagiaConfig.DISPEL_RADIUS.get();
            for (LivingEntity target : serverLevel.getEntitiesOfClass(LivingEntity.class,
                    caster.getBoundingBox().inflate(radius), entity -> entity.isAlive() && !entity.isSpectator()
                            && !untouchable(entity) && entity.distanceToSqr(caster) <= radius * radius)) {
                // Snapshot: remover possessao, por exemplo, pode remover outro efeito no mesmo alvo.
                for (var effect : new ArrayList<>(target.getActiveEffects())) target.removeEffect(effect.getEffect());
                EffectCleanup.sweep(target);
                ControlSpells.syncVoice(target, null);
            }
            sound(caster, SoundEvents.BEACON_DEACTIVATE, 1.2f, 1.4f);
        }
        super.onCast(level, spellLevel, caster, source, data);
    }
}
