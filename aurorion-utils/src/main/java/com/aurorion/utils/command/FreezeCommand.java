package com.aurorion.utils.command;

import com.aurorion.utils.AurorionUtils;
import com.aurorion.utils.freeze.FreezeManager;
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

import java.util.Collection;

/**
 * {@code /freeze <jogadores>} / {@code /unfreeze <jogadores>} — aceita nome, {@code @a},
 * {@code @e[team=...]}, qualquer seletor padrao do jogo (via {@link EntityArgument#players()}).
 * Pensado pra pausar todo mundo num evento (ex: {@code /freeze @a}) tanto quanto pra travar uma
 * pessoa so. Congelar de novo quem ja esta congelado (ou destravar quem nao esta) e um no-op
 * silencioso por jogador — nunca um erro pro comando inteiro.
 */
@EventBusSubscriber(modid = AurorionUtils.MOD_ID)
public final class FreezeCommand {
    private FreezeCommand() {
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(Commands.literal("freeze")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("jogadores", EntityArgument.players())
                        .executes(FreezeCommand::freeze)));

        dispatcher.register(Commands.literal("unfreeze")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("jogadores", EntityArgument.players())
                        .executes(FreezeCommand::unfreeze)));
    }

    private static int freeze(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Collection<ServerPlayer> players = EntityArgument.getPlayers(context, "jogadores");

        int changed = 0;
        for (ServerPlayer player : players) {
            if (FreezeManager.freeze(player)) changed++;
        }

        int frozen = changed;
        context.getSource().sendSuccess(() ->
                Component.translatable("commands.aurorion_utils.freeze.success", frozen), true);
        return changed;
    }

    private static int unfreeze(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Collection<ServerPlayer> players = EntityArgument.getPlayers(context, "jogadores");

        int changed = 0;
        for (ServerPlayer player : players) {
            if (FreezeManager.unfreeze(player)) changed++;
        }

        int unfrozen = changed;
        context.getSource().sendSuccess(() ->
                Component.translatable("commands.aurorion_utils.unfreeze.success", unfrozen), true);
        return changed;
    }
}
