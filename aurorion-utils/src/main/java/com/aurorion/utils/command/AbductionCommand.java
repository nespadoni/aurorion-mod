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
import org.jetbrains.annotations.Nullable;

/**
 * {@code /abduzir <jogador> [cor]} — puxa jogador ate a posicao do executor.
 * {@code /abduzir <jogador> <destino> [cor]} — puxa jogador ate a posicao de outro jogador online.
 * {@code /abduzir voltar <jogador> [cor]} — leva de volta pra ultima origem salva.
 *
 * <p>{@code [cor]} e opcional em toda forma: omitida, vale o {@code beamColorRgb} da config. Aceita
 * tanto os nomes da paleta (que aparecem como sugestao ao digitar) quanto hexadecimal cru — ver
 * {@link com.aurorion.utils.abduction.BeamColor}.</p>
 *
 * <p>Os ramos {@code <cor>} sao registrados <b>antes</b> de {@code <destino>} de proposito: o
 * Brigadier tenta os filhos na ordem de registro e fica com o primeiro que parseia inteiro, e so
 * {@link BeamColorArgument} sabe recusar o que nao e cor ({@code EntityArgument.player()} aceita
 * qualquer palavra como nome). Efeito colateral aceito: um jogador chamado exatamente como uma cor
 * da paleta precisa ser mirado como destino por seletor ({@code @p}, {@code @a[name=...]}).</p>
 *
 * <p>Nivel de operador 2 em toda a arvore — e uma ferramenta de staff, nao algo auto-aplicavel.</p>
 */
@EventBusSubscriber(modid = AurorionUtils.MOD_ID)
public final class AbductionCommand {
    private static final String COLOR_ARG = "cor";

    private AbductionCommand() {
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(Commands.literal("abduzir")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("voltar")
                        .then(Commands.argument("jogador", EntityArgument.player())
                                .executes(context -> returnPlayer(context, null))
                                .then(Commands.argument(COLOR_ARG, BeamColorArgument.beamColor())
                                        .executes(context -> returnPlayer(context, chosenColor(context))))))
                .then(Commands.argument("jogador", EntityArgument.player())
                        .executes(context -> abductToExecutor(context, null))
                        .then(Commands.argument(COLOR_ARG, BeamColorArgument.beamColor())
                                .executes(context -> abductToExecutor(context, chosenColor(context))))
                        .then(Commands.argument("destino", EntityArgument.player())
                                .executes(context -> abductToDestination(context, null))
                                .then(Commands.argument(COLOR_ARG, BeamColorArgument.beamColor())
                                        .executes(context -> abductToDestination(context, chosenColor(context)))))));
    }

    private static int abductToExecutor(CommandContext<CommandSourceStack> context, @Nullable Integer beamColor)
            throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(context, "jogador");
        ServerPlayer executor = context.getSource().getPlayerOrException();
        return runAbduction(context, target, executor, beamColor);
    }

    private static int abductToDestination(CommandContext<CommandSourceStack> context, @Nullable Integer beamColor)
            throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(context, "jogador");
        ServerPlayer destination = EntityArgument.getPlayer(context, "destino");
        return runAbduction(context, target, destination, beamColor);
    }

    private static int runAbduction(CommandContext<CommandSourceStack> context, ServerPlayer target,
                                    ServerPlayer destinationSource, @Nullable Integer beamColor) {
        AbductionManager.Result result =
                AbductionManager.startAbduction(target, destinationSource, beamColor);

        if (result == AbductionManager.Result.ALREADY_ACTIVE) {
            context.getSource().sendFailure(
                    Component.translatable("commands.aurorion_utils.abduzir.alreadyActive", target.getName()));
            return 0;
        }

        context.getSource().sendSuccess(() -> Component.translatable(
                "commands.aurorion_utils.abduzir.success", target.getName(), destinationSource.getName()), true);
        return 1;
    }

    private static int returnPlayer(CommandContext<CommandSourceStack> context, @Nullable Integer beamColor)
            throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(context, "jogador");
        AbductionManager.Result result = AbductionManager.startReturn(target, beamColor);

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
            case DESTINATION_UNAVAILABLE -> {
                context.getSource().sendFailure(Component.translatable(
                        "commands.aurorion_utils.abduzir.voltar.destinationUnavailable", target.getName()));
                return 0;
            }
            default -> {
                context.getSource().sendSuccess(() -> Component.translatable(
                        "commands.aurorion_utils.abduzir.voltar.success", target.getName()), true);
                return 1;
            }
        }
    }

    /**
     * So chamado nos ramos que realmente tem {@code <cor>}; nos outros passa-se {@code null} e o
     * {@code AbductionManager} cai na cor da config.
     */
    private static Integer chosenColor(CommandContext<CommandSourceStack> context) {
        return BeamColorArgument.getBeamColor(context, COLOR_ARG);
    }
}
