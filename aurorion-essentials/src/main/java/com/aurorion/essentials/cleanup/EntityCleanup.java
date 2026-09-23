package com.aurorion.essentials.cleanup;

import com.aurorion.core.level.ProtectedDrops;
import com.aurorion.essentials.AurorionEssentials;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import java.util.ArrayList;
import java.util.List;

/**
 * O trabalho de verdade, chamado no maximo 1x por ciclo (ver {@link CleanupScheduler}) — nunca em
 * todo tick. Remover N entidades custa O(N) so nesse tick; nos milhares de ticks entre um ciclo e
 * outro, o custo e zero. So mexe em itens dropados e orbs de XP — nunca mobs, veiculos, molduras
 * ou blocos.
 */
public final class EntityCleanup {
    private EntityCleanup() {
    }

    /** Aviso sutil: actionbar, uma unica vez por ciclo, sem poluir o chat. */
    public static void warn(MinecraftServer server) {
        int seconds = CleanupConfig.WARNING_SECONDS_BEFORE.get();
        Component message = Component.translatable("aurorion_essentials.cleanup.warning", seconds)
                .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC);

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.displayClientMessage(message, true);
        }
    }

    public static void run(MinecraftServer server) {
        boolean cleanItems = CleanupConfig.CLEAN_ITEMS.get();
        boolean cleanOrbs = CleanupConfig.CLEAN_EXPERIENCE_ORBS.get();
        if (!cleanItems && !cleanOrbs) return;

        int removed = 0;
        for (ServerLevel level : server.getAllLevels()) {
            removed += clean(level, cleanItems, cleanOrbs);
        }

        if (removed > 0) {
            AurorionEssentials.LOGGER.info(
                    "Cleanup periodico removeu {} entidade(s) (itens dropados + orbs de XP)", removed);
        }
    }

    private static int clean(ServerLevel level, boolean cleanItems, boolean cleanOrbs) {
        int removed = 0;

        // Nao chame discard() dentro desta iteracao. O armazenamento de entidades usado por
        // NeoForge/C2ME pode remover a entrada imediatamente, invalidando o iterador (e causando
        // ArrayIndexOutOfBoundsException em Int2ObjectLinkedOpenHashMap). A limpeza roda raramente,
        // entao esta lista temporaria e o custo seguro correto.
        List<Entity> doomed = new ArrayList<>();
        for (Entity entity : level.getEntities().getAll()) {
            // Drop protegido e espolio de morte que alguem ainda pode chamar de volta (Relicario do
            // aurorion_limbo). A protecao tem prazo, entao o proximo ciclo depois dele o recolhe.
            boolean shouldRemove = (cleanItems && entity instanceof ItemEntity item
                    && !ProtectedDrops.isProtected(item))
                    || (cleanOrbs && entity instanceof ExperienceOrb);
            if (shouldRemove) doomed.add(entity);
        }
        for (Entity entity : doomed) {
            if (!entity.isRemoved()) { entity.discard(); removed++; }
        }
        return removed;
    }
}
