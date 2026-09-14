package com.aurorion.limbo.finale;

import com.aurorion.core.character.CharacterData;
import com.aurorion.limbo.network.FinalePayload;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Server owns death, isolation, viewing progress and disconnect. No client acknowledgement. */
public final class FinaleManager {
    private static final Map<UUID, ServerPlayer> VIEWERS = new HashMap<>();
    private FinaleManager() { }

    public static boolean isDead(ServerPlayer player) {
        return CharacterData.get(player.server).isDead(player.getUUID());
    }

    /** Idempotent, including old expired saves and disconnects during the transition. */
    public static void expire(MinecraftServer server, UUID account) {
        var characters = CharacterData.get(server);
        var data = FinaleData.get(server);
        if (!characters.isDead(account)) {
            data.put(account, new FinaleRecord(characters.current(account).id(), FinaleScript.configured()));
            characters.markDead(account);
        }
        ServerPlayer player = server.getPlayerList().getPlayer(account);
        if (player != null) resume(player);
    }

    public static boolean resume(ServerPlayer player) {
        if (!isDead(player)) return false;
        UUID account = player.getUUID();
        var record = FinaleData.get(player.server).record(account);
        if (record == null || record.finished()
                || !record.characterId().equals(CharacterData.get(player.server).current(account).id())) {
            player.connection.disconnect(disconnectReason());
            return true;
        }
        isolate(player);
        if (VIEWERS.put(account, player) != player) {
            record.resumeViewing(System.nanoTime() / 1_000_000);
            send(player, new FinalePayload(false, record.elapsed(), record.script()));
            if (!player.connection.hasChannel(FinalePayload.TYPE.id())) {
                player.sendSystemMessage(Component.literal(record.script().phrase() + "\n\n"
                        + String.join("\n\n", record.script().paragraphs())));
            }
        }
        return true;
    }

    private static void isolate(ServerPlayer player) {
        player.stopRiding();
        if (player.containerMenu != player.inventoryMenu) player.closeContainer();
        if (!player.isSpectator()) player.setGameMode(GameType.SPECTATOR);
        player.setCamera(player);
        player.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
    }

    public static void sweep(MinecraftServer server) {
        long now = System.nanoTime() / 1_000_000;
        if (VIEWERS.isEmpty()) return;
        var data = FinaleData.get(server);
        var iterator = VIEWERS.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            ServerPlayer player = entry.getValue();
            if (server.getPlayerList().getPlayer(entry.getKey()) != player) { iterator.remove(); continue; }
            FinaleRecord record = data.record(entry.getKey());
            if (record == null || !record.characterId().equals(CharacterData.get(server).current(entry.getKey()).id())) {
                iterator.remove(); player.connection.disconnect(disconnectReason()); continue;
            }
            isolate(player);
            long before = record.elapsed();
            record.advanceViewing(now);
            data.setDirty();
            if (before / 60_000 != record.elapsed() / 60_000
                    || before < record.script().deathTitleMillis() && record.elapsed() >= record.script().deathTitleMillis()) {
                send(player, new FinalePayload(false, record.elapsed(), record.script()));
                if (!player.connection.hasChannel(FinalePayload.TYPE.id()) && record.elapsed() >= record.script().deathTitleMillis()) {
                    player.sendSystemMessage(Component.literal("Você está morto."));
                }
            }
            if (record.finished()) {
                iterator.remove();
                data.complete(entry.getKey());
                player.connection.disconnect(disconnectReason());
            }
        }
    }

    public static void logout(ServerPlayer player) { VIEWERS.remove(player.getUUID()); }

    public static void preview(ServerPlayer player) {
        if (!isDead(player)) send(player, new FinalePayload(true, 0, FinaleScript.configured()));
    }

    public static Component disconnectReason() {
        return Component.literal("Você está morto.\nA história deste personagem chegou ao fim.\n"
                + "Sua conta não foi banida. Para voltar a jogar, será necessário criar um novo personagem.");
    }

    public static void send(ServerPlayer player, FinalePayload payload) {
        if (player.connection.hasChannel(FinalePayload.TYPE.id())) PacketDistributor.sendToPlayer(player, payload);
    }

    public static void reset() { VIEWERS.clear(); }
}
