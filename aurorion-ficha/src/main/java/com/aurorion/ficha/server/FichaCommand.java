package com.aurorion.ficha.server;

import com.aurorion.ficha.AurorionFicha;
import com.aurorion.ficha.config.FichaConfig;
import com.aurorion.ficha.sheet.Catalog;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.List;

/**
 * {@code /ficha}: a alternativa em comando para tudo que a tela faz — e a única via do cliente sem o
 * mod, que recebe as respostas como linhas clicáveis no chat.
 *
 * <pre>
 * /ficha                               abre a ficha (sem o mod: resumo e link do site)
 * /ficha rolar &lt;perícia|atributo&gt; [cd] [secreto]
 * /ficha dados &lt;fórmula&gt;                rolagem livre (2d6+3, 4d6kh3)
 * /ficha pedido [id] [recusar]          responde ao pedido do mestre (sem id: o mais novo)
 * /ficha vincular [limpar | &lt;tipo&gt; &lt;id&gt;]
 * /ficha mestre                        mesa do mestre
 * /ficha mestre pedir &lt;jogadores&gt; &lt;perícia|atributo|fórmula&gt; [cd]
 * /ficha mestre pedir &lt;jogadores&gt; dados &lt;fórmula&gt; [cd]   pedido de dados (rolagem livre: 2d6, 1d20+2)
 * /ficha mestre cancelar &lt;id&gt;          tira o pedido de quem ainda não respondeu
 * /ficha mestre ver &lt;jogador&gt;
 * </pre>
 *
 * <p>{@code /ficha teste} é comando do cliente (tela fictícia, ver {@code client/FichaTestCommand}).
 */
@EventBusSubscriber(modid = AurorionFicha.MOD_ID)
public final class FichaCommand {
    private static final SuggestionProvider<CommandSourceStack> TESTS =
            (context, builder) -> SharedSuggestionProvider.suggest(Catalog.rollTargets(), builder);
    /** Os atalhos de dados da mesa do mestre (o argumento aceita qualquer fórmula do motor). */
    static final List<String> DICE_PRESETS = List.of("1d20", "1d100", "2d6", "1d6", "1d4", "2x1d20");
    private static final SuggestionProvider<CommandSourceStack> DICE =
            (context, builder) -> SharedSuggestionProvider.suggest(DICE_PRESETS, builder);
    /** {@code /ficha mestre pedir}: os testes e, depois deles, os atalhos de dados. */
    private static final SuggestionProvider<CommandSourceStack> ASK_TESTS = (context, builder) -> {
        java.util.List<String> all = new java.util.ArrayList<>(Catalog.rollTargets());
        all.addAll(DICE_PRESETS);
        return SharedSuggestionProvider.suggest(all, builder);
    };
    /** Os pedidos do mestre esperando quem digita, do mais novo ao mais antigo. Sugestão roda na thread do servidor. */
    private static final SuggestionProvider<CommandSourceStack> PENDING = (context, builder) -> {
        ServerPlayer player = context.getSource().getPlayer();
        return SharedSuggestionProvider.suggest(player == null ? List.of() : FichaServer.pendingRequestIds(player), builder);
    };
    /** Os pedidos ainda abertos do mestre que digita. */
    private static final SuggestionProvider<CommandSourceStack> MINE = (context, builder) -> {
        ServerPlayer player = context.getSource().getPlayer();
        return SharedSuggestionProvider.suggest(player == null ? List.of() : FichaServer.openRequestIds(player), builder);
    };

    private FichaCommand() {
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("ficha")
                .executes(context -> run(context, player -> FichaServer.open(player, false)));

        root.then(Commands.literal("rolar")
                .then(Commands.argument("teste", StringArgumentType.word()).suggests(TESTS)
                        .executes(context -> roll(context, 0, false))
                        .then(Commands.literal("secreto").executes(context -> roll(context, 0, true)))
                        .then(Commands.argument("cd", IntegerArgumentType.integer(1, 99))
                                .executes(context -> roll(context, IntegerArgumentType.getInteger(context, "cd"), false))
                                .then(Commands.literal("secreto")
                                        .executes(context -> roll(context, IntegerArgumentType.getInteger(context, "cd"), true))))));

        root.then(Commands.literal("dados")
                .then(Commands.argument("formula", StringArgumentType.greedyString())
                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(List.of("1d20", "2d6", "1d20+3", "4d6kh3"), builder))
                        .executes(context -> run(context, player ->
                                FichaServer.freeRollCommand(player, StringArgumentType.getString(context, "formula"), false)))));

