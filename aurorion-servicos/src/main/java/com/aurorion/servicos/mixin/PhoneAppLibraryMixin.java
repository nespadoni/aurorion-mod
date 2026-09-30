package com.aurorion.servicos.mixin;

import com.aurorion.servicos.client.PhoneBridge;
import com.aurorion.servicos.client.ServicosClient;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * O app tambem aparece na Biblioteca de Apps do celular, na categoria "Aurorion". A lista da
 * biblioteca e imutavel ({@code List.of}), entao e trocada por uma copia com o app no fim.
 */
@Pseudo
@Mixin(targets = "com.mattupolis.phone.client.gui.PhoneAppLibraryScreen", remap = false)
public abstract class PhoneAppLibraryMixin {
    @Shadow
    @Final
    @Mutable
    private List<Object> apps;

    @Inject(method = "<init>", at = @At("RETURN"), require = 0)
    private void aurorion_servicos$addApp(CallbackInfo ci) {
        Object app = PhoneBridge.libraryApp(ServicosClient.APP_ID, ServicosClient.APP_NAME, "Aurorion",
                ServicosClient.ICON, ServicosClient.APP_COLOR);
        if (app == null || apps == null) return;
        List<Object> copy = new ArrayList<>(apps);
        copy.add(app);
        apps = copy;
    }

    @Inject(method = "openApp", at = @At("HEAD"), cancellable = true, require = 0)
    private void aurorion_servicos$open(String id, CallbackInfo ci) {
        if (!ServicosClient.APP_ID.equals(id)) return;
        PhoneBridge.click();
        ServicosClient.open("buscar", null);
        ci.cancel();
    }
}
