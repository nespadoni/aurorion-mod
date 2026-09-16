package com.aurorion.economia.command;

import com.aurorion.economia.AurorionEconomia;
import com.aurorion.economia.money.Money;
import com.aurorion.economia.money.Transfer;
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
 * {@code /pagar <jogador> <quantia>} — move dinheiro entre dois personagens.
 *
 * <p>Sem limite de distancia nem exigencia de estar perto: o balao de fala do {@code aurorion-talk}
 * ja torna toda negociacao publica, e obrigar proximidade so faria o pagamento acontecer fora do
 * jogo.</p>
 */
@EventBusSubscriber(modid = AurorionEconomia.MOD_ID)
public final class PagarCommand {
    private PagarCommand() {
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("pagar")
                .then(Commands.argument("jogador", EntityArgument.player())
                        .then(Commands.argument("quantia", StringArgumentType.word())
                                .executes(PagarCommand::pay))));
    }

    private static int pay(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer payer = context.getSource().getPlayerOrException();
        ServerPlayer target = EntityArgument.getPlayer(context, "jogador");
        long amount = MoneyArgument.get(context, "quantia");

        Transfer.Result result = Wallet.transfer(payer.server, payer.getUUID(), target.getUUID(), amount);
        if (!result.ok()) {
            context.getSource().sendFailure(failure(result, payer.server, payer));
            return 0;
        }

        context.getSource().sendSuccess(() -> Component.translatable(
                "commands.aurorion_economia.pagar.sent", Money.describe(amount), target.getDisplayName()), false);
        target.sendSystemMessage(Component.translatable(
                "commands.aurorion_economia.pagar.received", Money.describe(amount), payer.getDisplayName()));
        return 1;
    }

    private static Component failure(Transfer.Result result, net.minecraft.server.MinecraftServer server,
                                     ServerPlayer payer) {
        return switch (result) {
            case INSUFFICIENT -> Component.translatable("commands.aurorion_economia.pagar.insufficient",
                    Money.describe(Wallet.balance(server, payer.getUUID())));
            case SAME_ACCOUNT -> Component.translatable("commands.aurorion_economia.pagar.self");
            case TARGET_FULL -> Component.translatable("commands.aurorion_economia.pagar.targetFull");
            case INVALID_AMOUNT, OK -> Component.translatable("commands.aurorion_economia.quantiaInvalida");
        };
    }
}