        root.then(Commands.literal("pedido")
                .executes(context -> run(context, player -> FichaServer.respond(player, "")))
                .then(Commands.argument("id", StringArgumentType.word()).suggests(PENDING)
                        .executes(context -> run(context, player -> FichaServer.respond(player, StringArgumentType.getString(context, "id"))))
                        .then(Commands.literal("recusar")
                                .executes(context -> run(context, player -> FichaServer.decline(player, StringArgumentType.getString(context, "id")))))));

        root.then(Commands.literal("vincular")
                .executes(context -> run(context, FichaServer::chooseBinding))
                .then(Commands.literal("limpar").executes(context -> run(context, FichaServer::clearBinding)))
                .then(Commands.argument("tipo", StringArgumentType.word())
                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(List.of("sheet", "npc"), builder))
                        .then(Commands.argument("id", LongArgumentType.longArg(1))
                                .executes(context -> {
                                    String type = StringArgumentType.getString(context, "tipo");
                                    if (!"sheet".equals(type) && !"npc".equals(type)) {
                                        context.getSource().sendFailure(ChatCards.error("Tipo inválido: use sheet ou npc."));
                                        return 0;
                                    }
                                    long id = LongArgumentType.getLong(context, "id");
                                    return run(context, player -> FichaServer.bind(player, type, id));
                                }))));

        // A mesma régua de FichaServer.isGm (permissaoMestre do perfil; o alt nasce sem OP e não é
        // mestre). Quem não é mestre nem vê o ramo no TAB; cada ação confere de novo no servidor.
        root.then(Commands.literal("mestre")
                .requires(source -> source.hasPermission(FichaConfig.get(FichaConfig.GM_PERMISSION)))
                .executes(context -> run(context, FichaServer::openTable))
                // <teste> aceita perícia, atributo ou fórmula ("2d6" é uma palavra válida no Brigadier;
                // "dados:2d6" não seria — ':' não entra em palavra sem aspas —, por isso o ramo "dados").
                .then(Commands.literal("pedir")
                        .then(Commands.argument("jogadores", EntityArgument.players())
                                .then(Commands.literal("dados")
                                        .then(Commands.argument("formula", StringArgumentType.string()).suggests(DICE)
                                                .executes(context -> ask(context, "formula", 0))
                                                .then(Commands.argument("cd", IntegerArgumentType.integer(1, 99))
                                                        .executes(context -> ask(context, "formula", IntegerArgumentType.getInteger(context, "cd"))))))
                                .then(Commands.argument("teste", StringArgumentType.word()).suggests(ASK_TESTS)
                                        .executes(context -> ask(context, "teste", 0))
                                        .then(Commands.argument("cd", IntegerArgumentType.integer(1, 99))
                                                .executes(context -> ask(context, "teste", IntegerArgumentType.getInteger(context, "cd")))))))
                .then(Commands.literal("cancelar")
                        .then(Commands.argument("id", StringArgumentType.word()).suggests(MINE)
                                .executes(context -> run(context, gm -> FichaServer.cancelRequest(gm, StringArgumentType.getString(context, "id"))))))
                .then(Commands.literal("ver")
                        .then(Commands.argument("jogador", EntityArgument.player())
                                .executes(context -> {
                                    ServerPlayer target = EntityArgument.getPlayer(context, "jogador");
                                    return run(context, gm -> FichaServer.peek(gm, target.getUUID()));
                                }))));

        event.getDispatcher().register(root);
    }

    private static int roll(CommandContext<CommandSourceStack> context, int dc, boolean secret) {
        String test = StringArgumentType.getString(context, "teste");
        return run(context, player -> FichaServer.rollCommand(player, test, dc, secret));
    }

    private static int ask(CommandContext<CommandSourceStack> context, String argument, int dc)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var players = EntityArgument.getPlayers(context, "jogadores");
        String test = StringArgumentType.getString(context, argument);
        boolean diceOnly = "formula".equals(argument);
        return run(context, gm -> FichaServer.askCommand(gm, players, test, dc, diceOnly));
    }

    private static int run(CommandContext<CommandSourceStack> context, java.util.function.Consumer<ServerPlayer> action) {
        ServerPlayer player = context.getSource().getPlayer();
        if (player == null) {
            context.getSource().sendFailure(ChatCards.error("A ficha é de um jogador: use dentro do jogo."));
            return 0;
        }
        action.accept(player);
        return 1;
    }
}
