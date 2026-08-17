package com.aurorion.portais.command;

import com.aurorion.portais.AurorionPortais;
import com.aurorion.portais.line.LineCatalog;
import com.aurorion.portais.line.Schedule;
import com.aurorion.portais.line.TransitLine;
import com.aurorion.portais.pass.PassData;
import com.aurorion.portais.pass.TransitPass;
import com.aurorion.portais.runtime.LineClock;
import com.aurorion.core.text.TimeFormat;
import com.aurorion.portais.runtime.TransitClock;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.DimensionArgument;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.Collection;
import java.util.List;

/**
 * {@code /portais} — o quadro de horarios da estacao. Para o jogador comum e so isso, e de
 * proposito: o comando informa, nunca transporta. Quem atravessa e o portal, no horario.
 *
 * <p>O resto e ferramenta de staff (nivel 2): abrir/fechar uma linha na mao para um evento, e
 * conceder passe individual. O passe usa {@link GameProfileArgument} em vez de seletor de entidade
 * porque e gravado por UUID — precisa dar para preparar o resgate de alguem que esta offline.
 */
@EventBusSubscriber(modid = AurorionPortais.MOD_ID)
public final class PortaisCommand {
    private static final int STAFF_LEVEL = 2;

    private static final SuggestionProvider<CommandSourceStack> LINE_SUGGESTIONS =
            (context, builder) -> SharedSuggestionProvider.suggestResource(LineCatalog.ids(), builder);

