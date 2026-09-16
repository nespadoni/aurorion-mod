package com.aurorion.limbo.client;

import com.aurorion.limbo.oracle.OracleEntity;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;

/**
 * O corpo do Oraculo: modelo de jogador com a skin do servidor.
 *
 * <p>Reaproveita {@code ModelLayers.PLAYER} do vanilla de proposito — a skin entregue e um PNG 64x64
 * no layout padrao, entao registrar uma camada propria so duplicaria a definicao que o jogo ja tem
 * assada. Trocar a aparencia passa a ser trocar o PNG, sem recompilar nada.
 */
public final class OracleRenderer extends HumanoidMobRenderer<OracleEntity, PlayerModel<OracleEntity>> {
    private static final ResourceLocation SKIN =
            ResourceLocation.fromNamespaceAndPath("aurorion_limbo", "textures/entity/oraculo.png");

    public OracleRenderer(EntityRendererProvider.Context context) {
        // false = bracos largos (modelo "Steve"). A skin entregue usa esse layout.
        super(context, new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false), 0.5F);
    }

    @Override public ResourceLocation getTextureLocation(OracleEntity entity) { return SKIN; }
}
