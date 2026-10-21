package com.aurorion.essentials.client;

import com.mojang.blaze3d.platform.NativeImage;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;

/**
 * O telefone manda as fotos de Mensagens, DMs do Gram e perfis do MattuTweet como JPEG, mas quem
 * recebe le com {@link NativeImage#read(InputStream)}, que no 1.21.1 recusa tudo que nao for PNG — e a
 * tela mostra "Photo could not be loaded". PNG continua pelo caminho original; JPEG passa pelo ImageIO.
 *
 * <h2>Foto de outro jogador e dado nao confiavel</h2>
 *
 * <p>Os bytes chegam de qualquer um que mande uma DM. Sem teto, um BMP de 60 bytes declarando
 * 40000x40000 derrubava o cliente de quem recebia por falta de memoria — e de novo a cada vez que a
 * conversa abria, porque a foto fica salva em disco. Por isso: teto de bytes, so PNG e JPEG, e as
 * dimensoes conferidas no cabecalho <b>antes</b> de alocar qualquer pixel.
 */
public final class PhonePhotoDecoder {
    private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};

    /** Mesmo teto do {@link PhoneImageImporter}: foto legitima nunca passa disto. */
    static final int MAX_BYTES = 10 * 1024 * 1024;
    /** Lado maximo aceito. Folga sobre os 2048 do importador para foto tirada pelo proprio telefone. */
    static final int MAX_SIDE = 4096;
    /** Cerca de 32 MB de pixels RGBA no pior caso. Cobre um print 3840x2160. */
    static final long MAX_PIXELS = 4096L * 2048L;

    private PhonePhotoDecoder() {
    }

    public static NativeImage read(InputStream input) throws IOException {
        byte[] bytes = input.readNBytes(MAX_BYTES + 1);
        if (bytes.length > MAX_BYTES) throw new IOException("Foto grande demais.");
        if (isPng(bytes)) {
            checkSize(pngWidth(bytes), pngHeight(bytes));
            return NativeImage.read(new ByteArrayInputStream(bytes));
        }
        if (!isJpeg(bytes)) throw new IOException("Formato de imagem nao reconhecido.");

        BufferedImage image;
        try (ImageInputStream stream = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            if (stream == null) throw new IOException("Formato de imagem nao reconhecido.");
            Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("jpeg");
            if (!readers.hasNext()) throw new IOException("Leitor de JPEG indisponivel.");
            ImageReader reader = readers.next();
            try {
                reader.setInput(stream, true, true);
                checkSize(reader.getWidth(0), reader.getHeight(0));
                image = reader.read(0);
            } finally {
                reader.dispose();
            }
        }
        if (image == null) throw new IOException("Formato de imagem nao reconhecido.");
        try {
            int width = image.getWidth();
            int height = image.getHeight();
            checkSize(width, height);
            int[] argb = image.getRGB(0, 0, width, height, null, 0, width);
            NativeImage result = new NativeImage(width, height, false);
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    result.setPixelRGBA(x, y, toAbgr(argb[y * width + x]));
                }
            }
            return result;
        } finally {
            image.flush();
        }
    }

    static void checkSize(long width, long height) throws IOException {
        if (width <= 0 || height <= 0 || width > MAX_SIDE || height > MAX_SIDE || width * height > MAX_PIXELS) {
            throw new IOException("Dimensoes de foto recusadas: " + width + "x" + height);
        }
    }

    static boolean isPng(byte[] bytes) {
        if (bytes.length < PNG_SIGNATURE.length) return false;
        for (int i = 0; i < PNG_SIGNATURE.length; i++) {
            if (bytes[i] != PNG_SIGNATURE[i]) return false;
        }
        return true;
    }

    static boolean isJpeg(byte[] bytes) {
        return bytes.length >= 3 && (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8 && (bytes[2] & 0xFF) == 0xFF;
    }

    /**
     * Largura do IHDR, que o formato obriga a ser o primeiro chunk: assinatura (8), tamanho (4),
     * "IHDR" (4), largura (4), altura (4). Sem IHDR no lugar, devolve 0 e a foto e recusada.
     */
    static long pngWidth(byte[] bytes) {
        return hasIhdr(bytes) ? readUInt(bytes, 16) : 0;
    }

    static long pngHeight(byte[] bytes) {
        return hasIhdr(bytes) ? readUInt(bytes, 20) : 0;
    }

    private static boolean hasIhdr(byte[] bytes) {
        return bytes.length >= 24 && bytes[12] == 'I' && bytes[13] == 'H' && bytes[14] == 'D' && bytes[15] == 'R';
    }

    private static long readUInt(byte[] bytes, int offset) {
        return (bytes[offset] & 0xFFL) << 24 | (bytes[offset + 1] & 0xFFL) << 16
                | (bytes[offset + 2] & 0xFFL) << 8 | bytes[offset + 3] & 0xFFL;
    }

    /** O ImageIO entrega ARGB; o {@code setPixelRGBA} do NativeImage espera ABGR. */
    static int toAbgr(int argb) {
        return argb & 0xFF00FF00 | (argb & 0xFF) << 16 | argb >>> 16 & 0xFF;
    }
}
