package com.aurorion.servicos.mixin;

import com.aurorion.servicos.client.AurorionPhoneScreen;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * O celular foi desenhado para escala de interface 3: o telefone troca a escala ao abrir uma tela
 * dele e devolve a do jogador ao fechar. A regra dele e "estende a base do telefone"; aqui ela passa
 * a valer tambem para as telas marcadas com {@link AurorionPhoneScreen} — sem isto o app abriria no
 * tamanho do jogador e, ao voltar para a tela inicial, o celular mudaria de tamanho na frente dele.
 */
@Pseudo
@Mixin(targets = "com.mattupolis.phone.client.gui.PhoneGuiScaleManager", remap = false)
public abstract class PhoneGuiScaleMixin {
    @Inject(method = "isPhoneScreen", at = @At("HEAD"), cancellable = true, require = 0)
    private static void aurorion_servicos$ourScreens(Screen screen, CallbackInfoReturnable<Boolean> cir) {
        if (screen instanceof AurorionPhoneScreen) cir.setReturnValue(true);
    }
}
