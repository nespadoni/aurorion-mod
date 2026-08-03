package com.aurorion.talk.mixin;

import com.aurorion.talk.client.ChatSuppressor;
import net.minecraft.client.GuiMessageTag;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Engole no HUD as mensagens que ja viraram balao.
 *
 * <p>Cancelamos aqui, e nao em {@code ChatListener}, para nao interferir na confirmacao de
 * assinaturas do chat seguro — cancelar antes faria o cliente reportar a mensagem como nao
 * processada.</p>
 */
@Mixin(ChatComponent.class)
public class ChatComponentMixin {

    @Inject(
            method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/GuiMessageTag;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void aurorion_talk$hideBalloonedMessage(Component message, MessageSignature signature,
                                                    GuiMessageTag tag, CallbackInfo ci) {
        if (ChatSuppressor.consume()) ci.cancel();
    }
}
