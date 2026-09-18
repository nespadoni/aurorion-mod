package com.aurorion.economia.command;

import com.aurorion.economia.AurorionEconomia;
import com.aurorion.economia.money.Money;
import com.aurorion.economia.server.HouseTreasury;
import com.aurorion.economia.server.Wallet;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * A torneira e o ralo da economia, na mao da staff: {@code /economia dar|tirar|definir}.
 *
 * <p>E de proposito que criar dinheiro exija um comando de gente, e nao um numero em arquivo de
 * config: enquanto nao existe uma fonte automatica decidida (ECONOMIA.md §7), todo obolo que entra
 * no servidor tem um responsavel e aparece no log de comandos.</p>
 */
@EventBusSubscriber(modid = AurorionEconomia.MOD_ID)
public final class EconomiaCommand {
    private EconomiaCommand() {
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("economia")
                .requires(source -> source.hasPermission(2))
                .then(action("dar", (server, player, amount) -> Wallet.add(server, player.getUUID(), amount)))
                .then(action("tirar", (server, player, amount) -> Wallet.add(server, player.getUUID(), -amount)))
                .then(action("definir", (server, player, amount) -> {
                    Wallet.set(server, player.getUUID(), amount);
                    return amount;
                }))
                .then(houseCommands()));
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> action(
            String name, Operation operation) {
        return Commands.literal(name)
                .then(Commands.argument("jogador", EntityArgument.player())
                        .then(Commands.argument("quantia", StringArgumentType.word())
                                .executes(context -> run(context, name, operation))));
    }

    private static int run(CommandContext<CommandSourceStack> context, String action, Operation operation)
            throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(context, "jogador");
        long amount = MoneyArgument.get(context, "quantia");
        long after = operation.apply(target.server, target, amount);

        context.getSource().sendSuccess(() -> Component.translatable(
                "commands.aurorion_economia.economia." + action,
                Money.describe(amount), target.getDisplayName(), Money.describe(after)), true);
        return 1;
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> houseCommands() {
        return Commands.literal("casa")
                .then(Commands.argument("casa", ResourceLocationArgument.id())
                        .then(Commands.literal("saldo").executes(EconomiaCommand::houseBalance))
                        .then(Commands.literal("dar")
                                .then(Commands.argument("quantia", StringArgumentType.word())
                                        .executes(context -> houseMoney(context, "dar"))))
                        .then(Commands.literal("tirar")
                                .then(Commands.argument("quantia", StringArgumentType.word())
                                        .executes(context -> houseMoney(context, "tirar"))))
                        .then(Commands.literal("definir")
                                .then(Commands.argument("quantia", StringArgumentType.word())
                                        .executes(context -> houseMoney(context, "definir"))))
                        .then(Commands.literal("cofre")
                                .then(Commands.argument("nivel", IntegerArgumentType.integer(0, HouseTreasury.MAX_LEVEL))
                                        .executes(EconomiaCommand::houseVault))));
    }

    private static int houseBalance(CommandContext<CommandSourceStack> context) {
        var server = context.getSource().getServer();
        var house = ResourceLocationArgument.getId(context, "casa");
        long balance = HouseTreasury.balance(server, house);
        long capacity = HouseTreasury.capacity(server, house);
        int level = HouseTreasury.vaultLevel(server, house);
        context.getSource().sendSuccess(() -> Component.literal(
                "Casa " + house + ": " + Money.describe(balance) + " / " + Money.describe(capacity)
                        + " (cofre nível " + level + ")."), false);
        return (int)Math.min(balance, Integer.MAX_VALUE);
    }

    private static int houseMoney(CommandContext<CommandSourceStack> context, String action)
            throws CommandSyntaxException {
        var server = context.getSource().getServer();
        var house = ResourceLocationArgument.getId(context, "casa");
        long amount = MoneyArgument.get(context, "quantia");
        long balance;
        if (action.equals("dar")) {
            HouseTreasury.Deposit deposit = HouseTreasury.deposit(server, house, amount);
            balance = deposit.balance();
            if (deposit.overflow() > 0) context.getSource().sendFailure(Component.literal(
                    "O cofre atingiu a capacidade; " + Money.describe(deposit.overflow()) + " não entrou."));
        } else if (action.equals("tirar")) {
            if (!HouseTreasury.withdraw(server, house, amount)) {
                context.getSource().sendFailure(Component.literal("O cofre não possui esse valor."));
                return 0;
            }
            balance = HouseTreasury.balance(server, house);
        } else {
            balance = HouseTreasury.set(server, house, amount);
        }
        long result = balance;
        context.getSource().sendSuccess(() -> Component.literal(
                "Cofre de " + house + ": " + Money.describe(result) + "."), true);
        return 1;
    }

    private static int houseVault(CommandContext<CommandSourceStack> context) {
        var server = context.getSource().getServer();
        var house = ResourceLocationArgument.getId(context, "casa");
        int requested = IntegerArgumentType.getInteger(context, "nivel");
        int level = HouseTreasury.setVaultLevel(server, house, requested);
        if (level != requested) {
            context.getSource().sendFailure(Component.literal(
                    "Não é possível reduzir o nível: o saldo atual ultrapassa a nova capacidade."));
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.literal(
                "Cofre de " + house + " definido no nível " + level + ", capacidade "
                        + Money.describe(HouseTreasury.capacity(server, house)) + "."), true);
        return level + 1;
    }

    @FunctionalInterface
    private interface Operation {
        long apply(net.minecraft.server.MinecraftServer server, ServerPlayer player, long amount);
    }
}
