package com.aurorion.essentials.mixin;

import com.aurorion.essentials.client.MattupolisPhoneNames;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * O e-mail do telefone e entregue pelo nick ({@code getPlayerByName} no servidor), mas a caixa de
 * entrada inteira mostra nomes de personagem. Quem digita no "Para" o nome que o celular mostra tem o
 * e-mail entregue: o nome vira o nick antes de sair do cliente.
 */
@Pseudo
@Mixin(targets = "com.mattupolis.phone.client.gui.PhoneMailStore", remap = false)
public abstract class PhoneMailRecipientMixin {
    @ModifyVariable(method = "sendMail", at = @At("HEAD"), argsOnly = true, ordinal = 0, require = 0)
    private static String aurorion_essentials$recipientNick(String recipient) {
        return MattupolisPhoneNames.nickFor(recipient);
    }
}
