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

    /** A resposta a uma escolha atualiza a tela aberta: rolagem e texto digitado continuam. */
    public static void openNpc(NpcScreenPayload payload) {
        var minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof NpcScreen open && !open.closed()) open.update(payload);
        else minecraft.setScreen(new NpcScreen(payload));
    }

    @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(NpcEntities.NPC.get(), NpcRenderer::new);
    }
}
