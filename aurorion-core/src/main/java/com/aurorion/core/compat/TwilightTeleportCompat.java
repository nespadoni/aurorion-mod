package com.aurorion.core.compat;

import com.aurorion.core.AurorionCore;
import net.neoforged.fml.ModList;

import java.lang.reflect.Method;
import java.util.function.Supplier;

/**
 * Faz um teleporte nosso acontecer <b>na hora</b>, mesmo com o Twilight Teleport instalado.
 *
 * <p>Estava escrito duas vezes, no {@code aurorion-limbo} e no {@code aurorion-magia}, com diferenca
 * so na mensagem de log; a batalha de intencao seria a terceira copia.
 *
 * <h2>O defeito que isto fecha</h2>
 *
 * <p>O Twilight Teleport intercepta {@code ServerPlayer#changeDimension} e {@code teleportTo} para
 * tocar a animacao dele: devolve o jogador <b>ainda no lugar</b> e agenda a viagem para alguns
 * segundos depois. Quem teleporta e confere o resultado logo em seguida quebra:
 *
 * <ul>
 *   <li>quem chamou ve o jogador na dimensao de origem e conclui que a viagem falhou — o resgate do
 *       Limbo nao fecha, o duelo do Reino das Almas desfaz metade;</li>
 *   <li>quando a viagem adiada finalmente roda, a autorizacao que so vale dentro da chamada sincrona
 *       (a {@code ForgottenDoor} do Limbo) ja foi limpa, e o {@code aurorion_portais} trata a saida
 *       como dimensao trancada e cancela. No log: "Deferred teleport was rejected".</li>
 * </ul>
 *
 * <h2>Como</h2>
 *
 * <p>O proprio Twilight deixa passar sem interceptar quando a causa registrada em
 * {@code TeleportCauseContext} e perola do End ou fruta do coro — e assim que ele libera os
 * teleportes que nao sao dele. Marcamos a mesma causa em volta da nossa chamada, pela API publica
 * dele, e desmarcamos no {@code finally}. Por reflexao, como as outras pontes opcionais: o jar dele
 * nao entra no classpath, e sem o mod isto vira uma chamada direta. Nao ignora trancas de dimensao
 * de outros mods: so evita o adiamento.
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
    private static synchronized boolean resolve() {
        if (resolved) return begin != null;
        resolved = true;
        if (!ModList.get().isLoaded(MOD_ID)) return false;

        try {
            Class<?> context = Class.forName(CONTEXT);
            Class causeType = Class.forName(CAUSE);
            cause = Enum.valueOf(causeType, BYPASS_CAUSE);
            begin = context.getMethod("begin", causeType);
            end = context.getMethod("end", causeType);
            AurorionCore.LOGGER.info("Twilight Teleport encontrado: teleportes do Aurorion passam sem adiamento.");
            return true;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            begin = null;
            end = null;
            AurorionCore.LOGGER.warn("Twilight Teleport presente mas a ponte nao iniciou ({}); "
                    + "resgates do Limbo, duelos e batalhas de intencao podem falhar se ele adiar a viagem.",
                    e.toString());
            return false;
        }
    }

    private static boolean invoke(Method method) {
        try {
            method.invoke(null, cause);
            return true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            AurorionCore.LOGGER.warn("Twilight Teleport recusou a marca de causa ({}).", e.toString());
            return false;
        }
    }
}
