package com.aurorion.talk.mixin;

import com.aurorion.talk.client.IncomingChat;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.multiplayer.chat.ChatListener;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.entity.EntityType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.time.Instant;

/**
 * Captura as falas dos jogadores no instante anterior a entrada no HUD do chat.
 *
 * <p>Sao dois caminhos porque nem todo servidor manda chat assinado: plugins de chat e o No Chat
 * Reports entregam a fala como mensagem de sistema.</p>
 */
@Mixin(ChatListener.class)
public class ChatListenerMixin {

    /** Chat assinado (vanilla). */
    @Inject(
            method = "showMessageToPlayer",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Gui;getChat()Lnet/minecraft/client/gui/components/ChatComponent;", ordinal = 0)
    )
    private void aurorion_talk$onSignedChat(ChatType.Bound boundChatType, PlayerChatMessage chatMessage,
                                            Component decoratedServerContent, GameProfile gameProfile,
                                            boolean onlyShowSecureChat, Instant timestamp,
                                            CallbackInfoReturnable<Boolean> cir) {
        IncomingChat.handle(chatMessage.sender(), chatMessage.signedContent());
    }

    /** Chat entregue como mensagem de sistema (No Chat Reports, plugins de chat). */
    @Inject(
            method = "handleSystemMessage",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/ChatComponent;addMessage(Lnet/minecraft/network/chat/Component;)V")
    )
    private void aurorion_talk$onSystemChat(Component component, boolean isOverlay, CallbackInfo ci) {
        if (!(component.getContents() instanceof TranslatableContents contents)) return;
        if (!contents.getKey().equals("chat.type.text")) return;
        if (contents.getArgs().length < 2) return;

        Object senderArg = contents.getArgs()[0];
        Object messageArg = contents.getArgs()[1];

        if (!(senderArg instanceof MutableComponent senderComponent)) return;

        HoverEvent hover = senderComponent.getStyle().getHoverEvent();
        if (hover == null || hover.getAction() != HoverEvent.Action.SHOW_ENTITY) return;

        HoverEvent.EntityTooltipInfo entity = hover.getValue(HoverEvent.Action.SHOW_ENTITY);
        if (entity == null || entity.type != EntityType.PLAYER) return;

        String message = messageArg instanceof Component text ? text.getString() : String.valueOf(messageArg);
        IncomingChat.handle(entity.id, message);
    }
}
