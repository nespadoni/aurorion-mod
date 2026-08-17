package com.aurorion.vidas.command;

import com.aurorion.vidas.AurorionVidas;
import com.aurorion.vidas.lives.LivesManager;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.Collection;

/**
 * {@code /vidas} — quantas vidas voce ainda tem. Para o jogador comum e so isso.
 *
 * <p>O resto e staff (nivel 2). {@code dar} e a saida do exilio: e o mesmo caminho que o item de
 * resgate vai usar quando existir, entao ele ja e o comportamento de referencia.
 *
 * <p>Usa {@link GameProfileArgument} e nao seletor de entidade porque vida e gravada por UUID —
 * precisa dar para acertar a de quem esta offline.
 */
@EventBusSubscriber(modid = AurorionVidas.MOD_ID)
public final class VidasCommand {
    private static final int STAFF_LEVEL = 2;

    private VidasCommand() {
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("vidas")
                .executes(VidasCommand::showSelf)
                .then(Commands.literal("ver")
                        .requires(source -> source.hasPermission(STAFF_LEVEL))
                        .then(Commands.argument("jogador", GameProfileArgument.gameProfile())
                                .executes(VidasCommand::view)))
                .then(Commands.literal("definir")
                        .requires(source -> source.hasPermission(STAFF_LEVEL))
                        .then(Commands.argument("jogador", GameProfileArgument.gameProfile())
                                .then(Commands.argument("quantidade", IntegerArgumentType.integer(0, 20))
                                        .executes(context -> apply(context, true,
                                                IntegerArgumentType.getInteger(context, "quantidade"))))))
                .then(Commands.literal("dar")
                        .requires(source -> source.hasPermission(STAFF_LEVEL))
                        .then(Commands.argument("jogador", GameProfileArgument.gameProfile())
                                .executes(context -> apply(context, false, 1))
                                .then(Commands.argument("quantidade", IntegerArgumentType.integer(1, 20))
                                        .executes(context -> apply(context, false,
                                                IntegerArgumentType.getInteger(context, "quantidade"))))))
                .then(Commands.literal("tirar")
                        .requires(source -> source.hasPermission(STAFF_LEVEL))
                        .then(Commands.argument("jogador", GameProfileArgument.gameProfile())
                                .executes(context -> apply(context, false, -1))
                                .then(Commands.argument("quantidade", IntegerArgumentType.integer(1, 20))
                                        .executes(context -> apply(context, false,
                                                -IntegerArgumentType.getInteger(context, "quantidade"))))))
                .then(Commands.literal("exilio")
                        .requires(source -> source.hasPermission(STAFF_LEVEL))
                        .then(Commands.literal("aqui")
                                .executes(VidasCommand::setExileHere))
                        .then(Commands.literal("ver")
                                .executes(VidasCommand::showExile))));
    }

    private static int showSelf(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        int lives = LivesManager.livesOf(player.server, player.getUUID());

        Component message = lives > 0
                ? Component.translatable("commands.aurorion_vidas.self", lives, LivesManager.maxLives())
                : Component.translatable("commands.aurorion_vidas.self.exilado");

        context.getSource().sendSuccess(() -> message, false);
        return lives;
    }

    private static int view(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        MinecraftServer server = context.getSource().getServer();
        int total = 0;

        for (GameProfile profile : GameProfileArgument.getGameProfiles(context, "jogador")) {
            int lives = LivesManager.livesOf(server, profile.getId());
            total += lives;

            context.getSource().sendSuccess(() -> Component.translatable(
                    "commands.aurorion_vidas.view", profile.getName(), lives, LivesManager.maxLives()), false);
        }
        return total;
    }

    /** @param absolute true para {@code definir} (valor final), false para {@code dar}/{@code tirar} (delta). */
    private static int apply(CommandContext<CommandSourceStack> context, boolean absolute, int amount)
            throws CommandSyntaxException {
        MinecraftServer server = context.getSource().getServer();
        Collection<GameProfile> profiles = GameProfileArgument.getGameProfiles(context, "jogador");

        for (GameProfile profile : profiles) {
            int result = absolute
                    ? LivesManager.setLives(server, profile.getId(), amount)
                    : LivesManager.addLives(server, profile.getId(), amount);

            context.getSource().sendSuccess(() -> Component.translatable(
                    "commands.aurorion_vidas.definido", profile.getName(), result, LivesManager.maxLives()), true);
        }
        return profiles.size();
    }

    private static int setExileHere(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ServerLevel level = player.serverLevel();
        BlockPos pos = player.blockPosition();

        if (level.dimension() != LivesManager.exileDimension()) {
            context.getSource().sendFailure(Component.translatable("commands.aurorion_vidas.exilio.dimensao_errada",
                    LivesManager.exileDimension().location().toString()));
            return 0;
        }

        LivesManager.setExileSpot(level, pos);
        context.getSource().sendSuccess(() -> Component.translatable("commands.aurorion_vidas.exilio.definido",
                pos.getX(), pos.getY(), pos.getZ()), true);
        return 1;
    }

    private static int showExile(CommandContext<CommandSourceStack> context) {
        BlockPos pos = LivesManager.exileSpot(context.getSource().getServer());
        String dimension = LivesManager.exileDimension().location().toString();

        if (pos == null) {
            context.getSource().sendSuccess(() -> Component.translatable(
                    "commands.aurorion_vidas.exilio.automatico", dimension), false);
            return 0;
        }

        context.getSource().sendSuccess(() -> Component.translatable("commands.aurorion_vidas.exilio.atual",
                dimension, pos.getX(), pos.getY(), pos.getZ()), false);
        return 1;
    }
}
