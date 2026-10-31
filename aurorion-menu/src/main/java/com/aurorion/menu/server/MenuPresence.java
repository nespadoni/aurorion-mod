package com.aurorion.menu.server;

import com.aurorion.menu.AurorionMenu;
import com.aurorion.menu.config.MenuConfig;
import com.aurorion.menu.network.MenuPayloads;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Quem está com o menu aberto ou meditando, para quem está perto ver: o braço erguido e o painel
 * holográfico, ou a pose da meditação. Só muda quando a pessoa abre, fecha, começa ou para — nunca por
 * tick. Vai para quem rastreia a pessoa; quem chega perto depois recebe no {@code StartTracking}.
 *
 * <p>Abrir o menu vale no máximo 4 vezes por segundo; o resto é descartado. Fechar nunca é descartado:
 * "Meditar" fecha o menu logo depois de pedir a meditação, e um fechar perdido deixaria o painel no ar.
 */
@EventBusSubscriber(modid = AurorionMenu.MOD_ID)
public final class MenuPresence {
    private static final long MIN_GAP_MS = 250;
    private static final Map<UUID, Integer> FLAGS = new HashMap<>();
    private static final Map<UUID, Long> LAST = new HashMap<>();

    private MenuPresence() {
    }

    public static int flags(UUID player) {
        return FLAGS.getOrDefault(player, 0);
    }

    public static void request(Player sender, MenuPayloads.Request request) {
        if (!(sender instanceof ServerPlayer player)) return;
        // Abrir o menu e qualquer pedido de meditação contam no limite; fechar o menu nunca é descartado.
        if (request.kind() != 0 || request.value() != 0) {
            long now = System.currentTimeMillis();
            Long last = LAST.get(player.getUUID());
            if (last != null && now - last < MIN_GAP_MS) return;
            LAST.put(player.getUUID(), now);
        }
        switch (request.kind()) {
            case 0 -> set(player, MenuPayloads.MENU, request.value() != 0);
            case 1 -> {
                if (request.value() != 0) Meditation.start(player);
                else Meditation.stop(player, null);
            }
            default -> {
            }
        }
    }

    /** O que os outros podem ver: sem {@code presenca.habilitada}, o menu aberto não sai do servidor. */
    private static int visible(int flags) {
        return MenuConfig.PRESENCE.get() ? flags : flags & ~MenuPayloads.MENU;
    }

    /** Liga ou desliga um bit e avisa quem vê, se mudou. */
    static void set(ServerPlayer player, int bit, boolean on) {
        int before = flags(player.getUUID());
        int after = on ? before | bit : before & ~bit;
        if (after == before) return;
        if (after == 0) FLAGS.remove(player.getUUID());
        else FLAGS.put(player.getUUID(), after);
        if (!MenuConfig.PRESENCE.get() && bit == MenuPayloads.MENU) return;
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, new MenuPayloads.Presence(player.getUUID(), visible(after)));
    }

    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (!(event.getTarget() instanceof ServerPlayer target) || !(event.getEntity() instanceof ServerPlayer viewer)) return;
        int flags = visible(flags(target.getUUID()));
        if (flags != 0) PacketDistributor.sendToPlayer(viewer, new MenuPayloads.Presence(target.getUUID(), flags));
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        Meditation.stop(player, null);
        set(player, MenuPayloads.MENU, false);
        FLAGS.remove(player.getUUID());
        LAST.remove(player.getUUID());
    }

    @SubscribeEvent
    public static void onStopped(ServerStoppedEvent event) {
        FLAGS.clear();
        LAST.clear();
        Meditation.clear();
    }
}
