package com.aurorion.ethereal.client;

import com.aurorion.ethereal.client.gui.CeremonyScreen;
import com.aurorion.ethereal.client.gui.HouseRevealScreen;
import com.aurorion.ethereal.client.gui.HouseSelectionScreen;
import com.aurorion.ethereal.client.gui.ProjectorConfigScreen;
import com.aurorion.ethereal.network.CeremonyClosedPayload;
import com.aurorion.ethereal.network.HouseChoiceResultPayload;
import com.aurorion.ethereal.network.OpenCeremonyPayload;
import com.aurorion.ethereal.network.OpenHouseSelectionPayload;
import com.aurorion.ethereal.network.OpenProjectorConfigPayload;
import com.aurorion.ethereal.network.RevealHousePayload;
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

    public static void openCeremony(OpenCeremonyPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> Minecraft.getInstance().setScreen(
                new CeremonyScreen(payload.questions(), payload.startAt())));
    }

    public static void ceremonyClosed(CeremonyClosedPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Minecraft minecraft = Minecraft.getInstance();

            if (minecraft.screen instanceof CeremonyScreen screen) {
                screen.close(payload.message());
                return;
            }
            if (minecraft.player != null) {
                minecraft.player.displayClientMessage(payload.message(), false);
            }
        });
    }

    /**
     * A revelacao entra por cima de qualquer coisa que esteja aberta.
     *
     * <p>Ela pode chegar horas depois das perguntas — inclusive no login seguinte, se a staff decidiu
     * com o jogador offline. Nao existe tela "esperando" para ela substituir.
     */
    public static void reveal(RevealHousePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> Minecraft.getInstance().setScreen(
                new HouseRevealScreen(payload.houseName(), payload.motto(), payload.color())));
    }

    public static void openProjectorConfig(OpenProjectorConfigPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> Minecraft.getInstance().setScreen(new ProjectorConfigScreen(payload)));
    }
}
