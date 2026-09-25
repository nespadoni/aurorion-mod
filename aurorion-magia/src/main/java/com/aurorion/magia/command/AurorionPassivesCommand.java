package com.aurorion.magia.command;

import com.aurorion.magia.AurorionMagia;
import com.aurorion.magia.passive.Passive;
import com.aurorion.magia.passive.PassiveData;
import com.aurorion.magia.passive.PassiveScrollItem;
import com.aurorion.magia.passive.Passives;
import com.aurorion.magia.registry.MagiaItems;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.Arrays;
import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * {@code /aurorion passivas} — as marcas do personagem.
 *
 * <pre>
 * /aurorion passivas conceder &lt;passiva&gt; &lt;alvos&gt;   (staff)
 * /aurorion passivas remover  &lt;passiva&gt; &lt;alvos&gt;   (staff)
 * /aurorion passivas pergaminho &lt;passiva&gt; &lt;alvos&gt; (staff)
 * /aurorion passivas ver &lt;jogador&gt;                (staff)
 *
 * /aurorion passivas minhas                       (qualquer um)
 * /aurorion passivas ligar    &lt;passiva&gt;           (o dono)
 * /aurorion passivas desligar &lt;passiva&gt;           (o dono)
 * </pre>
 *
 * <p>A divisao de permissao e a parte que importa. <b>Conceder e da staff</b>, como a aula de magia:
 * ninguem ganha uma passiva jogando. <b>Ligar e desligar e do dono</b>, sem permissao nenhuma — a
 * Presenca Aterradora existe para o vilao entrar em cena e sair dela, e pedir staff a cada entrada
 * mataria a mecanica.
 *
 * <p>{@code pergaminho} entrega o item em vez da marca: serve para a staff colocar a passiva num bau
 * de premio, na mao de um NPC, ou simplesmente dar em cena, para a pessoa ler quando quiser.
 *
 * <p>O literal {@code aurorion} e compartilhado com {@code /aurorion spells} e com os outros mods do
 * ecossistema; o Brigadier junta os filhos sozinho.
 */
@EventBusSubscriber(modid = AurorionMagia.MOD_ID)
public final class AurorionPassivesCommand {
    private static final int STAFF_LEVEL = 2;
    private static final String ARG_ID = "passiva";
    private static final String ARG_TARGETS = "alvos";
    private static final String ARG_PLAYER = "jogador";

    private static final SuggestionProvider<CommandSourceStack> PASSIVE_IDS = (context, builder) ->
            SharedSuggestionProvider.suggestResource(
                    Arrays.stream(Passive.values()).map(Passive::id), builder);

