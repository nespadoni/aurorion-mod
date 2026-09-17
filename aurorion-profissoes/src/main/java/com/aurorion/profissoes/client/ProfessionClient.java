package com.aurorion.profissoes.client;

import com.aurorion.profissoes.AurorionProfissoes;
import com.aurorion.profissoes.api.ProfessionApi;
import com.aurorion.profissoes.data.Profession;
import com.aurorion.profissoes.network.PanelPayload;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

@EventBusSubscriber(modid = AurorionProfissoes.MOD_ID, value = Dist.CLIENT)
public final class ProfessionClient {
    private ProfessionClient() {}
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { ProfessionApi.clientProfession = Profession.NONE; }
    public static void open(PanelPayload payload) { Minecraft.getInstance().setScreen(new ProfessionScreen(payload)); }
}
