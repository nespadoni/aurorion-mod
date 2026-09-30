package com.aurorion.essentials.mixin;

import com.aurorion.essentials.client.MattupolisPhoneNames;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * O HUD da ligacao (a janelinha com avatar e duracao que fica na tela com o celular fechado) le o
 * nome de quem esta do outro lado por estes dois metodos, que so servem para desenhar. A inicial do
 * avatar sai do mesmo texto, entao ela tambem passa a ser a do personagem.
 */
@Pseudo
@Mixin(targets = "com.mattupolis.phone.client.gui.PhoneCallCompactOverlay", remap = false)
public abstract class PhoneCallOverlayNameMixin {
    @Inject(method = {"getDisplayName", "getIncomingDisplayName"}, at = @At("RETURN"), cancellable = true, require = 0)
    private static void aurorion_essentials$characterName(CallbackInfoReturnable<String> cir) {
        cir.setReturnValue(MattupolisPhoneNames.display(cir.getReturnValue()));
    }
}
