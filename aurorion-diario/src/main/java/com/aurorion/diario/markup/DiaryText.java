package com.aurorion.diario.markup;

import java.util.regex.Pattern;

/**
 * Tetos de texto entre o site, o servidor e a tela do jogo.
 *
 * <p>Sao duas contas diferentes, e misturar as duas derrubava jogador: o backend limita por
 * <b>caractere Unicode</b> (titulo 200, texto 30 mil), e o {@code writeUtf(texto, max)} do Minecraft
 * conta <b>unidades UTF-16</b> — um emoji vale um para o site e dois para o pacote. Um titulo legitimo
 * de 200 caracteres com emoji estourava o pacote, o encoder lancava excecao e o Netty desconectava a
 * pessoa. Por isso todo texto que sai do servidor passa por aqui antes de virar pacote: corta pelo teto
 * do pacote sem partir um par substituto, e texto grande demais para o jogo vira um aviso, nunca um
 * pedaco.
 */
public final class DiaryText {
    /**
     * Mostrado no lugar do texto que nao cabe no pacote. A entrada abre so para leitura, e o servidor
     * recusa gravar um texto que contenha esta linha: salvar o aviso apagaria o texto de verdade no site.
     */
    public static final String TOO_LONG = "[[texto longo demais para o jogo — abra no site]]";

    private static final Pattern KEY = Pattern.compile("[A-Za-z0-9-]{1,40}");

    private DiaryText() {
    }

    /**
     * Corta em ate {@code maxUnits} unidades UTF-16 — a conta do {@code writeUtf} — sem deixar um
     * surrogate sozinho no fim. {@code null} vira texto vazio.
     */
    public static String clip(String text, int maxUnits) {
        if (text == null) return "";
        if (text.length() <= maxUnits) return text;
        int end = Math.max(0, maxUnits);
        if (end > 0 && Character.isHighSurrogate(text.charAt(end - 1))) end--;
        return text.substring(0, end);
    }

    /** Corta em ate {@code maxCodePoints} caracteres Unicode — a conta do backend. */
    public static String clipCodePoints(String text, int maxCodePoints) {
        if (text == null) return "";
        if (text.length() <= maxCodePoints) return text; // cada caractere ocupa ao menos uma unidade
        if (text.codePointCount(0, text.length()) <= maxCodePoints) return text;
        return text.substring(0, text.offsetByCodePoints(0, Math.max(0, maxCodePoints)));
    }

    /** O texto inteiro, se couber em {@code maxUnits}; senao o aviso {@link #TOO_LONG}. */
    public static String fitOrPlaceholder(String markup, int maxUnits) {
        if (markup == null) return "";
        return markup.length() <= maxUnits ? markup : TOO_LONG;
    }

    public static boolean isPlaceholder(String markup) {
        return markup != null && markup.contains(TOO_LONG);
    }

    /**
     * Chave de sessao de edicao e id de operacao vindos do cliente: UUID ou parecido. Qualquer outra
     * coisa e recusada — inclusive quebra de linha, que forjaria linhas no log do servidor.
     */
    public static boolean validKey(String key) {
        return key != null && KEY.matcher(key).matches();
    }
}
