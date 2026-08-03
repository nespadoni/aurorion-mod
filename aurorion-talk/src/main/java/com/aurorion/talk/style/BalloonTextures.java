package com.aurorion.talk.style;

import com.aurorion.talk.AurorionTalk;
import net.minecraft.resources.ResourceLocation;

/**
 * Convencao de pastas das texturas de balao.
 *
 * <p>Adicionar arte nova e so soltar o PNG na pasta certa e reexportar o jar — o catalogo e
 * descoberto em runtime, nada precisa ser registrado em codigo.</p>
 *
 * <pre>
 * assets/aurorion_talk/textures/gui/balloon/skin/  balao inteiro, folha 32x32 nine-slice
 * assets/aurorion_talk/textures/gui/balloon/deco/  enfeite, quadrado (16x16, 32x32, ...)
 * </pre>
 */
public final class BalloonTextures {
    public static final String SKIN_DIR = "textures/gui/balloon/skin";
    public static final String DECO_DIR = "textures/gui/balloon/deco";

    public static final ResourceLocation DEFAULT_SKIN = skin("balloon.png");

    /** Teto de tamanho do caminho: o servidor aceita o que o cliente manda, entao limitamos a forma. */
    private static final int MAX_PATH_LENGTH = 128;

    private BalloonTextures() {
    }

    public static ResourceLocation skin(String file) {
        return ResourceLocation.fromNamespaceAndPath(AurorionTalk.MOD_ID, SKIN_DIR + "/" + file);
    }

    public static ResourceLocation decoration(String file) {
        return ResourceLocation.fromNamespaceAndPath(AurorionTalk.MOD_ID, DECO_DIR + "/" + file);
    }

    /**
     * Valida a <em>forma</em> de um caminho vindo da rede.
     *
     * <p>O servidor dedicado nao carrega {@code assets/}, entao ele nao tem como saber quais PNGs
     * existem de fato. Em vez de fingir que valida, restringimos o caminho ao namespace e as pastas
     * do mod: o pior caso vira uma textura faltando no cliente, nunca um caminho arbitrario.</p>
     */
    public static boolean isAllowed(ResourceLocation texture, String directory) {
        String path = texture.getPath();

        return texture.getNamespace().equals(AurorionTalk.MOD_ID)
                && path.length() <= MAX_PATH_LENGTH
                && path.startsWith(directory + "/")
                && path.endsWith(".png")
                && path.indexOf("..") < 0;
    }

    public static boolean isSkin(ResourceLocation texture) {
        return isAllowed(texture, SKIN_DIR);
    }

    public static boolean isDecoration(ResourceLocation texture) {
        return isAllowed(texture, DECO_DIR);
    }

    /** Nome legivel para a GUI: {@code .../deco/flor.png} vira {@code Flor}. */
    public static String displayName(ResourceLocation texture) {
        String path = texture.getPath();
        String file = path.substring(path.lastIndexOf('/') + 1).replace(".png", "").replace('_', ' ');

        return file.isEmpty() ? path : Character.toUpperCase(file.charAt(0)) + file.substring(1);
    }
}
