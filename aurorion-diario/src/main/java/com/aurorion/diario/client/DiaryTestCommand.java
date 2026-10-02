package com.aurorion.diario.client;

import com.aurorion.diario.AurorionDiario;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

/** Abre a tela real com dados locais, sem depender da integração do servidor. */
@EventBusSubscriber(modid = AurorionDiario.MOD_ID, value = Dist.CLIENT)
public final class DiaryTestCommand {
    private static boolean pendingOpen;

    private DiaryTestCommand() {
    }

    @SubscribeEvent
    public static void register(RegisterClientCommandsEvent event) {
        var command = Commands.literal("diario")
                .then(Commands.literal("teste").executes(context -> queueOpen()));
        // Nos runs de desenvolvimento, /diario já abre o teste sem consultar o servidor.
        // Com o JAR instalado, o comando sem argumentos continua sendo do servidor.
        if (!FMLEnvironment.production) command.executes(context -> queueOpen());
        event.getDispatcher().register(command);
    }

    private static int queueOpen() {
        // O chat fecha sua tela ao concluir o comando; só abre no próximo tick.
        pendingOpen = true;
        return 1;
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (!pendingOpen) return;
        pendingOpen = false;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null && minecraft.player != null) {
            minecraft.setScreen(DiaryScreen.forTesting());
        }
    }
}
