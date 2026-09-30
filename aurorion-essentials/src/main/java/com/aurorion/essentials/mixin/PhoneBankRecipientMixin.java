package com.aurorion.essentials.mixin;

import com.aurorion.essentials.client.MattupolisPhoneNames;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * PIX pelo nome do personagem: o campo "para" aceita o nome que o celular mostra, e o nick sai no
 * pacote. A confirmacao ja desenhou o nome (o {@code trimText} da tela troca nick por nome), entao o
 * jogador confirma vendo quem vai receber.
 */
@Pseudo
@Mixin(targets = "com.mattupolis.phone.client.gui.PhoneBankScreen", remap = false)
public abstract class PhoneBankRecipientMixin {
    @Shadow private String pendingTarget;

    @Inject(method = "confirmTransfer", at = @At("HEAD"), require = 0)
    private void aurorion_essentials$targetNick(CallbackInfo ci) {
        this.pendingTarget = MattupolisPhoneNames.nickFor(this.pendingTarget);
    }
}
