package com.aurorion.integracao.outbox;

import java.util.List;

/**
 * O que aconteceu numa tentativa de envio de um lote.
 *
 * @param resolved   por posicao do lote: {@code true} se o backend deu uma resposta final ao fato
 *                   (aceito, duplicado ou rejeitado). So faz sentido em {@link Kind#DELIVERED}.
 * @param waitMillis em {@link Kind#RETRY}, quanto o servidor pediu para esperar (0 = backoff normal).
 * @param rejections codigos de rejeicao devolvidos, para o log (nunca o corpo do fato).
 */
public record Attempt(Kind kind, boolean[] resolved, long waitMillis, List<String> rejections, String detail) {

    public enum Kind {
        /** O backend respondeu item a item. */
        DELIVERED,
        /** Falha transitoria: rede, 5xx, 429. O lote fica e volta depois. */
        RETRY,
        /** 401/403: credencial errada. Insistir nao resolve; espera longa e aviso no log. */
        FORBIDDEN
    }

    public static Attempt retry(long waitMillis, String detail) {
        return new Attempt(Kind.RETRY, new boolean[0], waitMillis, List.of(), detail);
    }

    public static Attempt forbidden(String detail) {
        return new Attempt(Kind.FORBIDDEN, new boolean[0], 0, List.of(), detail);
    }
}
