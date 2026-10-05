package com.aurorion.essentials.streamer;

import com.aurorion.essentials.AurorionEssentials;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

/** Comando local: todo jogador pode preparar sua propria live sem consultar o servidor. */
@EventBusSubscriber(modid = AurorionEssentials.MOD_ID, value = Dist.CLIENT)
public final class StreamerClientCommands {
    private StreamerClientCommands() { }

    @SubscribeEvent
    public static void register(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("streamer")
                .executes(context -> status())
                .then(Commands.literal("status").executes(context -> status()))
                .then(Commands.literal("on").executes(context -> setMode(StreamerMode.OPERATIONS)))
                .then(Commands.literal("ativar").executes(context -> setMode(StreamerMode.OPERATIONS)))
                .then(Commands.literal("off").executes(context -> setMode(StreamerMode.OFF)))
                .then(Commands.literal("desativar").executes(context -> setMode(StreamerMode.OFF)))
                .then(Commands.literal("total").executes(context -> setMode(StreamerMode.ALL_SYSTEM))));
    }

    private static int setMode(StreamerMode mode) {
        StreamerConfig.MODE.set(mode);
        try {
            StreamerConfig.SPEC.save();
        } catch (RuntimeException error) {
            AurorionEssentials.LOGGER.error("Could not save local streamer preference", error);
            local("Modo aplicado nesta sessao, mas nao foi possivel salvar a preferencia.", ChatFormatting.RED);
        }
        return status();
    }

    private static int status() {
        String description = switch (StreamerConfig.MODE.get()) {
            case OFF -> "desativado. /streamer on ativa; /streamer total oculta os outros avisos do sistema.";
            case OPERATIONS -> "ativado: comandos do Minecraft, teleporte, mudancas de modo de jogo, mortes e avisos administrativos conhecidos ocultos no chat.";
            case ALL_SYSTEM -> "total: avisos do sistema ocultos. Falas de NPC e outros avisos de alguns mods tambem podem ser ocultos.";
        };
        local("Modo streamer " + description + " /streamer off desativa.", ChatFormatting.GOLD);
        return 1;
    }

    private static void local(String text, ChatFormatting color) {
        // Bypass de ChatListener: ligar/desligar/status permanecem visiveis no modo total.
        Minecraft.getInstance().gui.getChat().addMessage(Component.literal("[Streamer] " + text).withStyle(color));
    }
}
