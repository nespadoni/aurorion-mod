package com.aurorion.essentials.mixin;

import com.aurorion.essentials.client.PhonePhotoDecoder;
import com.mojang.blaze3d.platform.NativeImage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.io.IOException;
import java.io.InputStream;

/**
 * Fotos recebidas chegam em JPEG e o telefone as abre com o leitor so-PNG do Minecraft
 * (ver {@link PhonePhotoDecoder}). Os tres metodos sao os que transformam a foto de outro jogador em
 * textura; a galeria local so tem PNG e fica como esta.
 */
@Pseudo
@Mixin(targets = {
        "com.mattupolis.phone.client.gui.PhoneMessagesStore",
        "com.mattupolis.phone.client.gui.PhoneInstagramDmStore",
        "com.mattupolis.phone.client.gui.PhoneTwitterStore"
}, remap = false)
public abstract class PhonePhotoFormatMixin {
    @Redirect(method = {"getTextureForMessage", "registerPhotoTexture", "registerProfileTexture"},
            at = @At(value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/platform/NativeImage;read(Ljava/io/InputStream;)Lcom/mojang/blaze3d/platform/NativeImage;"),
            require = 0)
    private static NativeImage aurorion_essentials$readAnyFormat(InputStream input) throws IOException {
        return PhonePhotoDecoder.read(input);
    }
}
