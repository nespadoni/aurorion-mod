package com.aurorion.essentials.command;

import com.aurorion.essentials.AurorionEssentials;
import com.aurorion.essentials.fakename.FakeName;
import com.aurorion.essentials.server.FakeNameManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
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
 * {@code /fakename clear [jogador]} — limpa o proprio (todo mundo) ou o de outro (nivel 2+).
 */
@EventBusSubscriber(modid = AurorionEssentials.MOD_ID)
public final class FakeNameCommand {
    private FakeNameCommand() {
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(Commands.literal("fakename")
                .then(Commands.literal("set")
                        .then(Commands.argument("name", StringArgumentType.string())
                                .executes(FakeNameCommand::set)))
                .then(Commands.literal("clear")
                        .executes(FakeNameCommand::clearSelf)
                        .then(Commands.argument("player", EntityArgument.player())
                                .requires(source -> source.hasPermission(2))
                                .executes(FakeNameCommand::clearOther))));
    }

    private static int set(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        String rawInput = StringArgumentType.getString(context, "name");

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
            case IMPERSONATION -> context.getSource().sendFailure(
                    Component.translatable("commands.aurorion_essentials.fakename.set.impersonation"));
        }
        return result == FakeNameManager.Result.OK ? 1 : 0;
    }

    private static int clearSelf(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        FakeNameManager.clear(player);

        context.getSource().sendSuccess(() ->
                Component.translatable("commands.aurorion_essentials.fakename.clear.success.self"), false);
        return 1;
    }

    private static int clearOther(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(context, "player");
        FakeNameManager.clear(target);

        context.getSource().sendSuccess(() ->
                Component.translatable("commands.aurorion_essentials.fakename.clear.success.other", target.getName()), false);
        return 1;
    }
}
