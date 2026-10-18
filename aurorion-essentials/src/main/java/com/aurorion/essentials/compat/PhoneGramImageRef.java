package com.aurorion.essentials.compat;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Os dois textos curtos que ficam no lugar da foto inteira de um post ou avatar do Gram.
 *
 * <ul>
 *   <li><b>Referencia</b> ({@code aurorion:sha256:<hash>}): o valor gravado no
 *       {@code server_social.properties} do telefone em vez do base64 da foto. A foto mora num arquivo
 *       proprio, com o hash no nome ({@link PhoneGramImageStore}).</li>
 *   <li><b>Marca</b> ({@code AURGRAM1:<hash>}, 73 bytes ASCII): o que viaja no lugar dos bytes da
 *       foto, dentro dos proprios records e pacotes do telefone. O servidor a usa para foto que ainda
 *       nao saiu do disco; o cliente que a recebe pede a foto so quando for desenha-la.</li>
 * </ul>
 *
 * <p>Nenhuma das duas colide com uma foto de verdade: JPEG comeca com {@code FF D8} e PNG com
 * {@code 89 50}, e o base64 que o telefone gravava nunca tem {@code :}.
 *
 * <p>Classe pura de proposito (so {@code java.*}): os formatos sao testados sem subir o jogo.
 */
public final class PhoneGramImageRef {
    public static final String REF_PREFIX = "aurorion:sha256:";
    /**
     * Teto por foto. O telefone comprime post e avatar para uns 31 KB antes de enviar; oito vezes isso
     * so vem de cliente modificado, e nao vai para o disco nem para a rede de ninguem.
     */
    public static final int MAX_IMAGE_BYTES = 256 * 1024;
    public static final int HASH_LENGTH = 64;

    private static final byte[] MARK_PREFIX = "AURGRAM1:".getBytes(StandardCharsets.US_ASCII);
    public static final int MARK_LENGTH = MARK_PREFIX.length + HASH_LENGTH;
    private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};

    private PhoneGramImageRef() {
    }

    public static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            // Toda JVM e obrigada a ter SHA-256.
            throw new IllegalStateException(e);
        }
    }

    /** 64 caracteres hexadecimais minusculos — e nada mais, porque vira nome de arquivo. */
    public static boolean isHash(String value) {
        if (value == null || value.length() != HASH_LENGTH) return false;
        for (int i = 0; i < HASH_LENGTH; i++) {
            char c = value.charAt(i);
            if ((c < '0' || c > '9') && (c < 'a' || c > 'f')) return false;
        }
        return true;
    }

    public static String ref(String hash) {
        return REF_PREFIX + hash;
    }

    /** O hash de uma referencia, ou {@code null} se o valor for outra coisa (o base64 antigo, vazio). */
    public static String hashOfRef(String value) {
        if (value == null || !value.startsWith(REF_PREFIX)) return null;
        String hash = value.substring(REF_PREFIX.length()).trim();
        return isHash(hash) ? hash : null;
    }

    public static byte[] mark(String hash) {
        byte[] mark = new byte[MARK_LENGTH];
        System.arraycopy(MARK_PREFIX, 0, mark, 0, MARK_PREFIX.length);
        byte[] text = hash.getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(text, 0, mark, MARK_PREFIX.length, HASH_LENGTH);
        return mark;
    }

    /** O hash de uma marca, ou {@code null} se os bytes forem uma foto de verdade (ou nada). */
    public static String hashOfMark(byte[] bytes) {
        if (bytes == null || bytes.length != MARK_LENGTH) return null;
        for (int i = 0; i < MARK_PREFIX.length; i++) {
            if (bytes[i] != MARK_PREFIX[i]) return null;
        }
        String hash = new String(bytes, MARK_PREFIX.length, HASH_LENGTH, StandardCharsets.US_ASCII);
        return isHash(hash) ? hash : null;
    }

    /** JPEG ou PNG dentro do teto: o que o telefone manda. O resto nao vai para o disco. */
    public static boolean isStorableImage(byte[] bytes) {
        if (bytes == null || bytes.length < 4 || bytes.length > MAX_IMAGE_BYTES) return false;
        boolean jpeg = bytes[0] == (byte) 0xFF && bytes[1] == (byte) 0xD8 && bytes[2] == (byte) 0xFF;
        if (jpeg) return true;
        if (bytes.length < PNG_SIGNATURE.length) return false;
        for (int i = 0; i < PNG_SIGNATURE.length; i++) {
            if (bytes[i] != PNG_SIGNATURE[i]) return false;
        }
        return true;
    }
}
