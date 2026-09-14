package com.aurorion.limbo.client;

import com.aurorion.limbo.rescue.RescuePortalEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

/**
 * A passagem: um disco preto girando, com a borda em violeta frio.
 *
 * <h2>Por que sem textura</h2>
 *
 * <p>Um PNG resolveria, e traria junto um binario no repositorio, um atlas a mais e um arquivo que
 * ninguem sabe reeditar depois. Aqui a forma sai de dois laços de trigonometria e a cor sai dos
 * vertices — o "preto" que foi pedido e um valor nesta classe, nao um arquivo para alguem abrir no
 * editor de imagem.
 *
 * <h2>Custo por frame</h2>
 *
 * <p>{@link #SEGMENTS} triangulos por anel, dois aneis: cerca de 100 vertices, e so enquanto uma
 * passagem estiver aberta e visivel. Nada e alocado por frame — o {@link PoseStack} e o
 * {@link VertexConsumer} vem prontos, e a unica matriz usada e a que ja esta na pilha (SDD §2, zero
 * alocacao no caminho quente do cliente).
 */
public class RescuePortalRenderer extends EntityRenderer<RescuePortalEntity> {
    /** Lados do disco. 32 ja fica redondo a olho nu e custa metade de 64. */
    private static final int SEGMENTS = 32;

    private static final float RADIUS = 1.25F;
    /** O miolo: preto de verdade, nao cinza escuro. */
    private static final int CORE_R = 4, CORE_G = 2, CORE_B = 8;
    /** A borda: violeta frio, o unico lugar com cor. */
    private static final int RIM_R = 96, RIM_G = 62, RIM_B = 140;

    public RescuePortalRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(RescuePortalEntity entity, float yaw, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int light) {
        // Fecha encolhendo: os ultimos segundos sao visiveis sem precisar de HUD nenhum.
        float closing = Mth.clamp(entity.ticksLeft() / 40.0F, 0.0F, 1.0F);
        if (closing <= 0.01F) return;

        float spin = (entity.tickCount + partialTick) * 1.5F;

        pose.pushPose();
        pose.translate(0.0D, 1.3D, 0.0D);
        // Sempre de frente para quem olha: a passagem e uma boca, nao uma placa.
        pose.mulPose(this.entityRenderDispatcher.cameraOrientation());
        pose.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(spin));

        VertexConsumer buffer = buffers.getBuffer(RenderType.lightning());
        Matrix4f matrix = pose.last().pose();

        disc(buffer, matrix, RADIUS * closing, CORE_R, CORE_G, CORE_B, 255);
        ring(buffer, matrix, RADIUS * closing, RADIUS * closing * 1.14F, RIM_R, RIM_G, RIM_B, 180);

        pose.popPose();
        super.render(entity, yaw, partialTick, pose, buffers, light);
    }

    /** Leque de triangulos a partir do centro. */
    private static void disc(VertexConsumer buffer, Matrix4f matrix, float radius,
                             int r, int g, int b, int alpha) {
        for (int i = 0; i < SEGMENTS; i++) {
            float a0 = (float) (i * 2 * Math.PI / SEGMENTS);
            float a1 = (float) ((i + 1) * 2 * Math.PI / SEGMENTS);

            buffer.addVertex(matrix, 0, 0, 0).setColor(r, g, b, alpha);
            buffer.addVertex(matrix, Mth.cos(a0) * radius, Mth.sin(a0) * radius, 0).setColor(r, g, b, alpha);
            buffer.addVertex(matrix, Mth.cos(a1) * radius, Mth.sin(a1) * radius, 0).setColor(r, g, b, alpha);
            // RenderType.lightning espera quads; o quarto vertice repete o terceiro e fecha o triangulo.
            buffer.addVertex(matrix, Mth.cos(a1) * radius, Mth.sin(a1) * radius, 0).setColor(r, g, b, alpha);
        }
    }

    /** Anel entre dois raios, com a cor esmaecendo para fora. */
    private static void ring(VertexConsumer buffer, Matrix4f matrix, float inner, float outer,
                             int r, int g, int b, int alpha) {
        for (int i = 0; i < SEGMENTS; i++) {
            float a0 = (float) (i * 2 * Math.PI / SEGMENTS);
            float a1 = (float) ((i + 1) * 2 * Math.PI / SEGMENTS);

            buffer.addVertex(matrix, Mth.cos(a0) * inner, Mth.sin(a0) * inner, 0).setColor(r, g, b, alpha);
            buffer.addVertex(matrix, Mth.cos(a0) * outer, Mth.sin(a0) * outer, 0).setColor(r, g, b, 0);
            buffer.addVertex(matrix, Mth.cos(a1) * outer, Mth.sin(a1) * outer, 0).setColor(r, g, b, 0);
            buffer.addVertex(matrix, Mth.cos(a1) * inner, Mth.sin(a1) * inner, 0).setColor(r, g, b, alpha);
        }
    }

    /** Nao ha textura: a cor vem dos vertices. O vanilla exige o metodo mesmo assim. */
    @Override
    public ResourceLocation getTextureLocation(RescuePortalEntity entity) {
        return ResourceLocation.withDefaultNamespace("textures/misc/white.png");
    }
}
