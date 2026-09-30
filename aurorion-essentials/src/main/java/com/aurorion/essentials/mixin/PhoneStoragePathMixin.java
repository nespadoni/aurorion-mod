package com.aurorion.essentials.mixin;

import com.aurorion.essentials.client.PhoneCharacterStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.nio.file.Path;

/**
 * Cada arquivo que o telefone grava no cliente passa a morar na pasta do personagem conectado
 * ({@link PhoneCharacterStorage}). Os stores pedem o caminho sempre pelo mesmo metodo privado, entao
 * reescrever o retorno dele muda leitura e gravacao juntas.
 */
@Pseudo
@Mixin(targets = {
        "com.mattupolis.phone.client.gui.PhoneBankStore",
        "com.mattupolis.phone.client.gui.PhoneCalendarStore",
        "com.mattupolis.phone.client.gui.PhoneCaseStore",
        "com.mattupolis.phone.client.gui.PhoneHealthStore",
        "com.mattupolis.phone.client.gui.PhoneHomeLayoutStore",
        "com.mattupolis.phone.client.gui.PhoneMarketplaceStore",
        "com.mattupolis.phone.client.gui.PhoneMessagesStore",
        "com.mattupolis.phone.client.gui.PhoneMineStoreState",
        "com.mattupolis.phone.client.gui.PhoneNotesStore",
        "com.mattupolis.phone.client.gui.PhoneSettingsStore",
        "com.mattupolis.phone.client.gui.PhoneWallpaperStore"
}, remap = false)
public abstract class PhoneStoragePathMixin {
    @Inject(method = {"getFavoritesFile", "getStoreFile", "getPath", "getExtrasFile", "getContactsStoreFile", "getPinStoreFile"},
            at = @At("RETURN"), cancellable = true, require = 0)
    private static void aurorion_essentials$characterFolder(CallbackInfoReturnable<Path> cir) {
        cir.setReturnValue(PhoneCharacterStorage.redirect(cir.getReturnValue()));
    }
}
