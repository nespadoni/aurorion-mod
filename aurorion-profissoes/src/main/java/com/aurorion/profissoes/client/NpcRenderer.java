package com.aurorion.profissoes.client;

import com.aurorion.profissoes.npc.ProfessionNpcEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;
import java.util.HashMap;
import java.util.Map;

/**
 * Corpo de jogador com a skin escolhida no JSON ({@code skin}, caminho de textura de resource pack).
 * Mesmo arranjo do {@code OracleRenderer}, com os dois modelos vanilla: braco largo e fino
 * ({@code slim_skin}). Skin ausente ou invalida cai no Steve/Alex padrao.
 */
public final class NpcRenderer extends HumanoidMobRenderer<ProfessionNpcEntity, PlayerModel<ProfessionNpcEntity>> {
    private static final ResourceLocation WIDE = ResourceLocation.withDefaultNamespace("textures/entity/player/wide/steve.png");
    private static final ResourceLocation SLIM = ResourceLocation.withDefaultNamespace("textures/entity/player/slim/alex.png");
    private final PlayerModel<ProfessionNpcEntity> wideModel;
    private final PlayerModel<ProfessionNpcEntity> slimModel;
    private final Map<String, ResourceLocation> skins = new HashMap<>();

    public NpcRenderer(EntityRendererProvider.Context context) {
        super(context, new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false), 0.5F);
        wideModel = model;
        slimModel = new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER_SLIM), true);
    }

    @Override
    public void render(ProfessionNpcEntity entity, float yaw, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int light) {
        model = entity.slimSkin() ? slimModel : wideModel;
        super.render(entity, yaw, partialTick, pose, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(ProfessionNpcEntity entity) {
        String skin = entity.skin();
        if (skin.isEmpty()) return entity.slimSkin() ? SLIM : WIDE;
        return skins.computeIfAbsent(skin, value -> {
            var location = ResourceLocation.tryParse(value);
            return location == null ? WIDE : location;
        });
    }
}
