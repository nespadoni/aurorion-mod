package com.aurorion.profissoes.client;

import com.aurorion.profissoes.AurorionProfissoes;
import com.aurorion.profissoes.api.ProfessionApi;
import com.aurorion.profissoes.data.Profession;
import com.aurorion.profissoes.network.*;
import com.aurorion.profissoes.server.ServiceManager;
import net.minecraft.client.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = AurorionProfissoes.MOD_ID, value = Dist.CLIENT)
public final class ProfessionClient {
    private static final KeyMapping OPEN = new KeyMapping("key.aurorion_profissoes.open", GLFW.GLFW_KEY_J, "key.categories.aurorion");
    private ProfessionClient() {}
    @SubscribeEvent public static void keys(RegisterKeyMappingsEvent event) { event.register(OPEN); }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        while (OPEN.consumeClick()) {
            var minecraft = Minecraft.getInstance();
            if (minecraft.player != null && minecraft.screen == null)
                PacketDistributor.sendToServer(new ActionPayload(ServiceManager.ZERO, "open"));
        }
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { ProfessionApi.clientProfession = Profession.NONE; }
    public static void open(PanelPayload payload) { Minecraft.getInstance().setScreen(new ProfessionScreen(payload)); }
}
