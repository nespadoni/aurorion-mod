package com.aurorion.magia.client;

import com.aurorion.magia.AurorionMagia;
import com.aurorion.magia.network.PassiveActionPayload;
import com.aurorion.magia.network.PassiveMenuPayloads;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * A tecla de passiva: {@code H} por padrao (o G e da Economia), em Controles > Aurorion.
 *
 * <ul>
 *   <li><b>Toque rapido</b>: a acao de sempre (Shift + tecla e a segunda acao).</li>
 *   <li><b>Segurar</b> ({@value #HOLD_TICKS} ticks): abre a roda de passivas ({@link PassiveWheelScreen}).
 *       Soltar em cima de uma fatia usa ou liga/desliga aquela passiva.</li>
 * </ul>
 *
 * <p>Um byte por toque, ou um pedido vazio ao segurar; quem decide o que acontece e o servidor.
 */
public final class PassiveKey {
    public static final KeyMapping USE = new KeyMapping("key.aurorion_magia.passiva", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_H, "key.categories.aurorion_magia");
    /** 0,3 s: curto o bastante para a roda nao parecer lenta, longo o bastante para o toque nao abri-la. */
    private static final int HOLD_TICKS = 6;

    private static boolean wasDown;
    private static int heldTicks;
    private static boolean requested;

    private PassiveKey() {
    }

    /**
     * A resposta do servidor chegou. Com a tecla ainda apertada, a roda abre no modo "soltar escolhe";
     * se ela ja foi solta (atraso de rede), abre do mesmo jeito, no modo de clique.
     */
    public static void openWheel(List<PassiveMenuPayloads.Entry> entries) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || entries.isEmpty() || minecraft.screen != null || !requested) return;
        requested = false;
        minecraft.setScreen(new PassiveWheelScreen(entries, USE.isDown()));
    }

    @EventBusSubscriber(modid = AurorionMagia.MOD_ID, value = Dist.CLIENT)
    public static final class ModBus {
        private ModBus() {
        }

        @SubscribeEvent
        public static void register(RegisterKeyMappingsEvent event) {
            event.register(USE);
        }
    }

    @EventBusSubscriber(modid = AurorionMagia.MOD_ID, value = Dist.CLIENT)
    public static final class GameBus {
        private GameBus() {
        }

        @SubscribeEvent
        public static void onClientTick(ClientTickEvent.Post event) {
            Minecraft minecraft = Minecraft.getInstance();
            // O que importa e quanto tempo a tecla ficou apertada; o clique contado so cobre o toque que
            // aperta e solta dentro do mesmo tick, que o isDown() sozinho perderia.
            boolean clicked = false;
            while (USE.consumeClick()) clicked = true;
            if (minecraft.player == null || minecraft.getConnection() == null || minecraft.screen != null) {
                wasDown = false;
                // Fechar a roda com a tecla ainda apertada: ela volta "apertada" ao jogo, e nao pode contar
                // como toque nem reabrir a roda. Zera no primeiro tick em que for solta.
                heldTicks = HOLD_TICKS;
                return;
            }
            boolean down = USE.isDown();
            if (down) {
                heldTicks++;
                if (heldTicks == HOLD_TICKS) {
                    requested = true;
                    PacketDistributor.sendToServer(new PassiveMenuPayloads.Request());
                }
            } else if ((wasDown || clicked) && heldTicks < HOLD_TICKS) {
                // A tecla fisica, e nao isShiftKeyDown(): o input de agachar e zerado enquanto o corpo esta
                // travado (fora do corpo, suprimido), e a acao alternativa sumiria justo ai.
                PacketDistributor.sendToServer(new PassiveActionPayload(minecraft.options.keyShift.isDown()));
            }
            if (!down) heldTicks = 0;
            wasDown = down;
        }
    }
}
