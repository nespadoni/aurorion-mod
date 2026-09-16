package com.aurorion.economia.command;

import com.aurorion.economia.AurorionEconomia;
import com.aurorion.economia.money.Money;
import com.aurorion.economia.server.Wallet;
import com.mojang.brigadier.arguments.StringArgumentType;
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
                })));
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

    @FunctionalInterface
    private interface Operation {
        long apply(net.minecraft.server.MinecraftServer server, ServerPlayer player, long amount);
    }
}
