package com.aurorion.menu.client;

import com.aurorion.menu.AurorionMenu;
import com.aurorion.menu.network.MenuPayloads;
import net.minecraft.Util;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * O que o servidor disse de cada jogador visível: menu aberto, meditando, e desde quando (para a animação
 * do braço e do painel). Só muda quando chega pacote; o render só lê.
 */
@EventBusSubscriber(modid = AurorionMenu.MOD_ID, value = Dist.CLIENT)
public final class ClientPresence {
    record State(int flags, long since) {
    }

    private static final Map<UUID, State> STATES = new HashMap<>();
    private static final State NONE = new State(0, 0);

    private ClientPresence() {
    }

    public static void accept(MenuPayloads.Presence payload) {
        if (payload.flags() == 0) {
            STATES.remove(payload.player());
            return;
        }
        State before = STATES.get(payload.player());
        boolean menuNow = (payload.flags() & MenuPayloads.MENU) != 0;
        boolean menuBefore = before != null && (before.flags() & MenuPayloads.MENU) != 0;
        long since = before != null && menuNow == menuBefore ? before.since() : Util.getMillis();
        STATES.put(payload.player(), new State(payload.flags(), since));
    }

    /** Saiu de vista: o estado pode mudar sem a gente saber; o servidor manda de novo ao voltar a rastrear. */
    @SubscribeEvent
    public static void onLeave(net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent event) {
        if (event.getLevel().isClientSide() && event.getEntity() instanceof net.minecraft.world.entity.player.Player player) {
            STATES.remove(player.getUUID());
        }
    }

    static State of(UUID player) {
        return STATES.getOrDefault(player, NONE);
    }

    static boolean menu(UUID player) {
        return (of(player).flags() & MenuPayloads.MENU) != 0;
    }

    static boolean meditating(UUID player) {
        return (of(player).flags() & MenuPayloads.MEDITATING) != 0;
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        STATES.clear();
    }
}
