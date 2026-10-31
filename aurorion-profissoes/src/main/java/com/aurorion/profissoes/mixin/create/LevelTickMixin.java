package com.aurorion.profissoes.mixin.create;

import com.aurorion.profissoes.craft.compat.MachineContext;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fim dos ticks de bloco de um mundo: a última máquina do Create marcada em {@link MachineContext} deixa de
 * valer, para nada fora do tick dela (comando, clique de jogador, entidade no tick seguinte) herdar a mão.
 * Só existe com o Create no pack (config de mixin {@code create}).
 */
@Mixin(Level.class)
public abstract class LevelTickMixin {
    @Inject(method = "tickBlockEntities", at = @At("RETURN"))
    private void aurorion$clearMachine(CallbackInfo ci) {
        MachineContext.clear();
    }
}
