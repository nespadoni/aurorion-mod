package com.aurorion.essentials.fakename;

import com.aurorion.core.rate.ActionCooldown;
import com.aurorion.essentials.AurorionEssentials;
import net.minecraft.Util;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

import java.nio.file.Files;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/** Bounded remote lookup; no command/world mutations occur on worker threads. */
@EventBusSubscriber(modid = AurorionEssentials.MOD_ID)
public final class NameProfileLookup {
    private static final ActionCooldown REQUESTS = new ActionCooldown(1_000);
    private static final UUID CONSOLE = new UUID(0, 0);
    private static MinecraftServer ownerServer;
    private static ThreadPoolExecutor executor;
    private NameProfileLookup() { }

    static void resolve(CommandSourceStack source, String query, Consumer<UUID> result) {
        if (!authorized(source)) return;
        MinecraftServer server = source.getServer();
        if (ownerServer != server) {
            reset();
            ownerServer = server;
            executor = new ThreadPoolExecutor(2, 2, 30, TimeUnit.SECONDS, new ArrayBlockingQueue<>(16), task -> {
                Thread thread = new Thread(task, "aurorion-name-lookup");
                thread.setDaemon(true);
                return thread;
            });
            executor.allowCoreThreadTimeOut(true);
        }
        UUID known = CharacterTarget.resolveKnown(server, query);
        if (known != null) { result.accept(known); return; }
        var cache = server.getProfileCache();
        if (cache == null) { result.accept(null); return; }
        UUID requester = source.getEntity() instanceof ServerPlayer player ? player.getUUID() : CONSOLE;
        if (!REQUESTS.acquire(requester, Util.getMillis())) {
            source.sendFailure(Component.literal("Aguarde um segundo entre consultas de nicks offline."));
            return;
        }
        boolean authenticated = server.usesAuthentication();
        var playerData = server.getWorldPath(LevelResource.PLAYER_DATA_DIR);
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        try {
            executor.execute(() -> {
                try {
                    if (Thread.currentThread().isInterrupted() || System.nanoTime() - deadline >= 0) {
                        deliver(source, deadline, null, result, true);
                        return;
                    }
                    UUID found = cache.get(query).filter(profile -> {
                        if (authenticated) return true;
                        UUID offline = UUIDUtil.createOfflinePlayerUUID(profile.getName());
                        return !offline.equals(profile.getId()) || Files.exists(playerData.resolve(offline + ".dat"));
                    }).map(profile -> profile.getId()).orElse(null);
                    deliver(source, deadline, found, result, false);
                } catch (RuntimeException failure) {
                    deliver(source, deadline, null, result, true);
                }
            });
            source.sendSystemMessage(Component.literal("Consultando o nick offline. Você receberá o resultado ao concluir."));
        } catch (RejectedExecutionException unavailable) {
            source.sendFailure(Component.literal("A fila de consulta de perfis está ocupada. Tente novamente em instantes."));
        }
    }

    private static void deliver(CommandSourceStack source, long deadline, UUID found,
                                Consumer<UUID> result, boolean failed) {
        try {
            source.getServer().execute(() -> {
                if (ownerServer != source.getServer() || !authorized(source)) return;
                if (failed || System.nanoTime() - deadline >= 0) {
                    source.sendFailure(Component.literal("A consulta de perfis falhou ou perdeu a validade. Tente novamente."));
                    return;
                }
                result.accept(found);
            });
        } catch (RuntimeException stopping) {
            // The server is stopping; no result may mutate the next world.
        }
    }

    private static boolean authorized(CommandSourceStack source) {
        if (!source.hasPermission(2)) return false;
        if (source.getEntity() instanceof ServerPlayer player) {
            return player.hasPermissions(2) && !player.hasDisconnected()
                    && source.getServer().getPlayerList().getPlayer(player.getUUID()) == player;
        }
        return true;
    }

    private static void reset() {
        if (executor != null) executor.shutdownNow();
        executor = null;
        ownerServer = null;
        REQUESTS.clear();
    }

    @SubscribeEvent public static void stop(ServerStoppedEvent event) { reset(); }
}
