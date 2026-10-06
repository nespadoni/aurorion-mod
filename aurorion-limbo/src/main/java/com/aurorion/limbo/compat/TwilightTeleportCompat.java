package com.aurorion.limbo.compat;

import com.aurorion.limbo.AurorionLimbo;
import net.neoforged.fml.ModList;

import java.lang.reflect.Method;
import java.util.function.Supplier;

/**
 * Faz os teleportes do Limbo acontecerem <b>na hora</b>, mesmo com o Twilight Teleport instalado.
 *
 * <h2>O defeito que isto fecha</h2>
 *
 * <p>O Twilight Teleport intercepta {@code ServerPlayer#changeDimension} e {@code teleportTo} para
 * tocar a animacao dele: devolve o jogador <b>ainda no lugar</b> e agenda a viagem para alguns
 * segundos depois. Para o Limbo isso quebra duas coisas ao mesmo tempo:
 *
 * <ul>
 *   <li>quem chamou ve o jogador na dimensao de origem e conclui que a viagem falhou — o resgate
 *       nao fecha, o resgatador "nao voltou do Limbo";</li>
 *   <li>quando a viagem adiada finalmente roda, a autorizacao da {@code ForgottenDoor} (que so vale
 *       dentro da chamada sincrona) ja foi limpa, o {@code aurorion_portais} trata a saida do Limbo
 *       como dimensao trancada e cancela. No log: "Deferred teleport was rejected".</li>
 * </ul>
 *
 * <p>O sintoma no servidor era o Vinculo de Alma sendo usado e o exilado nunca saindo.
 *
 * <h2>Como</h2>
 *
 * <p>O proprio Twilight deixa passar sem interceptar quando a causa registrada em
 * {@code TeleportCauseContext} e perola do End ou fruta do coro — e assim que ele libera os
 * teleportes que nao sao dele. Marcamos a mesma causa em volta da nossa chamada, pela API publica
 * dele, e desmarcamos no {@code finally}. Por reflexao, como as outras pontes opcionais: o jar dele
 * nao entra no classpath, e sem o mod isto vira uma chamada direta.
 *
 * <p>Nunca lanca: uma versao do Twilight com API diferente desliga a ponte e o teleporte segue sem
 * ela — o mesmo comportamento de antes, nao um servidor que cai.
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
            AurorionLimbo.LOGGER.info("Twilight Teleport encontrado: teleportes do Limbo passam sem adiamento.");
            return true;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            begin = null;
            end = null;
            AurorionLimbo.LOGGER.warn("Twilight Teleport presente mas a ponte nao iniciou ({}); "
                    + "resgates podem falhar se ele adiar a viagem.", e.toString());
            return false;
        }
    }

    private static boolean invoke(Method method) {
        try {
            method.invoke(null, cause);
            return true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            AurorionLimbo.LOGGER.warn("Twilight Teleport recusou a marca de causa ({}).", e.toString());
            return false;
        }
    }
}
