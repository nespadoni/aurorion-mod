package com.aurorion.magia.client;

import com.aurorion.magia.entity.MagiaProjectileEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix4f;

/**
 * O desenho dos projeteis das magias novas. Barril, bomba e bigorna sao os blocos do vanilla
 * (girando, quando voam); a esfera, a Tempera e a garra sao luz, na mesma tinta aditiva dos selos; o
 * shuriken e uma estrela de quatro laminas girando rapido. O relogio do Pyke e o do Sova sao
 * invisiveis.
 */
public class MagiaProjectileRenderer extends EntityRenderer<MagiaProjectileEntity> {
    private final BlockRenderDispatcher blocks;

    public MagiaProjectileRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.blocks = context.getBlockRenderDispatcher();
        this.shadowRadius = 0;
    }

    @Override
    public ResourceLocation getTextureLocation(MagiaProjectileEntity entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }

    @Override
    public boolean shouldRender(MagiaProjectileEntity entity, Frustum frustum, double x, double y, double z) {
        return frustum.isVisible(entity.getBoundingBox().inflate(entity.size() + 1));
    }

    @Override
    public void render(MagiaProjectileEntity entity, float yaw, float partial, PoseStack pose, MultiBufferSource buffers,
                       int light) {
        float life = entity.tickCount + partial;
        switch (entity.shape()) {
            case DOLIUM -> spinningBlock(pose, buffers, light, Blocks.BARREL.defaultBlockState(), entity.size(), life * 18);
            case PYROBOLUS -> spinningBlock(pose, buffers, light, Blocks.TNT.defaultBlockState(), entity.size(), life * 10);
            case INCUS -> {
                pose.pushPose();
                pose.mulPose(Axis.YP.rotationDegrees(-entity.getYRot()));
                pose.translate(-.5, 0, -.5);
                blocks.renderSingleBlock(Blocks.ANVIL.defaultBlockState(), pose, buffers, light, OverlayTexture.NO_OVERLAY);
                pose.popPose();
            }
            case STELLA -> {
                pose.pushPose();
                pose.translate(0, .3, 0);
                pose.mulPose(Axis.YP.rotationDegrees(-entity.getYRot()));
                pose.mulPose(Axis.XP.rotationDegrees(entity.getXRot()));
                pose.mulPose(Axis.YP.rotationDegrees(life * 45));
                star(buffers.getBuffer(SigilRenderer.glowType()), pose.last().pose(), entity.size());
                pose.popPose();
            }
            case SPHAERA -> {
                pose.pushPose();
                pose.translate(0, .3, 0);
                VertexConsumer glow = buffers.getBuffer(SigilRenderer.glowType());
                float radius = entity.size() * (1 + .03F * Mth.sin(life * .5F));
                KitVisuals.sphere(glow, pose.last().pose(), radius, 0x8CD8FF, .3F, 12, 24);
                KitVisuals.sphere(glow, pose.last().pose(), radius * .7F, 0xE8FBFF, .6F, 10, 20);
                pose.popPose();
            }
            case TEMPERIES -> glowBall(pose, buffers, entity.size(), 0xFFCC4D);
            case RAPAX -> glowBall(pose, buffers, entity.size(), 0xFFD34A);
            default -> {
            }
        }
    }

    /** Bloco girando em volta do proprio centro, na escala pedida. */
    private void spinningBlock(PoseStack pose, MultiBufferSource buffers, int light, BlockState state, float scale, float degrees) {
        pose.pushPose();
        pose.translate(0, .3, 0);
        pose.scale(scale, scale, scale);
        pose.mulPose(Axis.YP.rotationDegrees(degrees));
        pose.mulPose(Axis.XP.rotationDegrees(degrees * .6F));
        pose.translate(-.5, -.5, -.5);
        blocks.renderSingleBlock(state, pose, buffers, light, OverlayTexture.NO_OVERLAY);
        pose.popPose();
    }

    private static void glowBall(PoseStack pose, MultiBufferSource buffers, float radius, int color) {
        pose.pushPose();
        pose.translate(0, .3, 0);
        VertexConsumer glow = buffers.getBuffer(SigilRenderer.glowType());
        KitVisuals.sphere(glow, pose.last().pose(), radius, color, .45F, 8, 16);
        KitVisuals.sphere(glow, pose.last().pose(), radius * .55F, 0xFFFFFF, .7F, 6, 12);
        pose.popPose();
    }

    /** Quatro laminas no plano horizontal do shuriken, mais finas na ponta. */
    private static void star(VertexConsumer out, Matrix4f m, float radius) {
        int edge = SigilGeometry.argb(0xE8ECF2, .9F);
        int core = SigilGeometry.argb(0x9AA4B2, .9F);
        for (int i = 0; i < 4; i++) {
            float a = i * Mth.HALF_PI;
            float tipX = Mth.cos(a) * radius, tipZ = Mth.sin(a) * radius;
            float sideX = Mth.cos(a + Mth.HALF_PI) * radius * .18F, sideZ = Mth.sin(a + Mth.HALF_PI) * radius * .18F;
            out.addVertex(m, 0, 0, 0).setColor(core);
            out.addVertex(m, sideX, 0, sideZ).setColor(core);
            out.addVertex(m, tipX, 0, tipZ).setColor(edge);
            out.addVertex(m, -sideX, 0, -sideZ).setColor(core);
        }
    }
}
