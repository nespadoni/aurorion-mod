package com.aurorion.ethereal.command;

import com.aurorion.ethereal.AurorionEthereal;
import com.aurorion.ethereal.ceremony.CeremonyManager;
import com.aurorion.ethereal.house.House;
import com.aurorion.ethereal.house.HouseCatalog;
import com.aurorion.ethereal.house.HouseManager;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.Collection;

/**
 * Consulta publica da casa e ferramentas de staff (nivel 2).
 * Definir aceita perfis offline; cerimonia exige um unico participante online no palco.
 */
@EventBusSubscriber(modid = AurorionEthereal.MOD_ID)
public final class HouseCommand {
    private static final SuggestionProvider<CommandSourceStack> HOUSE_SUGGESTIONS =
            (context, builder) -> SharedSuggestionProvider.suggestResource(HouseCatalog.ids(), builder);

    private HouseCommand() {
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("casa")
                .executes(HouseCommand::showSelf)
                .then(Commands.literal("listar")
                        .requires(source -> source.hasPermission(2))
                        .executes(HouseCommand::list))
                .then(Commands.literal("ver")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("jogador", GameProfileArgument.gameProfile())
                                .executes(HouseCommand::view)))
                .then(Commands.literal("definir")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("jogador", GameProfileArgument.gameProfile())
                                .then(Commands.argument("casa", ResourceLocationArgument.id())
                                        .suggests(HOUSE_SUGGESTIONS)
                                        .executes(HouseCommand::set))))
                .then(Commands.literal("limpar")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("jogador", GameProfileArgument.gameProfile())
                                .executes(HouseCommand::clear)))
                .then(Commands.literal("cerimonia")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("cancelar")
                                .then(Commands.argument("jogador", GameProfileArgument.gameProfile())
                                        .executes(HouseCommand::ceremonyCancel)))
                        .then(Commands.argument("jogador", EntityArgument.player())
                                .executes(context -> ceremony(context, false))
                                .then(Commands.argument("casa", ResourceLocationArgument.id())
                                        .suggests(HOUSE_SUGGESTIONS)
                                        .executes(context -> ceremony(context, true))))));
    }

    // --- Consulta ------------------------------------------------------------------------------

    private static int showSelf(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        MinecraftServer server = context.getSource().getServer();

        ResourceLocation id = HouseManager.houseIdOf(server, player.getUUID());
        if (id == null) {
            context.getSource().sendSuccess(() -> Component.translatable("commands.aurorion_ethereal.casa.none"), false);
            return 0;
        }

        House house = HouseCatalog.get(id);
        if (house == null) {
            context.getSource().sendFailure(Component.translatable("commands.aurorion_ethereal.casa.missing", id.toString()));
            return 0;
        }

        context.getSource().sendSuccess(() ->
                Component.translatable("commands.aurorion_ethereal.casa.self", house.coloredName()), false);
        return 1;
    }

    private static int list(CommandContext<CommandSourceStack> context) {
        MinecraftServer server = context.getSource().getServer();

        if (HouseCatalog.isEmpty()) {
            context.getSource().sendFailure(Component.translatable("commands.aurorion_ethereal.casa.empty"));
            return 0;
        }

        for (House house : HouseCatalog.all()) {
            int members = HouseManager.membersOf(server, house.id());
            Component line = house.hasCapacity()
                    ? Component.translatable("commands.aurorion_ethereal.casa.list.entry.capped",
                    house.coloredName(), house.id().toString(), members, house.capacity())
                    : Component.translatable("commands.aurorion_ethereal.casa.list.entry",
                    house.coloredName(), house.id().toString(), members);

            context.getSource().sendSuccess(() -> line, false);
        }
        return HouseCatalog.all().size();
    }

    private static int view(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        MinecraftServer server = context.getSource().getServer();
        int found = 0;

        for (GameProfile profile : GameProfileArgument.getGameProfiles(context, "jogador")) {
            ResourceLocation id = HouseManager.houseIdOf(server, profile.getId());
            House house = id == null ? null : HouseCatalog.get(id);

            Component line;
            if (id == null) {
                line = Component.translatable("commands.aurorion_ethereal.casa.view.none", profile.getName());
            } else if (house == null) {
                line = Component.translatable("commands.aurorion_ethereal.casa.view.missing", profile.getName(), id.toString());
            } else {
                line = Component.translatable("commands.aurorion_ethereal.casa.view.house", profile.getName(), house.coloredName());
                found++;
            }
            context.getSource().sendSuccess(() -> line, false);
        }
        return found;
    }

    // --- Administracao -------------------------------------------------------------------------

    private static int set(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        MinecraftServer server = context.getSource().getServer();
        ResourceLocation houseId = ResourceLocationArgument.getId(context, "casa");

        House house = HouseCatalog.get(houseId);
        if (house == null) {
            context.getSource().sendFailure(
                    Component.translatable("commands.aurorion_ethereal.casa.unknown", houseId.toString()));
            return 0;
        }

        Collection<GameProfile> profiles = GameProfileArgument.getGameProfiles(context, "jogador");
        for (GameProfile profile : profiles) {
            HouseManager.assign(server, profile.getId(), houseId);
            context.getSource().sendSuccess(() -> Component.translatable(
                    "commands.aurorion_ethereal.casa.set", profile.getName(), house.coloredName()), true);
        }
        return profiles.size();
    }

    private static int clear(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        MinecraftServer server = context.getSource().getServer();
        Collection<GameProfile> profiles = GameProfileArgument.getGameProfiles(context, "jogador");
        int changed = 0;

        for (GameProfile profile : profiles) {
            if (HouseManager.clear(server, profile.getId())) {
                changed++;
                context.getSource().sendSuccess(() -> Component.translatable(
                        "commands.aurorion_ethereal.casa.clear", profile.getName()), true);
            } else {
                context.getSource().sendFailure(Component.translatable(
                        "commands.aurorion_ethereal.casa.clear.none", profile.getName()));
            }
        }
        return changed;
    }

    // --- Cerimonia -----------------------------------------------------------------------------

    /** Usa a casa cadastrada ou atribui e revela em uma unica acao da staff. */
    private static int ceremony(CommandContext<CommandSourceStack> context, boolean explicitHouse)
            throws CommandSyntaxException {
        MinecraftServer server = context.getSource().getServer();
        ServerPlayer player = EntityArgument.getPlayer(context, "jogador");
        ResourceLocation houseId = explicitHouse ? ResourceLocationArgument.getId(context, "casa")
                : HouseManager.houseIdOf(server, player.getUUID());
        if (houseId == null) {
            context.getSource().sendFailure(Component.translatable(
                    "commands.aurorion_ethereal.cerimonia.no_house", player.getScoreboardName()));
            return 0;
        }
        switch (CeremonyManager.bind(server, player.getUUID(), houseId)) {
            case UNKNOWN_HOUSE -> context.getSource().sendFailure(Component.translatable(
                    "commands.aurorion_ethereal.casa.unknown", houseId.toString()));
            case OFFLINE -> context.getSource().sendFailure(Component.translatable(
                    "commands.aurorion_ethereal.cerimonia.unavailable"));
            case ALREADY_RUNNING -> context.getSource().sendFailure(Component.translatable(
                    "commands.aurorion_ethereal.cerimonia.busy"));
            case OK -> {
                context.getSource().sendSuccess(() -> Component.translatable(
                        "commands.aurorion_ethereal.cerimonia.started", player.getDisplayName()), false);
                return 1;
            }
        }
        return 0;
    }

    /** Tira da fila um rito que ainda nao tocou, ou corta um que esta acontecendo. */
    private static int ceremonyCancel(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        MinecraftServer server = context.getSource().getServer();
        Collection<GameProfile> profiles = GameProfileArgument.getGameProfiles(context, "jogador");
        int cancelled = 0;

        for (GameProfile profile : profiles) {
            if (CeremonyManager.cancel(server, profile.getId())) cancelled++;
        }

        if (cancelled == 0) {
            context.getSource().sendFailure(
                    Component.translatable("commands.aurorion_ethereal.cerimonia.nothing_to_cancel"));
            return 0;
        }

        int total = cancelled;
        context.getSource().sendSuccess(() -> Component.translatable(
                "commands.aurorion_ethereal.cerimonia.cancelled", total), true);
        return cancelled;
    }
}