    private PortaisCommand() {
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("portais")
                .executes(PortaisCommand::timetable)
                .then(Commands.literal("horarios")
                        .executes(PortaisCommand::timetable))
                .then(Commands.literal("abrir")
                        .requires(source -> source.hasPermission(STAFF_LEVEL))
                        .then(Commands.argument("linha", ResourceLocationArgument.id())
                                .suggests(LINE_SUGGESTIONS)
                                .executes(context -> open(context, -1))
                                .then(Commands.argument("minutos", IntegerArgumentType.integer(1, 1440))
                                        .executes(context -> open(context, IntegerArgumentType.getInteger(context, "minutos"))))))
                .then(Commands.literal("fechar")
                        .requires(source -> source.hasPermission(STAFF_LEVEL))
                        .then(Commands.argument("linha", ResourceLocationArgument.id())
                                .suggests(LINE_SUGGESTIONS)
                                .executes(PortaisCommand::close)))
                .then(Commands.literal("passe")
                        .requires(source -> source.hasPermission(STAFF_LEVEL))
                        .then(Commands.literal("dar")
                                .then(Commands.argument("jogador", GameProfileArgument.gameProfile())
                                        .then(Commands.argument("dimensao", DimensionArgument.dimension())
                                                .executes(context -> grant(context, 0, 1))
                                                .then(Commands.argument("usos", IntegerArgumentType.integer(1, 999))
                                                        .executes(context -> grant(context, 0, IntegerArgumentType.getInteger(context, "usos")))
                                                        .then(Commands.argument("minutos", IntegerArgumentType.integer(1, 60 * 24 * 365))
                                                                .executes(context -> grant(context,
                                                                        IntegerArgumentType.getInteger(context, "minutos"),
                                                                        IntegerArgumentType.getInteger(context, "usos"))))))))
                        .then(Commands.literal("ver")
                                .then(Commands.argument("jogador", GameProfileArgument.gameProfile())
                                        .executes(PortaisCommand::listPasses)))
                        .then(Commands.literal("tirar")
                                .then(Commands.argument("jogador", GameProfileArgument.gameProfile())
                                        .executes(context -> revoke(context, null))
                                        .then(Commands.argument("dimensao", DimensionArgument.dimension())
                                                .executes(context -> revoke(context,
                                                        DimensionArgument.getDimension(context, "dimensao").dimension())))))));
    }

    // --- Quadro de horarios --------------------------------------------------------------------

    private static int timetable(CommandContext<CommandSourceStack> context) {
        if (TransitClock.isEmpty()) {
            context.getSource().sendFailure(Component.translatable("commands.aurorion_portais.vazio"));
            return 0;
        }

        long now = System.currentTimeMillis();
        context.getSource().sendSuccess(
                () -> Component.translatable("commands.aurorion_portais.cabecalho").withStyle(ChatFormatting.GOLD), false);

        List<LineClock> clocks = TransitClock.all();
        for (int i = 0; i < clocks.size(); i++) {
            Component line = describe(clocks.get(i), now);
            context.getSource().sendSuccess(() -> line, false);
        }
        return clocks.size();
    }

    private static Component describe(LineClock clock, long now) {
        TransitLine line = clock.line();

        if (clock.isOpen()) {
            return Component.translatable("commands.aurorion_portais.linha.aberta",
                    line.coloredName(), TimeFormat.duration(clock.closesAt() - now)).withStyle(ChatFormatting.GREEN);
        }

        long next = clock.nextDeparture();
        if (next == Schedule.NEVER) {
            return Component.translatable("commands.aurorion_portais.linha.sem_horario", line.coloredName())
                    .withStyle(ChatFormatting.DARK_GRAY);
        }

        Component when = Component.translatable("commands.aurorion_portais.linha.fechada",
                line.coloredName(),
                TimeFormat.clockTime(next, line.schedule().zone()),
                TimeFormat.duration(next - now)).withStyle(ChatFormatting.GRAY);

        return line.station()
                .map(station -> when.copy().append(" ").append(
                        Component.translatable("commands.aurorion_portais.linha.estacao", station.label())
                                .withStyle(ChatFormatting.DARK_GRAY)))
                .orElse(when.copy());
    }

    // --- Abertura e fechamento manual ----------------------------------------------------------

    private static int open(CommandContext<CommandSourceStack> context, int minutes) {
        LineClock clock = requireLine(context);
        if (clock == null) return 0;

        int duration = minutes > 0 ? minutes : clock.line().schedule().openMinutes();
        clock.forceOpen(context.getSource().getServer(), System.currentTimeMillis(), duration);
        TransitClock.refreshAnyOpen();

        context.getSource().sendSuccess(() -> Component.translatable("commands.aurorion_portais.abrir",
                clock.line().coloredName(), duration), true);
        return 1;
    }

    private static int close(CommandContext<CommandSourceStack> context) {
        LineClock clock = requireLine(context);
        if (clock == null) return 0;

        if (!clock.forceClose(context.getSource().getServer(), System.currentTimeMillis())) {
            context.getSource().sendFailure(Component.translatable("commands.aurorion_portais.fechar.ja_fechada",
                    clock.line().coloredName()));
            return 0;
        }
        TransitClock.refreshAnyOpen();

        context.getSource().sendSuccess(() -> Component.translatable("commands.aurorion_portais.fechar",
                clock.line().coloredName()), true);
        return 1;
    }

    private static LineClock requireLine(CommandContext<CommandSourceStack> context) {
        ResourceLocation id = ResourceLocationArgument.getId(context, "linha");
        LineClock clock = TransitClock.forLine(id);

        if (clock == null) {
            context.getSource().sendFailure(
                    Component.translatable("commands.aurorion_portais.linha.desconhecida", id.toString()));
        }
        return clock;
    }

    // --- Passes --------------------------------------------------------------------------------

    private static int grant(CommandContext<CommandSourceStack> context, int minutes, int uses) throws CommandSyntaxException {
        MinecraftServer server = context.getSource().getServer();
        ResourceKey<Level> dimension = DimensionArgument.getDimension(context, "dimensao").dimension();
        long expiresAt = minutes > 0 ? System.currentTimeMillis() + minutes * 60_000L : TransitPass.NO_EXPIRY;

        Collection<GameProfile> profiles = GameProfileArgument.getGameProfiles(context, "jogador");
        PassData data = PassData.get(server);

        for (GameProfile profile : profiles) {
            data.grant(profile.getId(), new TransitPass(dimension, expiresAt, uses));
            context.getSource().sendSuccess(() -> Component.translatable("commands.aurorion_portais.passe.dado",
                    profile.getName(), dimension.location().toString(), uses), true);
        }
        return profiles.size();
    }

    private static int listPasses(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        MinecraftServer server = context.getSource().getServer();
        long now = System.currentTimeMillis();
        PassData data = PassData.get(server);
        int total = 0;

        for (GameProfile profile : GameProfileArgument.getGameProfiles(context, "jogador")) {
            List<TransitPass> passes = data.list(profile.getId(), now);
            if (passes.isEmpty()) {
                context.getSource().sendSuccess(() -> Component.translatable(
                        "commands.aurorion_portais.passe.nenhum", profile.getName()), false);
                continue;
            }

            for (TransitPass pass : passes) {
                Component detail = pass.expiresByTime()
                        ? Component.translatable("commands.aurorion_portais.passe.entrada.temporario",
                        profile.getName(), pass.dimension().location().toString(),
                        describeUses(pass), TimeFormat.duration(pass.expiresAt() - now))
                        : Component.translatable("commands.aurorion_portais.passe.entrada",
                        profile.getName(), pass.dimension().location().toString(), describeUses(pass));

                context.getSource().sendSuccess(() -> detail, false);
                total++;
            }
        }
        return total;
    }

    private static Component describeUses(TransitPass pass) {
        return pass.expiresByUse()
                ? Component.translatable("commands.aurorion_portais.passe.usos", pass.uses())
                : Component.translatable("commands.aurorion_portais.passe.usos.ilimitado");
    }

    private static int revoke(CommandContext<CommandSourceStack> context, ResourceKey<Level> dimension) throws CommandSyntaxException {
        MinecraftServer server = context.getSource().getServer();
        PassData data = PassData.get(server);
        int removed = 0;

        for (GameProfile profile : GameProfileArgument.getGameProfiles(context, "jogador")) {
            int count = dimension == null
                    ? data.revokeAll(profile.getId())
                    : (data.revoke(profile.getId(), dimension) ? 1 : 0);

            if (count == 0) {
                context.getSource().sendFailure(Component.translatable(
                        "commands.aurorion_portais.passe.nenhum", profile.getName()));
                continue;
            }
            removed += count;
            context.getSource().sendSuccess(() -> Component.translatable(
                    "commands.aurorion_portais.passe.tirado", profile.getName(), count), true);
        }
        return removed;
    }
}
