package com.aurorion.utils.client;

import com.aurorion.utils.entity.FreezeAnchorEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import org.joml.Matrix4f;

/**
 * A ancora em si nunca aparece; o que se desenha e um anel de geada aos pes de quem esta preso,
 * para quem olha entender que aquela pessoa esta congelada e nao so parada.
 *
 * <p>Dois aneis e doze marcas, em luz aditiva ({@link RenderType#lightning()}), respirando devagar.
 * Umas cem quads por congelado visivel, sem textura e sem alocacao.
 */
public class FreezeAnchorRenderer extends EntityRenderer<FreezeAnchorEntity> {
    private static final int COLOR = 0xBFE8FF;
    private static final int SEGMENTS = 40;

    public FreezeAnchorRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(FreezeAnchorEntity entity) {
        return ResourceLocation.withDefaultNamespace("textures/entity/beacon_beam.png");
    }

    @Override
    public void render(FreezeAnchorEntity entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        if (entity.getPassengers().isEmpty()) return;
        Entity passenger = entity.getPassengers().get(0);
        float life = entity.tickCount + partialTick;
        float alpha = 0.35f + 0.15f * Mth.sin(life * 0.08f);
        float radius = passenger.getBbWidth() * 0.75f + 0.25f;

        VertexConsumer out = buffer.getBuffer(RenderType.lightning());
        Matrix4f matrix = poseStack.last().pose();
        band(out, matrix, radius - 0.05f, radius, 0.03f, alpha);
        band(out, matrix, radius * 0.72f - 0.03f, radius * 0.72f, 0.031f, alpha * 0.7f);
        for (int i = 0; i < 12; i++) {
            float angle = i * Mth.TWO_PI / 12 + life * 0.01f;
            float cos = Mth.cos(angle), sin = Mth.sin(angle);
            float nx = -sin * 0.02f, nz = cos * 0.02f;
            float inner = radius * 0.78f, outer = radius * 0.95f;
            int tint = argb(alpha);
            out.addVertex(matrix, inner * cos + nx, 0.032f, inner * sin + nz).setColor(tint);
            out.addVertex(matrix, outer * cos + nx, 0.032f, outer * sin + nz).setColor(tint);
            out.addVertex(matrix, outer * cos - nx, 0.032f, outer * sin - nz).setColor(tint);
            out.addVertex(matrix, inner * cos - nx, 0.032f, inner * sin - nz).setColor(tint);
        }
    }

    private static void band(VertexConsumer out, Matrix4f m, float inner, float outer, float y, float alpha) {
        int tint = argb(alpha);
        for (int i = 0; i < SEGMENTS; i++) {
            float a = i * Mth.TWO_PI / SEGMENTS, b = (i + 1) * Mth.TWO_PI / SEGMENTS;
            out.addVertex(m, inner * Mth.cos(a), y, inner * Mth.sin(a)).setColor(tint);
            out.addVertex(m, inner * Mth.cos(b), y, inner * Mth.sin(b)).setColor(tint);
            out.addVertex(m, outer * Mth.cos(b), y, outer * Mth.sin(b)).setColor(tint);
            out.addVertex(m, outer * Mth.cos(a), y, outer * Mth.sin(a)).setColor(tint);
        }
    }

    private static int argb(float alpha) {
        return Mth.clamp(Math.round(alpha * 255), 0, 255) << 24 | COLOR;
    }
}
