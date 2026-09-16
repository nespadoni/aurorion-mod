package com.aurorion.profissoes.command;

import com.aurorion.profissoes.AurorionProfissoes;
import com.aurorion.profissoes.data.*;
import com.aurorion.profissoes.network.ProfessionsNetwork;
import com.aurorion.profissoes.server.*;
import com.mojang.brigadier.arguments.BoolArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

@EventBusSubscriber(modid = AurorionProfissoes.MOD_ID)
public final class ProfessionCommand {
    private ProfessionCommand() {}
    @SubscribeEvent public static void register(RegisterCommandsEvent event) {
        var root = Commands.literal("profissao").requires(source -> source.hasPermission(2));
        var target = Commands.argument("jogador", EntityArgument.player());
        for (var profession : Profession.values()) target.then(Commands.literal(profession.id()).executes(context -> {
            var player = EntityArgument.getPlayer(context, "jogador");
            ProfessionData.get(player.server).assign(player.getUUID(), profession);
            ServiceManager.forget(player.getUUID()); ProfessionsNetwork.sync(player);
            context.getSource().sendSuccess(() -> Component.literal(player.getScoreboardName() + ": " + profession.label()), true);
            return 1;
        }));
        root.then(Commands.literal("definir").then(target));
        root.then(Commands.literal("ver").then(Commands.argument("jogador", EntityArgument.player()).executes(context -> {
            var player = EntityArgument.getPlayer(context, "jogador");
            context.getSource().sendSuccess(() -> Component.literal(player.getScoreboardName() + ": " + ProfessionData.get(player.server).of(player.getUUID()).label()), false);
            return 1;
        })));
        root.then(Commands.literal("mending_adm").then(Commands.argument("permitir", BoolArgumentType.bool()).executes(context -> {
            var player = context.getSource().getPlayerOrException();
            var item = player.getMainHandItem();
            if (!item.isDamageableItem()) {
                context.getSource().sendFailure(Component.literal("Segure o equipamento que será autorizado.")); return 0;
            }
            SpecialtyRules.authorizeMending(item, BoolArgumentType.getBool(context, "permitir"));
            player.inventoryMenu.broadcastFullState();
            context.getSource().sendSuccess(() -> Component.literal("Autorização de Mending atualizada no equipamento."), true);
            return 1;
        })));
        event.getDispatcher().register(root);
    }
}
