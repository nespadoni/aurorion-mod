package com.aurorion.utils.client;

import com.aurorion.utils.entity.FreezeAnchorEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

/**
 * A ancora de freeze nunca aparece na tela — precisa de um renderer registrado (o jogo exige um
 * pra qualquer EntityType), mas ele nao desenha nada.
 */
public class FreezeAnchorRenderer extends EntityRenderer<FreezeAnchorEntity> {
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
        // De proposito: nao desenha nada.
    }
}
