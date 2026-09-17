package com.aurorion.ethereal.ranking;

import net.minecraft.network.chat.Component;

/**
 * O que um projetor mostra — as "metas" do Projetor Aeonico.
 *
 * <p>As cinco primeiras sao as do mod de referencia, com o mesmo simbolo e a mesma ordem; as duas
 * ultimas ja existiam no placar do ecossistema. Estao no mesmo enum porque sao a mesma pergunta com
 * chaves diferentes — "ordene X por Y".
 *
 * @param houses    true quando o ranking e de casas; false quando e de jogadores.
 * @param ascending true quando o placar celebra o menor numero (as casas piores avaliadas).
 */
public enum BoardMode {
    TOP_HOUSES("top_houses", "★", true, false),
    WORST_HOUSES("worst_houses", "↓", true, true),
    TOP_PLAYERS("top_players", "◆", false, false),
    WORST_PLAYERS("worst_players", "◇", false, true),
    MISSIONS("missions", "✎", false, false),
    DEATHS("deaths", "☠", false, false),
    DUEL_WINS("duel_wins", "⚔", false, false),
    RICHEST_HOUSES("richest_houses", "$", true, false),
    RICHEST_PLAYERS("richest_players", "$", false, false);

    private final String id;
    private final String icon;
    private final boolean houses;
    private final boolean ascending;

    BoardMode(String id, String icon, boolean houses, boolean ascending) {
        this.id = id;
        this.icon = icon;
        this.houses = houses;
        this.ascending = ascending;
    }

    public Component displayName() {
        return Component.translatable("gui.aurorion_ethereal.mode." + id);
    }

    /**
     * A linha de ranking deste modo, ja com o numero e a unidade certa.
     *
     * <p>E um {@link Component} e nao texto pronto porque "pts", "missoes" e "mortes" sao palavras:
     * montadas como string no servidor, elas sairiam no idioma do <em>servidor</em> para todo mundo.
     * O projetor do mod de referencia tinha isso fixo em portugues dentro do codigo.
     */
    public Component line(int rank, String label, int value) {
        return Component.translatable("aurorion_ethereal.board.line." + id, rank, label, value);
    }

    /** O simbolo que abre o cabecalho do holograma. */
    public String icon() {
        return icon;
    }

    public String id() {
        return id;
    }

    public boolean isHouseMode() {
        return houses;
    }

    public boolean isAscending() {
        return ascending;
    }

    public BoardMode next() {
        BoardMode[] values = values();
        return values[(ordinal() + 1) % values.length];
    }

    /** Modo trafegado como ordinal na rede; um valor fora da faixa volta ao padrao em vez de estourar. */
    public static BoardMode byOrdinal(int ordinal) {
        BoardMode[] values = values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : TOP_HOUSES;
    }

    /**
     * Modo gravado como id no bloco.
     *
     * <p>Id e nao ordinal em disco de proposito: acrescentar um modo no meio do enum um dia nao pode
     * fazer todos os projetores ja construidos no mundo trocarem de conteudo sozinhos.
     */
    public static BoardMode byId(String id) {
        for (BoardMode mode : values()) {
            if (mode.id.equals(id)) {
                return mode;
            }
        }
        return TOP_HOUSES;
    }
}
