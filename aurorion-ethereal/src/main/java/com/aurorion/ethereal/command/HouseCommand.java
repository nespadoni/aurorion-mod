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
 * {@code /casa} — mostra a sua casa. Para o jogador comum, e so isso: quem vincula e a cerimonia no
 * altar, nao o comando. O ritual seria decorativo se desse para pular ele digitando.
 *
 * <p>O resto e ferramenta de staff (nivel 2): {@code listar}, {@code ver}, {@code definir},
 * {@code limpar} e a arvore {@code cerimonia}. Usam {@link GameProfileArgument} em vez de seletor de
 * entidade porque casa e gravada por UUID — e o caso que mais importa, confirmar o veredito de quem
 * respondeu ontem e ja deslogou, e justamente o de jogador offline.
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
                        .then(Commands.argument("jogador", GameProfileArgument.gameProfile())
                                .then(Commands.argument("casa", ResourceLocationArgument.id())
                                        .suggests(HOUSE_SUGGESTIONS)
                                        .executes(HouseCommand::ceremony)))));
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

    /**
     * Define a casa e toca o Rito de Vinculacao.
     *
     * <p>Substituiu {@code iniciar}, {@code confirmar}, {@code ver} e {@code pendentes}. Aqueles
     * quatro existiam para conduzir um questionario e decidir em cima do resultado dele; sem as
     * perguntas, sobrou o que a staff sempre quis fazer numa linha so — dizer de qual casa a pessoa e.
     *
     * <p>A diferenca para {@code /casa definir} e a cena: {@code definir} grava em silencio, util
     * para corrigir engano; {@code cerimonia} grava e apresenta.
     */
    private static int ceremony(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        MinecraftServer server = context.getSource().getServer();
        ResourceLocation houseId = ResourceLocationArgument.getId(context, "casa");
        Collection<GameProfile> profiles = GameProfileArgument.getGameProfiles(context, "jogador");
        int bound = 0;

        for (GameProfile profile : profiles) {
            switch (CeremonyManager.bind(server, profile.getId(), houseId)) {
                case OK, QUEUED -> bound++;
                case UNKNOWN_HOUSE -> context.getSource().sendFailure(Component.translatable(
                        "commands.aurorion_ethereal.casa.unknown", houseId.toString()));
                case ALREADY_RUNNING -> context.getSource().sendFailure(Component.translatable(
                        "commands.aurorion_ethereal.cerimonia.already_running", profile.getName()));
            }
        }

        if (bound == 0) return 0;

        House house = HouseCatalog.get(houseId);
        int total = bound;
        context.getSource().sendSuccess(() -> Component.translatable(
                "commands.aurorion_ethereal.cerimonia.bound", total,
                house == null ? Component.literal(houseId.toString()) : house.coloredName()), true);
        return bound;
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
