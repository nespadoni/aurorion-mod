package com.aurorion.essentials.mixin;

import com.aurorion.essentials.client.PhoneCharacterStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.nio.file.Path;

/**
 * Fotos recebidas por mensagem e fotos da camera do celular, por personagem. O telefone monta essas
 * pastas como {@code gameDir.resolve("mattupolis_phone").resolve(...)} no meio do metodo; so o passo
 * {@code "mattupolis_phone"} e trocado pela pasta do personagem.
 *
 * <p>Os prints do jogo ({@code screenshots/}) continuam aparecendo na galeria de todos: sao do PC,
 * tirados com F2, e nao de um personagem.
 */
@Pseudo
@Mixin(targets = {
        "com.mattupolis.phone.client.gui.PhoneGalleryStore",
        "com.mattupolis.phone.client.PhoneClientPacketHandler"
}, remap = false)
public abstract class PhoneGalleryPathMixin {
    @Redirect(method = {"getLatestPhotos", "saveIncomingPhoto"},
            at = @At(value = "INVOKE", target = "Ljava/nio/file/Path;resolve(Ljava/lang/String;)Ljava/nio/file/Path;"),
            require = 0)
    private static Path aurorion_essentials$characterPhotos(Path base, String other) {
        return PhoneCharacterStorage.resolveRoot(base, other);
    }
}
