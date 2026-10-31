package com.aurorion.magia.command;

import com.aurorion.magia.AurorionMagia;
import com.aurorion.magia.node.Node;
import com.aurorion.magia.node.NodeData;
import com.aurorion.magia.node.Nodes;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * {@code /aurorion no} — os tres nos de magia. Staff (nivel 2).
 *
 * <pre>
 * /aurorion no liberar &lt;1|2|3&gt; [posicao]   liga a cena ali (padrao: onde voce esta)
 * /aurorion no desativar &lt;1|2|3&gt;            apaga um
 * /aurorion no desativar todos
 * /aurorion no listar
 * </pre>
 *
 * <p>A cena fica ate ser desativada, reinicio do servidor inclusive. Liberar um no que ja esta ligado
 * o muda de lugar e toca a abertura de novo.
 */
@EventBusSubscriber(modid = AurorionMagia.MOD_ID)
public final class NodeCommand {
    private static final int STAFF_LEVEL = 2;
    private static final String ARG_NODE = "no";
    private static final String ARG_POS = "posicao";

    private NodeCommand() {
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("aurorion")
                .then(Commands.literal("no")
                        .requires(source -> source.hasPermission(STAFF_LEVEL))
                        .then(Commands.literal("liberar")
                                .then(Commands.argument(ARG_NODE, IntegerArgumentType.integer(1, Node.COUNT))
                                        .executes(context -> release(context, false))
                                        .then(Commands.argument(ARG_POS, Vec3Argument.vec3())
                                                .executes(context -> release(context, true)))))
                        .then(Commands.literal("desativar")
                                .then(Commands.literal("todos").executes(NodeCommand::endAll))
                                .then(Commands.argument(ARG_NODE, IntegerArgumentType.integer(1, Node.COUNT))
                                        .executes(NodeCommand::end)))
                        .then(Commands.literal("listar").executes(NodeCommand::list))));
    }

    private static int release(CommandContext<CommandSourceStack> context, boolean explicit) {
        CommandSourceStack source = context.getSource();
        int index = IntegerArgumentType.getInteger(context, ARG_NODE);
        // Sem posicao: o centro do bloco em que a staff pisa, e o circulo nasce alinhado ao chao.
        Vec3 pos = explicit ? Vec3Argument.getVec3(context, ARG_POS)
                : Vec3.atBottomCenterOf(BlockPos.containing(source.getPosition()));
        Nodes.release(source.getServer(), index, source.getLevel(), pos);
        source.sendSuccess(() -> Component.translatable("commands.aurorion_magia.no_liberado", index,
                BlockPos.containing(pos).toShortString()), true);
        return 1;
    }

    private static int end(CommandContext<CommandSourceStack> context) {
        int index = IntegerArgumentType.getInteger(context, ARG_NODE);
        if (!Nodes.end(context.getSource().getServer(), index)) {
            context.getSource().sendFailure(Component.translatable("commands.aurorion_magia.no_inativo", index));
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.translatable("commands.aurorion_magia.no_desativado", index), true);
        return 1;
    }

    private static int endAll(CommandContext<CommandSourceStack> context) {
        int ended = 0;
        for (int index = 1; index <= Node.COUNT; index++) {
            if (Nodes.end(context.getSource().getServer(), index)) ended++;
        }
        int count = ended;
        context.getSource().sendSuccess(() -> Component.translatable("commands.aurorion_magia.nos_desativados", count), true);
        return ended;
    }

    private static int list(CommandContext<CommandSourceStack> context) {
        var nodes = NodeData.get(context.getSource().getServer()).all();
        if (nodes.isEmpty()) {
            context.getSource().sendSuccess(() -> Component.translatable("commands.aurorion_magia.nos_nenhum"), false);
            return 0;
        }
        for (Node node : nodes) {
            context.getSource().sendSuccess(() -> Component.translatable("commands.aurorion_magia.no_linha", node.index(),
                    node.dimension().location().toString(), BlockPos.containing(node.pos()).toShortString()), false);
        }
        return nodes.size();
    }
}
