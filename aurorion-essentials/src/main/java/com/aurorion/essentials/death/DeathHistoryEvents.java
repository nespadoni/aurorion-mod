package com.aurorion.essentials.death;

import com.aurorion.core.death.DeathId;
import com.aurorion.essentials.AurorionEssentials;
import com.aurorion.essentials.privacy.PrivacyConfig;
import com.aurorion.essentials.privacy.Visibility;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@EventBusSubscriber(modid = AurorionEssentials.MOD_ID)
public final class DeathHistoryEvents {
    private record Pending(LivingDeathEvent event, CompoundTag snapshot, Component message) { }
    private static final List<Pending> PENDING = new ArrayList<>();
    private static long pendingBytes;
    private static MinecraftServer owner;
    private static DeathHistoryStore store;
    private DeathHistoryEvents() { }

    public static DeathHistoryStore store(MinecraftServer server) {
        if (owner != server) {
            if (store != null) store.close();
            store = new DeathHistoryStore(server); owner = server;
        }
        return store;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void capture(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!DeathHistoryConfig.ENABLED.get() && PrivacyConfig.DEATH_MESSAGES.get() != Visibility.ADMINS) return;
        if (PENDING.size() >= 128) {
            AurorionEssentials.LOGGER.error("Death capture queue full for {}", player.getUUID());
            return;
        }
        try {
            Component cause = player.getCombatTracker().getDeathMessage();
            // O mesmo id que o espolio do Limbo grava nos drops desta morte: e o que permite mostrar
            // aqui que o Relicario ja chamou os itens de volta.
            UUID id = DeathId.of(player);
            CompoundTag snapshot;
            try {
                snapshot = DeathHistoryConfig.ENABLED.get() ? DeathSnapshot.capture(player, cause.getString(), id)
                        : DeathSnapshot.metadata(player, cause.getString(), id);
            } catch (RuntimeException | LinkageError e) {
                AurorionEssentials.LOGGER.error("Full death capture failed for {}; retaining location/cause", player.getUUID(), e);
                snapshot = DeathSnapshot.metadata(player, cause.getString(), id);
                snapshot.putString("CaptureError", "Falha na serializacao do jogador; consulte o log.");
            }
            long bytes = snapshot.sizeInBytes();
            if (bytes > 16L * 1024 * 1024 || pendingBytes + bytes > 64L * 1024 * 1024) {
                AurorionEssentials.LOGGER.error("Death snapshot exceeds memory budget for {}; retaining metadata", player.getUUID());
                snapshot = DeathSnapshot.metadata(player, cause.getString(), id);
                snapshot.putString("CaptureError", "Snapshot excedeu o limite de memoria; consulte o log.");
                bytes = snapshot.sizeInBytes();
            }
            pendingBytes += bytes;
            PENDING.add(new Pending(event, snapshot, cause.copy()));
        } catch (RuntimeException | LinkageError e) {
            // Backups must never interrupt the actual death pipeline.
            AurorionEssentials.LOGGER.error("Death capture failed for {}", player.getUUID(), e);
        }
    }

    @SubscribeEvent
    public static void commit(ServerTickEvent.Post event) {
        if (PENDING.isEmpty()) return;
        for (Pending pending : PENDING) {
            // Evaluate cancellation AFTER every mod has handled the event, including PlayerRevive.
            if (pending.event.isCanceled()) continue;
            MinecraftServer server = event.getServer();
            boolean save = DeathHistoryConfig.ENABLED.get();
            if (!save) { announce(server, pending, false); continue; }
            DeathHistoryStore repository = store(server);
            int retention = DeathHistoryConfig.RETENTION.get();
            try {
                repository.submitSnapshot(pending.snapshot, () -> {
                    boolean saved = false;
                    try { repository.saveDeath(pending.snapshot, retention); saved = true; }
                    catch (Exception e) { AurorionEssentials.LOGGER.error("Death history write failed id={} owner={}",
                            pending.snapshot.getUUID("Id"), pending.snapshot.getUUID("Owner"), e); }
                    boolean completed = saved;
                    server.execute(() -> announce(server, pending, completed));
                });
            } catch (RuntimeException e) {
                AurorionEssentials.LOGGER.error("Death history queue rejected snapshot", e);
                announce(server, pending, false);
            }
        }
        PENDING.clear();
        pendingBytes = 0;
    }

    private static void announce(MinecraftServer server, Pending pending, boolean saved) {
        if (PrivacyConfig.DEATH_MESSAGES.get() != Visibility.ADMINS) return;
        CompoundTag tag = pending.snapshot;
        var message = Component.literal("[admin] ").withStyle(ChatFormatting.DARK_RED)
                .append(pending.message.copy().withStyle(ChatFormatting.GRAY))
                .append(Component.literal(" (" + tag.getString("Name") + ")").withStyle(ChatFormatting.DARK_GRAY));
        String id = tag.getUUID("Id").toString();
        if (saved) {
            message.append(button(" [TP]", "/deathhistory tp " + id, "Ir ao local da morte (OP 2+)"));
            message.append(button(" [Inventario]", "/deathhistory view " + id, "Consultar snapshot sem retirar itens"));
        } else {
            // A direct command still enforces vanilla /execute and /tp permissions when clicked.
            String tp = "/execute in " + tag.getString("Dimension") + " run tp @s "
                    + tag.getDouble("X") + " " + tag.getDouble("Y") + " " + tag.getDouble("Z");
            message.append(button(" [TP]", tp, "Ir ao local exato da morte"));
            message.append(Component.literal(" [historico indisponivel]").withStyle(ChatFormatting.RED));
        }
        for (ServerPlayer admin : server.getPlayerList().getPlayers()) if (admin.hasPermissions(2)) admin.sendSystemMessage(message);
    }

    public static Component button(String text, String command, String tooltip) {
        return Component.literal(text).withStyle(s -> s.withColor(ChatFormatting.AQUA)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(tooltip))));
    }

    @SubscribeEvent public static void stop(ServerStoppedEvent event) {
        PENDING.clear();
        pendingBytes = 0;
        if (store != null) store.close();
        store = null; owner = null;
    }
}
