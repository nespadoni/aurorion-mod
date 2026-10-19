package com.aurorion.intencao.client;

import com.aurorion.intencao.AurorionIntencao;
import com.aurorion.intencao.registry.IntencaoEntities;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/** Registros de cliente, no bus do mod. As reacoes ficam em {@link ClientIntencao}, no bus do jogo. */
@EventBusSubscriber(modid = AurorionIntencao.MOD_ID, value = Dist.CLIENT)
public final class IntencaoClientSetup {
    private IntencaoClientSetup() {
    }

    @SubscribeEvent
    public static void registerLayers(RegisterGuiLayersEvent event) {
        // A neblina e cenario: abaixo da barra de itens e do chat. A cena cobre tudo — as palpebras
        // fechando nao podem deixar a hotbar desenhada por cima.
        event.registerBelow(VanillaGuiLayers.CAMERA_OVERLAYS, IntencaoLayers.Fog.ID, new IntencaoLayers.Fog());
        event.registerAboveAll(IntencaoLayers.Scene.ID, new IntencaoLayers.Scene());
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(IntencaoEntities.ECHO.get(), EchoRenderer::new);
        event.registerEntityRenderer(IntencaoEntities.FRAGMENT.get(), FragmentRenderer::new);
    }
}
