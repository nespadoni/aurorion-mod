package com.aurorion.profissoes.npc;

import com.aurorion.profissoes.AurorionProfissoes;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.jetbrains.annotations.Nullable;
import java.util.Comparator;
import java.util.List;

/**
 * Comandos da staff, nivel 2:
 *
 * <pre>
 * /npc criar &lt;id&gt;          invoca o NPC do JSON na sua posicao, olhando para onde voce olha
 * /npc definir &lt;id&gt;        troca o id do NPC mais proximo (ate 4 blocos)
 * /npc remover             remove o NPC mais proximo (ate 4 blocos)
 * /npc listar              ids carregados e avisos do arquivo
 * /npc recarregar          le config/aurorion/npcs.json de novo e atualiza os NPCs carregados
 * /npc estoque &lt;id&gt; repor  repoe o estoque das ofertas limitadas de um NPC
 * </pre>
 */
@EventBusSubscriber(modid = AurorionProfissoes.MOD_ID)
public final class NpcCommand {
    private static final SuggestionProvider<CommandSourceStack> IDS =
            (context, builder) -> SharedSuggestionProvider.suggest(NpcCatalog.ids(), builder);
    private NpcCommand() {}

    @SubscribeEvent public static void register(RegisterCommandsEvent event) {
        var root = Commands.literal("npc").requires(source -> source.hasPermission(2));
        root.then(Commands.literal("criar").then(Commands.argument("id", StringArgumentType.word()).suggests(IDS)
                .executes(NpcCommand::create)));
        root.then(Commands.literal("definir").then(Commands.argument("id", StringArgumentType.word()).suggests(IDS)
                .executes(NpcCommand::define)));
        root.then(Commands.literal("remover").executes(NpcCommand::remove));
        root.then(Commands.literal("listar").executes(NpcCommand::list));
        root.then(Commands.literal("recarregar").executes(NpcCommand::reload));
        root.then(Commands.literal("estoque").then(Commands.argument("id", StringArgumentType.word()).suggests(IDS)
                .then(Commands.literal("repor").executes(NpcCommand::restock))));
        event.getDispatcher().register(root);
    }

    private static int create(CommandContext<CommandSourceStack> context) {
        var source = context.getSource();
        String id = StringArgumentType.getString(context, "id");
        if (NpcCatalog.get(id) == null) { unknown(source, id); return 0; }
        var npc = NpcEntities.NPC.get().create(source.getLevel());
        if (npc == null) return 0;
        var pos = source.getPosition();
        float yaw = source.getRotation().y;
        npc.moveTo(pos.x, pos.y, pos.z, yaw, 0.0F);
        npc.setYHeadRot(yaw);
        npc.setYBodyRot(yaw);
        npc.setNpcId(id);
        source.getLevel().addFreshEntity(npc);
        source.sendSuccess(() -> Component.literal("NPC \"" + id + "\" criado."), true);
        return 1;
    }

    private static int define(CommandContext<CommandSourceStack> context) {
        var source = context.getSource();
        String id = StringArgumentType.getString(context, "id");
        if (NpcCatalog.get(id) == null) { unknown(source, id); return 0; }
        var npc = nearest(source);
        if (npc == null) { source.sendFailure(Component.literal("Nenhum NPC a até 4 blocos.")); return 0; }
        npc.setNpcId(id);
        source.sendSuccess(() -> Component.literal("NPC agora é \"" + id + "\"."), true);
        return 1;
    }

    private static int remove(CommandContext<CommandSourceStack> context) {
        var source = context.getSource();
        var npc = nearest(source);
        if (npc == null) { source.sendFailure(Component.literal("Nenhum NPC a até 4 blocos.")); return 0; }
        String id = npc.npcId();
        npc.discard();
        source.sendSuccess(() -> Component.literal("NPC \"" + id + "\" removido."), true);
        return 1;
    }

    private static int list(CommandContext<CommandSourceStack> context) {
        var source = context.getSource();
        var ids = List.copyOf(NpcCatalog.ids());
        source.sendSuccess(() -> Component.literal(ids.isEmpty() ? "Nenhum NPC configurado em " + NpcCatalog.file()
                : "NPCs (" + ids.size() + "): " + String.join(", ", ids)), false);
        reportErrors(source, NpcCatalog.errors());
        return ids.size();
    }

    private static int reload(CommandContext<CommandSourceStack> context) {
        var source = context.getSource();
        var errors = NpcCatalog.reload(source.getServer());
        int bodies = NpcEvents.refreshAll(source.getServer());
        source.sendSuccess(() -> Component.literal("NPCs recarregados: " + NpcCatalog.ids().size()
                + " configurado(s), " + bodies + " atualizado(s) no mundo."), true);
        reportErrors(source, errors);
        return NpcCatalog.ids().size();
    }

    private static int restock(CommandContext<CommandSourceStack> context) {
        var source = context.getSource();
        String id = StringArgumentType.getString(context, "id");
        int reset = NpcStockData.get(source.getServer()).reset(id);
        source.sendSuccess(() -> Component.literal("Estoque de \"" + id + "\" reposto (" + reset + " oferta(s))."), true);
        return 1;
    }

    private static @Nullable ProfessionNpcEntity nearest(CommandSourceStack source) {
        var pos = source.getPosition();
        return source.getLevel().getEntitiesOfClass(ProfessionNpcEntity.class, AABB.ofSize(pos, 8, 8, 8),
                        npc -> npc.distanceToSqr(pos) <= 16)
                .stream().min(Comparator.comparingDouble(npc -> npc.distanceToSqr(pos))).orElse(null);
    }

    private static void unknown(CommandSourceStack source, String id) {
        source.sendFailure(Component.literal("NPC \"" + id + "\" não existe em " + NpcCatalog.file()
                + ". Use /npc recarregar depois de editar."));
    }

    private static void reportErrors(CommandSourceStack source, List<String> errors) {
        if (errors.isEmpty()) return;
        source.sendFailure(Component.literal(errors.size() + " aviso(s) no arquivo de NPCs:"));
        errors.stream().limit(8).forEach(error -> source.sendFailure(Component.literal("• " + error)));
        if (errors.size() > 8) source.sendFailure(Component.literal("… o restante está no log do servidor."));
    }
}
