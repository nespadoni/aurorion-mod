package com.aurorion.ficha.network;

import com.aurorion.ficha.AurorionFicha;
import com.aurorion.ficha.server.FichaServer;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

/**
 * Canal da ficha. Opcional: cliente sem o mod entra no servidor normalmente; os comandos respondem
 * no chat com linhas clicáveis para ele.
 *
 * <h2>Versão</h2>
 * A negociação do NeoForge só tolera a <b>falta</b> do canal opcional. Cliente com o mod em outra
 * {@link #PROTOCOL_VERSION} não cai no chat: é recusado na entrada, com a mensagem de versão
 * incompatível do próprio NeoForge. Por isso a versão só muda quando o formato de um pacote que já
 * existe muda (e então o modpack sobe junto). Pacote novo não precisa: tipo que um lado não conhece
 * fica de fora do canal ({@link #send} confere) e o resto continua.
 *
 * <h2>O que vem do cliente</h2>
 * Não é confiável. Os codecs têm teto em todo texto e lista (o que passa disso derruba só a
 * conexão de quem mandou); {@link FloodGuard} corta a rajada ainda na thread da rede, antes do
 * {@code enqueueWork}; o {@link FichaServer} confere de novo permissão, ids, CD e alvo de cada
 * pedido. Erro de código ao tratar um pacote vira aviso no log, não desconexão do jogador.
 *
 * <p>As classes de cliente são citadas pelo nome completo só dentro do teste de {@link Dist}
 * (SDD §3.1, §13.5): o servidor dedicado nunca as carrega.
 */
@EventBusSubscriber(modid = AurorionFicha.MOD_ID)
public final class FichaNetwork {
    /**
     * Muda junto com o formato dos pacotes que já existem (ver a nota de versão acima). 2: o
     * {@code Ask} ganhou {@code formula} (pedido de dados do mestre).
     */
    private static final String PROTOCOL_VERSION = "2";

    /**
     * Rajada de 16 pacotes, depois 8 por segundo. Abrir a ficha manda 2 (abrir + candidatos); a mesa
     * do mestre, 1 por envio. Só cliente adulterado ou tecla presa chega perto.
     */
    private static final FloodGuard FLOOD = new FloodGuard(16, 8, System::currentTimeMillis);

    private FichaNetwork() {
    }

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION).optional();

        registrar.playToServer(FichaPayloads.Action.TYPE, FichaPayloads.Action.STREAM_CODEC,
                (payload, context) -> fromClient(context, null, player -> FichaServer.action(player, payload)));
        registrar.playToServer(FichaPayloads.Roll.TYPE, FichaPayloads.Roll.STREAM_CODEC,
                (payload, context) -> fromClient(context, "rolar", player -> FichaServer.roll(player, payload)));
        registrar.playToServer(FichaPayloads.Ask.TYPE, FichaPayloads.Ask.STREAM_CODEC,
                (payload, context) -> fromClient(context, "mestre", player -> FichaServer.ask(player, payload)));

        registrar.playToClient(FichaPayloads.View.TYPE, FichaPayloads.View.STREAM_CODEC, (payload, context) -> {
            if (FMLEnvironment.dist == Dist.CLIENT) context.enqueueWork(() -> com.aurorion.ficha.client.FichaClient.view(payload));
        });
        registrar.playToClient(FichaPayloads.Candidates.TYPE, FichaPayloads.Candidates.STREAM_CODEC, (payload, context) -> {
            if (FMLEnvironment.dist == Dist.CLIENT) context.enqueueWork(() -> com.aurorion.ficha.client.FichaClient.candidates(payload));
        });
        registrar.playToClient(FichaPayloads.Card.TYPE, FichaPayloads.Card.STREAM_CODEC, (payload, context) -> {
            if (FMLEnvironment.dist == Dist.CLIENT) context.enqueueWork(() -> com.aurorion.ficha.client.FichaClient.card(payload));
        });
        registrar.playToClient(FichaPayloads.Request.TYPE, FichaPayloads.Request.STREAM_CODEC, (payload, context) -> {
            if (FMLEnvironment.dist == Dist.CLIENT) context.enqueueWork(() -> com.aurorion.ficha.client.FichaClient.request(payload));
        });
        registrar.playToClient(FichaPayloads.Status.TYPE, FichaPayloads.Status.STREAM_CODEC, (payload, context) -> {
            if (FMLEnvironment.dist == Dist.CLIENT) context.enqueueWork(() -> com.aurorion.ficha.client.FichaClient.status(payload));
        });
        registrar.playToClient(FichaPayloads.Table.TYPE, FichaPayloads.Table.STREAM_CODEC, (payload, context) -> {
            if (FMLEnvironment.dist == Dist.CLIENT) context.enqueueWork(() -> com.aurorion.ficha.client.FichaClient.table(payload));
        });
        registrar.playToClient(FichaPayloads.TableUpdate.TYPE, FichaPayloads.TableUpdate.STREAM_CODEC, (payload, context) -> {
            if (FMLEnvironment.dist == Dist.CLIENT) context.enqueueWork(() -> com.aurorion.ficha.client.FichaClient.tableUpdate(payload));
        });
        registrar.playToClient(FichaPayloads.Notice.TYPE, FichaPayloads.Notice.STREAM_CODEC, (payload, context) -> {
            if (FMLEnvironment.dist == Dist.CLIENT) context.enqueueWork(() -> com.aurorion.ficha.client.FichaClient.notice(payload));
        });
    }

    /**
     * Um pacote do cliente: passa pelo {@link FloodGuard} na thread da rede e só então vai para a
     * thread do servidor. {@code busyContext}: a tela que espera resposta ({@code rolar},
     * {@code mestre}) recebe "ocupado" em vez de ficar travada em "rolando…" — no máximo um aviso a
     * cada 2 s. Ação sem tela à espera ({@code null}) só é descartada.
     */
    private static void fromClient(IPayloadContext context, @Nullable String busyContext, Consumer<ServerPlayer> work) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        FloodGuard.Verdict verdict = FLOOD.acquire(player.getUUID());
        if (verdict != FloodGuard.Verdict.PASS) {
            if (verdict == FloodGuard.Verdict.DROP_NOTIFY && busyContext != null) {
                context.reply(new FichaPayloads.Status(busyContext, "ocupado", "Muitas ações seguidas: espere um instante."));
            }
            return;
        }
        context.enqueueWork(() -> {
            // O jogador pode ter saído entre a chegada do pacote e esta tarefa.
            if (player.hasDisconnected()) return;
            try {
                work.accept(player);
            } catch (RuntimeException e) {
                AurorionFicha.LOGGER.warn("Ficha: erro ao tratar um pacote de {}", player.getGameProfile().getName(), e);
            }
        });
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        FLOOD.forget(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onStopped(ServerStoppedEvent event) {
        FLOOD.clear();
    }

    /** O cliente tem as telas e o HUD da ficha? */
    public static boolean canOpen(ServerPlayer player) {
        return player.connection != null && player.connection.hasChannel(FichaPayloads.View.TYPE);
    }

    /** Só para quem tem o canal: cliente sem o mod não recebe pacote desconhecido. */
    public static void send(ServerPlayer player, CustomPacketPayload payload) {
        if (player.connection != null && player.connection.hasChannel(payload.type())) {
            PacketDistributor.sendToPlayer(player, payload);
        }
    }

    /** Jogadores com balde no teto de rajada (diagnóstico). */
    public static int floodBuckets() {
        return FLOOD.size();
    }
}
