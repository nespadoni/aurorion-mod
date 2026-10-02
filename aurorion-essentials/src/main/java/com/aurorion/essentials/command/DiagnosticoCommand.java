package com.aurorion.essentials.command;

import com.aurorion.core.diagnostics.ServerDiagnostics;
import com.aurorion.core.rate.ActionCooldown;
import com.aurorion.essentials.AurorionEssentials;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/** Aggregates optional providers without making Essentials depend on Diary or Integration. */
@EventBusSubscriber(modid = AurorionEssentials.MOD_ID)
public final class DiagnosticoCommand {
    private static final ActionCooldown QUERIES = new ActionCooldown(1_000);
    private DiagnosticoCommand() { }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("aurorion")
                .then(Commands.literal("diagnostico").requires(source -> source.hasPermission(2))
                        .executes(context -> {
                            var source = context.getSource();
                            if (source.getEntity() instanceof ServerPlayer player
                                    && !QUERIES.acquire(player.getUUID(), Util.getMillis())) return 0;
                            var report = ServerDiagnostics.snapshot();
                            source.sendSystemMessage(Component.literal("Diagnóstico Aurorion — filas do servidor")
                                    .withStyle(ChatFormatting.GOLD));
                            for (String section : java.util.List.of("Integração", "Diário")) {
                                source.sendSystemMessage(Component.literal(section).withStyle(ChatFormatting.YELLOW));
                                var lines = report.getOrDefault(section,
                                        java.util.List.of("Módulo não instalado ou ainda não iniciado."));
                                for (String line : lines) source.sendSystemMessage(Component.literal("  " + line));
                            }
                            return 1;
                        })));
    }

    @SubscribeEvent public static void stop(ServerStoppedEvent event) { QUERIES.clear(); }
}
