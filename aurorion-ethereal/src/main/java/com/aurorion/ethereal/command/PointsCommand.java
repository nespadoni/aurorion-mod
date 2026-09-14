package com.aurorion.ethereal.command;

import com.aurorion.ethereal.AurorionEthereal;
import com.aurorion.ethereal.house.House;
import com.aurorion.ethereal.house.HouseCatalog;
import com.aurorion.ethereal.house.HouseData;
import com.aurorion.ethereal.ranking.BoardMode;
import com.aurorion.ethereal.ranking.BoardService;
import com.aurorion.ethereal.ranking.RankingData;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.Collection;
import java.util.Map;

/**
 * {@code /pontos} — a ferramenta de staff que alimenta os placares.
 *
 * <p>Duas moedas diferentes de proposito:
 *
 * <ul>
 *   <li>{@code /pontos aluno} mexe no ponto <b>do jogador</b>, que conta duas vezes: no ranking de
 *       alunos e no total da casa dele.</li>
 *   <li>{@code /pontos casa} mexe no <b>bonus</b> da casa, para premiar (ou punir) a casa inteira
 *       sem mentir sobre o que cada aluno fez.</li>
 * </ul>
 *
 * <p>Tudo por {@link GameProfileArgument}: dar ponto a quem ganhou uma prova ontem nao pode depender
 * de a pessoa estar online agora.
 */
@EventBusSubscriber(modid = AurorionEthereal.MOD_ID)
public final class PointsCommand {
    private static final SuggestionProvider<CommandSourceStack> HOUSE_SUGGESTIONS =
            (context, builder) -> SharedSuggestionProvider.suggestResource(HouseCatalog.ids(), builder);

    private PointsCommand() {
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("pontos")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("casa")
                        .then(Commands.argument("casa", ResourceLocationArgument.id())
                                .suggests(HOUSE_SUGGESTIONS)
                                .then(Commands.argument("quantidade", IntegerArgumentType.integer())
                                        .executes(PointsCommand::housePoints))))
                .then(Commands.literal("aluno")
                        .then(Commands.argument("jogador", GameProfileArgument.gameProfile())
                                .then(Commands.argument("quantidade", IntegerArgumentType.integer())
                                        .executes(PointsCommand::playerPoints))))
                .then(Commands.literal("missao")
                        .then(Commands.argument("jogador", GameProfileArgument.gameProfile())
                                .executes(PointsCommand::mission)))
                .then(Commands.literal("ver")
                        .executes(PointsCommand::show)));
    }

    private static int housePoints(CommandContext<CommandSourceStack> context) {
        MinecraftServer server = context.getSource().getServer();
        ResourceLocation houseId = ResourceLocationArgument.getId(context, "casa");
        int amount = IntegerArgumentType.getInteger(context, "quantidade");

        House house = HouseCatalog.get(houseId);
        if (house == null) {
            context.getSource().sendFailure(
                    Component.translatable("commands.aurorion_ethereal.casa.unknown", houseId.toString()));
            return 0;
        }

        RankingData data = RankingData.get(server);
        data.adjustHouseBonus(houseId, amount);
        BoardService.refreshHouses(server);

        int total = data.houseTotals(HouseData.get(server), HouseCatalog.all()).getOrDefault(houseId, 0);
        context.getSource().sendSuccess(() -> Component.translatable(
                "commands.aurorion_ethereal.pontos.casa", signed(amount), house.coloredName(), total), true);
        return 1;
    }

    private static int playerPoints(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        MinecraftServer server = context.getSource().getServer();
        int amount = IntegerArgumentType.getInteger(context, "quantidade");
        Collection<GameProfile> profiles = GameProfileArgument.getGameProfiles(context, "jogador");
        RankingData data = RankingData.get(server);

        for (GameProfile profile : profiles) {
            data.adjustPlayerPoints(profile.getId(), profile.getName(), amount);
            int total = data.playerPoints(profile.getId());

            context.getSource().sendSuccess(() -> Component.translatable(
                    "commands.aurorion_ethereal.pontos.aluno", signed(amount), profile.getName(), total), true);

            ServerPlayer online = server.getPlayerList().getPlayer(profile.getId());
            if (online != null) {
                online.sendSystemMessage(Component.translatable(
                        "aurorion_ethereal.pontos.received", signed(amount)).withStyle(ChatFormatting.YELLOW));
            }
        }

        // Ponto de aluno conta duas vezes: no ranking de alunos e no total da casa dele.
        BoardService.refreshPoints(server);
        return profiles.size();
    }

    private static int mission(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        MinecraftServer server = context.getSource().getServer();
        Collection<GameProfile> profiles = GameProfileArgument.getGameProfiles(context, "jogador");
        RankingData data = RankingData.get(server);

        for (GameProfile profile : profiles) {
            data.recordMission(profile.getId(), profile.getName());
            context.getSource().sendSuccess(() -> Component.translatable(
                    "commands.aurorion_ethereal.pontos.missao", profile.getName()), true);
        }

        BoardService.refresh(server, BoardMode.MISSIONS);
        return profiles.size();
    }

    private static int show(CommandContext<CommandSourceStack> context) {
        MinecraftServer server = context.getSource().getServer();

        if (HouseCatalog.isEmpty()) {
            context.getSource().sendFailure(Component.translatable("commands.aurorion_ethereal.casa.empty"));
            return 0;
        }

        Map<ResourceLocation, Integer> totals = RankingData.get(server)
                .houseTotals(HouseData.get(server), HouseCatalog.all());

        context.getSource().sendSuccess(() ->
                Component.translatable("commands.aurorion_ethereal.pontos.header")
                        .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);

        for (House house : HouseCatalog.all()) {
            int total = totals.getOrDefault(house.id(), 0);
            context.getSource().sendSuccess(() -> Component.literal("  ")
                    .append(house.coloredName())
                    .append(Component.literal(": " + total).withStyle(ChatFormatting.WHITE)), false);
        }
        return HouseCatalog.all().size();
    }

    /** O sinal explicito evita a leitura ambigua de "10 pontos" quando o comando tirou pontos. */
    private static String signed(int amount) {
        return amount >= 0 ? "+" + amount : String.valueOf(amount);
    }
}
