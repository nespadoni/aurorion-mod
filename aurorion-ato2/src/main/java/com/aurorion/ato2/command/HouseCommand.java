package com.aurorion.ato2.command;

import com.aurorion.ato2.AurorionAto2;
import com.aurorion.ato2.house.House;
import com.aurorion.ato2.house.HouseCatalog;
import com.aurorion.ato2.house.HouseManager;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
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

/**
 * {@code /casa} — mostra a sua casa. Para o jogador comum, e so isso: quem escolhe e o altar, nao o
 * comando. O ritual seria decorativo se desse para pular ele digitando.
 *
 * <p>O resto e ferramenta de staff (nivel 2): {@code listar}, {@code ver}, {@code definir} e
 * {@code limpar}. Usam {@link GameProfileArgument} em vez de seletor de entidade porque casa e
 * gravada por UUID — precisa dar para consertar a de quem esta offline.
 */
@EventBusSubscriber(modid = AurorionAto2.MOD_ID)
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
                                .executes(HouseCommand::clear))));
    }

    private static int showSelf(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        MinecraftServer server = context.getSource().getServer();

        ResourceLocation id = HouseManager.houseIdOf(server, player.getUUID());
        if (id == null) {
            context.getSource().sendSuccess(() -> Component.translatable("commands.aurorion_ato2.casa.none"), false);
            return 0;
        }

        House house = HouseCatalog.get(id);
        if (house == null) {
            context.getSource().sendFailure(Component.translatable("commands.aurorion_ato2.casa.missing", id.toString()));
            return 0;
        }

        context.getSource().sendSuccess(() ->
                Component.translatable("commands.aurorion_ato2.casa.self", house.coloredName()), false);
        return 1;
    }

    private static int list(CommandContext<CommandSourceStack> context) {
        MinecraftServer server = context.getSource().getServer();

        if (HouseCatalog.isEmpty()) {
            context.getSource().sendFailure(Component.translatable("commands.aurorion_ato2.casa.empty"));
            return 0;
        }

        for (House house : HouseCatalog.all()) {
            int members = HouseManager.membersOf(server, house.id());
            Component line = house.hasCapacity()
                    ? Component.translatable("commands.aurorion_ato2.casa.list.entry.capped",
                    house.coloredName(), house.id().toString(), members, house.capacity())
                    : Component.translatable("commands.aurorion_ato2.casa.list.entry",
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
                line = Component.translatable("commands.aurorion_ato2.casa.view.none", profile.getName());
            } else if (house == null) {
                line = Component.translatable("commands.aurorion_ato2.casa.view.missing", profile.getName(), id.toString());
            } else {
                line = Component.translatable("commands.aurorion_ato2.casa.view.house", profile.getName(), house.coloredName());
                found++;
            }
            context.getSource().sendSuccess(() -> line, false);
        }
        return found;
    }

    private static int set(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        MinecraftServer server = context.getSource().getServer();
        ResourceLocation houseId = ResourceLocationArgument.getId(context, "casa");

        House house = HouseCatalog.get(houseId);
        if (house == null) {
            context.getSource().sendFailure(
                    Component.translatable("commands.aurorion_ato2.casa.unknown", houseId.toString()));
            return 0;
        }

        Collection<GameProfile> profiles = GameProfileArgument.getGameProfiles(context, "jogador");
        for (GameProfile profile : profiles) {
            HouseManager.assign(server, profile.getId(), houseId);
            context.getSource().sendSuccess(() -> Component.translatable(
                    "commands.aurorion_ato2.casa.set", profile.getName(), house.coloredName()), true);
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
                        "commands.aurorion_ato2.casa.clear", profile.getName()), true);
            } else {
                context.getSource().sendFailure(Component.translatable(
                        "commands.aurorion_ato2.casa.clear.none", profile.getName()));
            }
        }
        return changed;
    }
}
