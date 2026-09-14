package com.aurorion.mundos.border;

import com.aurorion.mundos.AurorionMundos;
import com.aurorion.mundos.config.MundosConfig;
import com.aurorion.mundos.mixin.DelegateBorderTargetAccessor;
import com.aurorion.mundos.mixin.WorldBorderListenersAccessor;
import com.aurorion.mundos.world.WorldCatalog;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundInitializeBorderPacket;
import net.minecraft.network.protocol.game.ClientboundSetBorderCenterPacket;
import net.minecraft.network.protocol.game.ClientboundSetBorderLerpSizePacket;
import net.minecraft.network.protocol.game.ClientboundSetBorderSizePacket;
import net.minecraft.network.protocol.game.ClientboundSetBorderWarningDelayPacket;
import net.minecraft.network.protocol.game.ClientboundSetBorderWarningDistancePacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.border.BorderChangeListener;
import net.minecraft.world.level.border.WorldBorder;

import java.util.Iterator;
import java.util.List;

/**
 * Faz cada mundo ter a sua propria barreira — no servidor e na tela do jogador.
 *
 * <p>O vanilla amarra as barreiras em dois lugares, e os dois precisam ser desfeitos:
 *
 * <ol>
 *   <li><b>No servidor</b>, {@code MinecraftServer#createLevels} pendura um
 *       {@code DelegateBorderChangeListener} da barreira do overworld em toda dimensao criada. Toda
 *       mudanca no overworld e copiada para as outras.</li>
 *   <li><b>Na rede</b>, {@code PlayerList#sendLevelInfo} manda
 *       {@code server.overworld().getWorldBorder()} — ignorando o {@code level} que recebeu como
 *       parametro. O jogador que entra num mundo nosso ve, desenhada, a barreira do overworld.</li>
 * </ol>
 *
 * <h2>Por que remover o delegado, e nao so evitar mexer no overworld</h2>
 *
 * <p>Porque "nao mexa no overworld" e disciplina de quem usa, e disciplina falha em silencio
 * (SDD §3.1). Duas coisas furam essa promessa sem ninguem notar: o {@code applySettings} que o
 * proprio {@code createLevels} chama <b>depois</b> de registrar os delegados, e qualquer
 * {@code /worldborder} da staff — que no vanilla escreve sempre no overworld. Removido o delegado, o
 * vazamento e impossivel em vez de improvavel.
 *
 * <h2>Custo</h2>
 *
 * <p>Zero por tick. O desamarre roda uma vez no boot; os pacotes saem quando alguem muda a barreira
 * ou troca de dimensao. O {@code tick()} de cada barreira o vanilla ja fazia — {@code ServerLevel}
 * sempre ticou a sua propria, mesmo quando ela era um espelho.
 */
public final class BorderSync {
    private BorderSync() {
    }

    /**
     * Solta as barreiras dos nossos mundos da barreira do overworld e devolve a cada uma o que estava
     * salvo. Uma passada, no {@code ServerStartedEvent}.
     */
    public static void install(MinecraftServer server) {
        WorldBorder overworld = server.overworld().getWorldBorder();
        BorderData data = BorderData.get(server);
        double defaultSize = MundosConfig.DEFAULT_BORDER_SIZE.get();

        for (ResourceKey<Level> dimension : WorldCatalog.managed()) {
            ServerLevel level = server.getLevel(dimension);
            if (level == null) {
                AurorionMundos.LOGGER.warn(
                        "'{}' tem seed declarado mas nao existe como dimensao — falta o JSON em data/<ns>/dimension/?",
                        dimension.location());
                continue;
            }

            WorldBorder border = level.getWorldBorder();
            unbind(overworld, border);

            BorderData.Settings settings = data.get(dimension);
            if (settings == null) {
                // Mundo subindo pela primeira vez. O que a barreira tem agora e copia do overworld,
                // herdada do applySettings de createLevels — nao serve de ponto de partida.
                settings = BorderData.Settings.initial(defaultSize);
            }
            settings.applyTo(border);
            data.remember(dimension, border);

            // Depois de aplicar, nunca antes: o listener existe para propagar mudancas futuras, e
            // nao ha para quem anunciar a restauracao inicial (nenhum jogador conectado ainda).
            border.addListener(new PerDimensionListener(level));
        }
    }

