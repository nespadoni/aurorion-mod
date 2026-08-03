package com.aurorion.utils.client;

import com.aurorion.utils.entity.AbductionBeamEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

/**
 * Reaproveita {@link BeaconRenderer#renderBeaconBeam} — o mesmo metodo publico que o jogo usa
 * para desenhar o feixe de um beacon, scroll de textura animado incluso — em vez de escrever
 * geometria de feixe do zero. So passamos um raio maior (pedido: "maior, animado") e a cor vinda
 * de {@link AbductionBeamEntity#getColor()}.
 *
 * <p>Nas fases HOLD e ASCEND o feixe fica sempre na altura cheia, do chao ate "o infinito do
 * ceu" — ele aparece de uma vez (nao cresce aos poucos), pra bater com a ideia de "desceu do ceu"
 * como um evento subito, nao uma animacao lenta. Na fase RETRACT, sem ninguem mais montado, e o
 * contrario: a base do feixe sobe (offset vertical crescente, altura desenhada encolhendo) ate
 * ele sumir e a entidade se descartar — usamos o proprio parametro {@code yOffset} de
 * {@code renderBeaconBeam} pra isso, sem geometria nova.</p>
 */
public class AbductionBeamRenderer extends EntityRenderer<AbductionBeamEntity> {
    private static final ResourceLocation BEAM_TEXTURE =
            ResourceLocation.withDefaultNamespace("textures/entity/beacon_beam.png");

    private static final int MAX_BEAM_HEIGHT = 300;

    public AbductionBeamRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(AbductionBeamEntity entity) {
        return BEAM_TEXTURE;
    }

    @Override
    public void render(AbductionBeamEntity entity, float entityYaw, float partialTick,
                        PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        int yOffset = 0;
        int height = MAX_BEAM_HEIGHT;

        if (entity.getPhase() == AbductionBeamEntity.PHASE_RETRACT) {
            yOffset = (int) (MAX_BEAM_HEIGHT * entity.getProgress());
            height = MAX_BEAM_HEIGHT - yOffset;
        }

        if (height > 0) {
            BeaconRenderer.renderBeaconBeam(poseStack, buffer, BEAM_TEXTURE, partialTick, 1.0F,
                    entity.level().getGameTime(), yOffset, height, entity.getColor(),
                    entity.getRadius(), entity.getRadius() * 1.2F);
        }

        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
    }
}
