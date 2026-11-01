package com.aurorion.magia.mixin;

import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.spells.blood.BloodStepSpell;
import io.redspace.ironsspellbooks.spells.ender.TeleportSpell;
import io.redspace.ironsspellbooks.spells.ice.FrostStepSpell;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Blood Step e Frost Step (Iron's 3.16.3) fazem cast direto do {@code additionalCastData} para
 * {@code TeleportData}; um dado de outra magia que sobrou ali (um alvo de mira) virava
 * {@code ClassCastException} no tick do mundo e derrubava o servidor. Dado que nao e de teleporte e
 * descartado: a magia cai no caminho normal, que mira pelo olhar.
 */
@Mixin(value = {BloodStepSpell.class, FrostStepSpell.class}, remap = false)
public abstract class StepSpellCastDataMixin {
    @Inject(method = "onCast", at = @At("HEAD"), require = 1)
    private void aurorion_magia$dropForeignCastData(Level level, int spellLevel, LivingEntity entity,
                                                    CastSource castSource, MagicData data, CallbackInfo callback) {
        if (data != null && data.getAdditionalCastData() != null
                && !(data.getAdditionalCastData() instanceof TeleportSpell.TeleportData)) {
            data.resetAdditionalCastData();
        }
    }
}
