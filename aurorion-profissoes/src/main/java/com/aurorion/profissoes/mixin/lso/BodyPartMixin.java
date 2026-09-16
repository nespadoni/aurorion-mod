package com.aurorion.profissoes.mixin.lso;

import com.aurorion.profissoes.compat.*;
import com.aurorion.profissoes.config.ProfessionsConfig;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Pseudo
@Mixin(targets = "sfiomn.legendarysurvivaloverhaul.common.attachments.bodydamage.BodyPart", remap = false)
public abstract class BodyPartMixin implements WoundPart {
    @Shadow private float damage;
    @Shadow private float maxHealth;
    @Shadow private int remainingHealingTicks;
    @Shadow private float healingPerTicks;
    @Unique private boolean aurorion$critical;
    @Inject(method = "setDamage", at = @At("HEAD"))
    private void aurorion$injury(float value, CallbackInfo ci) {
        if (ProfessionsConfig.enabled() && value > damage && InjuryPolicy.severe(value, maxHealth, ProfessionsConfig.severeThreshold()))
            aurorion$critical = true;
    }
    @ModifyVariable(method = "heal", at = @At("HEAD"), argsOnly = true)
    private float aurorion$stabilize(float amount) {
        return ProfessionsConfig.enabled() && aurorion$critical
                ? InjuryPolicy.firstAid(amount, damage, maxHealth, ProfessionsConfig.firstAidCeiling()) : amount;
    }
    @Inject(method = "writeNbt", at = @At("RETURN"))
    private void aurorion$save(CompoundTag tag, CallbackInfoReturnable<CompoundTag> cir) {
        cir.getReturnValue().putBoolean("AurorionCritical_" + LsoCompat.name(this), aurorion$critical);
    }
    @Inject(method = "readNBT", at = @At("RETURN"))
    private void aurorion$load(CompoundTag tag, CallbackInfo ci) {
        String key = "AurorionCritical_" + LsoCompat.name(this);
        aurorion$critical = tag.contains(key) ? tag.getBoolean(key)
                : InjuryPolicy.severe(damage, maxHealth, ProfessionsConfig.severeThreshold());
    }
    public float aurorionHealth() { return Math.max(0, maxHealth - damage); }
    public float aurorionMaxHealth() { return maxHealth; }
    public boolean aurorionCritical() { return aurorion$critical; }
    public void aurorionTreat() {
        aurorion$critical = false; damage = 0; remainingHealingTicks = 0; healingPerTicks = 0;
    }
}
