package com.aurorion.essentials.client;

import com.aurorion.essentials.AurorionEssentials;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;

/** Comando apenas do cliente: /celular importar C:\\Imagens\\foto.jpg. */
@EventBusSubscriber(modid = AurorionEssentials.MOD_ID, value = Dist.CLIENT)
public final class PhoneImageImportCommand {
    private PhoneImageImportCommand() {
    }

    @SubscribeEvent
    public static void register(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("celular")
                .then(Commands.literal("importar")
                        .executes(context -> help())
                        .then(Commands.argument("caminho", StringArgumentType.greedyString())
                                .executes(context -> start(StringArgumentType.getString(context, "caminho"))))));
    }

    private static int help() {
        feedback("Use /celular importar <caminho de uma imagem PNG ou JPG no seu computador>.");
        return 1;
    }

    private static int start(String rawPath) {
        String value = rawPath.trim();
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            value = value.substring(1, value.length() - 1);
        }
        Path source;
        try {
            source = Path.of(value);
        } catch (InvalidPathException e) {
            feedback("O caminho da imagem e invalido.");
            return 0;
        }
        return PhoneGalleryImport.start(source, PhoneImageImportCommand::feedback) ? 1 : 0;
    }

    private static void feedback(String message) {
        // Feedback local permanece visivel mesmo com modo streamer total.
        Minecraft.getInstance().gui.getChat().addMessage(Component.literal(message));
    }
}
