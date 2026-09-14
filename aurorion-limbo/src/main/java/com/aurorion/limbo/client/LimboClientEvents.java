package com.aurorion.limbo.client;

import com.aurorion.limbo.AurorionLimbo;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.FogType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@EventBusSubscriber(modid = AurorionLimbo.MOD_ID, value = Dist.CLIENT)
public final class LimboClientEvents {
    private static final ResourceLocation LIMBO = ResourceLocation.parse("aurorion_limbo:limbo");
    private LimboClientEvents() { }

    @SubscribeEvent public static void register(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.EXPERIENCE_LEVEL, ResourceLocation.parse("aurorion_limbo:exile"),
                (graphics, delta) -> {
                    if (Minecraft.getInstance().player != null && Minecraft.getInstance().screen == null) LimboHudLayer.render(graphics);
                });
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) { ClientLimbo.tick(); }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { ClientLimbo.clear(); }
    @SubscribeEvent public static void fog(ViewportEvent.ComputeFogColor event) {
        var level = Minecraft.getInstance().level;
        if (level == null || !level.dimension().location().equals(LIMBO) || event.getCamera().getFluidInCamera() != FogType.NONE) return;
        event.setRed(.055F); event.setGreen(.073F); event.setBlue(.11F);
    }
}
