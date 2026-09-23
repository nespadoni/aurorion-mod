package com.aurorion.utils.command;

import com.aurorion.utils.AurorionUtils;
import com.aurorion.utils.freeze.FreezeManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
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

import java.util.Collection;

/**
 * {@code /freeze <jogadores> [segundos]} e {@code /unfreeze <jogadores>}. Staff (nivel 2).
 *
 * <p>{@code <jogadores>} e qualquer seletor do jogo: nome, {@code @a}, {@code @a[team=casa]},
 * {@code @p}, {@code @a[distance=..20]}. Pensado tanto para pausar todo mundo num evento quanto
 * para travar uma pessoa so.
 *
 * <ul>
 *   <li>Sem segundos: congelado ate o {@code /unfreeze} — inclusive depois de morrer e relogar.</li>
 *   <li>Com segundos: solta sozinho no fim.</li>
 * </ul>
 *
 * <p>Congelar quem ja esta congelado (ou soltar quem nao esta) e no-op por jogador, nunca erro do
 * comando inteiro. O que "congelado" significa esta em {@link FreezeManager}.
 */
@EventBusSubscriber(modid = AurorionUtils.MOD_ID)
public final class FreezeCommand {
    private static final String ARG_PLAYERS = "jogadores";
    private static final String ARG_SECONDS = "segundos";

    private FreezeCommand() {
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(Commands.literal("freeze")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument(ARG_PLAYERS, EntityArgument.players())
                        .executes(context -> freeze(context, FreezeManager.FOREVER))
                        .then(Commands.argument(ARG_SECONDS, IntegerArgumentType.integer(1, 86_400))
                                .executes(context -> freeze(context,
                                        IntegerArgumentType.getInteger(context, ARG_SECONDS) * 20)))));

        dispatcher.register(Commands.literal("unfreeze")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument(ARG_PLAYERS, EntityArgument.players())
                        .executes(FreezeCommand::unfreeze)));
    }

    private static int freeze(CommandContext<CommandSourceStack> context, int ticks) throws CommandSyntaxException {
        Collection<ServerPlayer> players = EntityArgument.getPlayers(context, ARG_PLAYERS);

        int changed = 0;
        for (ServerPlayer player : players) {
            if (FreezeManager.freezeByCommand(player, ticks)) changed++;
        }

        int frozen = changed;
        int total = players.size();
        context.getSource().sendSuccess(() -> ticks == FreezeManager.FOREVER
                ? Component.translatable("commands.aurorion_utils.freeze.success", frozen, total)
                : Component.translatable("commands.aurorion_utils.freeze.success.timed", frozen, total, ticks / 20), true);
        return changed;
    }

    private static int unfreeze(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Collection<ServerPlayer> players = EntityArgument.getPlayers(context, ARG_PLAYERS);

        int changed = 0;
        for (ServerPlayer player : players) {
            if (FreezeManager.unfreezeByCommand(player)) changed++;
        }

        int unfrozen = changed;
        context.getSource().sendSuccess(() ->
                Component.translatable("commands.aurorion_utils.unfreeze.success", unfrozen), true);
        return changed;
    }
}
