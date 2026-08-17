package com.aurorion.utils.abduction;

import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Paleta de cores nomeadas do feixe de abducao — a lista que aparece como sugestao ao digitar
 * {@code /abduzir <jogador> <cor>}.
 *
 * <p>Nomes em portugues e sem acento de proposito: sao digitados dentro de um comando, entao
 * precisam ser escrevíveis sem depender de teclado com acentuacao. Alem dos nomes, {@link #parse}
 * tambem aceita hexadecimal cru ({@code FF7A00} ou {@code #FF7A00}), pra quem quiser uma cor que
 * nao esta na lista — a mesma funcao serve o comando e o valor {@code beamColorRgb} da config.</p>
 *
 * <p>Tons muito escuros ficam de fora: o feixe e desenhado com blend aditivo (herdado de
 * {@code BeaconRenderer}), entao quanto mais escura a cor menos o feixe aparece — um "preto" seria
 * uma opcao que na pratica nao desenha nada.</p>
 */
public enum BeamColor {
    ROXO(0x9B30FF),
    LILAS(0xC77DFF),
    MAGENTA(0xFF00C0),
    ROSA(0xFF6FD0),
    VERMELHO(0xFF2A2A),
    CARMESIM(0xE01038),
    LARANJA(0xFF7A00),
    DOURADO(0xFFB000),
    AMARELO(0xFFE000),
    LIMA(0xA6FF00),
    VERDE(0x22DD44),
    TURQUESA(0x1FD6B8),
    CIANO(0x00E5FF),
    AZUL(0x2E7DFF),
    ANIL(0x5B4BE0),
    CINZA(0x9AA0A6),
    BRANCO(0xFFFFFF);

    private static final Map<String, BeamColor> BY_ID = new LinkedHashMap<>();
    private static final List<String> IDS;

    static {
        for (BeamColor color : values()) {
            BY_ID.put(color.id(), color);
        }
        IDS = List.copyOf(BY_ID.keySet());
    }

    private final int rgb;

    BeamColor(int rgb) {
        this.rgb = rgb;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public int rgb() {
        return rgb;
    }

    /** Ordem de declaracao, que e a ordem em que as sugestoes aparecem no chat. */
    public static List<String> ids() {
        return IDS;
    }

    /**
     * @return o RGB correspondente, ou {@code null} se {@code input} nao for nem um nome da paleta
     *         nem um hexadecimal RRGGBB valido. Devolver {@code null} (em vez de um branco de
     *         fallback) e o que permite ao {@code BeamColorArgument} rejeitar a entrada e deixar o
     *         Brigadier cair no ramo {@code <destino>} — ver {@code AbductionCommand}.
     */
    @Nullable
    public static Integer parse(String input) {
        if (input == null || input.isEmpty()) return null;

        BeamColor named = BY_ID.get(input.toLowerCase(Locale.ROOT));
        if (named != null) return named.rgb;

        String hex = input.startsWith("#") ? input.substring(1) : input;
        if (hex.length() != 6) return null;

        int value = 0;
        for (int i = 0; i < 6; i++) {
            int digit = Character.digit(hex.charAt(i), 16);
            if (digit < 0) return null;
            value = (value << 4) | digit;
        }
        return value;
    }
}
