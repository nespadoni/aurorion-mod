package com.aurorion.mundos.command;

import com.aurorion.mundos.AurorionMundos;
import com.aurorion.mundos.config.MundosConfig;
import com.aurorion.mundos.portal.DimensionLink;
import com.aurorion.mundos.portal.LinkCatalog;
import com.aurorion.mundos.world.WorldCatalog;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.DimensionArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.border.WorldBorder;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.List;

/**
 * {@code /mundos} — ferramenta de staff. Mostra o estado dos mundos e ajusta a barreira de cada um.
 *
 * <h2>Por que nao basta o {@code /worldborder} do vanilla</h2>
 *
 * <p>Porque ele e meio de cada. {@code /worldborder get} e {@code add} leem
 * {@code source.getLevel()} — a dimensao de quem executou —, mas {@code set}, {@code center},
 * {@code damage} e {@code warning} escrevem em {@code source.getServer().overworld()}. Num servidor
 * de uma barreira so isso nunca aparece; com uma barreira por mundo, significa que metade do comando
 * mente sobre onde esta mexendo.
 *
 * <p>Aqui a dimensao e sempre argumento explicito. Nao ha "a barreira daqui": ha a barreira de um
 * mundo nomeado.
 *
 * <p>O comando recusa dimensao que este mod nao gerencia, em vez de mexer nela. Alterar a barreira do
 * overworld, do Nether ou de uma dimensao de mod continua sendo trabalho do {@code /worldborder} — e
 * assim ninguem descobre por acidente que o comando de um mod mexeu no mundo principal.
 */
@EventBusSubscriber(modid = AurorionMundos.MOD_ID)
public final class MundosCommand {
    private static final int STAFF_LEVEL = 2;

    private static final SimpleCommandExceptionType NOT_MANAGED = new SimpleCommandExceptionType(
            Component.translatable("aurorion_mundos.comando.nao_gerenciada"));

