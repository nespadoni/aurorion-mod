package com.aurorion.essentials.tab;

import com.aurorion.core.house.HouseChangedEvent;
import com.aurorion.core.house.HouseGate;
import com.aurorion.essentials.AurorionEssentials;
import com.aurorion.essentials.fakename.FakeName;
import com.aurorion.essentials.fakename.FakeNameRegistry;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Nome do personagem na cor da casa, na tab.
 *
 * <p>A cor de quem esta online fica num mapa concorrente, porque a linha da tab e montada onde o pacote
 * nasce — e um mod de tab pode monta-lo fora da thread do servidor. Nada roda por tick: a cor e lida no
 * login e de novo so quando o Ethereal avisa ({@link HouseChangedEvent}) — {@code /casa definir}, a
 * cerimonia, ou um {@code /reload} que mude a cor de uma casa.
 *
 * <p>Os dois pontos que pintam sao mixins: {@code TabListNameMixin} (o nome que o NeoForge/vanilla
 * calcula) e {@code PlayerInfoEntryMixin} (qualquer entrada da tab que alguem monte, inclusive o Just
 * Essentials). Os dois chamam {@link #decorate}, que e idempotente.
 */
@EventBusSubscriber(modid = AurorionEssentials.MOD_ID)
public final class TabNames {
    private static final Map<UUID, Integer> COLORS = new ConcurrentHashMap<>();
    /** Falso no cliente de um servidor remoto: la nao ha config de servidor nem casa para consultar. */
    private static volatile boolean running;

    private TabNames() {
    }

    /** O que mostrar na tab para {@code player}, partindo do que ja ia ({@code incoming}). */
    @Nullable
    public static Component decorate(UUID player, @Nullable String realName, @Nullable Component incoming) {
        if (!running) return incoming;
        FakeName fake = FakeNameRegistry.get(player);
        Integer color = COLORS.get(player);
        if (fake == null && color == null) return incoming;
        return TabNameFormat.decorate(incoming, fake == null ? null : fake.component(),
                fake == null ? null : fake.plain(), realName, color);
    }

    /**
     * Le a cor da casa de quem acabou de entrar. Chamado no login, antes do refresh da tab.
     *
     * @return true se a pessoa tem cor de casa (a tab dela precisa ser reenviada).
     */
    public static boolean track(ServerPlayer player) {
        return update(player.server, player.getUUID()) && COLORS.containsKey(player.getUUID());
    }

    public static void forget(UUID player) {
        COLORS.remove(player);
    }

    /** @return true se a cor mudou. */
    private static boolean update(MinecraftServer server, UUID player) {
        Integer now = TabListConfig.houseColor() ? HouseGate.colorOf(server, player) : null;
        Integer before = now == null ? COLORS.remove(player) : COLORS.put(player, now);
        return !Objects.equals(before, now);
    }

    /** Uma pessoa trocou de casa, ou ({@code player == null}) o catalogo foi recarregado. */
    @SubscribeEvent
    public static void houseChanged(HouseChangedEvent event) {
        if (!running) return;
        MinecraftServer server = event.server();
        List<ServerPlayer> changed = new ArrayList<>();
        if (event.player() != null) {
            ServerPlayer player = server.getPlayerList().getPlayer(event.player());
            if (player != null && update(server, player.getUUID())) changed.add(player);
        } else {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (update(server, player.getUUID())) changed.add(player);
            }
        }
        if (!changed.isEmpty()) {
            server.getPlayerList().broadcastAll(new ClientboundPlayerInfoUpdatePacket(
                    EnumSet.of(ClientboundPlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME), changed));
        }
    }

    @SubscribeEvent
    public static void starting(ServerStartingEvent event) {
        running = true;
    }

    @SubscribeEvent
    public static void stopped(ServerStoppedEvent event) {
        running = false;
        COLORS.clear();
    }
}
