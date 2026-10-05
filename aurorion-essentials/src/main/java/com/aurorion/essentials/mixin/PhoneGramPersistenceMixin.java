package com.aurorion.essentials.mixin;

import com.aurorion.essentials.client.PhoneConversations;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** As DMs nao sao reenviadas ao restaurar; somente os records e fotos locais sao repostos. */
@Pseudo
@Mixin(targets = "com.mattupolis.phone.client.gui.PhoneInstagramDmStore", remap = false)
public abstract class PhoneGramPersistenceMixin {
    @Inject(method = {
            "addMessageInternal(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;JZLjava/lang/String;Ljava/lang/String;)V",
            "setThreadUnread(Ljava/lang/String;I)V", "removeThreadForUser(Ljava/lang/String;)V"
    }, at = @At("RETURN"), require = 0)
    private static void aurorion_essentials$saveHistory(CallbackInfo ci) {
        PhoneConversations.gramChanged();
    }

    @Inject(method = "registerPhotoTexture(Ljava/lang/String;[B)V", at = @At("HEAD"), require = 0)
    private static void aurorion_essentials$savePhoto(String id, byte[] bytes, CallbackInfo ci) {
        PhoneConversations.saveGramPhoto(id, bytes);
    }

    @Inject(method = "getPhotoTexture(Ljava/lang/String;)Lnet/minecraft/resources/ResourceLocation;",
            at = @At("RETURN"), cancellable = true, require = 0)
    private static void aurorion_essentials$restorePhoto(String id, CallbackInfoReturnable<ResourceLocation> cir) {
        if (cir.getReturnValue() == null) {
            Object texture = PhoneConversations.restoreGramPhoto(id);
            if (texture instanceof ResourceLocation location) cir.setReturnValue(location);
        }
    }
}