    private MundosCommand() {
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("mundos")
                .requires(source -> source.hasPermission(STAFF_LEVEL))
                .executes(MundosCommand::list)
                .then(Commands.literal("ligacoes")
                        .executes(MundosCommand::links))
                .then(Commands.literal("borda")
                        .then(Commands.argument("dimensao", DimensionArgument.dimension())
                                .then(Commands.literal("ver")
                                        .executes(MundosCommand::show))
                                .then(Commands.literal("set")
                                        .then(Commands.argument("tamanho", DoubleArgumentType.doubleArg(1.0D, 5.9999968E7D))
                                                .executes(context -> setSize(context, 0))
                                                .then(Commands.argument("segundos", IntegerArgumentType.integer(0, 2147483))
                                                        .executes(context -> setSize(context, IntegerArgumentType.getInteger(context, "segundos"))))))
                                .then(Commands.literal("centro")
                                        .then(Commands.argument("x", DoubleArgumentType.doubleArg(-3.0E7D, 3.0E7D))
                                                .then(Commands.argument("z", DoubleArgumentType.doubleArg(-3.0E7D, 3.0E7D))
                                                        .executes(MundosCommand::setCenter))))
                                .then(Commands.literal("aviso")
                                        .then(Commands.literal("distancia")
                                                .then(Commands.argument("blocos", IntegerArgumentType.integer(0, 30000000))
                                                        .executes(MundosCommand::setWarningBlocks)))
                                        .then(Commands.literal("tempo")
                                                .then(Commands.argument("segundos", IntegerArgumentType.integer(0, 2147483))
                                                        .executes(MundosCommand::setWarningTime))))
                                .then(Commands.literal("dano")
                                        .then(Commands.literal("porbloco")
                                                .then(Commands.argument("valor", DoubleArgumentType.doubleArg(0.0D))
                                                        .executes(MundosCommand::setDamage)))
                                        .then(Commands.literal("zona")
                                                .then(Commands.argument("blocos", DoubleArgumentType.doubleArg(0.0D))
                                                        .executes(MundosCommand::setSafeZone)))))));
    }

    // --- Informacao ------------------------------------------------------------------------------

    private static int list(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        List<ResourceKey<Level>> managed = List.copyOf(WorldCatalog.managed());

        if (managed.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("aurorion_mundos.comando.lista.vazio")
                    .withStyle(ChatFormatting.GRAY), false);
            return 0;
        }

        source.sendSuccess(() -> Component.translatable("aurorion_mundos.comando.lista.cabecalho")
                .withStyle(ChatFormatting.GOLD), false);

        for (ResourceKey<Level> dimension : managed) {
            ServerLevel level = source.getServer().getLevel(dimension);
            Long seed = WorldCatalog.seedFor(dimension);
            boolean generation = WorldCatalog.allowsRuntimeGeneration(dimension);

            Component status = level == null
                    ? Component.translatable("aurorion_mundos.comando.lista.ausente").withStyle(ChatFormatting.RED)
                    : Component.literal(String.format("%.0f", level.getWorldBorder().getSize()));

            source.sendSuccess(() -> Component.translatable("aurorion_mundos.comando.lista.linha",
                    Component.literal(dimension.location().toString()).withStyle(ChatFormatting.AQUA),
                    Component.literal(seed == null ? "?" : seed.toString()),
                    status,
                    Component.translatable(generation
                            ? "aurorion_mundos.comando.geracao.ligada"
                            : "aurorion_mundos.comando.geracao.desligada")
                            .withStyle(generation ? ChatFormatting.YELLOW : ChatFormatting.GREEN)), false);
        }

        return managed.size();
    }

    private static int links(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        List<DimensionLink> all = LinkCatalog.all();

        if (all.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("aurorion_mundos.comando.ligacoes.vazio")
                    .withStyle(ChatFormatting.GRAY), false);
            return 0;
        }

        source.sendSuccess(() -> Component.translatable("aurorion_mundos.comando.ligacoes.cabecalho")
                .withStyle(ChatFormatting.GOLD), false);

        for (DimensionLink link : all) {
            Component area = link.area()
                    .map(value -> (Component) Component.literal(
                            " [" + value.centerX() + ", " + value.centerZ() + " r=" + value.radius() + "]")
                            .withStyle(ChatFormatting.DARK_GRAY))
                    .orElse(Component.empty());

            source.sendSuccess(() -> Component.translatable("aurorion_mundos.comando.ligacoes.linha",
                    Component.literal(link.from().location().toString()).withStyle(ChatFormatting.AQUA),
                    Component.literal(link.to().location().toString()).withStyle(ChatFormatting.AQUA),
                    Component.literal(String.valueOf(link.searchRadius().orElseGet(MundosConfig.DEFAULT_SEARCH_RADIUS::get))),
                    area), false);
        }

        return all.size();
    }

    private static int show(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerLevel level = managed(context);
        WorldBorder border = level.getWorldBorder();

        context.getSource().sendSuccess(() -> Component.translatable("aurorion_mundos.comando.borda.ver",
                Component.literal(level.dimension().location().toString()).withStyle(ChatFormatting.AQUA),
                Component.literal(String.format("%.0f", border.getSize())),
                Component.literal(String.format("%.0f, %.0f", border.getCenterX(), border.getCenterZ()))), false);
        return 1;
    }

    // --- Ajustes ---------------------------------------------------------------------------------

    private static int setSize(CommandContext<CommandSourceStack> context, int seconds) throws CommandSyntaxException {
        ServerLevel level = managed(context);
        WorldBorder border = level.getWorldBorder();
        double target = DoubleArgumentType.getDouble(context, "tamanho");

        if (seconds > 0) {
            border.lerpSizeBetween(border.getSize(), target, seconds * 1000L);
            context.getSource().sendSuccess(() -> Component.translatable("aurorion_mundos.comando.borda.lerp",
                    name(level), Component.literal(String.format("%.0f", target)),
                    Component.literal(String.valueOf(seconds))), true);
        } else {
            border.setSize(target);
            context.getSource().sendSuccess(() -> Component.translatable("aurorion_mundos.comando.borda.set",
                    name(level), Component.literal(String.format("%.0f", target))), true);
        }

        return 1;
    }

    private static int setCenter(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerLevel level = managed(context);
        double x = DoubleArgumentType.getDouble(context, "x");
        double z = DoubleArgumentType.getDouble(context, "z");

        level.getWorldBorder().setCenter(x, z);
        context.getSource().sendSuccess(() -> Component.translatable("aurorion_mundos.comando.borda.centro",
                name(level), Component.literal(String.format("%.0f, %.0f", x, z))), true);
        return 1;
    }

    private static int setWarningBlocks(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerLevel level = managed(context);
        int blocks = IntegerArgumentType.getInteger(context, "blocos");

        level.getWorldBorder().setWarningBlocks(blocks);
        context.getSource().sendSuccess(() -> Component.translatable("aurorion_mundos.comando.borda.aviso_distancia",
                name(level), Component.literal(String.valueOf(blocks))), true);
        return 1;
    }

    private static int setWarningTime(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerLevel level = managed(context);
        int seconds = IntegerArgumentType.getInteger(context, "segundos");

        level.getWorldBorder().setWarningTime(seconds);
        context.getSource().sendSuccess(() -> Component.translatable("aurorion_mundos.comando.borda.aviso_tempo",
                name(level), Component.literal(String.valueOf(seconds))), true);
        return 1;
    }

    private static int setDamage(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerLevel level = managed(context);
        double amount = DoubleArgumentType.getDouble(context, "valor");

        level.getWorldBorder().setDamagePerBlock(amount);
        context.getSource().sendSuccess(() -> Component.translatable("aurorion_mundos.comando.borda.dano",
                name(level), Component.literal(String.format("%.2f", amount))), true);
        return 1;
    }

    private static int setSafeZone(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerLevel level = managed(context);
        double blocks = DoubleArgumentType.getDouble(context, "blocos");

        level.getWorldBorder().setDamageSafeZone(blocks);
        context.getSource().sendSuccess(() -> Component.translatable("aurorion_mundos.comando.borda.zona",
                name(level), Component.literal(String.format("%.0f", blocks))), true);
        return 1;
    }

    // --- Apoio -----------------------------------------------------------------------------------

    /**
     * A dimensao do argumento, desde que seja um mundo deste mod.
     *
     * <p>Recusar em vez de obedecer e o ponto: a barreira do overworld nao e nossa, e um comando que
     * a alterasse por descuido derrubaria todo mundo para dentro de um circulo sem aviso.
     */
    private static ServerLevel managed(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerLevel level = DimensionArgument.getDimension(context, "dimensao");
        if (!WorldCatalog.isManaged(level.dimension())) {
            throw NOT_MANAGED.create();
        }
        return level;
    }

    private static Component name(ServerLevel level) {
        return Component.literal(level.dimension().location().toString()).withStyle(ChatFormatting.AQUA);
    }
}
