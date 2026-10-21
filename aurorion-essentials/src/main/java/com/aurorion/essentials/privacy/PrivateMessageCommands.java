package com.aurorion.essentials.privacy;

import com.aurorion.essentials.AurorionEssentials;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Quem pode usar as mensagens privadas do vanilla ({@code /msg}, {@code /tell}, {@code /w},
 * {@code /teammsg}, {@code /tm}) — o predicate que os mixins {@code WhisperCommandMixin} e
 * {@code TeamMsgCommandMixin} anexam aos comandos.
 *
 * <p>{@code /teammsg} entrou junto porque e o mesmo canal fora da cena: com a privacidade ligada, um
 * time do scoreboard (de minigame, de evento) virava chat privado para quem nao pode usar {@code /msg}.
 *
 * <p><b>Reenvio da arvore de comandos.</b> O predicate le a config na hora, entao o servidor obedece
 * a mudanca imediatamente. O cliente nao: ele recebeu a arvore de comandos no login, ja filtrada pelo
 * predicate daquele instante, e e com ela que sugere e colore o comando. Ligar a restricao com gente
 * online deixava o {@code /msg} aparecendo para quem nao pode mais usa-lo (e o contrario ao desligar).
 * Como o {@code DerivedConfig} do core, o gatilho e o proprio valor: uma vez por segundo compara com o
 * ultimo visto e, se mudou, reenvia a arvore para quem esta online. Nao ha evento de config para
 * alguem esquecer de ligar.
 */
@EventBusSubscriber(modid = AurorionEssentials.MOD_ID)
public final class PrivateMessageCommands {
    private static final int OP_PERMISSION_LEVEL = 2;
    private static final int CHECK_INTERVAL_TICKS = 20;

    /** {@code null} = ainda nao visto neste servidor. */
    private static Boolean lastRestricted;
    private static int ticks;

    private PrivateMessageCommands() {
    }

    public static boolean allowed(CommandSourceStack source) {
        return !restricted() || source.hasPermission(OP_PERMISSION_LEVEL);
    }

    private static boolean restricted() {
        try {
            return PrivacyConfig.RESTRICT_PRIVATE_MESSAGES.get();
        } catch (IllegalStateException notLoaded) {
            // Config de servidor ainda nao carregada (registro dos comandos no menu, por exemplo): o
            // padrao da config, restrito, e o lado seguro.
            return true;
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (++ticks < CHECK_INTERVAL_TICKS) return;
        ticks = 0;
        boolean now = restricted();
        if (lastRestricted == null) {
            lastRestricted = now;
            return;
        }
        if (lastRestricted == now) return;
        lastRestricted = now;
        resendCommandTrees(event.getServer());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        lastRestricted = null;
        ticks = 0;
    }

    private static void resendCommandTrees(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            server.getCommands().sendCommands(player);
        }
    }
}
