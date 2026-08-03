package com.aurorion.utils.command;

import com.aurorion.utils.AurorionUtils;
import com.aurorion.utils.abduction.AbductionManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * {@code /abduzir <jogador>} — puxa jogador ate a posicao do executor.
 * {@code /abduzir <jogador> <destino>} — puxa jogador ate a posicao de outro jogador online.
 * {@code /abduzir voltar <jogador>} — leva de volta pra ultima origem salva.
 *
 * <p>Nivel de operador 2 em toda a arvore — e uma ferramenta de staff, nao algo auto-aplicavel.</p>
 */
@EventBusSubscriber(modid = AurorionUtils.MOD_ID)
public final class AbductionCommand {
    private AbductionCommand() {
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(Commands.literal("abduzir")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("voltar")
                        .then(Commands.argument("jogador", EntityArgument.player())
                                .executes(AbductionCommand::returnPlayer)))
                .then(Commands.argument("jogador", EntityArgument.player())
                        .executes(AbductionCommand::abductToExecutor)
                        .then(Commands.argument("destino", EntityArgument.player())
                                .executes(AbductionCommand::abductToDestination))));
    }

    private static int abductToExecutor(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(context, "jogador");
        ServerPlayer executor = context.getSource().getPlayerOrException();
        return runAbduction(context, target, executor);
    }

    private static int abductToDestination(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(context, "jogador");
        ServerPlayer destination = EntityArgument.getPlayer(context, "destino");
        return runAbduction(context, target, destination);
    }

    private static int runAbduction(CommandContext<CommandSourceStack> context, ServerPlayer target, ServerPlayer destinationSource) {
        AbductionManager.Result result = AbductionManager.startAbduction(target, destinationSource);

        if (result == AbductionManager.Result.ALREADY_ACTIVE) {
            context.getSource().sendFailure(
                    Component.translatable("commands.aurorion_utils.abduzir.alreadyActive", target.getName()));
            return 0;
        }

        context.getSource().sendSuccess(() -> Component.translatable(
                "commands.aurorion_utils.abduzir.success", target.getName(), destinationSource.getName()), true);
        return 1;
    }

    private static int returnPlayer(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(context, "jogador");
        AbductionManager.Result result = AbductionManager.startReturn(target);

        switch (result) {
            case ALREADY_ACTIVE -> {
                context.getSource().sendFailure(
                        Component.translatable("commands.aurorion_utils.abduzir.alreadyActive", target.getName()));
                return 0;
            }
            case NO_SAVED_ORIGIN -> {
                context.getSource().sendFailure(
                        Component.translatable("commands.aurorion_utils.abduzir.voltar.noOrigin", target.getName()));
                return 0;
            }
            default -> {
                context.getSource().sendSuccess(() -> Component.translatable(
                        "commands.aurorion_utils.abduzir.voltar.success", target.getName()), true);
                return 1;
            }
        }
    }
}
