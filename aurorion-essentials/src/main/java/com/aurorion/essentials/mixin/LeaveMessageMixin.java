package com.aurorion.essentials.mixin;

import com.aurorion.essentials.privacy.PrivacyConfig;
import net.minecraft.network.chat.Component;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Intercepta o broadcast de "Fulano saiu do jogo" para ocultar inclusive de operadores
 * quando {@link PrivacyConfig#HIDE_JOIN_LEAVE_MESSAGES} esta ligado.
 *
 * <p>Diferente da mensagem de entrada, essa nao e mais broadcastada de dentro de
 * {@code PlayerList#remove} — o vanilla move essa chamada para
 * {@code ServerGamePacketListenerImpl#removePlayerFromWorld} (metodo privado, chamado a partir de
 * {@code onDisconnect}), antes mesmo de {@code PlayerList#remove} rodar.</p>
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class LeaveMessageMixin {

    @Redirect(method = "removePlayerFromWorld", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/players/PlayerList;broadcastSystemMessage(Lnet/minecraft/network/chat/Component;Z)V"))
    private void aurorion_essentials$hideLeaveMessage(PlayerList self, Component message, boolean bypassHiddenChat) {
        if (!PrivacyConfig.HIDE_JOIN_LEAVE_MESSAGES.get()) {
            self.broadcastSystemMessage(message, bypassHiddenChat);
        }
    }
}
