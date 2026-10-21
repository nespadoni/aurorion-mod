package com.aurorion.essentials.command;

import com.aurorion.core.rate.ActionCooldown;
import com.aurorion.essentials.AurorionEssentials;
import com.aurorion.essentials.fakename.FakeName;
import com.aurorion.essentials.server.FakeNameManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.Util;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * {@code /fakename set <nome>} — troca o proprio nome exibido (aceita "&" para cor, ex: "&6Nome").
 * {@code /fakename clear [jogador]} — limpa o proprio ou o de outro.
 *
 * <p>So staff (nivel 2+). O nome de jogador vem do personagem ({@code aurorion_personagem}); com o
 * comando aberto, qualquer um adotava o nome de um personagem offline e recebia o PIX e o e-mail
 * dele pelo celular.
 */
@EventBusSubscriber(modid = AurorionEssentials.MOD_ID)
public final class FakeNameCommand {
    /**
     * Cada troca manda pacote para os 80 jogadores (nome, tab, diretorio do celular, voz). Sem freio,
     * um macro trocando o nome de alguem em loop virava trafego para o servidor inteiro. Contado por
     * conta afetada: e ela que gera o broadcast, venha a troca de quem vier.
     */
    private static final long CHANGE_COOLDOWN_MS = 10_000L;
    private static final ActionCooldown CHANGES = new ActionCooldown(CHANGE_COOLDOWN_MS);

    private FakeNameCommand() {
    }

    /** @return true se a conta ainda esta no intervalo — a mensagem de recusa ja foi enviada. */
    private static boolean throttled(CommandSourceStack source, ServerPlayer target) {
        long now = Util.getMillis();
        if (CHANGES.acquire(target.getUUID(), now)) return false;
        long seconds = Math.max(1L, (CHANGES.remainingMillis(target.getUUID(), now) + 999L) / 1000L);
        source.sendFailure(Component.translatable("commands.aurorion_essentials.fakename.cooldown", seconds));
        return true;
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(Commands.literal("fakename")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("set")
                        .then(Commands.argument("name", StringArgumentType.string())
                                .executes(FakeNameCommand::set)))
                .then(Commands.literal("clear")
                        .executes(FakeNameCommand::clearSelf)
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(FakeNameCommand::clearOther))));
    }

    private static int set(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        String rawInput = StringArgumentType.getString(context, "name");
        if (throttled(context.getSource(), player)) return 0;

        FakeNameManager.Result result = FakeNameManager.set(player, rawInput);
        switch (result) {
            case OK -> {
                Component fakeComponent = FakeName.parse(rawInput).component();
                context.getSource().sendSuccess(() ->
                        Component.translatable("commands.aurorion_essentials.fakename.set.success", fakeComponent), false);
            }
            case BLANK -> context.getSource().sendFailure(
                    Component.translatable("commands.aurorion_essentials.fakename.set.blank"));
            case TOO_LONG -> context.getSource().sendFailure(
                    Component.translatable("commands.aurorion_essentials.fakename.set.tooLong", FakeName.MAX_LENGTH));
            case HIDDEN_CHARACTERS -> context.getSource().sendFailure(
                    Component.translatable("commands.aurorion_essentials.fakename.set.hiddenCharacters"));
            case IMPERSONATION -> context.getSource().sendFailure(
                    Component.translatable("commands.aurorion_essentials.fakename.set.impersonation"));
        }
        return result == FakeNameManager.Result.OK ? 1 : 0;
    }

    private static int clearSelf(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        if (throttled(context.getSource(), player)) return 0;
        FakeNameManager.clear(player);

        context.getSource().sendSuccess(() ->
                Component.translatable("commands.aurorion_essentials.fakename.clear.success.self"), false);
        return 1;
    }

    private static int clearOther(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(context, "player");
        if (throttled(context.getSource(), target)) return 0;
        FakeNameManager.clear(target);

        context.getSource().sendSuccess(() ->
                Component.translatable("commands.aurorion_essentials.fakename.clear.success.other", target.getName()), false);
        return 1;
    }
}
