package com.aurorion.servicos.mixin;

import com.aurorion.servicos.client.ServicosClient;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** O icone do app nas notificacoes da tela de bloqueio. */
@Pseudo
@Mixin(targets = "com.mattupolis.phone.client.gui.PhoneLockScreen", remap = false)
public abstract class PhoneLockNotificationMixin {
    @Inject(method = "getNotificationAppIcon", at = @At("HEAD"), cancellable = true, require = 0)
    private void aurorion_servicos$icon(String appName, String sourceType, CallbackInfoReturnable<ResourceLocation> cir) {
        if (ServicosClient.APP_NAME.equals(appName)) cir.setReturnValue(ServicosClient.ICON);
    }
}
