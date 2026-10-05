package com.aurorion.essentials.client;

import com.mojang.blaze3d.platform.NativeImage;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * O telefone manda as fotos de Mensagens, DMs do Gram e perfis do MattuTweet como JPEG, mas quem
 * recebe le com {@link NativeImage#read(InputStream)}, que no 1.21.1 recusa tudo que nao for PNG — e a
 * tela mostra "Photo could not be loaded". PNG continua pelo caminho original; o resto passa pelo ImageIO.
 */
public final class PhonePhotoDecoder {
    private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};

    private PhonePhotoDecoder() {
    }

    public static NativeImage read(InputStream input) throws IOException {
        byte[] bytes = input.readAllBytes();
        if (isPng(bytes)) return NativeImage.read(new ByteArrayInputStream(bytes));
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
        if (image == null) throw new IOException("Formato de imagem nao reconhecido.");
        try {
            int width = image.getWidth();
            int height = image.getHeight();
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

    static boolean isPng(byte[] bytes) {
        if (bytes.length < PNG_SIGNATURE.length) return false;
        for (int i = 0; i < PNG_SIGNATURE.length; i++) {
            if (bytes[i] != PNG_SIGNATURE[i]) return false;
        }
        return true;
    }

    /** O ImageIO entrega ARGB; o {@code setPixelRGBA} do NativeImage espera ABGR. */
    static int toAbgr(int argb) {
        return argb & 0xFF00FF00 | (argb & 0xFF) << 16 | argb >>> 16 & 0xFF;
    }
}
