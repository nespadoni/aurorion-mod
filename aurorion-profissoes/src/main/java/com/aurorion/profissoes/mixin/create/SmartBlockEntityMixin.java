package com.aurorion.profissoes.mixin.create;

import com.aurorion.profissoes.craft.compat.MachineContext;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Create: toda máquina ({@code SmartBlockEntity}) se marca em {@link MachineContext} no começo do próprio
 * tick, para o teste de ofício saber de quem é a mão (quem montou a máquina). As subclasses chamam
 * {@code super.tick()} no começo, então a marca vale para o resto do tick delas.
 */
@Pseudo
@Mixin(targets = "com.simibubi.create.foundation.blockEntity.SmartBlockEntity", remap = false)
public abstract class SmartBlockEntityMixin {
    @Inject(method = "tick()V", at = @At("HEAD"), require = 0)
    private void aurorion$enter(CallbackInfo ci) {
        BlockEntity self = (BlockEntity) (Object) this;
        if (self.getLevel() != null && !self.getLevel().isClientSide()) MachineContext.enter(self);
    }
}