    private AurorionPassivesCommand() {
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("aurorion")
                .then(Commands.literal("passivas")
                        .then(staff("conceder", AurorionPassivesCommand::grant))
                        .then(staff("remover", AurorionPassivesCommand::revoke))
                        .then(staff("pergaminho", AurorionPassivesCommand::scroll))
                        .then(Commands.literal("ver")
                                .requires(source -> source.hasPermission(STAFF_LEVEL))
                                .then(Commands.argument(ARG_PLAYER, EntityArgument.player())
                                        .executes(AurorionPassivesCommand::list)))
                        .then(Commands.literal("minhas").executes(AurorionPassivesCommand::mine))
                        .then(toggle("ligar", true))
                        .then(toggle("desligar", false))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> staff(
            String literal, Action action) {
        return Commands.literal(literal)
                .requires(source -> source.hasPermission(STAFF_LEVEL))
                .then(Commands.argument(ARG_ID, ResourceLocationArgument.id())
                        .suggests(PASSIVE_IDS)
                        .then(Commands.argument(ARG_TARGETS, EntityArgument.players())
                                .executes(context -> apply(context, action))));
    }

    /** Ligar e desligar nao pedem permissao: quem nao tem a passiva simplesmente recebe um "nao". */
    private static LiteralArgumentBuilder<CommandSourceStack> toggle(String literal, boolean on) {
        return Commands.literal(literal)
                .then(Commands.argument(ARG_ID, ResourceLocationArgument.id())
                        .suggests(PASSIVE_IDS)
                        .executes(context -> switchOwn(context, on)));
    }

    private interface Action {
        boolean run(ServerPlayer player, Passive passive);
    }

    private static int apply(CommandContext<CommandSourceStack> context, Action action)
            throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        Passive passive = resolve(context);
        if (passive == null) return 0;

        Collection<ServerPlayer> players = EntityArgument.getPlayers(context, ARG_TARGETS);
        int changed = 0;
        for (ServerPlayer player : players) {
            if (action.run(player, passive)) changed++;
        }
        int result = changed;
        int total = players.size();
        source.sendSuccess(() -> Component.translatable("commands.aurorion_magia.passivas_aplicadas",
                passive.displayName(), result, total), true);
        return changed;
    }

    private static boolean grant(ServerPlayer player, Passive passive) {
        return Passives.grant(player, passive);
    }

    private static boolean revoke(ServerPlayer player, Passive passive) {
        return Passives.revoke(player, passive);
    }

    /** Entrega o pergaminho; se a mochila estiver cheia, ele cai aos pes da pessoa. */
    private static boolean scroll(ServerPlayer player, Passive passive) {
        ItemStack stack = PassiveScrollItem.of(MagiaItems.PASSIVE_SCROLL.get(), passive);
        if (!player.getInventory().add(stack)) player.drop(stack, false);
        return true;
    }

    private static int switchOwn(CommandContext<CommandSourceStack> context, boolean on)
            throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayerOrException();
        Passive passive = resolve(context);
        if (passive == null) return 0;

        if (!Passives.has(player, passive)) {
            source.sendFailure(Component.translatable("commands.aurorion_magia.passiva_nao_tem", passive.displayName()));
            return 0;
        }
        if (!passive.isToggleable()) {
            source.sendFailure(Component.translatable("commands.aurorion_magia.passiva_sem_interruptor",
                    passive.displayName()));
            return 0;
        }
        if (!Passives.setActive(player, passive, on)) {
            source.sendFailure(Component.translatable(on
                    ? "commands.aurorion_magia.passiva_ja_ligada"
                    : "commands.aurorion_magia.passiva_ja_desligada", passive.displayName()));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable(on
                        ? "commands.aurorion_magia.passiva_ligada"
                        : "commands.aurorion_magia.passiva_desligada", passive.displayName())
                .withStyle(on ? ChatFormatting.GOLD : ChatFormatting.GRAY), false);
        return 1;
    }

    private static int list(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(context, ARG_PLAYER);
        return report(context.getSource(), player, player.getDisplayName().getString());
    }

    private static int mine(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        return report(context.getSource(), player, player.getDisplayName().getString());
    }

    private static int report(CommandSourceStack source, ServerPlayer player, String name) {
        PassiveData data = PassiveData.get(player.server);
        Set<Passive> owned = data.owned(player.getUUID());
        String joined = owned.isEmpty() ? "-" : owned.stream()
                .map(passive -> passive.displayName().getString()
                        + (passive.isToggleable() ? data.isActive(player.getUUID(), passive) ? " (ligada)" : " (desligada)" : ""))
                .collect(Collectors.joining(", "));
        source.sendSuccess(() -> Component.translatable("commands.aurorion_magia.passivas_lista", name, joined), false);
        return owned.size();
    }

    private static Passive resolve(CommandContext<CommandSourceStack> context) {
        ResourceLocation id = ResourceLocationArgument.getId(context, ARG_ID);
        Passive passive = Passives.byId(id);
        if (passive == null) {
            context.getSource().sendFailure(
                    Component.translatable("commands.aurorion_magia.passiva_desconhecida", id.toString()));
        }
        return passive;
    }
}
