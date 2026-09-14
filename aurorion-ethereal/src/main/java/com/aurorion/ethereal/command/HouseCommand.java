package com.aurorion.ethereal.command;

import com.aurorion.core.text.TimeFormat;
import com.aurorion.ethereal.AurorionEthereal;
import com.aurorion.ethereal.ceremony.CeremonyData;
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
import net.minecraft.commands.arguments.EntityArgument;
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
import java.util.UUID;

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
                        .then(Commands.literal("iniciar")
                                .then(Commands.argument("jogador", EntityArgument.player())
                                        .executes(HouseCommand::ceremonyStart)))
                        .then(Commands.literal("confirmar")
                                .then(Commands.argument("jogador", GameProfileArgument.gameProfile())
                                        .then(Commands.argument("casa", ResourceLocationArgument.id())
                                                .suggests(HOUSE_SUGGESTIONS)
                                                .executes(HouseCommand::ceremonyConfirm))))
                        .then(Commands.literal("ver")
                                .then(Commands.argument("jogador", GameProfileArgument.gameProfile())
                                        .executes(HouseCommand::ceremonyView)))
                        .then(Commands.literal("cancelar")
                                .then(Commands.argument("jogador", GameProfileArgument.gameProfile())
                                        .executes(HouseCommand::ceremonyCancel)))
                        .then(Commands.literal("pendentes")
                                .executes(HouseCommand::ceremonyPending))));
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
     * Comeca uma cerimonia conduzida: quem digitou passa a receber cada resposta ao vivo e o veredito
     * no fim. E o caminho do evento ao vivo; o do dia a dia e o jogador clicar no altar sozinho.
     */
    private static int ceremonyStart(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(context, "jogador");
        ServerPlayer conductor = context.getSource().getPlayer();

        Component failure = switch (CeremonyManager.start(target, conductor)) {
            case OK -> null;
            case NO_QUESTIONS -> Component.translatable("commands.aurorion_ethereal.cerimonia.no_questions");
            case NO_HOUSES -> Component.translatable("commands.aurorion_ethereal.casa.empty");
            case ALREADY_RUNNING -> Component.translatable("commands.aurorion_ethereal.cerimonia.already_running", target.getGameProfile().getName());
            case AWAITING_VERDICT -> Component.translatable("commands.aurorion_ethereal.cerimonia.awaiting", target.getGameProfile().getName());
            case ALREADY_BOUND -> Component.translatable("commands.aurorion_ethereal.cerimonia.already_bound", target.getGameProfile().getName());
        };

        if (failure != null) {
            context.getSource().sendFailure(failure);
            return 0;
        }

        context.getSource().sendSuccess(() -> Component.translatable(
                "commands.aurorion_ethereal.cerimonia.started", target.getGameProfile().getName()), true);
        return 1;
    }

    private static int ceremonyConfirm(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        MinecraftServer server = context.getSource().getServer();
        ResourceLocation houseId = ResourceLocationArgument.getId(context, "casa");
        Collection<GameProfile> profiles = GameProfileArgument.getGameProfiles(context, "jogador");
        int confirmed = 0;

        for (GameProfile profile : profiles) {
            switch (CeremonyManager.confirm(server, profile.getId(), houseId)) {
                case OK -> {
                    confirmed++;
                    House house = HouseCatalog.get(houseId);
                    context.getSource().sendSuccess(() -> Component.translatable(
                            "commands.aurorion_ethereal.cerimonia.confirmed",
                            profile.getName(),
                            house == null ? Component.literal(houseId.toString()) : house.coloredName()), true);
                }
                case NO_VERDICT -> context.getSource().sendFailure(Component.translatable(
                        "commands.aurorion_ethereal.cerimonia.no_verdict", profile.getName()));
                case UNKNOWN_HOUSE -> context.getSource().sendFailure(Component.translatable(
                        "commands.aurorion_ethereal.casa.unknown", houseId.toString()));
            }
        }
        return confirmed;
    }

    /** Reenvia o veredito completo, com os botoes — para quem entrou depois de a cerimonia terminar. */
    private static int ceremonyView(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        MinecraftServer server = context.getSource().getServer();
        int shown = 0;

        for (GameProfile profile : GameProfileArgument.getGameProfiles(context, "jogador")) {
            boolean sent = CeremonyManager.sendVerdict(server,
                    line -> context.getSource().sendSuccess(() -> line, false), profile.getId());
            if (sent) {
                shown++;
            } else {
                context.getSource().sendFailure(Component.translatable(
                        "commands.aurorion_ethereal.cerimonia.no_verdict", profile.getName()));
            }
        }
        return shown;
    }

    private static int ceremonyCancel(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        MinecraftServer server = context.getSource().getServer();
        int cancelled = 0;

        for (GameProfile profile : GameProfileArgument.getGameProfiles(context, "jogador")) {
            if (CeremonyManager.cancel(server, profile.getId())) {
                cancelled++;
                context.getSource().sendSuccess(() -> Component.translatable(
                        "commands.aurorion_ethereal.cerimonia.cancelled", profile.getName()), true);
            } else {
                context.getSource().sendFailure(Component.translatable(
                        "commands.aurorion_ethereal.cerimonia.nothing", profile.getName()));
            }
        }
        return cancelled;
    }

    private static int ceremonyPending(CommandContext<CommandSourceStack> context) {
        MinecraftServer server = context.getSource().getServer();
        var pending = CeremonyManager.pending(server);

        if (pending.isEmpty()) {
            context.getSource().sendSuccess(() ->
                    Component.translatable("commands.aurorion_ethereal.cerimonia.pending.empty"), false);
            return 0;
        }

        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, CeremonyData.Verdict> entry : pending) {
            CeremonyData.Verdict verdict = entry.getValue();
            House suggested = verdict.suggested() == null ? null : HouseCatalog.get(verdict.suggested());

            context.getSource().sendSuccess(() -> Component.translatable(
                    "commands.aurorion_ethereal.cerimonia.pending.entry",
                    verdict.playerName(),
                    suggested == null ? Component.translatable("commands.aurorion_ethereal.cerimonia.pending.no_suggestion")
                            : suggested.coloredName(),
                    TimeFormat.duration(Math.max(0, now - verdict.finishedAt()))), false);
        }
        return pending.size();
    }
}
