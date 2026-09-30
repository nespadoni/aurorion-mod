package com.aurorion.servicos.mixin;

import com.aurorion.servicos.client.PhoneBridge;
import com.aurorion.servicos.client.ServicosClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Tocar no icone abre o app. O telefone decide que tela abrir por {@code id.contains(...)} numa
 * cadeia de ifs; na busca, o "id" chega como {@code id + " " + termos}. Por isso a checagem aqui e
 * pelo comeco do texto, antes da cadeia do telefone rodar.
 */
@Pseudo
@Mixin(targets = "com.mattupolis.phone.client.gui.PhoneHomeScreen", remap = false)
public abstract class PhoneHomeOpenAppMixin {
    @Inject(method = "openHomeApp", at = @At("HEAD"), cancellable = true, require = 0)
    private void aurorion_servicos$open(@Coerce Object app, CallbackInfo ci) {
        String id = PhoneBridge.accessor(app, "id");
        if (!id.startsWith(ServicosClient.APP_ID)) return;
        PhoneBridge.click();
        ServicosClient.open("buscar", null);
        ci.cancel();
    }
}
