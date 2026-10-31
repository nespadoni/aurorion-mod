package com.aurorion.menu.client;

import com.aurorion.core.client.menu.MenuUi;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.Util;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;

/**
 * O menu visto de fora: um painel holográfico borrado na frente da pessoa, na altura do peito, voltado para
 * onde ela olha. A textura já vem borrada ({@code holo.png}): quem está perto vê que ela mexe num menu, não o
 * que ela lê. Surge crescendo, respira devagar, e some a mais de 32 blocos.
 *
 * <p>Um quad só, translúcido e com luz cheia, desenhado no fim do {@code render} do jogador (depois do
 * corpo); sem alocação por quadro.
 */
final class HoloRenderer {
    /**
     * Translúcido comum (grava profundidade) com luz cheia, desenhado <b>depois</b> do corpo: o emissivo não
     * grava profundidade, e desenhado antes o boneco o cobria — o painel parecia estar atrás da pessoa.
     */
    private static final RenderType TYPE = RenderType.entityTranslucent(MenuUi.HOLO);
    private static final float W = 0.95f, H = 0.62f;
    private static final double MAX_DISTANCE_SQR = 32 * 32;

    private HoloRenderer() {
    }

    static void render(AbstractClientPlayer player, float partial, PoseStack pose, MultiBufferSource buffers, double distanceSqr) {
        if (distanceSqr > MAX_DISTANCE_SQR) return;
        ClientPresence.State state = ClientPresence.of(player.getUUID());
        float age = (Util.getMillis() - state.since()) / 1000f;
        float grow = Mth.clamp(age / 0.28f, 0, 1);
        float scale = 0.35f + 0.65f * (1 - (1 - grow) * (1 - grow));
        float alpha = (0.72f + 0.12f * Mth.sin(age * 2.4f)) * grow;

        float yaw = Mth.rotLerp(partial, player.yHeadRotO, player.yHeadRot);
        float rad = yaw * Mth.DEG_TO_RAD;
        pose.pushPose();
        pose.translate(-Mth.sin(rad) * 0.62, player.getBbHeight() * 0.68, Mth.cos(rad) * 0.62);
        pose.mulPose(Axis.YP.rotationDegrees(-yaw));
        pose.mulPose(Axis.XP.rotationDegrees(-12));
        pose.scale(scale, scale, scale);
        PoseStack.Pose last = pose.last();
        VertexConsumer vc = buffers.getBuffer(TYPE);
        int a = (int) (alpha * 255);
        vertex(vc, last, -W / 2, -H / 2, 0, 1, a);
        vertex(vc, last, W / 2, -H / 2, 1, 1, a);
        vertex(vc, last, W / 2, H / 2, 1, 0, a);
        vertex(vc, last, -W / 2, H / 2, 0, 0, a);
        pose.popPose();
    }

    private static void vertex(VertexConsumer vc, PoseStack.Pose pose, float x, float y, float u, float v, int alpha) {
        vc.addVertex(pose, x, y, 0).setColor(255, 255, 255, alpha).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT).setNormal(pose, 0, 0, 1);
    }
}
