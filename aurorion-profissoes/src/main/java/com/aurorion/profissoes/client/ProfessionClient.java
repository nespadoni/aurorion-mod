package com.aurorion.profissoes.client;

import com.aurorion.profissoes.AurorionProfissoes;
import com.aurorion.profissoes.api.ProfessionApi;
import com.aurorion.profissoes.data.Profession;
import com.aurorion.profissoes.network.NpcScreenPayload;
import com.aurorion.profissoes.network.PanelPayload;
import com.aurorion.profissoes.npc.NpcEntities;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(modid = AurorionProfissoes.MOD_ID, value = Dist.CLIENT)
public final class ProfessionClient {
    private ProfessionClient() {}
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { ProfessionApi.clientProfession = Profession.NONE; }
    public static void open(PanelPayload payload) { Minecraft.getInstance().setScreen(new ProfessionScreen(payload)); }

    /** Uma resposta do servidor na mesma aba mantem a rolagem: comprar o 5o item nao volta a lista ao topo. */
    public static void openNpc(NpcScreenPayload payload) {
        var minecraft = Minecraft.getInstance();
        int scroll = minecraft.screen instanceof NpcScreen old && old.tab() == payload.tab() ? old.scroll() : 0;
        minecraft.setScreen(new NpcScreen(payload, scroll));
    }

    @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(NpcEntities.NPC.get(), NpcRenderer::new);
    }
}
