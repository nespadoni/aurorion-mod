package com.aurorion.diario.server;

import com.aurorion.diario.AurorionDiario;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** {@code /diario}: abre o diário do personagem atual. */
@EventBusSubscriber(modid = AurorionDiario.MOD_ID)
public final class DiaryCommand {
    private DiaryCommand() {
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("diario").executes(context -> {
            ServerPlayer player = context.getSource().getPlayer();
            if (player == null) return 0;
            DiaryServer.open(player);
            return 1;
        }));
    }
}
