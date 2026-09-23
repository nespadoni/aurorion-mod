package com.aurorion.utils.client;

import com.aurorion.utils.AurorionUtils;
import com.aurorion.utils.entity.ModEntities;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

@EventBusSubscriber(modid = AurorionUtils.MOD_ID, value = Dist.CLIENT)
public final class UtilsClientEvents {
    private UtilsClientEvents() {
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.ABDUCTION_BEAM.get(), AbductionBeamRenderer::new);
        event.registerEntityRenderer(ModEntities.FREEZE_ANCHOR.get(), FreezeAnchorRenderer::new);
    }

    @SubscribeEvent
    public static void registerLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.CAMERA_OVERLAYS, FrostLayer.ID, new FrostLayer());
    }
}
