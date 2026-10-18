package com.aurorion.essentials.compat;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Os formatos curtos que ficam no lugar das fotos do Gram, e a leitura do arquivo do telefone. */
class PhoneGramImageRefTest {
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 16, 'J', 'F', 'I', 'F'};
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0, 0, 0, 13};

    @Test
    void markRoundTripsAndNeverLooksLikeAPhoto() {
        String hash = PhoneGramImageRef.sha256(JPEG);
        byte[] mark = PhoneGramImageRef.mark(hash);
        assertEquals(PhoneGramImageRef.MARK_LENGTH, mark.length);
        assertEquals(hash, PhoneGramImageRef.hashOfMark(mark));
        assertFalse(PhoneGramImageRef.isStorableImage(mark));
        assertNull(PhoneGramImageRef.hashOfMark(JPEG));
        assertNull(PhoneGramImageRef.hashOfMark(PNG));
        assertNull(PhoneGramImageRef.hashOfMark(new byte[0]));
        assertNull(PhoneGramImageRef.hashOfMark(null));
    }

    @Test
    void refRoundTripsAndRejectsAnythingElse() {
        String hash = PhoneGramImageRef.sha256(PNG);
        assertEquals(hash, PhoneGramImageRef.hashOfRef(PhoneGramImageRef.ref(hash)));
        assertNull(PhoneGramImageRef.hashOfRef(Base64.getEncoder().encodeToString(JPEG)));
        assertNull(PhoneGramImageRef.hashOfRef(""));
        assertNull(PhoneGramImageRef.hashOfRef(null));
        // O hash vira nome de arquivo: nada de caminho, maiuscula ou tamanho errado.
        assertNull(PhoneGramImageRef.hashOfRef(PhoneGramImageRef.REF_PREFIX + "../../server.properties"));
        assertNull(PhoneGramImageRef.hashOfRef(PhoneGramImageRef.REF_PREFIX + hash.toUpperCase()));
        assertNull(PhoneGramImageRef.hashOfRef(PhoneGramImageRef.REF_PREFIX + hash.substring(1)));
    }

    @Test
    void onlyJpegAndPngWithinTheLimitAreStored() {
        assertTrue(PhoneGramImageRef.isStorableImage(JPEG));
        assertTrue(PhoneGramImageRef.isStorableImage(PNG));
        assertFalse(PhoneGramImageRef.isStorableImage("GIF89a....".getBytes(StandardCharsets.US_ASCII)));
        byte[] huge = new byte[PhoneGramImageRef.MAX_IMAGE_BYTES + 1];
        System.arraycopy(JPEG, 0, huge, 0, JPEG.length);
        assertFalse(PhoneGramImageRef.isStorableImage(huge));
    }

    @Test
    void sameBytesSameHash() {
        assertEquals(PhoneGramImageRef.sha256(JPEG.clone()), PhoneGramImageRef.sha256(JPEG));
        assertTrue(PhoneGramImageRef.isHash(PhoneGramImageRef.sha256(JPEG)));
    }

    /** O arquivo antigo (base64) continua sendo lido; a referencia vira marca sem tocar no disco. */
    @Test
    void restoreReadsBothFormats() {
        assertArrayEquals(JPEG, PhoneGramImageStore.restore(Base64.getEncoder().encodeToString(JPEG)));
        String hash = PhoneGramImageRef.sha256(JPEG);
        assertEquals(hash, PhoneGramImageRef.hashOfMark(PhoneGramImageStore.restore(PhoneGramImageRef.ref(hash))));
        assertEquals(0, PhoneGramImageStore.restore("").length);
        assertEquals(0, PhoneGramImageStore.restore("nao e base64!").length);
    }
}
