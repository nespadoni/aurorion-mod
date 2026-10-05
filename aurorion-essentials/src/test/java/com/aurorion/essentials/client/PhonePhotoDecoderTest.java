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
