package com.aurorion.core.character;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Display text is never an identifier. Formatting codes and control characters are not names.
 *
 * <h2>Por que so letras latinas</h2>
 *
 * <p>O nome do personagem e o que os outros leem no chat, na lista e no site. Aceitar qualquer letra
 * do Unicode deixava criar "Alda" com o {@code а} cirilico, ou com o {@code Ａ} de largura cheia, e
 * passar pelo indice de nomes como outra pessoa; marcas combinantes sem limite empilhavam acentos ate
 * esconder o texto ao redor. Os nomes sao em portugues, entao a regra e: letras latinas, com os acentos
 * do latim (precompostos ou, no maximo, uma marca por letra e quatro no nome todo), tudo em NFC.
 *
 * <p>A regra vale so para nome <b>novo</b> — o construtor e chamado na criacao e no renomear. Nomes ja
 * gravados sao lidos como texto pelo {@link CharacterData} e continuam carregando como estao.
 */
public record CharacterName(String firstName, String lastName) {
    public static final int PART_LIMIT = 24;
    public static final int FULL_LIMIT = 48;
    /** Marcas combinantes no nome inteiro, depois do NFC (acento comum ja vira letra precomposta). */
    public static final int MAX_MARKS = 4;
    private static final String SEPARATORS = " '’-";

    public CharacterName {
        firstName = normalize(firstName);
        lastName = normalize(lastName);
        int first = marksIn(firstName);
        int last = marksIn(lastName);
        if (first < 0 || last < 0 || first + last > MAX_MARKS
                || firstName.length() + lastName.length() + 1 > FULL_LIMIT)
            throw new IllegalArgumentException(
                    "Informe nome e sobrenome: só letras do alfabeto latino (acentos valem), de 2 a 24 caracteres cada, até 48 no total.");
    }

    private static String normalize(String value) {
        return Normalizer.normalize(value.strip(), Normalizer.Form.NFC).replaceAll(" +", " ");
    }

    /**
     * Confere uma parte do nome: grupos de letras separados por um espaco, apostrofo ou hifen.
     *
     * @return quantas marcas combinantes a parte usa, ou -1 se ela nao e um nome
     */
    private static int marksIn(String value) {
        if (value.length() < 2 || value.length() > PART_LIMIT) return -1;
        int marks = 0;
        boolean needLetter = true; // comeco, ou logo depois de um separador
        boolean markable = false;  // a ultima coisa foi uma letra ainda sem marca
        for (int i = 0; i < value.length(); ) {
            int cp = value.codePointAt(i);
            i += Character.charCount(cp);
            if (isNameLetter(cp)) {
                needLetter = false;
                markable = true;
            } else if (isNameMark(cp)) {
                if (!markable) return -1;
                markable = false;
                marks++;
            } else if (SEPARATORS.indexOf(cp) >= 0) {
                if (needLetter) return -1;
                needLetter = true;
                markable = false;
            } else {
                return -1;
            }
        }
        return needLetter ? -1 : marks;
    }

    /**
     * Letras dos blocos latinos de uso comum. Ficam de fora as que so imitam outra letra ({@code ı}
     * sem ponto, {@code ſ} longo, os indicadores {@code ª º}), as de largura cheia, as fonéticas e o
     * Latin Extended-B (que traz ate cliques parecidos com {@code |} e {@code !}).
     */
    static boolean isNameLetter(int cp) {
        if (!Character.isLetter(cp)) return false;
        if (cp < 0x80) return true; // A-Z, a-z
        if (cp >= 0xC0 && cp <= 0xFF) return cp != 0xD7 && cp != 0xF7; // Latin-1: À-ÿ menos × e ÷
        if (cp >= 0x100 && cp <= 0x17F) return cp != 0x131 && cp != 0x138 && cp != 0x149 && cp != 0x17F;
        return cp >= 0x1E00 && cp <= 0x1EFF; // Latin Extended Additional: ạ, ẽ, ẞ...
    }

    /**
     * Acentos do bloco Combining Diacritical Marks. Ficam de fora o {@code U+034F} (invisivel) e as
     * marcas duplas, que se estendem sobre a letra vizinha.
     */
    static boolean isNameMark(int cp) {
        return cp >= 0x300 && cp <= 0x36F && cp != 0x34F && (cp < 0x35C || cp > 0x362);
    }

    public String fullName() { return firstName + " " + lastName; }
    public String key() { return key(fullName()); }
    public static String key(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT);
    }
}
