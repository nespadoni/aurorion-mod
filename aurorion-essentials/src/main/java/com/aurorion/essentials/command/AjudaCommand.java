package com.aurorion.essentials.command;

import com.aurorion.essentials.AurorionEssentials;
import com.aurorion.essentials.help.HelpRequestManager;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * {@code /ajuda <descrição>} — pedido de socorro do jogador. Avisa todo operador online com nome
 * real, fakename, descricao e localizacao de quem chamou; se ninguem com OP estiver online, avisa
 * o proprio jogador para usar o Discord em vez de deixar o pedido cair no vazio.
 */
@EventBusSubscriber(modid = AurorionEssentials.MOD_ID)
public final class AjudaCommand {
    private AjudaCommand() {
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("ajuda")
                .then(Commands.argument("descricao", StringArgumentType.greedyString())
                        .executes(AjudaCommand::request)));
    }

    private static int request(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        String description = StringArgumentType.getString(context, "descricao");

        int notified = HelpRequestManager.notifyOps(player, description);
        if (notified > 0) {
            context.getSource().sendSuccess(() ->
                    Component.translatable("commands.aurorion_essentials.ajuda.sent", notified), false);
        } else {
            context.getSource().sendFailure(
                    Component.translatable("commands.aurorion_essentials.ajuda.noOp"));
        }
        return notified;
    }
}
