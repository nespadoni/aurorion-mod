package com.aurorion.utils.mixin.copycats;

import com.aurorion.utils.render.CopycatsCompat;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Copycats+ 3.0.9: cada leitura de NBT de um copycat chama {@code notifyUpdate()} — inclusive no
 * cliente, a cada chunk carregado e a cada pacote do bloco.
 *
 * <p>No Create, {@code notifyUpdate()} e {@code setChanged()} + {@code sendData()}. No cliente, o
 * {@code sendData()} ja nao faz nada (so age num {@code ServerLevel}), e o {@code setChanged()} so marca o
 * chunk e avisa os seis vizinhos de "sinal de saida" — trabalho de comparador, que o cliente nao usa.
 * Numa construcao com milhares de copycats, isso e o carregamento de cada chunk mais lento.
 *
 * <p>Cancela so para BEs de copycat e so no cliente. Servidor intocado: la o aviso e o que manda o
 * bloco para os jogadores.
 */
@Pseudo
@Mixin(targets = "com.simibubi.create.foundation.blockEntity.SyncedBlockEntity", remap = false)
public abstract class CopycatClientNotifyMixin {
    @Inject(method = "notifyUpdate()V", at = @At("HEAD"), cancellable = true, require = 0)
    private void aurorion_utils$skipClientNotify(CallbackInfo callback) {
        Object self = this;
        if (!(self instanceof BlockEntity blockEntity)) return;
        Level level = blockEntity.getLevel();
        if (level != null && level.isClientSide() && CopycatsCompat.isCopycatBlockEntity(self)) callback.cancel();
    }
}
