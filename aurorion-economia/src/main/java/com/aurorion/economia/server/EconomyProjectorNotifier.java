package com.aurorion.economia.server;

import com.aurorion.economia.AurorionEconomia;
import net.minecraft.Util;
import net.minecraft.server.MinecraftServer;

import java.lang.reflect.Method;

/**
 * Ponte opcional: economia funciona sem Ethereal, e o projetor atualiza por evento quando existe.
 *
 * <p>{@link #refresh} so marca que o ranking mudou; quem atualiza de fato e o {@link #tick}, no
 * maximo uma vez a cada {@value #MIN_INTERVAL_MS} ms. Antes cada pagamento recalculava o ranking na
 * hora — e um cliente forjado mandando transferencias de 1 fragmento pelo celular fazia o servidor
 * reordenar e reenviar os projetores dezenas de vezes por segundo.</p>
 */
final class EconomyProjectorNotifier {
    private static final long MIN_INTERVAL_MS = 1_000L;
    private static boolean resolved;
    private static Method refresh;
    private static boolean pending;
    private static long last;

    private EconomyProjectorNotifier() { }

    static void refresh(MinecraftServer server) {
        pending = true;
    }

    /** Chamado a cada tick do servidor; barato quando nada mudou. */
    static void tick(MinecraftServer server) {
        if (!pending) return;
        long now = Util.getMillis();
        if (now - last < MIN_INTERVAL_MS) return;
        pending = false;
        last = now;
        if (!resolve()) return;
        try {
            refresh.invoke(null, server);
        } catch (ReflectiveOperationException error) {
            AurorionEconomia.LOGGER.warn("Nao foi possivel atualizar os Projetores Aeonicos de riqueza.", error);
        }
    }

    static void clear() {
        pending = false;
        last = 0L;
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
