package com.aurorion.essentials.mixin;

import com.aurorion.essentials.privacy.PrivacyConfig;
import com.aurorion.essentials.privacy.PrivacyMessages;
import net.minecraft.advancements.PlayerAdvancements;
import net.minecraft.network.chat.Component;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Intercepta o broadcast de "Fulano completou a conquista..." para restringir a visibilidade a
 * quem tem OP quando {@link PrivacyConfig#HIDE_ADVANCEMENT_MESSAGES} esta ligado. Nao mexe no
 * toast (aviso no canto da tela) — esse e sincronizado direto para quem desbloqueou a conquista,
 * nunca broadcastado para os outros jogadores.
 */
@Mixin(PlayerAdvancements.class)
public abstract class AdvancementAnnounceMixin {

    @Redirect(method = "award", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/players/PlayerList;broadcastSystemMessage(Lnet/minecraft/network/chat/Component;Z)V"))
    private void aurorion_essentials$hideAdvancementMessage(PlayerList playerList, Component message, boolean bypassHiddenChat) {
        PrivacyMessages.broadcastUnlessHidden(playerList, message, bypassHiddenChat, PrivacyConfig.HIDE_ADVANCEMENT_MESSAGES.get());
    }
}
