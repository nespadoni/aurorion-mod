package com.aurorion.essentials.client;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class PhonePhotoDecoderTest {
    @Test
    void onlyPngGoesThroughTheMinecraftReader() throws Exception {
        assertTrue(PhonePhotoDecoder.isPng(encode("png")));
        assertFalse(PhonePhotoDecoder.isPng(encode("jpg")));
        assertFalse(PhonePhotoDecoder.isPng(new byte[0]));
    }

    @Test
    void onlyJpegGoesThroughImageIo() throws Exception {
        assertTrue(PhonePhotoDecoder.isJpeg(encode("jpg")));
        assertFalse(PhonePhotoDecoder.isJpeg(encode("bmp")));
        assertFalse(PhonePhotoDecoder.isJpeg(encode("png")));
    }

    @Test
    void pngDimensionsComeFromTheHeader() throws Exception {
        byte[] png = encode("png");
        assertEquals(4, PhonePhotoDecoder.pngWidth(png));
        assertEquals(4, PhonePhotoDecoder.pngHeight(png));
        assertEquals(0, PhonePhotoDecoder.pngWidth(new byte[12]));
    }

    @Test
    void hugeDimensionsAreRefusedBeforeDecoding() {
        assertThrows(java.io.IOException.class, () -> PhonePhotoDecoder.checkSize(40000, 40000));
        assertThrows(java.io.IOException.class, () -> PhonePhotoDecoder.checkSize(0, 10));
        assertThrows(java.io.IOException.class, () -> PhonePhotoDecoder.checkSize(4096, 4096));
        assertDoesNotThrow(() -> PhonePhotoDecoder.checkSize(3840, 2160));
    }

    @Test
    void argbBecomesAbgrKeepingAlphaAndGreen() {
        assertEquals(0x80332211, PhonePhotoDecoder.toAbgr(0x80112233));
        assertEquals(0xFF0000FF, PhonePhotoDecoder.toAbgr(0xFFFF0000));
    }

    private static byte[] encode(String format) throws Exception {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            assertTrue(ImageIO.write(new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB), format, output));
            return output.toByteArray();
        }
    }
}
