package com.aurorion.magia.compat;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Quem esta sem voz, e ate quando.
 *
 * <p>Separado do efeito de status porque o Simple Voice Chat consulta isto da <b>thread de audio</b>,
 * a cada pacote de microfone (dezenas por segundo por pessoa falando). Ler efeito de entidade de
 * outra thread nao e seguro; um mapa concorrente com prazo em relogio de parede e.
 *
 * <h2>Quem manda e o efeito, nao o relogio</h2>
 *
 * <p>O fim do efeito (vencer, leite, {@code /effect clear}) chama {@link #unmute} — e isso que solta a
 * voz na hora certa. O relogio de parede e so a trava de seguranca para um fim que ninguem viu, e por
 * isso e folgado: o dobro da duracao mais {@value #SLACK_MILLIS} ms. Com prazo exato, um servidor a
 * 10 TPS devolvia o microfone na metade do silencio, porque o efeito anda em ticks e o relogio nao.
 *
 * <p>Sair do jogo tira a entrada ({@link #unmute} no logout) e entrar a refaz com o que sobra do
 * efeito ({@link #restore}): o efeito nao anda com a pessoa offline, e o relogio andava — quem
 * deslogava silenciado voltava falando no Voice Chat com o efeito ainda na tela.
 */
public final class VoiceMute {
    private static final Map<UUID, Long> MUTED_UNTIL = new ConcurrentHashMap<>();
    private static final long SLACK_MILLIS = 5000;

    private VoiceMute() {
    }

    /** @param millis a duracao do efeito, em milissegundos ({@code ticks * 50}) */
    public static void mute(UUID player, long millis) {
        MUTED_UNTIL.put(player, System.currentTimeMillis() + millis * 2 + SLACK_MILLIS);
    }

    /**
     * Entrou com o silencio ainda ativo: a voz volta a ficar muda pelo que sobra dele.
     *
     * @param remainingTicks duracao que falta no efeito; negativo = sem prazo
     */
    public static void restore(UUID player, int remainingTicks) {
        mute(player, remainingTicks < 0 ? Long.MAX_VALUE / 4 : remainingTicks * 50L);
    }

    public static void unmute(UUID player) {
        MUTED_UNTIL.remove(player);
    }

    /** Custo por pacote de voz: uma busca em hash e, quando o prazo venceu, uma remocao. */
    public static boolean isMuted(UUID player) {
        Long until = MUTED_UNTIL.get(player);
        if (until == null) return false;
        if (until > System.currentTimeMillis()) return true;
        MUTED_UNTIL.remove(player, until);
        return false;
    }

    public static void clear() {
        MUTED_UNTIL.clear();
    }
}
