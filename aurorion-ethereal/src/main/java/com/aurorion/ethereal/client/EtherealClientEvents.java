package com.aurorion.ethereal.client;

import com.aurorion.ethereal.AurorionEthereal;
import com.aurorion.ethereal.registry.EtherealBlockEntities;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

@EventBusSubscriber(modid = AurorionEthereal.MOD_ID, value = Dist.CLIENT)
public final class EtherealClientEvents {
    private EtherealClientEvents() {
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(EtherealBlockEntities.AEONIC_PROJECTOR.get(),
                AeonicProjectorRenderer::new);
    }

    /** O relogio de toda animacao de rito. Sai na primeira linha quando nao ha nenhum acontecendo. */
    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        RiteClient.tick();
    }

    /** Circulos, runas e nome da casa, inclusive quando o corpo esta fora do alcance de render. */
    @SubscribeEvent
    public static void renderWorld(RenderLevelStageEvent event) {
        RiteRenderer.render(event);
    }

    /** Nome e lema, so para quem esta passando pelo rito. */
    @SubscribeEvent
    public static void renderGui(RenderGuiEvent.Post event) {
        RiteOverlay.render(event.getGuiGraphics());
    }

    /** Ids de entidade nao valem no proximo mundo. */
    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        RiteClient.clear();
    }
}
