package com.aurorion.essentials.mixin;

import com.aurorion.essentials.AurorionEssentials;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.CommonHooks;
import org.spongepowered.asm.mixin.Mixin;
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
 * listener quebrado perde o turno dele e o resto da morte acontece normalmente: mensagem, drop,
 * contagem de vida no {@code aurorion-vidas}, respawn. O stack trace vai inteiro para o log, com o
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
            AurorionEssentials.LOGGER.error(
                    "Um listener de LivingDeathEvent lancou excecao. A morte foi concluida mesmo assim "
                            + "(sem esta rede, o servidor cairia e o jogador ficaria com 0 de vida sem morrer). "
                            + "Conserte ou remova o mod do topo do stack trace.", failure);
            return event;
        }
    }
}
