package com.aurorion.essentials.mixin;

import com.aurorion.essentials.streamer.StreamerChatFilter;
import com.aurorion.essentials.streamer.StreamerConfig;
import net.minecraft.client.multiplayer.chat.ChatListener;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Cancela apenas a exibicao local de SYSTEM, sem alterar a confirmacao do chat assinado nem logs
 * do servidor. Em HEAD tambem evita ler avisos administrativos na narracao de acessibilidade.
 */
@Mixin(ChatListener.class)
public abstract class StreamerChatListenerMixin {
    @Inject(method = "handleSystemMessage(Lnet/minecraft/network/chat/Component;Z)V",
            at = @At("HEAD"), cancellable = true)
    private void aurorion_essentials$filterStreamerChat(Component message, boolean overlay, CallbackInfo ci) {
        if (StreamerChatFilter.shouldHide(message, overlay, StreamerConfig.MODE.get())) ci.cancel();
    }
}
