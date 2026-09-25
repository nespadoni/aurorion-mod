package com.aurorion.vidas.compat;

import com.aurorion.vidas.AurorionVidas;
import net.minecraft.server.MinecraftServer;

/** Atualiza o Projetor Aeonico sem tornar aurorion-vidas dependente do Ethereal. */
public final class EtherealProjectorNotifier {
    private EtherealProjectorNotifier() { }

    public static void refreshLives(MinecraftServer server) {
        try {
            Class<?> service = Class.forName("com.aurorion.ethereal.ranking.BoardService");
            service.getMethod("refreshLives", MinecraftServer.class).invoke(null, server);
        } catch (ClassNotFoundException ignored) {
            // Ethereal nao esta carregado neste ambiente.
        } catch (ReflectiveOperationException error) {
            AurorionVidas.LOGGER.warn("Ethereal presente, mas sem suporte ao placar de vidas.", error);
        }
    }
}
