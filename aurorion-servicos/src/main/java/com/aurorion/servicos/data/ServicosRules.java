package com.aurorion.servicos.data;

import java.util.UUID;

/**
 * As regras do app, sem Minecraft: limites, limpeza de texto, quem pode fazer o que com um pedido e
 * quando as coisas expiram. O {@code ServicosManager} so consulta daqui — e o que o teste cobre.
 */
public final class ServicosRules {
    public static final int MAX_ADS = 3;
    public static final int MAX_OPEN_ORDERS = 3;
    public static final int MAX_JOBS = 3;
    public static final int MAX_CANDIDATES = 50;

    /**
     * Pedidos novos (diretos ou abertos) por conta por dia UTC. Sem isto, criar e cancelar em
     * sequencia gravava um pedido a cada toque no save e avisava todos os profissionais a cada vez:
     * um jogador sozinho enchia o {@code aurorion_servicos.dat} e o celular de todo mundo.
     */
    public static final int MAX_ORDERS_PER_DAY = 12;
    /**
     * Teto do servidor inteiro. Ao chegar nele, os pedidos encerrados mais antigos saem primeiro; so
     * se todos estiverem em andamento o pedido novo e recusado. Com alts, o limite por conta nao
     * basta: este e o que mantem o arquivo e as buscas lineares do tamanho de "centenas".
     */
    public static final int MAX_ORDERS_TOTAL = 2_000;
    /** Intervalo minimo entre dois pedidos novos da mesma conta. */
    public static final long ORDER_COOLDOWN_MS = 20_000L;
    /** Um profissional recebe no maximo um aviso de pedido/candidato neste intervalo; o selo conta o resto. */
    public static final long NOTICE_COOLDOWN_MS = 5_000L;

    public static final int TITLE = 40;
    public static final int DESCRIPTION = 160;
    public static final int PRICE = 24;
    public static final int ORDER_TEXT = 120;

    private static final long MINUTE = 60_000L;
    private static final long HOUR = 60 * MINUTE;
    private static final long DAY = 24 * HOUR;

    /** Pedido que ninguem aceitou em 2 h ja nao e urgencia de ninguem. */
    public static final long OPEN_ORDER_TTL = 2 * HOUR;
    /** Aceito e nunca concluido: da-se por concluido depois de um dia. */
    public static final long ACCEPTED_TTL = DAY;
    /** Concluido, cancelado, recusado ou expirado some da lista um dia depois. */
    public static final long FINISHED_TTL = DAY;
    public static final long JOB_TTL = 14 * DAY;
    /** Anuncio de quem nao entra ha 30 dias sai do app. */
    public static final long AD_IDLE_TTL = 30 * DAY;

    private ServicosRules() {
    }

    /**
     * Texto vindo do cliente: sem codigo de cor ({@code §}), sem caractere de controle, espacos
     * colapsados e no maximo {@code max} caracteres.
     */
    public static String clean(String raw, int max) {
        if (raw == null) return "";
        StringBuilder out = new StringBuilder(Math.min(raw.length(), max));
        boolean space = false;
        for (int i = 0; i < raw.length() && out.length() < max; i++) {
            char c = raw.charAt(i);
            if (c == '§') {
                i++; // o codigo de formatacao vem logo depois: "§c" some inteiro
                continue;
            }
            if (Character.isISOControl(c) && !Character.isWhitespace(c)) continue;
            if (Character.isWhitespace(c)) {
                space = out.length() > 0;
                continue;
            }
            if (space && out.length() < max - 1) out.append(' ');
            space = false;
            out.append(c);
        }
        return out.toString();
    }

    /**
     * @param providesCategory quem tenta aceitar tem anuncio na area do pedido — so profissional da
     *                         area pega pedido aberto
     */
    public static boolean canAccept(Pedido pedido, UUID who, boolean providesCategory) {
        if (pedido.status() != PedidoStatus.ABERTO || who.equals(pedido.requester())) return false;
        return pedido.direct() ? who.equals(pedido.target()) : providesCategory;
    }

    public static boolean canRefuse(Pedido pedido, UUID who) {
        return pedido.status() == PedidoStatus.ABERTO && pedido.direct() && who.equals(pedido.target());
    }

    public static boolean canComplete(Pedido pedido, UUID who) {
        return pedido.status() == PedidoStatus.ACEITO && (who.equals(pedido.requester()) || who.equals(pedido.provider()));
    }

    /** O cliente desiste enquanto ninguem fez; depois de aceito, qualquer dos dois desiste. */
    public static boolean canCancel(Pedido pedido, UUID who) {
        if (pedido.status() == PedidoStatus.ABERTO) return who.equals(pedido.requester());
        return pedido.status() == PedidoStatus.ACEITO && (who.equals(pedido.requester()) || who.equals(pedido.provider()));
    }

    /** O pedido como fica com o passar do tempo: aberto vence, aceito se da por concluido. */
    public static Pedido age(Pedido pedido, long now) {
        if (pedido.status() == PedidoStatus.ABERTO && now - pedido.createdAt() >= OPEN_ORDER_TTL) {
            return pedido.with(PedidoStatus.EXPIRADO, pedido.provider(), now);
        }
        if (pedido.status() == PedidoStatus.ACEITO && now - pedido.updatedAt() >= ACCEPTED_TTL) {
            return pedido.with(PedidoStatus.CONCLUIDO, pedido.provider(), now);
        }
        return pedido;
    }

    /**
     * Pedido cancelado antes de alguem aceitar nao tem historia para guardar: sai do save na hora,
     * em vez de ficar um dia como "Cancelado". E o que impede o ciclo criar/cancelar de crescer o
     * arquivo — a cota diaria continua contando o pedido.
     */
    public static boolean discardOnCancel(Pedido pedido) {
        return pedido.status() == PedidoStatus.ABERTO && pedido.provider() == null;
    }

    /** O dia (UTC) da cota diaria. Pura para o teste; virada a meia-noite UTC, igual para todos. */
    public static long dayOf(long now) {
        return Math.floorDiv(now, DAY);
    }

    public static boolean purge(Pedido pedido, long now) {
        return pedido.status().finished() && now - pedido.updatedAt() >= FINISHED_TTL;
    }

    public static boolean jobExpired(Vaga vaga, long now) {
        return now - vaga.createdAt() >= JOB_TTL;
    }

    public static boolean adExpired(Anuncio anuncio, long now) {
        return now - anuncio.seenAt() >= AD_IDLE_TTL;
    }
}
