package com.aurorion.ethereal.client;

import com.aurorion.ethereal.client.gui.HouseSelectionScreen;
import com.aurorion.ethereal.client.gui.HouseMuralScreen;
import com.aurorion.ethereal.client.gui.ProtectorTargetScreen;
import com.aurorion.ethereal.client.gui.ProjectorConfigScreen;
import com.aurorion.ethereal.network.HouseChoiceResultPayload;
import com.aurorion.ethereal.network.OpenHouseSelectionPayload;
import com.aurorion.ethereal.network.OpenProjectorConfigPayload;
import com.aurorion.ethereal.network.RitePayload;
import com.aurorion.ethereal.network.HouseMuralPayloads;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Ponta cliente de todos os payloads. Fica numa classe separada do {@code EtherealNetwork} de
 * proposito: assim o servidor dedicado nunca precisa resolver nenhuma classe que importe
 * {@code Minecraft}.
 */
@OnlyIn(Dist.CLIENT)
public final class EtherealClientNetwork {
    private EtherealClientNetwork() {
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

    /**
     * Um rito comecando ou terminando por perto.
     *
     * <p>Nao abre tela nenhuma, e essa e a diferenca para a revelacao antiga: a cena acontece no
     * mundo, e uma tela modal cobriria justamente o que ha para ver. O cliente so guarda o estado —
     * quem desenha sao {@link RiteRenderer} e {@link RiteOverlay}.
     */
    public static void rite(RitePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> RiteClient.accept(payload));
    }

    public static void openProjectorConfig(OpenProjectorConfigPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> Minecraft.getInstance().setScreen(new ProjectorConfigScreen(payload)));
    }

    public static void openMural(HouseMuralPayloads.Open payload, IPayloadContext context) {
        context.enqueueWork(() -> Minecraft.getInstance().setScreen(new HouseMuralScreen(payload)));
    }

    public static void openProtectorTargets(HouseMuralPayloads.OpenTargets payload, IPayloadContext context) {
        context.enqueueWork(() -> Minecraft.getInstance().setScreen(new ProtectorTargetScreen(payload)));
    }
}