    /**
     * Tira da barreira do overworld o delegado que empurra as mudancas dela para {@code border}.
     *
     * <p>Percorre a lista de ouvintes por falta de alternativa: o delegado e criado inline no vanilla
     * e ninguem guarda a referencia, entao ele so pode ser identificado pelo alvo. Delegado de
     * dimensao que nao e nossa fica onde esta — Nether, End e dimensao de mod continuam seguindo o
     * overworld como sempre seguiram.
     */
    private static void unbind(WorldBorder overworld, WorldBorder border) {
        List<BorderChangeListener> listeners = ((WorldBorderListenersAccessor) overworld).aurorion_mundos$listeners();

        for (Iterator<BorderChangeListener> iterator = listeners.iterator(); iterator.hasNext(); ) {
            BorderChangeListener listener = iterator.next();
            if (listener instanceof BorderChangeListener.DelegateBorderChangeListener delegate
                    && ((DelegateBorderTargetAccessor) delegate).aurorion_mundos$target() == border) {
                iterator.remove();
            }
        }
    }

    /**
     * Manda ao jogador a barreira da dimensao em que ele esta.
     *
     * <p>Chamado <b>depois</b> do {@code sendLevelInfo} do vanilla, que mandou a do overworld — o
     * pacote de inicializacao e absoluto, entao o ultimo a chegar vence. Corrigir por cima custa um
     * pacote em tres eventos raros (entrar, trocar de dimensao, renascer); a alternativa seria um
     * mixin no {@code PlayerList}, um ponto de conflito a mais num modpack pesado por nenhum ganho.
     */
    public static void sendTo(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)) return;
        if (!WorldCatalog.isManaged(level.dimension())) return;

        player.connection.send(new ClientboundInitializeBorderPacket(level.getWorldBorder()));
    }

    /**
     * Avisa <b>so quem esta nesta dimensao</b>, e grava a mudanca.
     *
     * <p>O do vanilla ({@code PlayerList#addWorldborderListener}) faz {@code broadcastAll}: com uma
     * barreira so isso estava certo, com uma por mundo mandaria a barreira errada para quem esta em
     * outro lugar.
     *
     * <p>Gravar aqui, e nao em quem chama, e o que faz a persistencia nao depender de ninguem lembrar
     * dela: qualquer caminho que mude a barreira — comando nosso, comando de outro mod, script —
     * passa por este listener e fica salvo (SDD §3.1).
     */
    private record PerDimensionListener(ServerLevel level) implements BorderChangeListener {
        @Override
        public void onBorderSizeSet(WorldBorder border, double newSize) {
            broadcast(new ClientboundSetBorderSizePacket(border));
            remember(border);
        }

        @Override
        public void onBorderSizeLerping(WorldBorder border, double oldSize, double newSize, long time) {
            broadcast(new ClientboundSetBorderLerpSizePacket(border));
            remember(border);
        }

        @Override
        public void onBorderCenterSet(WorldBorder border, double x, double z) {
            broadcast(new ClientboundSetBorderCenterPacket(border));
            remember(border);
        }

        @Override
        public void onBorderSetWarningTime(WorldBorder border, int newTime) {
            broadcast(new ClientboundSetBorderWarningDelayPacket(border));
            remember(border);
        }

        @Override
        public void onBorderSetWarningBlocks(WorldBorder border, int newDistance) {
            broadcast(new ClientboundSetBorderWarningDistancePacket(border));
            remember(border);
        }

        /** Dano nao trafega: o vanilla tambem nao manda: e decidido no servidor, a cada tick, por jogador. */
        @Override
        public void onBorderSetDamagePerBlock(WorldBorder border, double newAmount) {
            remember(border);
        }

        @Override
        public void onBorderSetDamageSafeZOne(WorldBorder border, double newSize) {
            remember(border);
        }

        private void broadcast(Packet<?> packet) {
            List<ServerPlayer> players = level.players();
            for (int i = 0; i < players.size(); i++) {
                players.get(i).connection.send(packet);
            }
        }

        private void remember(WorldBorder border) {
            BorderData.get(level.getServer()).remember(level.dimension(), border);
        }
    }
}
