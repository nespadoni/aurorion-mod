package com.aurorion.essentials.client;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class PhoneImageImporterTest {
    @TempDir
    Path directory;

    @Test
    void jpegBecomesAGalleryPngWithoutChangingItsSource() throws Exception {
        Path source = directory.resolve("externa.jpg");
        ImageIO.write(new BufferedImage(2100, 1050, BufferedImage.TYPE_INT_RGB), "jpg", source.toFile());
        byte[] original = Files.readAllBytes(source);
        byte[] png = PhoneImageImporter.prepare(source);
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
        assertEquals(2048, image.getWidth());
        assertEquals(1024, image.getHeight());
        Path character = directory.resolve("personagem");
        Path imported = PhoneImageImporter.save(png, character);
        assertEquals(character.resolve("camera"), imported.getParent());
        assertTrue(imported.toString().endsWith(".png"));
        assertArrayEquals(original, Files.readAllBytes(source));
        assertArrayEquals(png, Files.readAllBytes(imported));
        assertNotEquals(imported, PhoneImageImporter.save(png, character));
        try (var entries = Files.list(imported.getParent())) {
            assertEquals(2, entries.count());
        }
    }

    @Test
    void pngKeepsTransparencyAndSmallDimensions() throws Exception {
        BufferedImage input = new BufferedImage(2, 3, BufferedImage.TYPE_INT_ARGB);
        input.setRGB(0, 0, 0x7F112233);
        Path source = directory.resolve("transparente.png");
        ImageIO.write(input, "png", source.toFile());
        BufferedImage output = ImageIO.read(new ByteArrayInputStream(PhoneImageImporter.prepare(source)));
        assertEquals(2, output.getWidth());
        assertEquals(3, output.getHeight());
        assertEquals(0x7F112233, output.getRGB(0, 0));
    }

    @Test
    void invalidAndOversizedFilesAreRejectedBeforeGalleryWrites() throws Exception {
        Path source = directory.resolve("falsa.png");
        Files.writeString(source, "nao e uma imagem");
        assertThrows(java.io.IOException.class, () -> PhoneImageImporter.prepare(source));
        Path oversized = directory.resolve("grande.png");
        try (var file = new java.io.RandomAccessFile(oversized.toFile(), "rw")) {
            file.setLength(PhoneImageImporter.MAX_FILE_BYTES + 1);
        }
        assertThrows(java.io.IOException.class, () -> PhoneImageImporter.prepare(oversized));
        assertFalse(Files.exists(directory.resolve("camera")));
    }
}
