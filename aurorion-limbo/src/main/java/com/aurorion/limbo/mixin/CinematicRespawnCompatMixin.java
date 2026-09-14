package com.aurorion.limbo.mixin;

import com.aurorion.limbo.AurorionLimbo;
import com.aurorion.vidas.client.ClientLives;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Impede o Cinematic Respawn de capturar a morte que manda o jogador ao Limbo.
 *
 * <p>O mod cinematografico esconde a tela de morte e mantem a entidade local em zero de vida
 * enquanto sobe a camera. Isso funciona para um respawn comum, mas pode ficar preso quando o
 * servidor troca a dimensao de respawn. Na ultima vida o Limbo ja possui sua propria chegada;
 * portanto deixamos a tela/respawn vanilla concluir e zeramos qualquer sequencia que tenha
 * comecado antes de o pacote de vidas chegar.
 *
 * <p>{@link Pseudo} mantem o Cinematic Respawn opcional. O alvo e escrito como texto e nenhuma
 * classe dele aparece na assinatura deste jar.
 */
@Pseudo
@Mixin(targets = "net.gamev.cinematic_respawn.client.RespawnCinematicController", remap = false)
public abstract class CinematicRespawnCompatMixin {
    private static Method aurorionLimbo$reset;
    private static boolean aurorionLimbo$resetUnavailable;
    private static boolean aurorionLimbo$finalDeathReset;

    @Inject(method = "onDeathScreenOpening", at = @At("HEAD"), cancellable = true, require = 0)
    private static void aurorionLimbo$keepVanillaDeathScreen(Minecraft minecraft, CallbackInfo ci) {
        if (aurorionLimbo$isFinalDeath(minecraft)) {
            ci.cancel();
        }
    }

    @Inject(method = "shouldSuppressDeathScreen", at = @At("HEAD"), cancellable = true, require = 0)
    private static void aurorionLimbo$doNotSuppressDeathScreen(
            Minecraft minecraft, Screen screen, CallbackInfoReturnable<Boolean> cir) {
        if (aurorionLimbo$isFinalDeath(minecraft)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true, require = 0)
    private static void aurorionLimbo$skipCinematicForFinalDeath(Minecraft minecraft, CallbackInfo ci) {
        if (!aurorionLimbo$isFinalDeath(minecraft)) {
            aurorionLimbo$finalDeathReset = false;
            return;
        }

        // O pacote de vidas e a abertura da DeathScreen percorrem a mesma conexao, mas outros mods
        // podem iniciar a sequencia um frame antes. Resetar torna essa corrida inofensiva.
        if (!aurorionLimbo$finalDeathReset) {
            aurorionLimbo$resetCinematic();
            aurorionLimbo$finalDeathReset = true;
        }
        ci.cancel();
    }

    private static boolean aurorionLimbo$isFinalDeath(Minecraft minecraft) {
        return ClientLives.known() && ClientLives.lives() <= 0
                && minecraft.player != null && minecraft.player.isDeadOrDying();
    }

    private static void aurorionLimbo$resetCinematic() {
        if (aurorionLimbo$resetUnavailable) return;
        try {
            Method reset = aurorionLimbo$reset;
            if (reset == null) {
                Class<?> controller = Class.forName(
                        "net.gamev.cinematic_respawn.client.RespawnCinematicController");
                reset = controller.getMethod("reset");
                aurorionLimbo$reset = reset;
            }
            reset.invoke(null);
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException
                 | InvocationTargetException | LinkageError error) {
            aurorionLimbo$resetUnavailable = true;
            AurorionLimbo.LOGGER.warn(
                    "Nao foi possivel zerar a sequencia do Cinematic Respawn; "
                            + "a tela vanilla ainda sera preservada.", error);
        }
    }
}
