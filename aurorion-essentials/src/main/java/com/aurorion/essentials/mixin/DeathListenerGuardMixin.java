package com.aurorion.essentials.mixin;

import com.aurorion.essentials.AurorionEssentials;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * A rede de seguranca da morte: um mod com bug no {@code LivingDeathEvent} nao derruba o servidor
 * nem deixa gente viva com zero de vida.
 *
 * <h2>O que aconteceu</h2>
 *
 * <p>{@code ServerPlayer.die} comeca disparando o {@code LivingDeathEvent}. Se um listener lanca
 * excecao ali, ela sobe pelo {@code die}, pelo tick do servidor e derruba o loop inteiro — e o
 * jogador fica <b>no meio da propria morte</b>: vida zero, {@code dead = false}, sem tela de morte,
 * sem respawn, sem drop. Foi exatamente isso no crash de 23/09/2026, com o
 * {@code jonesbounty} ({@code OnplayerkillProcedure}, {@code ArrayList.remove(-1)}) disparado por uma
 * morte do PlayerRevive.
 *
 * <p>Este redirect envolve <b>um</b> ponto — o disparo do evento de morte — num {@code try/catch}. O
 * listener quebrado perde o turno dele (e os listeners seguintes tambem — ver abaixo) e o resto
 * da morte acontece normalmente: mensagem, drop, respawn. O stack trace vai inteiro para o log, com o
 * nome do mod culpado, para que o problema seja consertado na origem em vez de ficar escondido.
 *
 * <h2>Por que engolir a excecao e o certo aqui</h2>
 *
 * <p>Nao e: o certo e o mod culpado nao lancar. Mas entre "um mod de recompensa perde um registro" e
 * "o servidor de oitenta pessoas cai e alguem fica morto-vivo", a escolha e obvia. O log em
 * {@code ERROR} existe para que a primeira metade nao seja esquecida.
 *
 * <p>{@code require = 0}: se uma versao futura do NeoForge mudar a linha, o mixin simplesmente nao
 * se aplica e o jogo sobe igual — sem a rede, mas sem quebrar. Conferir no log de inicializacao
 * depois de atualizar o NeoForge.
 *
 * <h2>O que a rede NAO consegue fazer, e quem cobre</h2>
 *
 * <p>O barramento para no listener que lancou: todos os que viriam depois dele (inclusive a cobranca
 * de vida do {@code aurorion-vidas}, em {@code LOW}) nao rodam. Retomar o despacho daqui nao e
 * possivel sem saber qual listener falhou, e repostar o evento rodaria de novo os que ja tinham
 * rodado — drop marcado duas vezes, vida cobrada duas vezes. Por isso a garantia da vida mora no
 * proprio {@code aurorion-vidas} ({@code DeathLedger}): ele abre a conta da morte em {@code HIGHEST}
 * e, se no fim do tick ela continuar aberta com o evento nao cancelado, cobra ali. Este mixin so
 * precisa devolver o evento intacto e avisar a staff de quem foi a morte.
 */
@Mixin(value = CommonHooks.class, remap = false)
public class DeathListenerGuardMixin {

    @Redirect(method = "onLivingDeath", require = 0, at = @At(value = "INVOKE",
            target = "Lnet/neoforged/bus/api/IEventBus;post(Lnet/neoforged/bus/api/Event;)Lnet/neoforged/bus/api/Event;"))
    private static Event aurorion_essentials$guardDeathListeners(IEventBus bus, Event event) {
        try {
            return bus.post(event);
        } catch (Throwable failure) {
            // Devolve o evento como veio: nao cancelado, ou seja, a morte segue.
            String who = aurorion_essentials$describe(event);
            AurorionEssentials.LOGGER.error(
                    "Um listener de LivingDeathEvent lancou excecao na morte de {}. A morte foi concluida mesmo "
                            + "assim (sem esta rede, o servidor cairia e o jogador ficaria com 0 de vida sem morrer). "
                            + "Conserte ou remova o mod do topo do stack trace.", who, failure);
            if (event instanceof LivingDeathEvent death && death.getEntity() instanceof ServerPlayer player) {
                aurorion_essentials$alertStaff(player, failure);
            }
            return event;
        }
    }

    @Unique
    private static String aurorion_essentials$describe(Event event) {
        try {
            if (event instanceof LivingDeathEvent death) {
                return death.getEntity().getName().getString() + " (" + death.getEntity().getStringUUID() + ")";
            }
        } catch (Throwable ignored) {
            // Descrever nao pode virar uma segunda falha dentro do catch.
        }
        return "?";
    }

    /**
     * Aviso a staff: WARN no log com o jogador e mensagem no chat de quem tem permissao 2.
     *
     * <p>Os listeners depois do quebrado nao rodaram — vida, registro do Limbo, espolio. O
     * {@code aurorion-vidas} acerta a vida sozinho; o resto pede olho humano, e quanto antes a staff
     * souber de quem foi a morte, menor o estrago.
     */
    @Unique
    private static void aurorion_essentials$alertStaff(ServerPlayer player, Throwable failure) {
        try {
            String name = player.getGameProfile().getName();
            AurorionEssentials.LOGGER.warn("ALERTA STAFF: a morte de {} ({}) pulou os listeners seguintes por causa de "
                            + "{}. Confira vidas, Limbo e espolio desse jogador.",
                    name, player.getStringUUID(), failure.getClass().getSimpleName());
            Component text = Component.literal("[Aurorion] Um mod falhou na morte de " + name
                    + "; confira vidas/Limbo/espolio dele. Detalhes no log.").withStyle(ChatFormatting.RED);
            for (ServerPlayer online : player.server.getPlayerList().getPlayers()) {
                if (online.hasPermissions(2)) online.sendSystemMessage(text);
            }
        } catch (Throwable ignored) {
            // O aviso e melhor esforco; nunca pode impedir a morte de terminar.
        }
    }
}
