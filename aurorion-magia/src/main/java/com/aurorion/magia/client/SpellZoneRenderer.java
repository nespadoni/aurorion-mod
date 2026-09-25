package com.aurorion.magia.client;

import com.aurorion.magia.config.MagiaClientConfig;
import com.aurorion.magia.entity.SpellZoneEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

/**
 * O desenho das tres zonas de vento — barreira, coluna e furacao.
 *
 * <p>A geometria segue a mesma tecnica dos selos ({@link SigilRenderer}): tinta translucida para o que
 * escurece, luz aditiva para o que brilha, sem textura e sem entidade extra. A diferenca e que aqui o
 * ponto de ancoragem ja e a propria entidade, entao o vanilla resolve sozinho a interpolacao da
 * posicao do furacao entre um tick e outro.
 *
 * <p>As particulas saem <b>daqui</b>, do quadro do renderizador, e nao do {@code tick} da entidade:
 * assim elas respeitam a opcao "Particulas" do vanilla e a {@code visualDistance} do mod, como todo o
 * resto do modulo. Uma zona fora de vista nao gasta nada — o vanilla nem chama este renderizador.
 */
public class SpellZoneRenderer extends EntityRenderer<SpellZoneEntity> {
    /** Textura obrigatoria pelo contrato de {@code EntityRenderer}; nada aqui a usa. */
    private static final ResourceLocation NO_TEXTURE =
            ResourceLocation.withDefaultNamespace("textures/misc/white.png");

    private static final int WIND_PALE = 0xDFFFF4;
    private static final int WIND_DEEP = 0x4E8C7A;
    private static final ParticleOptions BREEZE = ClientSpellVisuals.WIND;
    /** Reaproveitado a cada quadro, como o do {@link SigilRenderer}: giro nao precisa alocar. */
    private static final Quaternionf ROTATION = new Quaternionf();

