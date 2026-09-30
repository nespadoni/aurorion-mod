package com.aurorion.essentials.server;

import com.aurorion.essentials.AurorionEssentials;
import com.aurorion.essentials.fakename.FakeName;
import com.aurorion.essentials.network.PhoneNameDirectoryPayload;
import com.mojang.authlib.GameProfile;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.GameProfileCache;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Leva ao cliente o diretorio de nomes do telefone ({@link PhoneNameDirectoryPayload}): inteiro no
 * login, e so a mudanca quando um nome muda.
 *
 * <p>O nick de quem esta offline vem do cache de perfis do servidor ({@code usercache.json}), o mesmo
 * que o vanilla usa para resolver dono de cranio e {@code /whitelist}. Conta que nunca passou por ele
 * fica fora — e a agenda do telefone tambem nao a conhece.
 *
 * <p>O diretorio montado fica guardado ate o servidor parar: 80 logins seguidos nao remontam nada, e
 * cada troca de nome so atualiza a propria entrada.
 */
@EventBusSubscriber(modid = AurorionEssentials.MOD_ID)
public final class PhoneNameDirectorySync {
    @Nullable
    private static Map<String, String> cached;

    private PhoneNameDirectorySync() {
    }

    @SubscribeEvent
    public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.getServer() == null) return;
        if (!hasChannel(player)) return;
        PacketDistributor.sendToPlayer(player, new PhoneNameDirectoryPayload(true, Map.copyOf(snapshot(player.getServer()))));
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        cached = null;
    }

    /** Um nome mudou (ou saiu, com {@code name == null}). */
    public static void changed(MinecraftServer server, UUID account, @Nullable String name) {
        String nick = nickOf(server, account);
        if (nick == null) return;
        if (cached != null) {
            if (name == null) cached.remove(nick);
            else if (cached.size() < PhoneNameDirectoryPayload.MAX_ENTRIES || cached.containsKey(nick)) cached.put(nick, name);
        }
        PhoneNameDirectoryPayload payload = new PhoneNameDirectoryPayload(false, Map.of(nick, name == null ? "" : name));
        for (ServerPlayer online : server.getPlayerList().getPlayers()) {
            if (hasChannel(online)) PacketDistributor.sendToPlayer(online, payload);
        }
    }

    private static Map<String, String> snapshot(MinecraftServer server) {
        if (cached != null) return cached;
        Map<String, String> built = new HashMap<>();
        for (Map.Entry<UUID, String> entry : FakeNameData.get(server).allRaw().entrySet()) {
            if (built.size() >= PhoneNameDirectoryPayload.MAX_ENTRIES) break;
            String nick = nickOf(server, entry.getKey());
            if (nick == null) continue;
            String name = FakeName.parse(entry.getValue()).plain();
            if (!name.isBlank()) built.put(nick, name);
        }
        cached = built;
        return built;
    }

    @Nullable
    private static String nickOf(MinecraftServer server, UUID account) {
        ServerPlayer online = server.getPlayerList().getPlayer(account);
        if (online != null) return online.getGameProfile().getName();
        GameProfileCache profiles = server.getProfileCache();
        if (profiles == null) return null;
        return profiles.get(account).map(GameProfile::getName).orElse(null);
    }

    private static boolean hasChannel(ServerPlayer player) {
        return player.connection != null && player.connection.hasChannel(PhoneNameDirectoryPayload.TYPE);
    }
}
