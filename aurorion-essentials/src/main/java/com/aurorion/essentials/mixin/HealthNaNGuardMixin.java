package com.aurorion.essentials.mixin;

import com.aurorion.essentials.death.HealthGuard;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Stack;

/**
 * Vida e absorcao nunca sao gravadas como NaN — e quem ja esta assim e consertado no tick.
 *
 * <p>Os tres pontos cobrem todos os caminhos: {@code setHealth} (dano, cura, comando, carga do save),
 * {@code setAbsorptionAmount} (o passo de absorcao do {@code actuallyHurt}, final em
 * {@code LivingEntity}, entao vale para {@code Player} tambem) e o {@code baseTick}, para o que
 * entrou sem passar por eles (save antigo, escrita direta no entity data). A regra e o porque estao
 * em {@link HealthGuard}.
 *
 * <p>No caminho normal o custo e um {@code Float.isNaN} por escrita e dois por tick de entidade.
 *
 * <p>Fica na config {@code deathguard} ({@code required = false}, {@code defaultRequire = 0}) como a
 * {@code DeathListenerGuardMixin}: se uma versao futura mudar um alvo, o jogo sobe sem a rede.
 */
@Mixin(LivingEntity.class)
public abstract class HealthNaNGuardMixin {
    @Shadow
    protected Stack<DamageContainer> damageContainers;

    @ModifyVariable(method = "setHealth", at = @At("HEAD"), argsOnly = true)
    private float aurorion_essentials$noNaNHealth(float health) {
        if (!Float.isNaN(health)) return health;
        DamageContainer hit = damageContainers == null || damageContainers.isEmpty() ? null : damageContainers.peek();
        return HealthGuard.health((LivingEntity) (Object) this, hit);
    }

    @ModifyVariable(method = "setAbsorptionAmount", at = @At("HEAD"), argsOnly = true)
    private float aurorion_essentials$noNaNAbsorption(float absorption) {
        return Float.isNaN(absorption) ? HealthGuard.absorption((LivingEntity) (Object) this) : absorption;
    }

    @Inject(method = "baseTick", at = @At("HEAD"))
    private void aurorion_essentials$repairNaN(CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (self.level().isClientSide()) return;
        if (Float.isNaN(self.getHealth()) || Float.isNaN(self.getAbsorptionAmount())) {
            HealthGuard.repair(self);
        }
    }
}
