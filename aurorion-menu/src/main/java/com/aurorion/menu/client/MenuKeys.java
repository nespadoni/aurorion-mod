package com.aurorion.menu.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

/**
 * Abrir o menu pelo teclado. Sem tecla de fábrica: Y e U já são do Cataclysm, do hazentouvelib e da roda
 * de passivas da magia, e o menu já abre pelo gesto da mão e pelo toque na tecla da ficha. Quem quiser
 * atribui em Controles.
 */
public final class MenuKeys {
    public static final KeyMapping OPEN = new KeyMapping("key.aurorion_menu.abrir", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, "key.categories.aurorion_menu");

    private MenuKeys() {
    }
}
