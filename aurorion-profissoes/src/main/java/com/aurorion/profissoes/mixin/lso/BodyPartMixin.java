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
    @Shadow @Final private float healthMultiplier;
    @Unique private boolean aurorion$critical;
    @Unique private float aurorion$defaultMaxHealth() {
        float base = 20 * healthMultiplier;
        return Float.isFinite(base) && base > 0 ? base : 20;
    }
    @Unique private void aurorion$repair() {
        if (!Float.isFinite(maxHealth) || maxHealth < 0)
            maxHealth = aurorion$defaultMaxHealth();
        damage = InjuryPolicy.cleanDamage(damage, maxHealth);
        if (remainingHealingTicks < 0 || !Float.isFinite(healingPerTicks) || healingPerTicks < 0 || damage == 0) {
            remainingHealingTicks = 0;
            healingPerTicks = 0;
        }
        if (damage == 0) aurorion$critical = false;
    }
    @ModifyVariable(method = "setDamage", at = @At("HEAD"), argsOnly = true)
    private float aurorion$damage(float value) {
        aurorion$repair();
        // NaN ignora a escrita; infinito de um golpe letal vira o maximo finito do membro.
        float clean = Float.isNaN(value) ? damage : value == Float.POSITIVE_INFINITY ? maxHealth
                : InjuryPolicy.cleanDamage(value, maxHealth);
        if (ProfessionsConfig.enabled() && clean > damage && InjuryPolicy.severe(clean, maxHealth, ProfessionsConfig.severeThreshold()))
            aurorion$critical = true;
        if (clean == 0) {
            aurorion$critical = false;
            remainingHealingTicks = 0;
            healingPerTicks = 0;
        }
        return clean;
    }
    @ModifyVariable(method = "setMaxHealth", at = @At("HEAD"), argsOnly = true)
    private float aurorion$maxHealth(float value) {
        aurorion$repair();
        return Float.isFinite(value) && value >= 0 ? value : maxHealth;
    }
    @Inject(method = {"heal", "hurt"}, at = @At("HEAD"))
    private void aurorion$beforeChange(float amount, CallbackInfo ci) {
        aurorion$repair();
    }
    @ModifyVariable(method = "heal", at = @At("HEAD"), argsOnly = true)
    private float aurorion$stabilize(float amount) {
        aurorion$repair();
        if (Float.isNaN(amount) || amount < 0) return 0;
        if (!Float.isFinite(amount)) amount = damage;
        return ProfessionsConfig.enabled() && aurorion$critical
                ? InjuryPolicy.firstAid(amount, damage, maxHealth, ProfessionsConfig.firstAidCeiling()) : amount;
    }
    @Inject(method = "writeNbt", at = @At("HEAD"))
    private void aurorion$beforeSave(CompoundTag tag, CallbackInfoReturnable<CompoundTag> cir) {
        aurorion$repair();
    }
    @ModifyVariable(method = "readNBT", at = @At("HEAD"), argsOnly = true)
    private CompoundTag aurorion$cleanSave(CompoundTag original) {
        String part = LsoCompat.name(this);
        CompoundTag clean = original.copy();
        for (String suffix : new String[]{"_damage", "_maxHealth", "_healingPerTicks"}) {
            String key = part + suffix;
            if (clean.contains(key) && (!Float.isFinite(clean.getFloat(key)) || clean.getFloat(key) < 0))
                clean.putFloat(key, suffix.equals("_maxHealth") ? aurorion$defaultMaxHealth() : 0);
        }
        return clean;
    }
    @Inject(method = {"getDamage", "getMaxHealth", "getHealingPerTicks"}, at = @At("HEAD"))
    private void aurorion$beforeRead(CallbackInfoReturnable<Float> cir) {
        aurorion$repair();
    }
    @Inject(method = "setHealing", at = @At("RETURN"))
    private void aurorion$afterHealing(int ticks, float amount, CallbackInfo ci) {
        aurorion$repair();
    }
    @Inject(method = "writeNbt", at = @At("RETURN"))
    private void aurorion$save(CompoundTag tag, CallbackInfoReturnable<CompoundTag> cir) {
        cir.getReturnValue().putBoolean("AurorionCritical_" + LsoCompat.name(this), aurorion$critical);
    }
    @Inject(method = "readNBT", at = @At("RETURN"))
    private void aurorion$load(CompoundTag tag, CallbackInfo ci) {
        aurorion$repair();
        String key = "AurorionCritical_" + LsoCompat.name(this);
        aurorion$critical = damage > 0 && (tag.contains(key) ? tag.getBoolean(key)
                : InjuryPolicy.severe(damage, maxHealth, ProfessionsConfig.severeThreshold()));
    }
    public float aurorionHealth() { return Math.max(0, maxHealth - damage); }
    public float aurorionMaxHealth() { return maxHealth; }
    public boolean aurorionCritical() { return aurorion$critical; }
    public void aurorionTreat() {
        aurorion$critical = false; damage = 0; remainingHealingTicks = 0; healingPerTicks = 0;
    }
}
