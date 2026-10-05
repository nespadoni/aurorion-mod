package com.aurorion.essentials.client;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.UUID;

/** Importa uma imagem local para a galeria existente, sem dependencias ou downloads. */
public final class PhoneImageImporter {
    static final long MAX_FILE_BYTES = 10L * 1024 * 1024;
    static final long MAX_SOURCE_PIXELS = 16_000_000;
    static final int MAX_SIDE = 2048;

    private PhoneImageImporter() {
    }

    /** Decodifica fora da thread de render; verifica o tamanho antes de alocar os pixels. */
    public static byte[] prepare(Path source) throws IOException {
        if (!Files.isRegularFile(source)) throw new IOException("O arquivo de imagem nao existe.");
        if (Files.size(source) > MAX_FILE_BYTES) throw new IOException("A imagem deve ter no maximo 10 MB.");
        try (ImageInputStream input = ImageIO.createImageInputStream(source.toFile())) {
            if (input == null) throw new IOException("Nao foi possivel abrir a imagem.");
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new IOException("Use uma imagem PNG ou JPG valida.");
            ImageReader reader = readers.next();
            try {
                String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                if (!format.equals("png") && !format.equals("jpeg") && !format.equals("jpg")) {
                    throw new IOException("Use uma imagem PNG ou JPG.");
                }
                reader.setInput(input, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || (long) width * height > MAX_SOURCE_PIXELS) {
                    throw new IOException("A imagem deve ter no maximo 16 megapixels.");
                }
                BufferedImage original = reader.read(0);
                double scale = Math.min(1.0, (double) MAX_SIDE / Math.max(width, height));
                BufferedImage result = new BufferedImage(Math.max(1, (int) Math.round(width * scale)),
                        Math.max(1, (int) Math.round(height * scale)), BufferedImage.TYPE_INT_ARGB);
                Graphics2D graphics = result.createGraphics();
                try {
                    graphics.setComposite(AlphaComposite.Src);
                    graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                    graphics.drawImage(original, 0, 0, result.getWidth(), result.getHeight(), null);
                } finally {
                    graphics.dispose();
                    original.flush();
                }
                try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                    if (!ImageIO.write(result, "png", output)) throw new IOException("Nao foi possivel converter a imagem.");
                    return output.toByteArray();
                } finally {
                    result.flush();
                }
            } finally {
                reader.dispose();
            }
        }
    }

    /** A galeria do telefone ja procura PNGs em camera; a origem permanece intacta. */
    public static Path save(byte[] png, Path characterDir) throws IOException {
        Path gallery = Files.createDirectories(characterDir.resolve("camera"));
        Path destination = gallery.resolve("importada_" + System.currentTimeMillis() + "_" + UUID.randomUUID() + ".png");
        Path pending = Files.createTempFile(gallery, ".importando_", ".tmp");
        try {
            Files.write(pending, png);
            // A lista da galeria nunca ve uma imagem pela metade.
            try {
                Files.move(pending, destination, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(pending, destination);
            }
        } finally {
            Files.deleteIfExists(pending);
        }
        return destination;
    }
}
