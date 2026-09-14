package com.aurorion.ethereal.network;

import com.aurorion.ethereal.AurorionEthereal;
import com.aurorion.ethereal.block.entity.AeonicProjectorBlockEntity;
import com.aurorion.ethereal.ceremony.CeremonyManager;
import com.aurorion.ethereal.client.EtherealClientNetwork;
import com.aurorion.ethereal.house.HouseManager;
import com.aurorion.ethereal.ranking.BoardMode;
import com.aurorion.ethereal.ranking.BoardService;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.jetbrains.annotations.Nullable;


/**
 * Registro e ponta servidora de todos os pacotes do mod.
 *
 * <p>Cada handler de cliente so toca em {@link EtherealClientNetwork} depois do teste de
 * {@code Dist}: assim o servidor dedicado nunca chega a carregar uma classe que importa
 * {@code Minecraft}.
 */
@EventBusSubscriber(modid = AurorionEthereal.MOD_ID)
public final class EtherealNetwork {
    /** Versao do protocolo. Bump quando mudar o formato de algum payload. */
    private static final String PROTOCOL_VERSION = "1";

    /** Alcance para editar um placar, em blocos ao quadrado. O cliente pode mentir na posicao. */
    private static final double MAX_EDIT_DISTANCE_SQR = 64.0;

    private EtherealNetwork() {
    }

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        // optional(): um cliente sem este mod nao deve ser barrado por causa dele — so nao consegue
        // usar o altar nem enxergar o holograma do placar.
        PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION).optional();

        registrar.playToClient(OpenHouseSelectionPayload.TYPE, OpenHouseSelectionPayload.STREAM_CODEC,
                EtherealNetwork::handleOpenSelection);
        registrar.playToClient(HouseChoiceResultPayload.TYPE, HouseChoiceResultPayload.STREAM_CODEC,
                EtherealNetwork::handleChoiceResult);
        registrar.playToClient(OpenCeremonyPayload.TYPE, OpenCeremonyPayload.STREAM_CODEC,
                EtherealNetwork::handleOpenCeremony);
        registrar.playToClient(CeremonyClosedPayload.TYPE, CeremonyClosedPayload.STREAM_CODEC,
                EtherealNetwork::handleCeremonyClosed);
        registrar.playToClient(RevealHousePayload.TYPE, RevealHousePayload.STREAM_CODEC,
                EtherealNetwork::handleReveal);
        registrar.playToClient(OpenProjectorConfigPayload.TYPE, OpenProjectorConfigPayload.STREAM_CODEC,
                EtherealNetwork::handleOpenProjectorConfig);

        registrar.playToServer(ChooseHousePayload.TYPE, ChooseHousePayload.STREAM_CODEC, EtherealNetwork::handleChoose);
        registrar.playToServer(CeremonyAnswerPayload.TYPE, CeremonyAnswerPayload.STREAM_CODEC, EtherealNetwork::handleAnswer);
        registrar.playToServer(SaveProjectorConfigPayload.TYPE, SaveProjectorConfigPayload.STREAM_CODEC, EtherealNetwork::handleSaveProjector);
    }

    // --- Ponta cliente --------------------------------------------------------------------------
    //
    // Seis metodos quase iguais, e nao um helper que receba `EtherealClientNetwork::algumaCoisa`.
    // A repeticao e o ponto: montar aquele method reference aconteceria no *registro*, que roda
    // tambem no servidor dedicado, e criar o handle exige resolver a classe de cliente — que la nao
    // existe. Aqui a referencia mora dentro do corpo do metodo, depois do teste de Dist, entao ela
    // so e resolvida quando de fato executa. Foi o mesmo raciocinio que barrou um helper de registro
    // de pacotes no aurorion-core (SDD §3.1): trocar seis linhas por um risco de derrubar o servidor
    // dedicado e pessimo negocio.

    private static void handleOpenSelection(OpenHouseSelectionPayload payload, IPayloadContext context) {
        if (FMLEnvironment.dist != Dist.CLIENT) return;
        EtherealClientNetwork.openSelection(payload, context);
    }

    private static void handleChoiceResult(HouseChoiceResultPayload payload, IPayloadContext context) {
        if (FMLEnvironment.dist != Dist.CLIENT) return;
        EtherealClientNetwork.choiceResult(payload, context);
    }

    private static void handleOpenCeremony(OpenCeremonyPayload payload, IPayloadContext context) {
        if (FMLEnvironment.dist != Dist.CLIENT) return;
        EtherealClientNetwork.openCeremony(payload, context);
    }

    private static void handleCeremonyClosed(CeremonyClosedPayload payload, IPayloadContext context) {
        if (FMLEnvironment.dist != Dist.CLIENT) return;
        EtherealClientNetwork.ceremonyClosed(payload, context);
    }

    private static void handleReveal(RevealHousePayload payload, IPayloadContext context) {
        if (FMLEnvironment.dist != Dist.CLIENT) return;
        EtherealClientNetwork.reveal(payload, context);
    }

    private static void handleOpenProjectorConfig(OpenProjectorConfigPayload payload, IPayloadContext context) {
        if (FMLEnvironment.dist != Dist.CLIENT) return;
        EtherealClientNetwork.openProjectorConfig(payload, context);
    }


    // --- Casas e cerimonia ---------------------------------------------------------------------

    private static void handleChoose(ChooseHousePayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        context.enqueueWork(() -> HouseManager.choose(player, payload.house()));
    }

    private static void handleAnswer(CeremonyAnswerPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        context.enqueueWork(() -> CeremonyManager.answer(player, payload.question(), payload.option()));
    }

    // --- Projetor Aeonico ------------------------------------------------------------------------

    public static void openProjectorConfig(ServerPlayer player, AeonicProjectorBlockEntity projector) {
        PacketDistributor.sendToPlayer(player, new OpenProjectorConfigPayload(
                projector.getBlockPos(), projector.mode().ordinal(),
                projector.holoWidth(), projector.holoHeight()));
    }

    private static void handleSaveProjector(SaveProjectorConfigPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        context.enqueueWork(() -> {
            AeonicProjectorBlockEntity projector = editableProjector(player, payload.pos());
            if (projector == null) return;

            projector.applyConfiguration(BoardMode.byOrdinal(payload.mode()),
                    payload.holoWidth(), payload.holoHeight());
            BoardService.refresh(projector);
        });
    }

    /**
     * O projetor que este jogador pode mesmo configurar agora.
     *
     * <p>Permissao e distancia sao conferidas <b>a cada pacote</b>, e nao so na abertura da tela: uma
     * tela aberta continua aberta depois de o jogador andar para longe, ou depois de perder o cargo.
     */
    @Nullable
    private static AeonicProjectorBlockEntity editableProjector(ServerPlayer player, BlockPos pos) {
        if (!player.hasPermissions(2) || player.blockPosition().distSqr(pos) > MAX_EDIT_DISTANCE_SQR) {
            return null;
        }
        return player.level().getBlockEntity(pos) instanceof AeonicProjectorBlockEntity projector ? projector : null;
    }
}
