package com.aurorion.magia.client;

import com.aurorion.magia.network.SpellVisualPayload;
import com.aurorion.magia.registry.MagiaEffects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;

/**
 * A camera da Gretta durante o Olhar Anacronico: os olhos dela passam para os do alvo.
 *
 * <p>So o cliente dela recebe o visual {@code ANACHRONIC_SIGHT}. A troca e de {@code setCameraEntity}
 * do proprio cliente — a do servidor arrastaria o corpo dela para dentro do alvo a cada tick. Ela volta
 * sozinha quando o efeito some, quando o alvo sai do alcance de rastreio, ou no logout.
 */
public final class AnachronicCamera {
    private static int watching = -1;

    private AnachronicCamera() {
    }

    static void accept(SpellVisualPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null) return;
        if (payload.ttl() <= 0) {
            reset();
            return;
        }
        if (payload.casterId() != player.getId()) return;
        watching = payload.targetId();
        Entity target = minecraft.level.getEntity(watching);
        freeze(player);
        if (target != null) minecraft.setCameraEntity(target);
    }

    /**
     * Com a camera em outro, o vanilla para de copiar o input para o corpo ({@code isControlledCamera}
     * falso) e o que estava apertado na hora da troca continuaria valendo: ela deslizaria e pularia
     * sozinha. Zera o movimento a cada tick enquanto estiver fora do corpo.
     */
    private static void freeze(LocalPlayer player) {
        player.xxa = 0;
        player.zza = 0;
        player.setJumping(false);
        player.setSprinting(false);
    }

    /** Todo tick do cliente: volta a camera se o olhar acabou ou o alvo sumiu. */
    static void tick() {
        if (watching < 0) return;
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null) {
            watching = -1;
            return;
        }
        if (!player.hasEffect(MagiaEffects.ANACHRONIC_SIGHT)) {
            reset();
            return;
        }
        freeze(player);
        Entity target = minecraft.level.getEntity(watching);
        if (target == null || target.isRemoved()) {
            // Saiu do alcance (teleporte longo): a camera espera no corpo ate o servidor trazer Gretta
            // para perto dele de novo, sem esquecer quem ela estava olhando.
            if (minecraft.getCameraEntity() != player) minecraft.setCameraEntity(player);
            return;
        }
        // O alvo pode ter acabado de entrar no alcance de rastreio, depois do pacote.
        if (minecraft.getCameraEntity() != target) minecraft.setCameraEntity(target);
    }

    static void reset() {
        watching = -1;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null && minecraft.getCameraEntity() != minecraft.player) {
            minecraft.setCameraEntity(minecraft.player);
        }
    }

    /** O corpo de Gretta nao anda sozinho enquanto ela esta nos olhos de outro. */
    static boolean locked() {
        LocalPlayer player = Minecraft.getInstance().player;
        return player != null && player.hasEffect(MagiaEffects.ANACHRONIC_SIGHT);
    }
}
