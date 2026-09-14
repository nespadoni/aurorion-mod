package com.aurorion.limbo.client;

import com.aurorion.limbo.AurorionLimbo;
import net.minecraft.sounds.SoundSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.sound.PlaySoundEvent;

@EventBusSubscriber(modid = AurorionLimbo.MOD_ID, value = Dist.CLIENT)
public final class FinaleClientEvents {
    private FinaleClientEvents() { }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) { ClientFinale.tick(); }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { ClientFinale.clear(); }
    @SubscribeEvent public static void hud(RenderGuiEvent.Pre event) { if (ClientFinale.cinematic()) event.setCanceled(true); }
    @SubscribeEvent public static void hand(RenderHandEvent event) { if (ClientFinale.cinematic()) event.setCanceled(true); }
    @SubscribeEvent public static void sound(PlaySoundEvent event) {
        if (!ClientFinale.active() || event.getSound() == null) return;
        var sound = event.getSound();
        if ((sound.getSource() == SoundSource.MUSIC || ClientFinale.cinematic())
                && !sound.getLocation().toString().equals(ClientFinale.script().music())) event.setSound(null);
    }
}
