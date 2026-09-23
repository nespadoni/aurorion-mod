package com.aurorion.profissoes.npc;

import com.aurorion.profissoes.AurorionProfissoes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

@EventBusSubscriber(modid = AurorionProfissoes.MOD_ID)
public final class NpcEvents {
    private NpcEvents() {}

    /**
     * Antes dos mundos carregarem: os NPCs dos chunks de spawn entram no mundo durante a subida e ja
     * precisam achar o catalogo pronto. Os registros (itens, componentes) ja existem neste ponto.
     */
    @SubscribeEvent public static void aboutToStart(ServerAboutToStartEvent event) {
        NpcService.started();
        NpcCatalog.reload(event.getServer());
    }

    @SubscribeEvent public static void join(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide() && event.getEntity() instanceof ProfessionNpcEntity npc) npc.applyConfig();
    }

    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        NpcService.forget(event.getEntity().getUUID());
    }

    @SubscribeEvent public static void stopped(ServerStoppedEvent event) {
        NpcService.clear();
        NpcCatalog.clear();
    }

    /** Reaplica nome e skin nos NPCs carregados. Os outros pegam a config nova ao entrar no mundo. */
    public static int refreshAll(MinecraftServer server) {
        int count = 0;
        for (ServerLevel level : server.getAllLevels()) {
            for (var npc : level.getEntities(NpcEntities.NPC.get(), npc -> true)) {
                npc.applyConfig();
                count++;
            }
        }
        return count;
    }
}
