package com.aurorion.essentials.mixin;

import com.aurorion.essentials.privacy.PrivacyConfig;
import com.aurorion.essentials.privacy.PrivacyMessages;
import net.minecraft.network.chat.Component;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Intercepta o broadcast de "Fulano entrou no jogo" para restringir a visibilidade a quem tem OP
 * quando {@link PrivacyConfig#HIDE_JOIN_LEAVE_MESSAGES} esta ligado — evita meta-gaming de quem
 * esta online sem depender de nenhum evento cancelavel (o vanilla nao expoe um).
 *
 * <p>A mensagem de saida ("Fulano saiu do jogo") nao e mais broadcastada daqui — ver
 * {@link LeaveMessageMixin}.</p>
 */
@Mixin(PlayerList.class)
public abstract class JoinLeaveMessageMixin {

    @Redirect(method = "placeNewPlayer", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/players/PlayerList;broadcastSystemMessage(Lnet/minecraft/network/chat/Component;Z)V"))
    private void aurorion_essentials$hideJoinMessage(PlayerList self, Component message, boolean bypassHiddenChat) {
        PrivacyMessages.broadcastUnlessHidden(self, message, bypassHiddenChat, PrivacyConfig.HIDE_JOIN_LEAVE_MESSAGES.get());
    }
}