    public SpellZoneRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0;
    }

    @Override
    public ResourceLocation getTextureLocation(SpellZoneEntity entity) {
        return NO_TEXTURE;
    }

    @Override
    public boolean shouldRender(SpellZoneEntity entity, Frustum frustum, double x, double y, double z) {
        // O raio da zona e maior que a hitbox de 0,5 bloco, entao o teste padrao a cortaria cedo.
        return frustum.isVisible(entity.getBoundingBox().inflate(entity.radius() + entity.height()));
    }

    @Override
    public void render(SpellZoneEntity zone, float yaw, float partial, PoseStack pose, MultiBufferSource buffers,
                       int light) {
        float fade = zone.fade(partial);
        if (fade <= .01F) return;
        float life = zone.tickCount + partial;
        float radius = zone.radius();
        float height = zone.height();

        VertexConsumer glow = buffers.getBuffer(SigilRenderer.glowType());
        VertexConsumer ink = buffers.getBuffer(SigilRenderer.inkType());

        pose.pushPose();
        Matrix4f matrix = pose.last().pose();
        switch (zone.shape()) {
            case WARD -> ward(glow, ink, pose, matrix, radius, height, life, fade);
            case COLUMN -> column(glow, pose, matrix, radius, height, life, fade);
            case STORM -> storm(glow, ink, pose, matrix, radius, height, life, fade);
        }
        pose.popPose();

        particles(zone, life);
    }

    /** A barreira: parede de vento fechada, o circulo no chao e o anel rúnico no topo. */
    private static void ward(VertexConsumer glow, VertexConsumer ink, PoseStack pose, Matrix4f matrix,
                             float radius, float height, float life, float fade) {
        float breath = .93F + .07F * Mth.sin(life * .18F);
        SigilGeometry.wall(glow, matrix, radius * breath, height, WIND_PALE, .30F * fade, 0, 48);
        SigilGeometry.wall(ink, matrix, radius * breath * .99F, height, WIND_DEEP, .10F * fade, 0, 48);
        SigilGeometry.band(glow, matrix, radius * .88F, radius, .04F, WIND_PALE, .55F * fade, 64);

        pose.pushPose();
        pose.mulPose(ROTATION.rotationY(life * .9F * Mth.DEG_TO_RAD));
        scaled(glow, pose, SigilGeometry.RUNE_RING, radius * .92F, .02F, WIND_PALE, .6F * fade, .03F);
        pose.popPose();
    }

    /** A coluna: o funil ao contrario, estreito embaixo e aberto em cima, e a espiral no chao. */
    private static void column(VertexConsumer glow, PoseStack pose, Matrix4f matrix, float radius, float height,
                               float life, float fade) {
        SigilGeometry.funnel(glow, matrix, radius * .55F, radius, height, life * .06F,
                WIND_PALE, .42F * fade, 0, 24, 8);
        pose.pushPose();
        pose.mulPose(ROTATION.rotationY(-life * 2.4F * Mth.DEG_TO_RAD));
        scaled(glow, pose, SigilGeometry.SPIRAL, radius, .025F, WIND_PALE, .7F * fade, .02F);
        pose.popPose();
    }

    /** O furacao: funil largo em cima, fechado embaixo, girando rapido e escurecendo o que cobre. */
    private static void storm(VertexConsumer glow, VertexConsumer ink, PoseStack pose, Matrix4f matrix,
                              float radius, float height, float life, float fade) {
        SigilGeometry.funnel(ink, matrix, radius * .25F, radius, height, -life * .09F,
                0x0E1A18, .55F * fade, .12F * fade, 28, 10);
        SigilGeometry.funnel(glow, matrix, radius * .3F, radius * 1.02F, height, -life * .12F,
                WIND_PALE, .30F * fade, 0, 28, 10);
        pose.pushPose();
        pose.mulPose(ROTATION.rotationY(-life * 6F * Mth.DEG_TO_RAD));
        scaled(glow, pose, SigilGeometry.SPIRAL, radius * .9F, .03F, WIND_PALE, .65F * fade, .02F);
        pose.popPose();
    }

    /** Uma malha de raio 1 escalada, com a largura do traco em blocos. Espelha o {@code drawMesh}. */
    private static void scaled(VertexConsumer out, PoseStack pose, float[] mesh, float radius, float width,
                               int color, float alpha, float height) {
        if (radius <= .001F || alpha <= .004F) return;
        pose.pushPose();
        pose.scale(radius, 1, radius);
        SigilGeometry.mesh(out, pose.last().pose(), mesh, width / radius, color, alpha, height);
        pose.popPose();
    }

    /**
     * O ar visivel. Respeita a opcao "Particulas" do vanilla e a {@code visualDistance}, como o resto
     * do modulo — e por isso nao vive no {@code tick} da entidade, que roda mesmo fora de vista.
     */
    private static void particles(SpellZoneEntity zone, float life) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.isPaused()) return;
        int stride = switch (minecraft.options.particles().get()) {
            case ALL -> 1;
            case DECREASED -> 2;
            case MINIMAL -> 4;
        };
        if (zone.tickCount % stride != 0 || !zone.claimParticleTick()) return;

        double maxDistance = MagiaClientConfig.VISUAL_DISTANCE.get();
        Vec3 eye = minecraft.gameRenderer.getMainCamera().getPosition();
        Vec3 center = zone.position();
        if (center.distanceToSqr(eye) > maxDistance * maxDistance) return;

        RandomSource random = minecraft.level.random;
        float radius = zone.radius();
        float height = zone.height();
        int count = Math.max(1, 3 / stride);
        for (int i = 0; i < count; i++) {
            double angle = random.nextDouble() * Math.PI * 2 + life * .2;
            double r = zone.shape() == SpellZoneEntity.Shape.WARD
                    ? radius
                    : radius * (0.35 + random.nextDouble() * 0.65);
            double y = random.nextDouble() * height;
            double x = center.x + Math.cos(angle) * r;
            double z = center.z + Math.sin(angle) * r;
            Vec3 swirl = new Vec3(-Math.sin(angle), 0, Math.cos(angle)).scale(0.22);
            double rise = switch (zone.shape()) {
                case WARD -> 0.02;
                case COLUMN -> 0.35;
                case STORM -> 0.12;
            };
            minecraft.level.addParticle(BREEZE, x, center.y + y, z, swirl.x, rise, swirl.z);
            if (random.nextInt(4) == 0) {
                minecraft.level.addParticle(ParticleTypes.CLOUD, x, center.y + y, z,
                        swirl.x * .5, rise * .5, swirl.z * .5);
            }
        }
        if (zone.shape() == SpellZoneEntity.Shape.STORM && zone.tickCount % (4 * stride) == 0) {
            minecraft.level.addParticle(ParticleTypes.GUST, center.x, center.y + height * .5, center.z, 0, 0, 0);
        }
    }
}
