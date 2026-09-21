package com.aurorion.essentials.mixin;

import com.aurorion.essentials.privacy.PrivacyConfig;
import com.aurorion.essentials.privacy.PrivacyMessages;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Manda "Fulano foi morto por..." so para quem modera, em vez de para o servidor inteiro.
 *
 * <p><b>O que continua acontecendo:</b> quem morreu recebe a causa da morte pelo
 * {@code ClientboundPlayerCombatKillPacket}, que sai antes deste ponto e nao passa por aqui — a
 * tela de morte continua dizendo o que matou a pessoa, seja qual for a config. O que muda e so o
 * broadcast para os outros.
 *
 * <p><b>Por que {@code @Redirect} e nao um evento:</b> pelo mesmo motivo dos outros tres mixins de
 * privacidade — o vanilla nao expoe nenhum evento cancelavel para essas mensagens. O
 * {@code LivingDeathEvent} decide se a morte acontece, nao quem le sobre ela.
 *
 * <p><b>Por que so uma chamada:</b> o {@code die} tem tres saidas de mensagem — o broadcast normal e
 * as duas variantes de time ({@code broadcastSystemToTeam} e {@code broadcastSystemToAllExceptTeam}).
 * As de time so rodam quando o jogador esta num time de scoreboard com
 * {@code deathMessageVisibility} diferente de {@code ALWAYS}, que nao e o caso deste servidor; o
 * vanilla cai no broadcast normal, que e o unico
 * {@code PlayerList.broadcastSystemMessage(Component;Z)} presente no bytecode de {@code die}
 * (conferido com {@code javap} no NeoForge 21.1.248). Se um dia o servidor usar times com essa
 * visibilidade, essas duas rotas passam por fora desta config.
 */
@Mixin(ServerPlayer.class)
public abstract class DeathMessageMixin {

    @Redirect(method = "die", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/players/PlayerList;broadcastSystemMessage(Lnet/minecraft/network/chat/Component;Z)V"))
    private void aurorion_essentials$hideDeathMessage(PlayerList playerList, Component message, boolean bypassHiddenChat) {
        PrivacyMessages.broadcast(playerList, message, bypassHiddenChat, PrivacyConfig.DEATH_MESSAGES.get());
    }
}
