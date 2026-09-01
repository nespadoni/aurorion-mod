package com.aurorion.talk.client;

import com.aurorion.talk.client.render.BalloonNineSlice;
import com.aurorion.talk.config.TalkConfig;
import com.aurorion.talk.style.BalloonStyle;
import com.aurorion.talk.util.BalloonHolder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.gui.Font;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import java.util.List;

/**
 * Desenha os baloes acima de um jogador.
 *
 * <p>Os quads vao direto para o {@link MultiBufferSource} da cena, sem instanciar
 * {@code GuiGraphics} nem usar {@code RenderSystem.polygonOffset} em modo imediato: em batch e sem
 * alocacao por frame, o que importa quando ha dezenas de jogadores falando ao mesmo tempo.</p>
 */
public final class BalloonRenderer {
    /** Toda a arte do balao cabe numa folha 32x32. */
    private static final float SHEET = 32.0F;

    private static final int LINE_HEIGHT = 9;
    /** Enfeites sao 16x16 e desenhados 1:1 — escalar pixel art quebra a nitidez. */
    private static final int DECORATION_SIZE = 16;

    /** Baloes sempre legiveis, mesmo numa caverna escura. */
    private static final int LIGHT = LightTexture.FULL_BRIGHT;

    private enum Pass {
        FRAME,
        DECORATION,
        TEXT
    }

    /** Consumer unico reaproveitado no render thread; evita uma lambda capturante por balao/frame. */
    private static final class FrameBoxConsumer implements BalloonNineSlice.BoxConsumer {
        private VertexConsumer vertices;
        private Matrix4f pose;
        private int tint;

        void prepare(VertexConsumer vertices, Matrix4f pose, int tint) {
            this.vertices = vertices;
            this.pose = pose;
            this.tint = tint;
        }

        @Override
        public void box(int x, int y, int w, int h, int u, int v, int uw, int vh) {
            quad(vertices, pose, tint, x, y, w, h, u, v, uw, vh);
        }
    }

    private static final FrameBoxConsumer FRAME_BOXES = new FrameBoxConsumer();

    private BalloonRenderer() {
    }

    public static void render(AbstractClientPlayer player, PoseStack poseStack, MultiBufferSource buffer,
                              EntityRenderDispatcher dispatcher, Font font, long gameTime) {
        List<BalloonMessage> messages = ((BalloonHolder) player).aurorion_talk$getBalloons();
        if (messages.isEmpty()) return;

        BalloonStyle style = ClientStyles.get(player.getUUID());
        int tint = 0xFF000000 | style.color();
        int textColor = 0xFF000000 | style.textColor();

        int minWidth = TalkConfig.MIN_BALLOON_WIDTH.get();
        int maxWidth = TalkConfig.MAX_BALLOON_WIDTH.get();
        int gap = TalkConfig.DISTANCE_BETWEEN_BALLOONS.get();

        poseStack.pushPose();
        poseStack.translate(0.0, player.getBbHeight() + TalkConfig.HEIGHT_OFFSET.get(), 0.0);
        // Billboard so no eixo Y: o balao acompanha a camera mas nunca tomba.
        poseStack.mulPose(Axis.YP.rotationDegrees(cameraYawDegrees(dispatcher.cameraOrientation()) + 180.0F));
        poseStack.scale(-0.025F, -0.025F, 0.025F);

        Matrix4f pose = poseStack.last().pose();

        // Uma passada por textura, nunca intercaladas. O VertexConsumer que o MultiBufferSource
        // devolve morre assim que outro RenderType pede o buffer compartilhado — BufferSource#getBuffer
        // fecha o batch anterior — entao segurar a referencia por cima de um getBuffer alheio estoura
        // "Not building!". De quebra, cada textura sai num draw call so para todos os baloes do jogador.
        VertexConsumer frames = buffer.getBuffer(RenderType.text(style.skin()));
        FRAME_BOXES.prepare(frames, pose, tint);
        renderPass(Pass.FRAME, messages, gameTime, gap, frames, pose, minWidth, maxWidth, font, buffer, textColor);

        if (style.hasDecoration()) {
            VertexConsumer decorations = buffer.getBuffer(RenderType.text(style.decoration().orElseThrow()));
            renderPass(Pass.DECORATION, messages, gameTime, gap, decorations, pose,
                    minWidth, maxWidth, font, buffer, textColor);
        }

        renderPass(Pass.TEXT, messages, gameTime, gap, frames, pose,
                minWidth, maxWidth, font, buffer, textColor);

        poseStack.popPose();
    }

