package com.aurorion.essentials.tab;

import com.aurorion.essentials.AurorionEssentials;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Quem esta no espectador some da tab — para todo mundo, inclusive para ele mesmo.
 *
 * <p>O vanilla ja tem o mecanismo: a entrada da tab carrega {@code listed}, e o cliente so desenha
 * quem esta listado. O {@code PlayerInfoEntryMixin} passa toda entrada por {@link #listed}, entao vale
 * tambem para a que um mod de tab monte por conta propria (Just Essentials).
 *
 * <p>Na thread do servidor a resposta vem do jogador de verdade — e assim que quem entra ja no
 * espectador nao pisca na tab no login, porque o {@code PlayerList} registra o jogador antes de montar
 * o pacote. Fora dela (um mod de tab montando em outra thread) vale o conjunto abaixo, atualizado na
 * troca de modo, no login e na saida. Nada roda a cada tick.
 */
@EventBusSubscriber(modid = AurorionEssentials.MOD_ID)
public final class HiddenSpectators {
    private static final Set<UUID> HIDDEN = ConcurrentHashMap.newKeySet();
    @Nullable private static volatile MinecraftServer server;

    private HiddenSpectators() {
    }

    /** O {@code listed} que vai na entrada da tab de {@code player}, partindo do que ja ia. */
    public static boolean listed(UUID player, boolean listed) {
        MinecraftServer running = server;
        if (!listed || running == null || !TabListConfig.hideSpectators()) return listed;
        if (running.isSameThread()) {
            ServerPlayer online = running.getPlayerList().getPlayer(player);
            if (online != null) return !online.isSpectator();
        }
        return !HIDDEN.contains(player);
    }

    /**
     * O evento chega <b>antes</b> da troca, e o vanilla so reenvia o modo de jogo, nunca o
     * {@code listed}. Entao a entrada nova sai no tick seguinte, ja com o modo trocado. {@code LOWEST}
     * para nao reagir a uma troca que outro mod cancelou.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void gameModeChanged(PlayerEvent.PlayerChangeGameModeEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        boolean was = event.getCurrentGameMode() == GameType.SPECTATOR;
        boolean will = event.getNewGameMode() == GameType.SPECTATOR;
        if (was == will) return;
        if (will) HIDDEN.add(player.getUUID());
        else HIDDEN.remove(player.getUUID());
        MinecraftServer owner = player.server;
        owner.tell(new TickTask(owner.getTickCount(), () -> {
            if (owner.getPlayerList().getPlayer(player.getUUID()) != player) return;
            owner.getPlayerList().broadcastAll(new ClientboundPlayerInfoUpdatePacket(
                    EnumSet.of(ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LISTED), List.of(player)));
        }));
    }

    @SubscribeEvent
    public static void loggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && player.isSpectator()) HIDDEN.add(player.getUUID());
    }

    @SubscribeEvent
    public static void loggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        HIDDEN.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void starting(ServerStartingEvent event) {
        server = event.getServer();
    }

    @SubscribeEvent
    public static void stopped(ServerStoppedEvent event) {
        server = null;
        HIDDEN.clear();
    }
}
