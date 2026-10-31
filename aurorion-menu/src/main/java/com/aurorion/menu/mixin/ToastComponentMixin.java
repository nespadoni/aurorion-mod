package com.aurorion.menu.mixin;

import com.aurorion.menu.client.MenuNotices;
import net.minecraft.client.gui.components.toasts.AdvancementToast;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Conquista nova: em vez do aviso no canto, o cartão do menu na frente da pessoa, com o personagem dela.
 * Os outros avisos do vanilla (receita, tutorial, sistema) continuam onde estão.
 */
@Mixin(ToastComponent.class)
public abstract class ToastComponentMixin {
    @Inject(method = "addToast", at = @At("HEAD"), cancellable = true)
    private void aurorion_menu$advancement(Toast toast, CallbackInfo ci) {
        if (toast instanceof AdvancementToast advancement
                && MenuNotices.advancement(((AdvancementToastAccessor) advancement).aurorion_menu$advancement())) {
            ci.cancel();
        }
    }
}
