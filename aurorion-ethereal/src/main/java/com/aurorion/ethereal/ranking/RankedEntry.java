package com.aurorion.ethereal.ranking;

/**
 * Uma linha de ranking pronta para exibir.
 *
 * @param id    UUID do jogador ou id da casa — e por ele que a GUI de pontuacao pede um ajuste.
 * @param color ARGB da linha, ou {@code 0} para a cor padrao do placar. E o que faz o ranking de
 *              casas sair no holograma com a cor de cada casa, que e o ponto de casa ter cor.
 */
public record RankedEntry(String id, String label, int value, int color) {
    public static final int DEFAULT_COLOR = 0;

    public RankedEntry(String id, String label, int value) {
        this(id, label, value, DEFAULT_COLOR);
    }
}
