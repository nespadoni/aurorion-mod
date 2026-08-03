package com.aurorion.talk.client.render;

/**
 * Layout nine-slice do balao, em espaco de pixels da folha 32x32 — sem depender de nenhuma API de
 * render. Usado tanto pelo desenho 3D acima da cabeca do jogador quanto pelo preview 2D da GUI, para
 * as duas versoes nunca desalinharem uma da outra.
 */
public final class BalloonNineSlice {
    /** Recebe uma caixa no espaco de pixels da folha: (x, y, largura, altura, u, v, uLargura, vAltura). */
    public interface BoxConsumer {
        void box(int x, int y, int w, int h, int u, int v, int uw, int vh);
    }

    private BalloonNineSlice() {
    }

    /** Topo do balao (Y mais negativo), usado tambem para posicionar o enfeite. */
    public static int top(int lineCount, int stackOffset) {
        int extraLines = lineCount - 1;
        return (-lineCount - extraLines * 7) - extraLines - stackOffset;
    }

    /**
     * Emite as 9 caixas do balao (3 colunas x topo/meio/base) e, se pedido, a seta.
     *
     * @param width       largura total do balao, em pixels (deve ser impar — quem chama garante isso)
     * @param lineCount   quantas linhas de texto o balao precisa acomodar
     * @param stackOffset deslocamento vertical por causa de baloes empilhados acima
     * @param withArrow   desenha a seta apontando para a cabeca (so o balao mais recente tem)
     */
    public static void emit(BoxConsumer box, int width, int lineCount, int stackOffset, boolean withArrow) {
        int extraLines = lineCount - 1;
        int halfWidth = width / 2;
        int top = top(lineCount, stackOffset);
        int midHeight = lineCount + extraLines * 8;
        int bottom = 5 - stackOffset;
        int midWidth = width - 4;

        // Coluna esquerda
        box.box(-halfWidth - 2, top, 5, 5, 0, 0, 5, 5);
        box.box(-halfWidth - 2, top + 5, 5, midHeight, 0, 6, 5, 1);
        box.box(-halfWidth - 2, bottom, 5, 5, 0, 8, 5, 5);

        // Miolo
        box.box(-halfWidth + 3, top, midWidth, 5, 6, 0, 5, 5);
        box.box(-halfWidth + 3, top + 5, midWidth, midHeight, 6, 6, 5, 1);
        box.box(-halfWidth + 3, bottom, midWidth, 5, 6, 8, 5, 5);

        // Coluna direita
        box.box(halfWidth - 2, top, 5, 5, 12, 0, 5, 5);
        box.box(halfWidth - 2, top + 5, 5, midHeight, 12, 6, 5, 1);
        box.box(halfWidth - 2, bottom, 5, 5, 12, 8, 5, 5);

        if (withArrow) box.box(-3, 9, 7, 4, 18, 6, 7, 4);
    }
}
