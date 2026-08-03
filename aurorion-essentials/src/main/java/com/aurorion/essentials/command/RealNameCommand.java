package com.aurorion.essentials.command;

import com.aurorion.essentials.AurorionEssentials;
import com.aurorion.essentials.fakename.FakeName;
import com.aurorion.essentials.fakename.FakeNameRegistry;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.Map;
import java.util.UUID;

/**
 * {@code /realname <nomeFalso>} — ferramenta de moderacao para descobrir quem esta por tras de
 * um nome falso visto no chat/tab list. Nivel 2+ porque isso desfaz o proposito cosmetico do
 * fakename; so staff deveria poder reverter a mascara de outro jogador.
 */
@EventBusSubscriber(modid = AurorionEssentials.MOD_ID)
public final class RealNameCommand {
    private RealNameCommand() {
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("realname")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("fakename", StringArgumentType.string())
                        .executes(RealNameCommand::lookup)));
    }

    private static int lookup(CommandContext<CommandSourceStack> context) {
        String query = StringArgumentType.getString(context, "fakename");

        boolean found = false;
        for (Map.Entry<UUID, FakeName> entry : FakeNameRegistry.all().entrySet()) {
            if (!entry.getValue().plain().equalsIgnoreCase(query)) continue;

            ServerPlayer player = context.getSource().getServer().getPlayerList().getPlayer(entry.getKey());
            if (player == null) continue;

            found = true;
            String realName = player.getGameProfile().getName();
            context.getSource().sendSuccess(() -> Component.translatable(
                    "commands.aurorion_essentials.realname.found", query, realName), false);
        }

        if (!found) {
            context.getSource().sendFailure(
                    Component.translatable("commands.aurorion_essentials.realname.notFound", query));
        }

        return found ? 1 : 0;
    }
}
