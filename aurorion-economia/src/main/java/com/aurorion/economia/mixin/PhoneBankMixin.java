package com.aurorion.economia.mixin;

import com.aurorion.economia.compat.PhoneBankBridge;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Da saldo ao app de banco do telefone. O porque de ser mixin, e nao uma API, esta no
 * {@link PhoneBankBridge}.
 *
 * <p>{@code require = 0} de proposito: se uma atualizacao do telefone mudar estes metodos, o app
 * volta a ficar desligado em vez de derrubar um servidor com 80 pessoas dentro. O aviso alto fica
 * por conta do {@code PhoneBankBridge}, que loga em ERROR quando acha o telefone mas nao a forma
 * esperada do banco.</p>
 *
 * <p>Injetamos so a sobrecarga de quatro argumentos: a de tres delega para ela no bytecode
 * (conferido com {@code javap}), entao as duas ficam cobertas por um hook so.</p>
 */
@Pseudo
@Mixin(targets = "com.mattupolis.phone.server.bank.PhoneBankServerStore", remap = false)
public abstract class PhoneBankMixin {

    @Inject(method = "getSnapshot", at = @At("HEAD"), cancellable = true, require = 0)
    private static void aurorion_economia$snapshot(ServerPlayer player, CallbackInfoReturnable<Object> cir) {
        Object snapshot = PhoneBankBridge.snapshot(player);
        if (snapshot != null) cir.setReturnValue(snapshot);
    }

    @Inject(method = "transfer(Lnet/minecraft/server/level/ServerPlayer;Ljava/lang/String;JLjava/lang/String;)"
            + "Lcom/mattupolis/phone/server/bank/PhoneBankServerStore$ActionResult;",
            at = @At("HEAD"), cancellable = true, require = 0)
    private static void aurorion_economia$transfer(ServerPlayer payer, String targetName, long amount,
                                                   String note, CallbackInfoReturnable<Object> cir) {
        Object result = PhoneBankBridge.transfer(payer, targetName, amount, note);
        if (result != null) cir.setReturnValue(result);
    }
}
