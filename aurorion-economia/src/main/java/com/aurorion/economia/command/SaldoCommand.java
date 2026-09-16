package com.aurorion.economia.command;

import com.aurorion.economia.AurorionEconomia;
import com.aurorion.economia.money.Money;
import com.aurorion.economia.server.Wallet;
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

/** {@code /saldo} — o proprio saldo. {@code /saldo <jogador>} — o de outro (nivel 2+). */
@EventBusSubscriber(modid = AurorionEconomia.MOD_ID)
public final class SaldoCommand {
    private SaldoCommand() {
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("saldo")
                .executes(SaldoCommand::self)
                .then(Commands.argument("jogador", EntityArgument.player())
                        .requires(source -> source.hasPermission(2))
                        .executes(SaldoCommand::other)));
    }

    private static int self(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        long balance = Wallet.balance(player.server, player.getUUID());

        context.getSource().sendSuccess(() -> Component.translatable(
                "commands.aurorion_economia.saldo.self", Money.describe(balance)), false);
        return (int) Math.min(balance, Integer.MAX_VALUE);
    }

    private static int other(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(context, "jogador");
        long balance = Wallet.balance(target.server, target.getUUID());

        context.getSource().sendSuccess(() -> Component.translatable(
                "commands.aurorion_economia.saldo.other", target.getDisplayName(), Money.describe(balance)), false);
        return (int) Math.min(balance, Integer.MAX_VALUE);
    }
}
