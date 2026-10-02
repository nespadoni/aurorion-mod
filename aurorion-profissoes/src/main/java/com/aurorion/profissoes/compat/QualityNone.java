package com.aurorion.profissoes.compat;

import org.jetbrains.annotations.Nullable;

/**
 * O "sem qualidade" do Quality Food, do jeito que ele reconhece: <b>pela referencia</b>.
 *
 * <h2>O bug</h2>
 *
 * <p>O Quality Food decide se desenha o icone de qualidade no slot com {@code quality == Quality.NONE}
 * ({@code GuiGraphicsMixin}, conferido com {@code javap} na 2.3.6) — o mesmo {@code ==} aparece em
 * {@code Quality.getType()} e {@code QualityUtils.isValidQuality}. O {@code Quality} e um record: salvar o
 * item em disco ou manda-lo ao cliente devolve uma <em>copia</em> igual ao NONE, mas outro objeto. A
 * copia passa no teste, o tipo cai em {@code QualityType.NONE}, cujo icone e {@code quality_food:none} —
 * sprite que nao existe no jar. Resultado: o xadrez roxo e preto na frente da comida sem qualidade.
 *
 * <p>Quem gravava o NONE no item era o proprio {@link FoodCompat#finish} (comida de quem nao e chef).
 * Agora ele tira o componente em vez de gravar NONE, e o {@code QualityUtilsMixin} troca qualquer copia
 * pelo original na leitura — o que conserta tambem a comida que ja esta espalhada pelos baus.
 *
 * <p>Carregada so quando o Quality Food esta presente (pelo mixin, que nao aplica sem ele, e pelo
 * {@link FoodCompat}, que so chega aqui com o componente registrado). Sem o mod, os campos ficam nulos e
 * tudo aqui vira identidade.
 */
public final class QualityNone {
    private static final String QUALITY = "de.cadentem.quality_food.core.codecs.Quality";

    @Nullable private static final Object NONE = constant("NONE");
    @Nullable private static final Object PLAYER_PLACED = constant("PLAYER_PLACED");

    private QualityNone() {}

    @Nullable
    private static Object constant(String field) {
        try {
            return Class.forName(QUALITY).getField(field).get(null);
        } catch (ReflectiveOperationException | LinkageError error) {
            return null;
        }
    }

    /**
     * A constante do Quality Food quando {@code quality} e uma copia dela; do contrario o proprio valor.
     * Roda a cada item desenhado na tela, entao e so {@code equals} de record, sem reflexao.
     */
    @Nullable
    public static Object canonical(@Nullable Object quality) {
        if (quality == null || quality == NONE || quality == PLAYER_PLACED) return quality;
        if (quality.equals(NONE)) return NONE;
        if (quality.equals(PLAYER_PLACED)) return PLAYER_PLACED;
        return quality;
    }

    /** Sem componente e componente "none" sao a mesma coisa para quem joga. */
    public static boolean isNone(@Nullable Object quality) {
        return quality == null || NONE != null && NONE.equals(quality);
    }
}
