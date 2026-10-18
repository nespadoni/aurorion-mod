package com.aurorion.essentials.network;

import com.aurorion.essentials.AurorionEssentials;
import com.aurorion.essentials.fakename.FakeName;
import com.aurorion.essentials.fakename.FakeNameRegistry;
import com.aurorion.essentials.server.PhoneGroupService;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@EventBusSubscriber(modid = AurorionEssentials.MOD_ID)
public final class EssentialsNetwork {
    /** Versao do protocolo. Bump quando mudar o formato de algum payload. */
    private static final String PROTOCOL_VERSION = "1";

    private EssentialsNetwork() {
    }

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION).optional();

        registrar.playToClient(SyncFakeNamesPayload.TYPE, SyncFakeNamesPayload.STREAM_CODEC, EssentialsNetwork::handleSync);
        registrar.playToClient(UpdateFakeNamePayload.TYPE, UpdateFakeNamePayload.STREAM_CODEC, EssentialsNetwork::handleUpdate);
        registrar.playToClient(RetiredPhoneContactsPayload.TYPE, RetiredPhoneContactsPayload.STREAM_CODEC,
                EssentialsNetwork::handleRetiredPhoneContacts);
        registrar.playToClient(PhoneNameDirectoryPayload.TYPE, PhoneNameDirectoryPayload.STREAM_CODEC,
                EssentialsNetwork::handlePhoneNameDirectory);

        // Grupos do app Mensagens. Pacotes novos, nao formato novo: a versao do protocolo fica.
        registrar.playToServer(PhoneGroupPayloads.Action.TYPE, PhoneGroupPayloads.Action.STREAM_CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) {
                context.enqueueWork(() -> PhoneGroupService.handleAction(player, payload));
            }
        });
        registrar.playToServer(PhoneGroupPayloads.Send.TYPE, PhoneGroupPayloads.Send.STREAM_CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) {
                context.enqueueWork(() -> PhoneGroupService.handleSend(player, payload));
            }
        });
        registrar.playToClient(PhoneGroupPayloads.Sync.TYPE, PhoneGroupPayloads.Sync.STREAM_CODEC, (payload, context) -> {
            if (FMLEnvironment.dist == Dist.CLIENT) {
                context.enqueueWork(() -> com.aurorion.essentials.client.PhoneGroupsClient.applySync(payload));
            }
        });
        registrar.playToClient(PhoneGroupPayloads.Messages.TYPE, PhoneGroupPayloads.Messages.STREAM_CODEC, (payload, context) -> {
            if (FMLEnvironment.dist == Dist.CLIENT) {
                context.enqueueWork(() -> com.aurorion.essentials.client.PhoneGroupsClient.receive(payload));
            }
        });
        registrar.playToClient(PhoneGroupPayloads.Notice.TYPE, PhoneGroupPayloads.Notice.STREAM_CODEC, (payload, context) -> {
            if (FMLEnvironment.dist == Dist.CLIENT) {
                context.enqueueWork(() -> com.aurorion.essentials.client.PhoneGroupsClient.notice(payload));
            }
        });
    }

    private static void handlePhoneNameDirectory(PhoneNameDirectoryPayload payload, IPayloadContext context) {
        if (FMLEnvironment.dist != Dist.CLIENT) return;
        context.enqueueWork(() -> com.aurorion.essentials.client.MattupolisPhoneNames.applyDirectory(
                payload.replace(), payload.entries()));
    }

    private static void handleRetiredPhoneContacts(RetiredPhoneContactsPayload payload, IPayloadContext context) {
        if (FMLEnvironment.dist != Dist.CLIENT) return;
        context.enqueueWork(() -> com.aurorion.essentials.client.PhoneContactCleanup.apply(payload.retired()));
    }

    private static void handleSync(SyncFakeNamesPayload payload, IPayloadContext context) {
        if (FMLEnvironment.dist != Dist.CLIENT) return;
        context.enqueueWork(() -> {
            FakeNameRegistry.clear();
            payload.names().forEach((uuid, raw) -> FakeNameRegistry.put(uuid, FakeName.parse(raw)));
            com.aurorion.essentials.client.FakeNameDisplayRefresh.refreshAll();
        });
    }

    private static void handleUpdate(UpdateFakeNamePayload payload, IPayloadContext context) {
        if (FMLEnvironment.dist != Dist.CLIENT) return;
        context.enqueueWork(() -> {
            if (payload.name().isPresent()) {
                FakeNameRegistry.put(payload.player(), FakeName.parse(payload.name().get()));
            } else {
                FakeNameRegistry.remove(payload.player());
            }
            com.aurorion.essentials.client.FakeNameDisplayRefresh.refresh(payload.player());
        });
    }
}