    /**
     * Percorre as mensagens ainda vivas, da mais recente (colada na cabeca) para a mais antiga
     * (empilhando para cima), entregando o deslocamento vertical ja acumulado.
     *
     * <p>O empilhamento mora aqui e so aqui: as passadas de balao, enfeite e texto tem que concordar
     * pixel a pixel, e recalcular a conta em tres lugares e como elas desalinhariam.</p>
     */
    private static void renderPass(Pass pass, List<BalloonMessage> messages, long gameTime, int gap,
                                   VertexConsumer vertices, Matrix4f pose, int minWidth, int maxWidth,
                                   Font font, MultiBufferSource buffer, int textColor) {
        int stackOffset = 0;
        int previousHeight = 0;
        boolean isNewest = true;

        for (int i = messages.size() - 1; i >= 0; i--) {
            BalloonMessage message = messages.get(i);
            if (message.expiresAtTick() <= gameTime) continue;

            if (previousHeight != 0) stackOffset += LINE_HEIGHT * previousHeight + gap;
            previousHeight = message.lineCount();

            switch (pass) {
                case FRAME -> {
                    int width = Mth.clamp(message.widestLine(), minWidth, maxWidth);
                    if (width % 2 == 0) width--; // largura impar centraliza a setinha certinho
                    BalloonNineSlice.emit(FRAME_BOXES, width, message.lineCount(), stackOffset, isNewest);
                }
                case DECORATION -> drawDecoration(vertices, pose, message.lineCount(), stackOffset);
                case TEXT -> drawText(font, buffer, pose, message.lines(), textColor, stackOffset);
            }
            isNewest = false;
        }
    }

    private static void drawDecoration(VertexConsumer vc, Matrix4f pose, int lineCount, int stackOffset) {
        int top = BalloonNineSlice.top(lineCount, stackOffset);

        // Sem tingimento: o enfeite tem cor propria e ficaria lavado pelo tint do balao.
        quadFullSheet(vc, pose, 0xFFFFFFFF, -DECORATION_SIZE / 2, top - DECORATION_SIZE + 4, DECORATION_SIZE, DECORATION_SIZE);
    }

    private static void drawText(Font font, MultiBufferSource buffer, Matrix4f pose,
                                 List<FormattedCharSequence> lines, int color, int stackOffset) {
        int y = -(LINE_HEIGHT * lines.size() - 10) - stackOffset;

        for (FormattedCharSequence line : lines) {
            // POLYGON_OFFSET puxa o texto para frente do balao sem depender da orientacao da camera.
            font.drawInBatch(line, -font.width(line) / 2.0F + 1.0F, y, color, false, pose, buffer,
                    Font.DisplayMode.POLYGON_OFFSET, 0, LIGHT);
            y += LINE_HEIGHT;
        }
    }

    /** Quad com UV apontando para uma regiao (em pixels) da folha 32x32. */
    private static void quad(VertexConsumer vc, Matrix4f pose, int argb,
                             int x, int y, int w, int h, int u, int v, int uw, int vh) {
        emit(vc, pose, argb, x, y, w, h, u / SHEET, v / SHEET, (u + uw) / SHEET, (v + vh) / SHEET);
    }

    /** Quad usando a textura inteira (enfeites tem arquivo proprio). */
    private static void quadFullSheet(VertexConsumer vc, Matrix4f pose, int argb, int x, int y, int w, int h) {
        emit(vc, pose, argb, x, y, w, h, 0.0F, 0.0F, 1.0F, 1.0F);
    }

    private static void emit(VertexConsumer vc, Matrix4f pose, int argb,
                             float x, float y, float w, float h,
                             float u0, float v0, float u1, float v1) {
        // Mesma ordem de vertices que o BakedGlyph do vanilla usa, para casar com o culling.
        vc.addVertex(pose, x, y, 0.0F).setColor(argb).setUv(u0, v0).setLight(LIGHT);
        vc.addVertex(pose, x, y + h, 0.0F).setColor(argb).setUv(u0, v1).setLight(LIGHT);
        vc.addVertex(pose, x + w, y + h, 0.0F).setColor(argb).setUv(u1, v1).setLight(LIGHT);
        vc.addVertex(pose, x + w, y, 0.0F).setColor(argb).setUv(u1, v0).setLight(LIGHT);
    }

    /** Yaw da camera em graus, extraido do quaternion de orientacao. */
    private static float cameraYawDegrees(Quaternionf orientation) {
        float w = orientation.w() * orientation.w();
        float x = orientation.x() * orientation.x();
        float y = orientation.y() * orientation.y();
        float z = orientation.z() * orientation.z();
        float sum = w + x + y + z;
        float sin = 2.0F * orientation.w() * orientation.x() - 2.0F * orientation.y() * orientation.z();

        if (Math.abs(sin) > 0.999F * sum) {
            return (float) Math.toDegrees(2.0 * Math.atan2(orientation.y(), orientation.w()));
        }

        double yaw = Math.atan2(
                2.0F * orientation.x() * orientation.z() + 2.0F * orientation.y() * orientation.w(),
                w - x - y + z);
        return (float) Math.toDegrees(yaw);
    }
}
