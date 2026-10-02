package com.aurorion.essentials.command;

import com.aurorion.core.character.AltData;
import com.aurorion.core.rate.ActionCooldown;
import com.aurorion.essentials.AurorionEssentials;
import com.aurorion.essentials.help.HelpRequestManager;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import java.util.UUID;

/**
 * {@code /ajuda <descrição>} — pedido de socorro do jogador. Avisa todo operador online com nome
 * real, fakename, descricao e localizacao de quem chamou.
 *
 * <p><b>{@code /ajuda} sozinho responde.</b> Sem o {@code executes} na raiz, o Brigadier devolvia
 * "Unknown or incomplete command" em vermelho — que e exatamente a cara de um comando que nao
 * existe. Quem digita so "/ajuda" esperando um menu precisa ser ensinado a usar, nao recusado.
 *
 * <p><b>Todo pedido vai para o log do servidor</b>, tenha ou nao operador online. Sem isso, um
 * pedido feito de madrugada sumia: ninguem recebia e nao sobrava registro para conferir depois — e
 * a unica evidencia de que o comando funcionou era alguem ter visto a mensagem na hora.
 */
@EventBusSubscriber(modid = AurorionEssentials.MOD_ID)
public final class AjudaCommand {
    private static final ActionCooldown REQUESTS = new ActionCooldown(30_000);
    private static final ActionCooldown NOTICES = new ActionCooldown(3_000);

    private AjudaCommand() {
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("ajuda")
                .executes(AjudaCommand::usage)
                .then(Commands.argument("descricao", StringArgumentType.greedyString())
                        .executes(AjudaCommand::request)));
    }

    private static int usage(CommandContext<CommandSourceStack> context) {
        context.getSource().sendSystemMessage(
                Component.translatable("commands.aurorion_essentials.ajuda.usage").withStyle(ChatFormatting.GOLD));
        return 0;
    }

    private static int request(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        UUID owner = AltData.get(player.server).ownerOf(player.getUUID());
        UUID account = owner == null ? player.getUUID() : owner;
        long now = Util.getMillis();
        if (!REQUESTS.acquire(account, now)) {
            if (NOTICES.acquire(account, now)) {
                long seconds = Math.max(1, (REQUESTS.remainingMillis(account, now) + 999) / 1_000);
                player.sendSystemMessage(Component.literal("Seu pedido anterior já foi enviado. Aguarde " + seconds + " segundo(s) para enviar outro."));
            }
            return 0;
        }
        String description = StringArgumentType.getString(context, "descricao")
                .replaceAll("\\p{Cntrl}", " ").strip();
        if (description.length() > 512) description = description.substring(0, 512);

        int notified = HelpRequestManager.notifyOps(player, description);
        AurorionEssentials.LOGGER.info("/ajuda de {} em {} ({} admin(s) avisado(s)): {}",
                player.getGameProfile().getName(), player.blockPosition().toShortString(), notified, description);

        if (notified > 0) {
            context.getSource().sendSuccess(() ->
                    Component.translatable("commands.aurorion_essentials.ajuda.sent", notified), false);
        } else {
            // Nao e sendFailure: o pedido foi aceito e registrado, so nao ha ninguem para atender
            // agora. Em vermelho de erro, a pessoa conclui que o comando quebrou e nao tenta o
            // Discord, que e justamente o que a mensagem esta pedindo.
            context.getSource().sendSystemMessage(
                    Component.translatable("commands.aurorion_essentials.ajuda.noOp").withStyle(ChatFormatting.GOLD));
        }
        return notified;
    }

    @SubscribeEvent
    public static void stop(ServerStoppedEvent event) {
        REQUESTS.clear();
        NOTICES.clear();
    }
}
