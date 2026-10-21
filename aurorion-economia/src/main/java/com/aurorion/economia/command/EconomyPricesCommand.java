package com.aurorion.economia.command;

import com.aurorion.economia.AurorionEconomia;
import com.aurorion.economia.config.EconomyConfig;
import com.aurorion.economia.land.LandDeed;
import com.aurorion.economia.land.LandZoneData;
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

import java.util.UUID;

/**
 * Precos, zonas e cadastro de terrenos, todos ajustaveis em jogo.
 *
 * <ul>
 *   <li>{@code /economia zona <0-4> <fragmentos_por_bloco>}: taxa da zona.</li>
 *   <li>{@code /economia zona regiao adicionar <zona> <x1> <z1> <x2> <z2>}: marca um retangulo da
 *       dimensao de quem executa com aquela zona ({@link LandZoneData}). {@code remover <id>} e
 *       {@code listar} completam. Lote fora de qualquer regiao paga a zona mais cara.</li>
 *   <li>{@code /economia terrenos revogar <escritura>}: cancela uma escritura (lote abandonado,
 *       venda irregular, cadastro ocupado de proposito). O valor pago nao volta sozinho.</li>
 * </ul>
 */
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
        root.then(Commands.literal("zona").then(zoneRegions()).then(Commands.argument("zona", IntegerArgumentType.integer(0, 4))
                .then(Commands.argument("fragmentos_por_bloco", LongArgumentType.longArg(1, Money.MAX / 1_048_576L))
                        .executes(context -> {
                            int zone = IntegerArgumentType.getInteger(context, "zona");
                            long rate = LongArgumentType.getLong(context, "fragmentos_por_bloco");
                            EconomyConfig.ZONE_RATES[zone].set(rate); EconomyConfig.SPEC.save();
                            context.getSource().sendSuccess(() -> Component.literal("Preço por bloco de "
                                    + EconomyConfig.ZONE_NAMES[zone] + " atualizado: " + rate + " Fragmentos."), true);
                            return 1;
                        }))));
        root.then(Commands.literal("terrenos").then(Commands.literal("revogar")
                .then(Commands.argument("escritura", StringArgumentType.word()).executes(context -> {
                    UUID id;
                    try {
                        id = UUID.fromString(StringArgumentType.getString(context, "escritura"));
                    } catch (IllegalArgumentException invalid) {
                        context.getSource().sendFailure(Component.literal("Informe o id da escritura (UUID), como aparece em /economia terrenos <jogador>."));
                        return 0;
                    }
                    LandDeed removed = WalletData.get(context.getSource().getServer()).revokeDeed(id);
                    if (removed == null) {
                        context.getSource().sendFailure(Component.literal("Escritura não encontrada (ou cadastro ilegível)."));
                        return 0;
                    }
                    AurorionEconomia.LOGGER.info("LAND_REVOKE deed={} ownerCharacter={} paid={} by={}", removed.id(),
                            removed.ownerCharacter(), removed.paid(), context.getSource().getTextName());
                    context.getSource().sendSuccess(() -> Component.literal("Escritura " + removed.id() + " de "
                            + removed.ownerName() + " revogada. O valor pago (" + Money.describe(removed.paid())
                            + ") não foi devolvido."), true);
                    return 1;
                })))
                .then(Commands.argument("jogador", EntityArgument.player()).executes(context -> {
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

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<net.minecraft.commands.CommandSourceStack> zoneRegions() {
        return Commands.literal("regiao")
                .then(Commands.literal("listar").executes(context -> {
                    var regions = LandZoneData.get(context.getSource().getServer()).regions();
                    context.getSource().sendSuccess(() -> Component.literal(regions.size() + " região(ões) de zona."
                            + " Lote fora de todas paga a zona mais cara."), false);
                    for (var region : regions) context.getSource().sendSuccess(() -> Component.literal("#" + region.id()
                            + " • " + EconomyConfig.ZONE_NAMES[region.zone()] + " • " + region.dimension()
                            + " • " + region.minX() + "," + region.minZ() + " → " + region.maxX() + "," + region.maxZ()), false);
                    return regions.size();
                }))
                .then(Commands.literal("remover").then(Commands.argument("id", IntegerArgumentType.integer(1))
                        .executes(context -> {
                            int id = IntegerArgumentType.getInteger(context, "id");
                            if (!LandZoneData.get(context.getSource().getServer()).remove(id)) {
                                context.getSource().sendFailure(Component.literal("Região #" + id + " não existe."));
                                return 0;
                            }
                            context.getSource().sendSuccess(() -> Component.literal("Região #" + id + " removida."), true);
                            return 1;
                        })))
                .then(Commands.literal("adicionar").then(Commands.argument("zona", IntegerArgumentType.integer(0, 4))
                        .then(Commands.argument("x1", IntegerArgumentType.integer(-30_000_000, 30_000_000))
                        .then(Commands.argument("z1", IntegerArgumentType.integer(-30_000_000, 30_000_000))
                        .then(Commands.argument("x2", IntegerArgumentType.integer(-30_000_000, 30_000_000))
                        .then(Commands.argument("z2", IntegerArgumentType.integer(-30_000_000, 30_000_000))
                        .executes(context -> {
                            int zone = IntegerArgumentType.getInteger(context, "zona");
                            var dimension = context.getSource().getLevel().dimension().location();
                            var region = LandZoneData.get(context.getSource().getServer()).add(dimension,
                                    IntegerArgumentType.getInteger(context, "x1"), IntegerArgumentType.getInteger(context, "z1"),
                                    IntegerArgumentType.getInteger(context, "x2"), IntegerArgumentType.getInteger(context, "z2"), zone);
                            if (region == null) {
                                context.getSource().sendFailure(Component.literal("Limite de " + LandZoneData.MAX_REGIONS
                                        + " regiões atingido. Remova uma antes."));
                                return 0;
                            }
                            context.getSource().sendSuccess(() -> Component.literal("Região #" + region.id() + " ("
                                    + EconomyConfig.ZONE_NAMES[zone] + ") marcada em " + dimension + ": "
                                    + region.minX() + "," + region.minZ() + " → " + region.maxX() + "," + region.maxZ() + "."), true);
                            return region.id();
                        })))))));
    }
}
