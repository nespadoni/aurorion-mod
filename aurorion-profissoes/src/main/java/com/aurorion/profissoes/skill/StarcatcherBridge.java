package com.aurorion.profissoes.skill;

import com.aurorion.profissoes.AurorionProfissoes;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;

/**
 * Chama de novo o {@code FishApi.spawnFishFromPlayerFishing} do Starcatcher (o segundo peixe do crítico de
 * pesca) sem o profissões compilar contra o mod. O método é achado uma vez; se a assinatura mudar numa
 * versão nova, o crítico só deixa de dar o peixe extra, e o log avisa uma vez.
 */
public final class StarcatcherBridge {
    @Nullable private static Method spawn;
    private static boolean resolved;

    private StarcatcherBridge() {
    }

    public static void spawnAgain(ServerPlayer player, boolean a, int b, boolean c, boolean d, int e) {
        Method method = method();
        if (method == null) return;
        try {
            method.invoke(null, player, a, b, c, d, e);
        } catch (ReflectiveOperationException | RuntimeException error) {
            AurorionProfissoes.LOGGER.warn("Ofício: o peixe extra do Starcatcher falhou", error);
        }
    }

    @Nullable
    private static synchronized Method method() {
        if (!resolved) {
            resolved = true;
            try {
                spawn = Class.forName("com.wdiscute.starcatcher.fish.FishApi").getMethod("spawnFishFromPlayerFishing",
                        ServerPlayer.class, boolean.class, int.class, boolean.class, boolean.class, int.class);
            } catch (ReflectiveOperationException | LinkageError error) {
                AurorionProfissoes.LOGGER.warn("Ofício: FishApi do Starcatcher mudou; o crítico de pesca não dá o peixe extra nele.");
            }
        }
        return spawn;
    }
}
