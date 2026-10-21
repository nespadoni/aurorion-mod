package com.aurorion.essentials.fakename;

import java.text.Normalizer;
import java.util.Locale;

/**
 * A forma comparavel de um nome exibido: o que o olho le, e nao os bytes que foram digitados.
 *
 * <p>Nome de personagem e a identidade que o servidor mostra — no chat, na tab, na agenda do celular
 * e no destinatario do PIX. Se duas grafias que a pessoa enxerga iguais comparassem diferente, a
 * protecao contra se passar por outro jogador era contornada com um caractere que nao aparece:
 * espaco de largura zero, marca de direcao (bidi), "Joao" com acento, letra cirilica no lugar da
 * latina, duas barras de espaco, codigo de cor no meio. Aqui tudo isso cai antes da comparacao:
 * <ol>
 *   <li>codigos de cor ({@code &6}, {@code §l}) — so mudam a aparencia;</li>
 *   <li>NFKC — letra de largura cheia, ligadura e sobrescrito viram a letra comum;</li>
 *   <li>caracteres invisiveis (controle, formato/bidi, uso privado, nao atribuidos, preenchedores);</li>
 *   <li>acentos e marcas combinantes (como o {@code CharacterName.key} do core);</li>
 *   <li>as letras cirilicas/gregas que tem cara de letra latina;</li>
 *   <li>espacos em sequencia viram um, sem espaco nas pontas, e sem diferenca de maiuscula.</li>
 * </ol>
 *
 * <p>Classe pura de proposito (nao conhece Minecraft): a mesma regra vale no servidor, ao reservar o
 * nome, e no cliente, ao resolver o nome que alguem digitou no celular — e da para testar sem o jogo.
 */
public final class NameKey {
    /** Cirilico e grego com cara de latino, na ordem de {@link #HOMOGLYPH_LATIN}. Ja em minusculas. */
    private static final String HOMOGLYPHS =
            "аеорсухіјѕ"      // а е о р с у х і ј ѕ
            + "ԁӏвкмнтз"                 // ԁ ӏ в к м н т з
            + "οανρτικυχ";         // ο α ν ρ τ ι κ υ χ
    private static final String HOMOGLYPH_LATIN = "aeopcyxijsdlbkmhtzoavptikux";

    private NameKey() {
    }

    /** A chave de comparacao. Vazia quando nao sobra nada visivel. */
    public static String of(String name) {
        if (name == null) return "";
        String text = stripFormatting(Normalizer.normalize(stripFormatting(name), Normalizer.Form.NFKC));
        text = Normalizer.normalize(text, Normalizer.Form.NFD);

        StringBuilder out = new StringBuilder(text.length());
        boolean pendingSpace = false;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);
            if (isSpace(cp)) {
                pendingSpace = !out.isEmpty();
                continue;
            }
            if (isInvisible(cp) || isMark(cp)) continue;
            if (pendingSpace) {
                out.append(' ');
                pendingSpace = false;
            }
            out.appendCodePoint(fold(cp));
        }
        return out.toString();
    }

    /**
     * Ha algum caractere que nao aparece na tela (controle, bidi, largura zero, preenchedor)? Um nome
     * assim e recusado de saida — nao existe motivo legitimo para um nome de personagem ter um.
     */
    public static boolean hasHiddenCharacters(String name) {
        for (int i = 0; i < name.length(); ) {
            int cp = name.codePointAt(i);
            i += Character.charCount(cp);
            if (cp == ' ') continue;
            if (isInvisible(cp) || (isSpace(cp))) return true;
        }
        return false;
    }

    /** Tira {@code &x}/{@code §x} quando {@code x} e um codigo de formatacao do Minecraft. */
    static String stripFormatting(String text) {
        StringBuilder out = null;
        int length = text.length();
        for (int i = 0; i < length; i++) {
            char c = text.charAt(i);
            if ((c == '&' || c == '§') && i + 1 < length && isFormattingCode(text.charAt(i + 1))) {
                if (out == null) out = new StringBuilder(length).append(text, 0, i);
                i++;
                continue;
            }
            if (out != null) out.append(c);
        }
        return out == null ? text : out.toString();
    }

    private static boolean isFormattingCode(char c) {
        char lower = Character.toLowerCase(c);
        return (lower >= '0' && lower <= '9') || (lower >= 'a' && lower <= 'f') || (lower >= 'k' && lower <= 'o')
                || lower == 'r';
    }

    private static boolean isSpace(int cp) {
        return Character.isWhitespace(cp) || Character.isSpaceChar(cp);
    }

    private static boolean isMark(int cp) {
        int type = Character.getType(cp);
        return type == Character.NON_SPACING_MARK || type == Character.ENCLOSING_MARK
                || type == Character.COMBINING_SPACING_MARK;
    }

    /** Controle, formato (inclui bidi e largura zero), uso privado, nao atribuido e os preenchedores. */
    private static boolean isInvisible(int cp) {
        if (isSpace(cp)) return false;
        int type = Character.getType(cp);
        if (type == Character.CONTROL || type == Character.FORMAT || type == Character.PRIVATE_USE
                || type == Character.SURROGATE || type == Character.UNASSIGNED) return true;
        // Letras e simbolos que o Unicode classifica como visiveis, mas que desenham nada.
        return cp == 0x115F || cp == 0x1160 || cp == 0x3164 || cp == 0xFFA0 || cp == 0x2800
                || cp == 0x034F || (cp >= 0xFE00 && cp <= 0xFE0F) || (cp >= 0xE0100 && cp <= 0xE01EF)
                || cp == 0x180E || cp == 0x17B4 || cp == 0x17B5;
    }

    private static int fold(int cp) {
        int lower = Character.toLowerCase(Character.toUpperCase(cp));
        int homoglyph = HOMOGLYPHS.indexOf(lower);
        return homoglyph >= 0 ? HOMOGLYPH_LATIN.charAt(homoglyph) : lower;
    }
}
