package com.aurorion.servicos.mixin;

import com.aurorion.servicos.client.PhoneBridge;
import com.aurorion.servicos.client.ServicosClient;
import com.aurorion.servicos.client.ServicosScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Notificacao do app na tela inicial: o icone certo ao lado dela, e tocar nela abre os pedidos
 * recebidos. Sem isto o telefone nao reconhece o nome "Servicos" e responde "app nao encontrado".
 */
@Pseudo
@Mixin(targets = "com.mattupolis.phone.client.gui.PhoneHomeScreen", remap = false)
public abstract class PhoneHomeNotificationMixin {
    @Inject(method = "getNotificationAppIcon", at = @At("HEAD"), cancellable = true, require = 0)
    private void aurorion_servicos$icon(String appName, String sourceType, CallbackInfoReturnable<ResourceLocation> cir) {
        if (ServicosClient.APP_NAME.equals(appName)) cir.setReturnValue(ServicosClient.ICON);
    }

    @Inject(method = "resolveNotificationTargetScreen", at = @At("HEAD"), cancellable = true, require = 0)
    private void aurorion_servicos$target(@Coerce Object notification, CallbackInfoReturnable<Screen> cir) {
        if (ServicosClient.APP_NAME.equals(PhoneBridge.accessor(notification, "appName"))) {
            cir.setReturnValue(new ServicosScreen("pedidos", "recebidos"));
        }
    }
}
