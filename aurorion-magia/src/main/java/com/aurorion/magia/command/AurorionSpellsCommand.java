package com.aurorion.magia.command;

import com.aurorion.magia.AurorionMagia;
import com.aurorion.magia.unlock.SpellAccess;
import com.aurorion.magia.unlock.SpellGrants;
import com.aurorion.magia.unlock.SpellGrants.GrantKind;
import com.aurorion.magia.unlock.SpellUnlockData;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import io.redspace.ironsspellbooks.api.registry.SchoolRegistry;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.SchoolType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.Collection;
import java.util.stream.Collectors;

/**
 * {@code /aurorion spells} — a secretaria da escola de magia. Staff (nivel 2).
 *
 * <pre>
 * /aurorion spells unlock spell  &lt;magia&gt;  &lt;alvos&gt;
 * /aurorion spells unlock school &lt;escola&gt; &lt;alvos&gt;
 * /aurorion spells lock   spell  &lt;magia&gt;  &lt;alvos&gt;
 * /aurorion spells lock   school &lt;escola&gt; &lt;alvos&gt;
 * /aurorion spells list &lt;jogador&gt;
 * </pre>
 *
 * <p>{@code <alvos>} e seletor vanilla: {@code @a}, {@code @a[team=sonserina]}, {@code @p}, nome.
 * Seletor so enxerga quem esta online, e isso e o comportamento certo para aula: libera quem esteve
 * presente. O jogador liberado recebe o aviso na hora e o Iron's e atualizado no mesmo tick.
 *
 * <p>Os ids sugeridos vem dos registros do Iron's, entao magias e escolas de outros addons aparecem
 * sozinhas no Tab. {@code unlock} recusa id que nao existe (erro de digitacao viraria liberacao
 * fantasma); {@code lock} aceita qualquer id, para limpar liberacao de addon que saiu do pack.
 *
 * <p>O literal {@code aurorion} e compartilhado: o Brigadier junta os filhos quando outro mod do
 * ecossistema registrar o seu {@code /aurorion <algo>}.
 */
@EventBusSubscriber(modid = AurorionMagia.MOD_ID)
public final class AurorionSpellsCommand {
    private static final int STAFF_LEVEL = 2;
    private static final String ARG_ID = "id";
    private static final String ARG_TARGETS = "alvos";
    private static final String ARG_PLAYER = "jogador";

    private static final SuggestionProvider<CommandSourceStack> SPELL_IDS = (context, builder) ->
            SharedSuggestionProvider.suggestResource(SpellRegistry.REGISTRY.keySet().stream()
                    .filter(id -> !id.equals(noneId())), builder);

    private static final SuggestionProvider<CommandSourceStack> SCHOOL_IDS = (context, builder) ->
            SharedSuggestionProvider.suggestResource(SchoolRegistry.REGISTRY.keySet(), builder);

    private AurorionSpellsCommand() {
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("aurorion")
                .then(Commands.literal("spells")
                        .requires(source -> source.hasPermission(STAFF_LEVEL))
                        .then(Commands.literal("unlock")
                                .then(kindBranch("spell", GrantKind.SPELL, SPELL_IDS, true))
                                .then(kindBranch("school", GrantKind.SCHOOL, SCHOOL_IDS, true)))
                        .then(Commands.literal("lock")
                                .then(kindBranch("spell", GrantKind.SPELL, SPELL_IDS, false))
                                .then(kindBranch("school", GrantKind.SCHOOL, SCHOOL_IDS, false)))
                        .then(Commands.literal("list")
                                .then(Commands.argument(ARG_PLAYER, EntityArgument.player())
                                        .executes(AurorionSpellsCommand::list)))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> kindBranch(
            String literal, GrantKind kind, SuggestionProvider<CommandSourceStack> suggestions, boolean grant) {
        return Commands.literal(literal)
                .then(Commands.argument(ARG_ID, ResourceLocationArgument.id())
                        .suggests(suggestions)
                        .then(Commands.argument(ARG_TARGETS, EntityArgument.players())
                                .executes(context -> change(context, kind, grant))));
    }

    private static int change(CommandContext<CommandSourceStack> context, GrantKind kind, boolean grant)
            throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ResourceLocation id = ResourceLocationArgument.getId(context, ARG_ID);

        if (grant && !exists(kind, id)) {
            source.sendFailure(Component.translatable(kind == GrantKind.SPELL
                    ? "commands.aurorion_magia.magia_desconhecida"
                    : "commands.aurorion_magia.escola_desconhecida", id.toString()));
            return 0;
        }

        Collection<ServerPlayer> players = EntityArgument.getPlayers(context, ARG_TARGETS);
        SpellUnlockData data = SpellUnlockData.get(source.getServer());
        Component name = displayName(kind, id);

        int changed = 0;
        for (ServerPlayer player : players) {
            boolean didChange = grant
                    ? data.grant(player.getUUID(), kind, id)
                    : data.revoke(player.getUUID(), kind, id);
            if (!didChange) continue;

            changed++;
            SpellAccess.reconcile(player);
            player.sendSystemMessage(Component.translatable(
                    grant ? "aurorion_magia.aprendeu" : "aurorion_magia.esqueceu", name)
                    .withStyle(grant ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.GRAY));
        }

        int total = players.size();
        int result = changed;
        source.sendSuccess(() -> Component.translatable(
                grant ? "commands.aurorion_magia.liberado" : "commands.aurorion_magia.bloqueado",
                name, result, total), true);
        return changed;
    }

    private static int list(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(context, ARG_PLAYER);
        SpellGrants grants = SpellUnlockData.get(context.getSource().getServer()).grantsOf(player.getUUID());

        context.getSource().sendSuccess(() -> Component.translatable("commands.aurorion_magia.lista",
                player.getDisplayName(), join(grants, GrantKind.SCHOOL), join(grants, GrantKind.SPELL)), false);
        return grants.view(GrantKind.SPELL).size() + grants.view(GrantKind.SCHOOL).size();
    }

    private static String join(SpellGrants grants, GrantKind kind) {
        String joined = grants.view(kind).stream().map(ResourceLocation::toString).sorted()
                .collect(Collectors.joining(", "));
        return joined.isEmpty() ? "-" : joined;
    }

    private static boolean exists(GrantKind kind, ResourceLocation id) {
        return kind == GrantKind.SPELL
                ? SpellRegistry.REGISTRY.containsKey(id) && !id.equals(noneId())
                : SchoolRegistry.REGISTRY.containsKey(id);
    }

    /** Nome traduzido quando o id existe; o id cru quando e de um addon que saiu do pack. */
    private static Component displayName(GrantKind kind, ResourceLocation id) {
        if (kind == GrantKind.SPELL && exists(kind, id)) {
            return Component.translatable(SpellRegistry.getSpell(id).getComponentId());
        }
        if (kind == GrantKind.SCHOOL) {
            SchoolType school = SchoolRegistry.REGISTRY.get(id);
            if (school != null) return school.getDisplayName();
        }
        return Component.literal(id.toString());
    }

    private static ResourceLocation noneId() {
        return SpellRegistry.none().getSpellResource();
    }
}
