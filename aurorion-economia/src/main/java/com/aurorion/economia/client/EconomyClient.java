package com.aurorion.economia.client;

import com.aurorion.economia.AurorionEconomia;
import com.aurorion.economia.client.gui.ChargeApprovalScreen;
import com.aurorion.economia.client.gui.ChargeComposerScreen;
import com.aurorion.economia.client.gui.EconomyStatusScreen;
import com.aurorion.economia.client.gui.InteractionMenuScreen;
import com.aurorion.economia.network.EconomyPayloads;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyModifier;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = AurorionEconomia.MOD_ID, value = Dist.CLIENT)
public final class EconomyClient {
    private static final KeyMapping INTERACT = new KeyMapping(
            "key.aurorion_economia.interact",
            KeyConflictContext.IN_GAME,
            KeyModifier.SHIFT,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_G,
            "key.categories.aurorion_economia");

    private EconomyClient() { }

    @SubscribeEvent
    public static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(INTERACT);
    }

    @SubscribeEvent
    public static void clientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        while (INTERACT.consumeClick()) {
            if (minecraft.player == null || minecraft.screen != null) continue;
            if (!(minecraft.hitResult instanceof EntityHitResult hit) || !(hit.getEntity() instanceof Player target)) {
                minecraft.player.displayClientMessage(
                        Component.literal("Olhe diretamente para um jogador próximo para interagir."), true);
                continue;
            }
            PacketDistributor.sendToServer(new EconomyPayloads.OpenRequest(target.getUUID()));
        }
    }

    public static void openMenu(EconomyPayloads.OpenMenu payload) {
        Minecraft.getInstance().setScreen(new InteractionMenuScreen(payload));
    }

    public static void openComposer(EconomyPayloads.OpenComposer payload) {
        Minecraft.getInstance().setScreen(new ChargeComposerScreen(payload));
    }

    public static void openApproval(EconomyPayloads.OpenApproval payload) {
        Minecraft.getInstance().setScreen(new ChargeApprovalScreen(payload));
    }

    public static void openStatus(EconomyPayloads.Status payload) {
        Minecraft.getInstance().setScreen(new EconomyStatusScreen(payload));
    }
}
