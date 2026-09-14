package com.aurorion.ethereal.client;

import com.aurorion.ethereal.AurorionEthereal;
import com.aurorion.ethereal.registry.EtherealBlockEntities;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(modid = AurorionEthereal.MOD_ID, value = Dist.CLIENT)
public final class EtherealClientEvents {
    private EtherealClientEvents() {
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(EtherealBlockEntities.AEONIC_PROJECTOR.get(),
                AeonicProjectorRenderer::new);
    }
}
