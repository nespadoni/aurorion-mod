package com.aurorion.essentials.mixin;

import com.aurorion.essentials.client.EssentialsPhoneScreen;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * As telas do essentials que ficam dentro do celular (menu de opcoes, telas do grupo) contam como
 * telas do telefone para a escala de interface: ver {@link EssentialsPhoneScreen}.
 */
@Pseudo
@Mixin(targets = "com.mattupolis.phone.client.gui.PhoneGuiScaleManager", remap = false)
public abstract class PhoneScreenScaleMixin {
    @Inject(method = "isPhoneScreen", at = @At("HEAD"), cancellable = true, require = 0)
    private static void aurorion_essentials$ourScreens(Screen screen, CallbackInfoReturnable<Boolean> cir) {
        if (screen instanceof EssentialsPhoneScreen) cir.setReturnValue(true);
    }
}
