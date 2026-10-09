package com.aurorion.magia.compat;

import com.aurorion.magia.AurorionMagia;
import net.neoforged.fml.ModList;

import java.lang.reflect.Method;
import java.util.function.Supplier;

/**
 * Mantem entrada e retorno do duelo sincronizados mesmo com o Twilight Teleport instalado.
 * Usa a mesma API publica opcional que a ponte ja existente no aurorion-limbo.
 * A causa ENDER_PEARL evita a animacao adiada; nao ignora trancas de dimensao de outros mods.
 */
public final class TwilightTeleportCompat {
    private static final String MOD_ID = "twilightteleport";
    private static final String CONTEXT = "net.ochibo.twilightteleport.server.TeleportCauseContext";
    private static final String CAUSE = CONTEXT + "$Cause";
    /** Uma das causas que o Twilight deixa passar sem interceptar (ver PendingTeleportManager). */
    private static final String BYPASS_CAUSE = "ENDER_PEARL";

    private static boolean resolved;
    private static Method begin;
    private static Method end;
    private static Object cause;

    private TwilightTeleportCompat() {
    }

    /** Roda o teleporte sem que o Twilight Teleport o adie. */
    public static <T> T immediate(Supplier<T> teleport) {
        if (!resolve()) return teleport.get();

        boolean begun = invoke(begin);
        try {
            return teleport.get();
        } finally {
            if (begun) invoke(end);
        }
    }

    /** Variante para teleporte sem retorno ({@code teleportTo}). */
    public static void immediateRun(Runnable teleport) {
        immediate(() -> {
            teleport.run();
            return null;
        });
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static boolean resolve() {
        if (resolved) return begin != null;
        resolved = true;
        if (!ModList.get().isLoaded(MOD_ID)) return false;

        try {
            Class<?> context = Class.forName(CONTEXT);
            Class causeType = Class.forName(CAUSE);
            cause = Enum.valueOf(causeType, BYPASS_CAUSE);
            begin = context.getMethod("begin", causeType);
            end = context.getMethod("end", causeType);
            AurorionMagia.LOGGER.info("Twilight Teleport encontrado: teleportes do Reino das Almas passam sem adiamento.");
            return true;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            begin = null;
            end = null;
            AurorionMagia.LOGGER.warn("Twilight Teleport presente mas a ponte nao iniciou ({}); "
                    + "duelos podem falhar se ele adiar a viagem.", e.toString());
            return false;
        }
    }

    private static boolean invoke(Method method) {
        try {
            method.invoke(null, cause);
            return true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            AurorionMagia.LOGGER.warn("Twilight Teleport recusou a marca de causa ({}).", e.toString());
            return false;
        }
    }
}
