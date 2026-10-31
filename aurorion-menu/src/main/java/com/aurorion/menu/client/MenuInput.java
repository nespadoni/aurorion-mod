package com.aurorion.menu.client;

import com.aurorion.core.client.menu.CharacterMenu;
import com.aurorion.menu.AurorionMenu;
import com.aurorion.menu.config.MenuClientConfig;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Como o menu abre e fecha: a tecla (sem atribuição de fábrica) ou o gesto — mão principal vazia, botão
 * direito segurado e a mira puxada para baixo, rápido. A mira volta para onde estava: o gesto não deixa a
 * pessoa olhando para o chão. Com o menu aberto, os dois fecham.
 */
@EventBusSubscriber(modid = AurorionMenu.MOD_ID, value = Dist.CLIENT)
public final class MenuInput {
    private static final float GESTURE_PITCH = 22f;
    private static final int GESTURE_TICKS = 12;
    private static float gesturePitch = Float.NaN;
    private static int gestureTicks;

    private MenuInput() {
    }

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return;
        if (MenuKeys.OPEN.consumeClick() && minecraft.screen == null) toggle();
        gesture(minecraft);
    }

    private static void gesture(Minecraft minecraft) {
        var player = minecraft.player;
        if (minecraft.screen != null || !minecraft.options.keyUse.isDown() || !player.getMainHandItem().isEmpty()
                || !player.getOffhandItem().isEmpty() || !MenuClientConfig.GESTURE.get()
                || minecraft.hitResult != null && minecraft.hitResult.getType() != net.minecraft.world.phys.HitResult.Type.MISS) {
            gesturePitch = Float.NaN;
            return;
        }
        if (Float.isNaN(gesturePitch)) {
            gesturePitch = player.getXRot();
            gestureTicks = 0;
            return;
        }
        if (++gestureTicks > GESTURE_TICKS) {
            gesturePitch = player.getXRot();
            gestureTicks = 0;
            return;
        }
        if (player.getXRot() - gesturePitch >= GESTURE_PITCH) {
            player.setXRot(gesturePitch);
            player.xRotO = gesturePitch;
            gesturePitch = Float.NaN;
            toggle();
        }
    }

    private static void toggle() {
        if (MenuView.isOpen()) MenuView.close(true);
        else CharacterMenu.open(null);
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        MenuHost.reset();
        gesturePitch = Float.NaN;
    }
}
