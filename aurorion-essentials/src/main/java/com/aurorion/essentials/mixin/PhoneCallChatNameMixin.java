package com.aurorion.essentials.mixin;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * "Fulano esta te ligando" no chat, quando alguem liga pelo telefone: o telefone monta a mensagem com
 * o nick da conta. Aqui ela sai com o nome exibido de quem liga — o do personagem, pelo
 * {@code PlayerNameMixin}. A chave de traducao e a mesma, entao o texto de cada idioma continua o do
 * telefone.
 */
@Pseudo
@Mixin(targets = "com.mattupolis.phone.voice.MattupolisVoiceCallManager", remap = false)
public abstract class PhoneCallChatNameMixin {
    @Redirect(method = "sendIncomingCallChatIfAllowed", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerPlayer;sendSystemMessage(Lnet/minecraft/network/chat/Component;)V"),
            require = 0)
    private static void aurorion_essentials$callerCharacterName(ServerPlayer receiver, Component message,
                                                                ServerPlayer caller, ServerPlayer target) {
        receiver.sendSystemMessage(Component.translatable("message.mattupolis_phone.call.incoming_chat", caller.getDisplayName()));
    }
}
