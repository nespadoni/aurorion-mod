package com.aurorion.essentials.mixin;

import com.aurorion.essentials.privacy.PrivacyConfig;
import com.aurorion.essentials.privacy.Visibility;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Broadcast publico da morte. Em ADMINS, DeathHistoryEvents envia um unico aviso privado com TP
 * e historico, inclusive com gamerule showDeathMessages desligada ou time com visibilidade NEVER.
 *
 * <p><b>O que continua acontecendo:</b> quem morreu recebe a causa da morte pelo
 * {@code ClientboundPlayerCombatKillPacket}, que sai antes deste ponto e nao passa por aqui — a
 * tela de morte continua dizendo o que matou a pessoa, seja qual for a config. O log do servidor
 * tambem continua registrando.
 *
 * <p><b>Por que {@code @Redirect} e nao um evento:</b> pelo mesmo motivo dos outros mixins de
 * privacidade — o vanilla nao expoe nenhum evento cancelavel para essas mensagens. O
 * {@code LivingDeathEvent} decide se a morte acontece, nao quem le sobre ela.
 *
 * <p><b>As tres saidas do {@code die}:</b> o broadcast normal e as duas variantes de time
 * ({@code broadcastSystemToTeam} e {@code broadcastSystemToAllExceptTeam}), usadas quando o jogador
 * esta num time de scoreboard com {@code deathMessageVisibility} diferente de {@code ALWAYS}. Antes so
 * a primeira era redirecionada, e um {@code /team modify ... deathMessageVisibility} passava por fora
 * da config. Agora as tres obedecem. Os alvos sao conferidos no bytecode pelo
 * {@code PrivacyMixinTargetTest}.
 */
@Mixin(ServerPlayer.class)
public abstract class DeathMessageMixin {

    @Redirect(method = "die", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/players/PlayerList;broadcastSystemMessage(Lnet/minecraft/network/chat/Component;Z)V"))
    private void aurorion_essentials$hideDeathMessage(PlayerList playerList, Component message, boolean bypassHiddenChat) {
        if (PrivacyConfig.DEATH_MESSAGES.get() == Visibility.EVERYONE) {
            playerList.broadcastSystemMessage(message, bypassHiddenChat);
        }
    }

    @Redirect(method = "die", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/players/PlayerList;broadcastSystemToTeam(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/network/chat/Component;)V"))
    private void aurorion_essentials$hideTeamDeathMessage(PlayerList playerList, Player player, Component message) {
        Visibility visibility = PrivacyConfig.DEATH_MESSAGES.get();
        if (visibility == Visibility.EVERYONE) playerList.broadcastSystemToTeam(player, message);
    }

    @Redirect(method = "die", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/players/PlayerList;broadcastSystemToAllExceptTeam(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/network/chat/Component;)V"))
    private void aurorion_essentials$hideOtherTeamsDeathMessage(PlayerList playerList, Player player, Component message) {
        Visibility visibility = PrivacyConfig.DEATH_MESSAGES.get();
        if (visibility == Visibility.EVERYONE) playerList.broadcastSystemToAllExceptTeam(player, message);
    }
}
