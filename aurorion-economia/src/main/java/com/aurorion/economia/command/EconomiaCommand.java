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
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

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

    /** Casa sem namespace ({@code venthra}) e casa do aurorion-ethereal, onde as cinco moram. */
    private static final String DEFAULT_HOUSE_NAMESPACE = "aurorion_ethereal";

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> houseCommands() {
        return Commands.literal("casa")
                .then(Commands.argument("casa", ResourceLocationArgument.id())
                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                houseSuggestions(context.getSource().getServer()), builder))
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
                                        .executes(EconomiaCommand::houseVault)))
                        .then(Commands.literal("salario")
                                .executes(EconomiaCommand::showSalary)
                                .then(Commands.literal("desligar").executes(EconomiaCommand::disableSalary))
                                .then(Commands.literal("pagar").executes(EconomiaCommand::paySalary))
                                .then(Commands.argument("quantia", StringArgumentType.word())
                                        .then(Commands.argument("dias",
                                                        IntegerArgumentType.integer(1, HouseTreasury.MAX_SALARY_DAYS))
                                                .executes(EconomiaCommand::setSalary)))));
    }

    /** {@code venthra} e {@code aurorion_ethereal:venthra} sao a mesma casa; {@code minecraft:} nunca e. */
    private static ResourceLocation house(CommandContext<CommandSourceStack> context) {
        ResourceLocation id = ResourceLocationArgument.getId(context, "casa");
        return id.getNamespace().equals(ResourceLocation.DEFAULT_NAMESPACE)
                ? ResourceLocation.fromNamespaceAndPath(DEFAULT_HOUSE_NAMESPACE, id.getPath()) : id;
    }

    /** Casas do catalogo do Ethereal (opcional, por reflexao) mais as que ja tem cofre gravado. */
    private static Collection<String> houseSuggestions(net.minecraft.server.MinecraftServer server) {
        List<String> ids = new ArrayList<>();
        try {
            Object catalog = Class.forName("com.aurorion.ethereal.house.HouseCatalog").getMethod("ids").invoke(null);
            if (catalog instanceof Collection<?> values) values.forEach(value -> ids.add(shortName(value.toString())));
        } catch (ReflectiveOperationException | LinkageError ignored) {
            // Sem o Ethereal, sugere so o que o save conhece.
        }
        HouseTreasury.knownHouses(server).forEach(id -> {
            String name = shortName(id.toString());
            if (!ids.contains(name)) ids.add(name);
        });
        return ids;
    }

    private static String shortName(String id) {
        String prefix = DEFAULT_HOUSE_NAMESPACE + ":";
        return id.startsWith(prefix) ? id.substring(prefix.length()) : id;
    }

    private static int houseBalance(CommandContext<CommandSourceStack> context) {
        var server = context.getSource().getServer();
        var house = house(context);
        long balance = HouseTreasury.balance(server, house);
        long capacity = HouseTreasury.capacity(server, house);
        int level = HouseTreasury.vaultLevel(server, house);
        context.getSource().sendSuccess(() -> Component.literal(
                "Casa " + house + ": " + Money.describe(balance) + " / " + Money.describe(capacity)
                        + " (cofre nível " + level + ")."), false);
        showSalary(context);
        return (int)Math.min(balance, Integer.MAX_VALUE);
    }

    private static int houseMoney(CommandContext<CommandSourceStack> context, String action)
            throws CommandSyntaxException {
        var server = context.getSource().getServer();
        var house = house(context);
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
        var house = house(context);
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

    private static int showSalary(CommandContext<CommandSourceStack> context) {
        var server = context.getSource().getServer();
        var house = house(context);
        long salary = HouseTreasury.salary(server, house);
        if (salary <= 0L) {
            context.getSource().sendSuccess(() -> Component.literal(
                    "Casa " + house + ": sem salário definido."), false);
            return 0;
        }
        int days = HouseTreasury.salaryDays(server, house);
        long next = HouseTreasury.nextSalaryInMillis(server, house);
        context.getSource().sendSuccess(() -> Component.literal("Salário de " + house + ": "
                + Money.describe(salary) + " a cada " + days + (days == 1 ? " dia" : " dias") + "; próximo em ")
                .append(com.aurorion.core.text.TimeFormat.duration(next)).append("."), false);
        return 1;
    }

    private static int setSalary(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        var server = context.getSource().getServer();
        var house = house(context);
        long amount = MoneyArgument.get(context, "quantia");
        int days = IntegerArgumentType.getInteger(context, "dias");
        HouseTreasury.setSalary(server, house, amount, days);
        context.getSource().sendSuccess(() -> Component.literal("Salário de " + house + " definido: "
                + Money.describe(amount) + " a cada " + days + (days == 1 ? " dia" : " dias")
                + ". O primeiro pagamento cai no cofre ao fim do primeiro período."), true);
        return 1;
    }

    private static int disableSalary(CommandContext<CommandSourceStack> context) {
        var house = house(context);
        HouseTreasury.setSalary(context.getSource().getServer(), house, 0L, 0);
        context.getSource().sendSuccess(() -> Component.literal("Salário de " + house + " desligado."), true);
        return 1;
    }

    private static int paySalary(CommandContext<CommandSourceStack> context) {
        var server = context.getSource().getServer();
        var house = house(context);
        if (HouseTreasury.salary(server, house) <= 0L) {
            context.getSource().sendFailure(Component.literal("Casa " + house + " não tem salário definido."));
            return 0;
        }
        HouseTreasury.Deposit deposit = HouseTreasury.paySalaryNow(server, house);
        if (deposit.overflow() > 0) context.getSource().sendFailure(Component.literal(
                "O cofre atingiu a capacidade; " + Money.describe(deposit.overflow()) + " não entrou."));
        context.getSource().sendSuccess(() -> Component.literal("Salário pago a " + house + ": "
                + Money.describe(deposit.accepted()) + ". Saldo: " + Money.describe(deposit.balance())
                + ". O relógio do próximo pagamento recomeçou agora."), true);
        return 1;
    }

    @FunctionalInterface
    private interface Operation {
        long apply(net.minecraft.server.MinecraftServer server, ServerPlayer player, long amount);
    }
}
