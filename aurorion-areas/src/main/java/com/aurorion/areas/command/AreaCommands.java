package com.aurorion.areas.command;

import com.aurorion.areas.AurorionAreas;
import com.aurorion.areas.api.AreaApi;
import com.aurorion.areas.config.AreasConfig;
import com.aurorion.areas.data.*;
import com.aurorion.areas.geometry.*;
import com.aurorion.areas.network.*;
import com.aurorion.areas.profile.*;
import com.aurorion.areas.region.AreaRegion;
import com.aurorion.areas.rules.*;
import com.aurorion.areas.server.AreaRuntime;
import com.mojang.brigadier.*;
import com.mojang.brigadier.arguments.*;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.*;
import net.minecraft.commands.*;
import net.minecraft.commands.arguments.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import java.util.*;
import static net.minecraft.commands.Commands.*;

public final class AreaCommands {
    private static final Map<UUID, AreaSelection> SELECTIONS = new HashMap<>();
    private static final Map<UUID, AreaPreview> PREVIEWS = new HashMap<>();
    private static final DynamicCommandExceptionType ERROR = new DynamicCommandExceptionType(value -> Component.literal(value.toString()));
    private static final List<String> RULES = Arrays.stream(AreaRule.ALL).map(AreaRule::key).toList();
    private static final int PREVIEW_TICKS = 600;
    private AreaCommands() {}
    @FunctionalInterface private interface Action { String execute(CommandContext<CommandSourceStack> c) throws CommandSyntaxException; }
    private static Command<CommandSourceStack> run(Action action) {
        return c -> {
            try {
                String response = action.execute(c);
                c.getSource().sendSuccess(() -> Component.literal(response), false);
                return 1;
            } catch (IllegalArgumentException e) { throw ERROR.create(e.getMessage()); }
        };
    }
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        var root = literal("area").requires(source -> source.hasPermission(2))
                .executes(run(c -> """
                        Áreas da staff:
                        /area selecao | ponto [x z] | desfazer | circulo <raio> | altura <min> <max>
                        /area criar <id> | adicionar <id> | recortar <id> | remover_forma <id> parte|recorte <índice>
                        /area perfil <id> <perfil> | regra <id> <regra> permitir|negar|herdar
                        /area prioridade <id> <n> | ambiente <id> <perfil>|nenhum|herdar
                        /area monstros <id> vida|dano <fator> | excecao <id> <jogador> <regra> true|false
                        /area nome <id> <nome> | ativar <id> true|false | mundo <perfil>|herdar
                        /area aqui [jogador] | ver <id> | listar | visualizar [id] | remover <id>
                        """));
        root.then(literal("selecao").executes(run(c -> start(c.getSource().getPlayerOrException()))));
        root.then(literal("ponto").executes(run(c -> point(c, false)))
                .then(argument("x", DoubleArgumentType.doubleArg(-30_000_000, 30_000_000))
                    .then(argument("z", DoubleArgumentType.doubleArg(-30_000_000, 30_000_000)).executes(run(c -> point(c, true))))));
        root.then(literal("desfazer").executes(run(c -> {
            var s = selection(c);
            if (s.points.isEmpty()) throw new IllegalArgumentException("Nenhum ponto para desfazer.");
            s.points.removeLast(); return "Último ponto removido. Restam " + s.points.size() + ".";
        })));
        root.then(literal("circulo").then(argument("raio", DoubleArgumentType.doubleArg(.5, 100_000)).executes(run(c -> {
            var p = c.getSource().getPlayerOrException();
            var s = new AreaSelection(p);
            s.radius = DoubleArgumentType.getDouble(c, "raio"); s.center = new Point2(p.getX(), p.getZ());
            SELECTIONS.put(p.getUUID(), s);
            return "Círculo selecionado no seu X/Z, raio " + s.radius + ". Ajuste /area altura se necessário.";
        }))));
        root.then(literal("altura").then(argument("min", DoubleArgumentType.doubleArg(
                        -AreaShape.MAX_ABS_HEIGHT, AreaShape.MAX_ABS_HEIGHT))
                .then(argument("max", DoubleArgumentType.doubleArg(
                        -AreaShape.MAX_ABS_HEIGHT, AreaShape.MAX_ABS_HEIGHT)).executes(run(c -> {
                    var s = selection(c); s.heights(DoubleArgumentType.getDouble(c, "min"), DoubleArgumentType.getDouble(c, "max"));
                    return "Alturas da seleção: " + s.minY + " a " + s.maxY + ".";
                })))));
        root.then(literal("criar").then(argument("id", StringArgumentType.word()).executes(run(AreaCommands::create))));
        root.then(literal("adicionar").then(area().executes(run(c -> append(c, false)))));
        root.then(literal("recortar").then(area().executes(run(c -> append(c, true)))));
        var shapeId = area();
        for (String kind : List.of("parte", "recorte")) {
            shapeId.then(literal(kind).then(argument("indice", IntegerArgumentType.integer(1, 32)).executes(run(c -> {
                var region = region(c);
                var volume = region.volume().removePart(IntegerArgumentType.getInteger(c, "indice") - 1, kind.equals("recorte"));
                return update(c, region.withVolume(volume), "Forma removida.");
            }))));
        }
        root.then(literal("remover_forma").then(shapeId));
        root.then(literal("prioridade").then(area().then(argument("valor", IntegerArgumentType.integer(-10000, 10000))
                .executes(run(c -> update(c, region(c).withPriority(IntegerArgumentType.getInteger(c, "valor")), "Prioridade atualizada."))))));
        root.then(literal("nome").then(area().then(argument("nome", StringArgumentType.greedyString())
                .executes(run(c -> update(c, region(c).withName(StringArgumentType.getString(c, "nome")), "Nome atualizado."))))));
        root.then(literal("ativar").then(area().then(argument("ativo", BoolArgumentType.bool())
                .executes(run(c -> update(c, region(c).withEnabled(BoolArgumentType.getBool(c, "ativo")), "Ativação atualizada."))))));
        root.then(literal("perfil").then(area().then(presetArgument().executes(run(c -> {
            var region = region(c);
            return update(c, region.withRules(preset(c).rules()), "Regras substituídas pelo perfil selecionado.");
        })))));
        root.then(literal("regra").then(area().then(ruleArgument().then(argument("decisao", StringArgumentType.word())
                .suggests((c, b) -> SharedSuggestionProvider.suggest(List.of("permitir", "negar", "herdar"), b))
                .executes(run(c -> {
                    var region = region(c);
                    var rules = region.rules().withFlag(StringArgumentType.getString(c, "regra"),
                            Decision.parse(StringArgumentType.getString(c, "decisao")));
                    return update(c, region.withRules(rules), "Regra atualizada.");
                }))))));
        var ambience = area()
                .then(literal("nenhum").executes(run(c -> ambience(c, AreaRules.NO_AMBIENCE))))
                .then(literal("herdar").executes(run(c -> ambience(c, null))))
                .then(argument("ambiente", ResourceLocationArgument.id())
                        .suggests((c, b) -> SharedSuggestionProvider.suggestResource(AreaProfiles.AMBIENCE.ids(), b))
                        .executes(run(c -> {
                            var id = ResourceLocationArgument.getId(c, "ambiente");
                            if (AreaProfiles.AMBIENCE.get(id) == null) throw new IllegalArgumentException("Ambiente inexistente: " + id);
                            return ambience(c, id);
                        })));
        root.then(literal("ambiente").then(ambience));
        var strength = area();
        for (String kind : List.of("vida", "dano")) strength.then(literal(kind)
                .then(argument("fator", DoubleArgumentType.doubleArg(-1, 20)).executes(run(c -> {
                    var region = region(c);
                    return update(c, region.withRules(region.rules().withStrength(kind.equals("vida"),
                            DoubleArgumentType.getDouble(c, "fator"))), "Força dos próximos monstros atualizada.");
                }))));
        root.then(literal("monstros").then(strength));
        root.then(literal("excecao").then(area()
                .then(argument("jogador", GameProfileArgument.gameProfile()).then(ruleArgument()
                        .then(argument("liberar", BoolArgumentType.bool()).executes(run(AreaCommands::exception)))))));
        root.then(literal("mundo")
                .then(literal("herdar").executes(run(c -> defaults(c, AreaRules.INHERIT))))
                .then(presetArgument().executes(run(c -> defaults(c, preset(c).rules())))));
        root.then(literal("aqui").executes(run(c -> here(c.getSource().getPlayerOrException())))
                .then(argument("alvo", EntityArgument.player()).executes(run(c -> here(EntityArgument.getPlayer(c, "alvo"))))));
        root.then(literal("ver").then(area().executes(run(c -> describe(region(c))))));
        root.then(literal("listar").executes(run(c -> {
            var list = data(c).all();
            if (list.isEmpty()) return "Nenhuma área criada.";
            StringBuilder out = new StringBuilder("Áreas (" + list.size() + "):");
            for (var a : list) out.append("\n").append(a.id()).append(" | ").append(a.dimension())
                    .append(" | prioridade ").append(a.priority()).append(a.enabled() ? "" : " | DESATIVADA");
            return out.toString();
        })));
        root.then(literal("visualizar").executes(run(c -> {
            var p = c.getSource().getPlayerOrException(); var s = selection(c);
            return show(p, s.dimension, new AreaVolume(List.of(s.shape()), List.of()), "Contorno da seleção");
        })).then(area().executes(run(c -> {
            var p = c.getSource().getPlayerOrException(); var r = region(c);
            if (!r.dimension().equals(p.level().dimension().location())) throw new IllegalArgumentException("Vá à dimensão da área para visualizar.");
            return show(p, r.dimension(), r.volume(), "Contorno de " + r.id());
        }))));
        root.then(literal("remover").then(area().executes(run(c -> {
            var region = region(c); data(c).delete(region.id()); audit(c);
            return "Área removida: " + region.id();
        }))));
        dispatcher.register(root);
    }
    private static RequiredArgumentBuilder<CommandSourceStack, String> area() {
        return argument("id", StringArgumentType.word()).suggests((c, b) ->
                SharedSuggestionProvider.suggest(data(c).all().stream().map(AreaRegion::id), b));
    }
    private static RequiredArgumentBuilder<CommandSourceStack, String> ruleArgument() {
        return argument("regra", StringArgumentType.string()).suggests((c, b) -> SharedSuggestionProvider.suggest(RULES, b));
    }
    private static RequiredArgumentBuilder<CommandSourceStack, ResourceLocation> presetArgument() {
        return argument("perfil", ResourceLocationArgument.id()).suggests((c, b) ->
                SharedSuggestionProvider.suggestResource(AreaProfiles.RULES.ids(), b));
    }
    private static RulePreset preset(CommandContext<CommandSourceStack> c) {
        var id = ResourceLocationArgument.getId(c, "perfil");
        var preset = AreaProfiles.RULES.get(id);
        if (preset == null) throw new IllegalArgumentException("Perfil inexistente: " + id);
        return preset;
    }
    private static AreaData data(CommandContext<CommandSourceStack> c) { return AreaData.get(c.getSource().getServer()); }
    private static AreaRegion region(CommandContext<CommandSourceStack> c) { return data(c).require(StringArgumentType.getString(c, "id")); }
    private static AreaSelection selection(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        var p = c.getSource().getPlayerOrException();
        var s = SELECTIONS.get(p.getUUID());
        if (s == null || !s.dimension.equals(p.level().dimension().location()))
            throw new IllegalArgumentException("Inicie /area selecao ou /area circulo <raio> nesta dimensão.");
        return s;
    }
    private static String start(ServerPlayer p) {
        SELECTIONS.put(p.getUUID(), new AreaSelection(p));
        return "Polígono iniciado. Caminhe pelo contorno usando /area ponto. Não repita o primeiro ponto ao fechar.";
    }
    private static String point(CommandContext<CommandSourceStack> c, boolean explicit) throws CommandSyntaxException {
        var s = selection(c); var p = c.getSource().getPlayerOrException();
        s.point(explicit ? DoubleArgumentType.getDouble(c, "x") : p.getX(), explicit ? DoubleArgumentType.getDouble(c, "z") : p.getZ());
        return "Ponto " + s.points.size() + " adicionado.";
    }
    private static String create(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        var s = selection(c); String id = StringArgumentType.getString(c, "id");
        if (data(c).all().stream().anyMatch(r -> r.id().equals(id))) throw new IllegalArgumentException("Já existe uma área com esse id.");
        return update(c, new AreaRegion(id, id, s.dimension, 0, true,
                new AreaVolume(List.of(s.shape()), List.of()), AreaRules.INHERIT, Map.of()),
                "Área criada. Aplique /area perfil ou /area regra para definir seu comportamento." + bypassNotice(c));
    }
    private static String append(CommandContext<CommandSourceStack> c, boolean hole) throws CommandSyntaxException {
        var r = region(c); var s = selection(c);
        if (!r.dimension().equals(s.dimension)) throw new IllegalArgumentException("Seleção e área devem estar na mesma dimensão.");
        return update(c, r.withVolume(r.volume().append(s.shape(), hole)), hole ? "Recorte adicionado." : "Parte adicionada.");
    }
    private static String ambience(CommandContext<CommandSourceStack> c, ResourceLocation id) {
        var r = region(c); return update(c, r.withRules(r.rules().withAmbience(id)), "Ambiente atualizado.");
    }
    private static String defaults(CommandContext<CommandSourceStack> c, AreaRules rules) {
        data(c).setDefaults(c.getSource().getLevel().dimension().location(), rules);
        audit(c);
        return "Regras de fundo desta dimensão atualizadas. Áreas explícitas continuam tendo precedência." + bypassNotice(c);
    }
    private static String exception(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        var region = region(c);
        var players = GameProfileArgument.getGameProfiles(c, "jogador");
        String key = StringArgumentType.getString(c, "regra");
        boolean allow = BoolArgumentType.getBool(c, "liberar");
        for (var profile : players) region = region.withException(profile.getId(), key, allow);
        return update(c, region, "Exceção " + (allow ? "concedida" : "removida") + " para " + players.size() + " personagem(ns).");
    }
    private static String update(CommandContext<CommandSourceStack> c, AreaRegion region, String message) {
        data(c).put(region); audit(c); return region.id() + ": " + message;
    }
    private static void audit(CommandContext<CommandSourceStack> c) {
        AurorionAreas.LOGGER.info("Area admin {}: {}", c.getSource().getTextName(), c.getInput());
    }
    private static String here(ServerPlayer p) {
        var rules = AreaRuntime.rules(p);
        var data = AreaData.get(p.server);
        StringBuilder out = new StringBuilder("Regras em ").append(p.getGameProfile().getName())
                .append(" (").append(p.level().dimension().location()).append("):");
        if (!AreasConfig.ENABLED.get()) out.append("\nSistema DESLIGADO na configuração.");
        if (AreaApi.bypass(p)) out.append("\nEste jogador tem bypass (espectador/criativo da staff).");
        for (AreaRule rule : AreaRule.ALL) {
            var owner = rules.owner(rule);
            out.append("\n").append(rule.key()).append(": ").append(rules.allows(rule) ? "permitir" : "negar")
                    .append(" <- ").append(owner == null ? "padrão da dimensão" : owner.id());
        }
        out.append("\nambiente: ").append(rules.ambience()).append(" | vida x").append(rules.mobHealth())
                .append(" | dano x").append(rules.mobDamage()).append("\nÁreas sobrepostas:");
        for (var area : data.all()) if (area.enabled() && area.dimension().equals(p.level().dimension().location())
                && area.volume().contains(p.getX(), p.getY(), p.getZ())) out.append(" ").append(area.id());
        return out.toString();
    }
    private static String describe(AreaRegion area) {
        StringBuilder out = new StringBuilder(area.id()).append(" — ").append(area.name()).append("\n")
                .append(area.dimension()).append(" | prioridade ").append(area.priority()).append(" | ativa ").append(area.enabled())
                .append("\nRegras: ").append(AreaJson.writeRules(area.rules())).append("\nFormas (índices começam em 1):");
        for (int i = 0; i < area.volume().parts().size(); i++) shapeDescription(out, "parte", i, area.volume().parts().get(i));
        for (int i = 0; i < area.volume().holes().size(); i++) shapeDescription(out, "recorte", i, area.volume().holes().get(i));
        out.append("\nExcecoes (UUID -> regras), total ").append(area.exceptions().size()).append(":");
        area.exceptions().entrySet().stream().sorted(Map.Entry.comparingByKey()).limit(12).forEach(entry ->
                out.append("\n").append(entry.getKey()).append(" -> ")
                        .append(entry.getValue().stream().sorted().limit(8).toList())
                        .append(entry.getValue().size() > 8 ? " ..." : ""));
        if (area.exceptions().size() > 12) out.append("\nLista abreviada; /area aqui <jogador> consulta a politica efetiva.");
        return out.toString();
    }
    private static void shapeDescription(StringBuilder out, String label, int i, AreaShape shape) {
        out.append("\n").append(label).append(" ").append(i + 1).append(": ")
                .append(shape.isCircle() ? "círculo r=" + shape.radius() : "polígono de " + shape.vertexCount() + " pontos")
                .append(" | Y ").append(shape.bounds().minY()).append(" a ").append(shape.bounds().maxY())
                .append(" | X ").append(shape.bounds().minX()).append(" a ").append(shape.bounds().maxX())
                .append(" | Z ").append(shape.bounds().minZ()).append(" a ").append(shape.bounds().maxZ());
    }
    /**
     * Contorno sólido para quem tem o módulo cliente; partículas para quem não tem.
     *
     * <p>O canal é opcional, então um cliente sem o mod simplesmente não receberia nada — e a staff
     * ficaria olhando para o nada sem saber por quê. O aviso é a diferença entre "não funciona" e
     * "falta o módulo aqui".
     */
    private static String show(ServerPlayer player, ResourceLocation dimension, AreaVolume volume, String what) {
        if (AreaNetwork.hasOutlineChannel(player)) {
            PREVIEWS.remove(player.getUUID());
            AreaNetwork.send(player, outline(dimension, volume));
            return what + " por 30 segundos, somente para você. Azul = partes, vermelho = recortes;"
                    + " as linhas fortes são o piso e o teto de cada forma, e a parede aparece em volta da sua altura.";
        }
        PREVIEWS.put(player.getUUID(), new AreaPreview(player, dimension, volume));
        return what + " por 30 segundos, em partículas: seu cliente não tem o módulo aurorion_areas,"
                + " então o contorno sólido não pode ser desenhado. Instale-o para ver o limite inteiro.";
    }
    private static AreaOutlinePayload outline(ResourceLocation dimension, AreaVolume volume) {
        List<AreaOutlinePayload.Shape> shapes = new ArrayList<>();
        for (AreaShape shape : volume.parts()) shapes.add(outlineShape(shape, false));
        for (AreaShape shape : volume.holes()) shapes.add(outlineShape(shape, true));
        return new AreaOutlinePayload(dimension, PREVIEW_TICKS, shapes);
    }
    private static AreaOutlinePayload.Shape outlineShape(AreaShape shape, boolean hole) {
        var points = shape.points();
        double[] xs = new double[points.size()], zs = new double[points.size()];
        for (int i = 0; i < points.size(); i++) { xs[i] = points.get(i).x(); zs[i] = points.get(i).z(); }
        return new AreaOutlinePayload.Shape(hole, shape.bounds().minY(), shape.bounds().maxY(), shape.radius(), xs, zs);
    }
    /**
     * Quem cria a área quase sempre é staff em criativo — e staff em criativo ignora as próprias
     * regras por padrão. Sem este aviso, o teste seguinte é voar dentro da escola, conseguir, e
     * concluir que a área não funciona.
     */
    private static String bypassNotice(CommandContext<CommandSourceStack> c) {
        ServerPlayer player = c.getSource().getPlayer();
        if (player == null || !AreaApi.bypass(player)) return "";
        return "\nAVISO: você tem bypass agora (criativo da staff ou espectador), então as regras desta área"
                + " não vão te afetar. Entre em sobrevivência ou peça a um jogador para testar.";
    }
    public static void tickPreview(ServerPlayer player) {
        if (PREVIEWS.isEmpty()) return; // Roda por jogador por tick; no caso normal nao ha previa alguma.
        var preview = PREVIEWS.get(player.getUUID());
        if (preview != null && !preview.tick(player)) PREVIEWS.remove(player.getUUID());
    }
    public static void forget(UUID id) { SELECTIONS.remove(id); PREVIEWS.remove(id); }
    public static void clear() { SELECTIONS.clear(); PREVIEWS.clear(); }
}
