package com.aurorion.economia.server;

import com.aurorion.economia.AurorionEconomia;
import net.minecraft.server.MinecraftServer;

import java.lang.reflect.Method;

/** Ponte opcional: economia funciona sem Ethereal, e o projetor atualiza por evento quando existe. */
final class EconomyProjectorNotifier {
    private static boolean resolved;
    private static Method refresh;

    private EconomyProjectorNotifier() { }

    static void refresh(MinecraftServer server) {
        if (!resolve()) return;
        try {
            refresh.invoke(null, server);
        } catch (ReflectiveOperationException error) {
            AurorionEconomia.LOGGER.warn("Nao foi possivel atualizar os Projetores Aeonicos de riqueza.", error);
        }
    }

    private static synchronized boolean resolve() {
        if (resolved) return refresh != null;
        resolved = true;
        try {
            Class<?> service = Class.forName("com.aurorion.ethereal.ranking.BoardService");
            refresh = service.getMethod("refreshEconomy", MinecraftServer.class);
        } catch (ClassNotFoundException ignored) {
            refresh = null;
        } catch (ReflectiveOperationException error) {
            AurorionEconomia.LOGGER.warn("Ethereal presente, mas sem suporte a ranking economico.", error);
            refresh = null;
        }
        return refresh != null;
    }
}
