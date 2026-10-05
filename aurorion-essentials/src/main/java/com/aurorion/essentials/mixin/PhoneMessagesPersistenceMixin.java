package com.aurorion.essentials.mixin;

import com.aurorion.essentials.client.PhoneConversations;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** O telefone original grava a agenda, mas mantem o historico apenas em campos estaticos. */
@Pseudo
@Mixin(targets = "com.mattupolis.phone.client.gui.PhoneMessagesStore", remap = false)
public abstract class PhoneMessagesPersistenceMixin {
    @Inject(method = "loadPlayerContactsIfNeeded()V", at = @At("RETURN"), require = 0)
    private static void aurorion_essentials$restoreHistory(CallbackInfo ci) {
        PhoneConversations.loadMessages();
    }

    @Inject(method = {
            "addMessage(Ljava/lang/String;Ljava/lang/String;ZLjava/lang/String;Ljava/lang/String;Ljava/nio/file/Path;III)V",
            "savePlayerContacts()V", "deleteThreadMessages(Ljava/lang/String;)V",
            "receiveMultiplayerText(Ljava/lang/String;Ljava/lang/String;)V",
            "receiveMultiplayerLocation(Ljava/lang/String;III)V", "receiveMultiplayerPhoto(Ljava/lang/String;Ljava/nio/file/Path;)V"
    }, at = @At("RETURN"), require = 0)
    private static void aurorion_essentials$saveHistory(CallbackInfo ci) {
        PhoneConversations.messagesChanged();
    }

    /** HEAD: so aqui ainda se sabe se havia nao lidas; a conversa aberta chama isto a cada tick. */
    @Inject(method = "markThreadRead(Ljava/lang/String;)V", at = @At("HEAD"), require = 0)
    private static void aurorion_essentials$saveIfWasUnread(String threadId, CallbackInfo ci) {
        PhoneConversations.threadReadRequested(threadId);
    }
}
