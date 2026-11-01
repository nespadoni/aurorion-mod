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
 * /aurorion no desativar &lt;1|2|3&gt;            apaga o desse no mais perto de voce
 * /aurorion no desativar id &lt;id&gt;            apaga um pelo numero do listar
 * /aurorion no desativar todos
 * /aurorion no listar
 * </pre>
 *
 * <p>O mesmo no pode estar ligado em varios lugares ao mesmo tempo: cada professor libera o da sua aula
 * e desliga o seu sem mexer no dos outros. A cena fica ate ser desativada, reinicio do servidor
 * inclusive. Liberar de novo no mesmo ponto nao duplica: toca a abertura de novo.
 */
@EventBusSubscriber(modid = AurorionMagia.MOD_ID)
public final class NodeCommand {
    private static final int STAFF_LEVEL = 2;
    private static final String ARG_NODE = "no";
    private static final String ARG_POS = "posicao";
    private static final String ARG_ID = "id";

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
                                .then(Commands.literal("id")
                                        .then(Commands.argument(ARG_ID, IntegerArgumentType.integer(1))
                                                .executes(NodeCommand::endById)))
                                .then(Commands.argument(ARG_NODE, IntegerArgumentType.integer(1, Node.COUNT))
                                        .executes(NodeCommand::endNearest)))
                        .then(Commands.literal("listar").executes(NodeCommand::list))));
    }

    private static int release(CommandContext<CommandSourceStack> context, boolean explicit) {
        CommandSourceStack source = context.getSource();
        int index = IntegerArgumentType.getInteger(context, ARG_NODE);
        // Sem posicao: o centro do bloco em que a staff pisa, e o circulo nasce alinhado ao chao.
        Vec3 pos = explicit ? Vec3Argument.getVec3(context, ARG_POS)
                : Vec3.atBottomCenterOf(BlockPos.containing(source.getPosition()));
        Node node = Nodes.release(source.getServer(), index, source.getLevel(), pos);
        if (node == null) {
            source.sendFailure(Component.translatable("commands.aurorion_magia.nos_cheio", Node.MAX_ACTIVE));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("commands.aurorion_magia.no_liberado", index,
                BlockPos.containing(pos).toShortString(), node.id()), true);
        return 1;
    }

    private static int endNearest(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        int index = IntegerArgumentType.getInteger(context, ARG_NODE);
        Node node = Nodes.nearest(source.getServer(), index, source.getLevel(), source.getPosition());
        if (node == null || !Nodes.end(source.getServer(), node.id())) {
            source.sendFailure(Component.translatable("commands.aurorion_magia.no_inativo", index));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("commands.aurorion_magia.no_desativado", index, node.id(),
                BlockPos.containing(node.pos()).toShortString()), true);
        return 1;
    }

    private static int endById(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        int id = IntegerArgumentType.getInteger(context, ARG_ID);
        Node node = NodeData.get(source.getServer()).get(id);
        if (node == null || !Nodes.end(source.getServer(), id)) {
            source.sendFailure(Component.translatable("commands.aurorion_magia.no_id_inativo", id));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("commands.aurorion_magia.no_desativado", node.index(), id,
                BlockPos.containing(node.pos()).toShortString()), true);
        return 1;
    }

    private static int endAll(CommandContext<CommandSourceStack> context) {
        int ended = Nodes.endAll(context.getSource().getServer());
        context.getSource().sendSuccess(() -> Component.translatable("commands.aurorion_magia.nos_desativados", ended), true);
        return ended;
    }

    private static int list(CommandContext<CommandSourceStack> context) {
        var nodes = NodeData.get(context.getSource().getServer()).all();
        if (nodes.isEmpty()) {
            context.getSource().sendSuccess(() -> Component.translatable("commands.aurorion_magia.nos_nenhum"), false);
            return 0;
        }
        for (Node node : nodes) {
            context.getSource().sendSuccess(() -> Component.translatable("commands.aurorion_magia.no_linha", node.id(),
                    node.index(), node.dimension().location().toString(), BlockPos.containing(node.pos()).toShortString()), false);
        }
        return nodes.size();
    }
}
