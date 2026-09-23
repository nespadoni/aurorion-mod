package com.aurorion.utils.client;

import com.aurorion.utils.AurorionUtils;
import com.aurorion.utils.entity.ModEntities;
import com.aurorion.utils.freeze.FreezeEffects;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;

/**
 * O congelado, visto de dentro: "como se as teclas nao funcionassem". So o mouse de olhar continua.
 *
 * <ul>
 *   <li>andar, pular, agachar e correr: o input de movimento e zerado antes de ir ao jogo — e por
 *       isso o Shift nunca chega ao servidor para desmontar;</li>
 *   <li>bater, usar, pegar bloco: o clique e engolido, sem nem balancar o braco;</li>
 *   <li>trocar de slot (numeros e roda), soltar item, trocar de mao, abrir inventario: as teclas
 *       sao consumidas antes de o jogo le-las.</li>
 * </ul>
 *
 * <p>Chat, comandos, menu de pausa, F5 e voz continuam: a pessoa precisa conseguir falar com a staff.
 *
 * <p>O cliente so esta aqui por conforto; quem garante e o servidor ({@code FreezeEvents}).
 */
@EventBusSubscriber(modid = AurorionUtils.MOD_ID, value = Dist.CLIENT)
public final class FreezeClientEvents {
    private static boolean wasPinned;

    private FreezeClientEvents() {
    }

    static boolean frozen() {
        LocalPlayer player = Minecraft.getInstance().player;
        return player != null && player.hasEffect(FreezeEffects.FROZEN);
    }

    @SubscribeEvent
    public static void onMovementInput(MovementInputUpdateEvent event) {
        if (!event.getEntity().hasEffect(FreezeEffects.FROZEN)) return;
        Input input = event.getInput();
        input.forwardImpulse = 0;
        input.leftImpulse = 0;
        input.up = false;
        input.down = false;
        input.left = false;
        input.right = false;
        input.jumping = false;
        input.shiftKeyDown = false;
    }

    @SubscribeEvent
    public static void onInteraction(InputEvent.InteractionKeyMappingTriggered event) {
        if (!frozen()) return;
        event.setSwingHand(false);
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onScroll(InputEvent.MouseScrollingEvent event) {
        if (frozen() && Minecraft.getInstance().screen == null) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onScreenOpening(ScreenEvent.Opening event) {
        if (frozen() && (event.getNewScreen() instanceof InventoryScreen
                || event.getNewScreen() instanceof CreativeModeInventoryScreen)) {
            event.setCanceled(true);
        }
    }

    /**
     * Antes de o jogo ler as teclas no tick, os cliques pendentes sao descartados. Aproveita para
     * trocar o aviso "Aperte Shift para desmontar" (que o jogo mostra ao montar na ancora) pelo
     * aviso de congelado.
     */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Pre event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        boolean frozen = frozen();
        if (frozen) {
            Options options = minecraft.options;
            for (KeyMapping hotbar : options.keyHotbarSlots) consume(hotbar);
            consume(options.keyDrop);
            consume(options.keySwapOffhand);
            consume(options.keyInventory);
            consume(options.keySprint);
            if (player != null) player.setSprinting(false);
        }

        boolean pinned = frozen && player != null && player.getVehicle() != null
                && player.getVehicle().getType() == ModEntities.FREEZE_ANCHOR.get();
        if (pinned && !wasPinned) {
            minecraft.gui.setOverlayMessage(Component.translatable("aurorion_utils.congelado"), false);
        }
        wasPinned = pinned;
    }

    private static void consume(KeyMapping key) {
        while (key.consumeClick()) {
            // So esvazia a fila de cliques.
        }
    }
}
