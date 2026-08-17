package com.aurorion.ato2.client;

import com.aurorion.ato2.client.gui.HouseSelectionScreen;
import com.aurorion.ato2.network.HouseChoiceResultPayload;
import com.aurorion.ato2.network.OpenHouseSelectionPayload;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Ponta cliente dos payloads do Ato 2. Fica numa classe separada do {@code Ato2Network} de proposito:
 * assim o servidor dedicado nunca precisa resolver nenhuma classe que importe {@code Minecraft}.
 */
@OnlyIn(Dist.CLIENT)
public final class Ato2ClientNetwork {
    private Ato2ClientNetwork() {
    }

    public static void openSelection(OpenHouseSelectionPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> Minecraft.getInstance().setScreen(
                new HouseSelectionScreen(payload.options(), payload.current().orElse(null), payload.locked())));
    }

    public static void choiceResult(HouseChoiceResultPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Minecraft minecraft = Minecraft.getInstance();

            if (minecraft.screen instanceof HouseSelectionScreen screen) {
                screen.onResult(payload.success(), payload.message());
                return;
            }

            // Tela ja fechada (o jogador apertou Esc no meio): o veredito ainda precisa aparecer.
            if (minecraft.player != null) {
                minecraft.player.displayClientMessage(payload.message(), false);
            }
        });
    }
}
