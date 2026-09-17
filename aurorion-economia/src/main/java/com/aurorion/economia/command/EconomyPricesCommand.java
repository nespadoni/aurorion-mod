package com.aurorion.economia.command;

import com.aurorion.economia.AurorionEconomia;
import com.aurorion.economia.config.EconomyConfig;
import com.aurorion.economia.money.Money;
import com.aurorion.economia.server.WalletData;
import com.aurorion.core.character.CharacterData;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

@EventBusSubscriber(modid = AurorionEconomia.MOD_ID)
public final class EconomyPricesCommand {
    private EconomyPricesCommand() { }
    @SubscribeEvent public static void register(RegisterCommandsEvent event) {
        var root = Commands.literal("economia").requires(source -> source.hasPermission(2));
        var prices = Commands.literal("preco").executes(context -> {
            for (int i = 0; i < 5; i++) {
                long price = EconomyConfig.UPGRADE_PRICES[i].get();
                String line = EconomyConfig.UPGRADE_IDS[i] + ": " + (price < 0 ? "desativado" : Money.describe(price));
                context.getSource().sendSuccess(() -> Component.literal(line), false);
            }
            return 1;
        });
        for (int i = 0; i < 5; i++) {
            int index = i;
            prices.then(Commands.literal(EconomyConfig.UPGRADE_IDS[i])
                    .then(Commands.argument("fragmentos", LongArgumentType.longArg(-1, Money.MAX)).executes(context -> {
                        long value = LongArgumentType.getLong(context, "fragmentos");
                        EconomyConfig.UPGRADE_PRICES[index].set(value); EconomyConfig.SPEC.save();
                        context.getSource().sendSuccess(() -> Component.literal("Preço atualizado: "
                                + EconomyConfig.UPGRADE_IDS[index] + " = " + value + " Fragmentos (-1 desativa)."), true);
                        return 1;
                    })));
        }
        root.then(prices);
        root.then(Commands.literal("zona").then(Commands.argument("zona", IntegerArgumentType.integer(0, 4))
                .then(Commands.argument("fragmentos_por_bloco", LongArgumentType.longArg(1, Money.MAX / 1_048_576L))
                        .executes(context -> {
                            int zone = IntegerArgumentType.getInteger(context, "zona");
                            long rate = LongArgumentType.getLong(context, "fragmentos_por_bloco");
                            EconomyConfig.ZONE_RATES[zone].set(rate); EconomyConfig.SPEC.save();
                            context.getSource().sendSuccess(() -> Component.literal("Preço por bloco de "
                                    + EconomyConfig.ZONE_NAMES[zone] + " atualizado: " + rate + " Fragmentos."), true);
                            return 1;
                        }))));
        root.then(Commands.literal("terrenos").then(Commands.argument("jogador", EntityArgument.player()).executes(context -> {
            var player = EntityArgument.getPlayer(context, "jogador");
            var character = CharacterData.get(player.server).find(player.getUUID());
            if (character == null) return 0;
            var deeds = WalletData.get(player.server).deeds().stream()
                    .filter(deed -> deed.ownerCharacter().equals(character.id())).toList();
            context.getSource().sendSuccess(() -> Component.literal(deeds.size() + " terreno(s) do personagem " + character.fullName()), false);
            for (var deed : deeds) context.getSource().sendSuccess(() -> Component.literal(deed.id() + " • "
                    + deed.width() + "x" + deed.length() + " • " + deed.dimension() + " • " + deed.x() + "," + deed.z()), false);
            return deeds.size();
        })));
        event.getDispatcher().register(root);
    }
}
