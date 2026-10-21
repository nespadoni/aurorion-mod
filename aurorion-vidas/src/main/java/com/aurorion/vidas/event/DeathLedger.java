package com.aurorion.vidas.event;

import com.aurorion.vidas.AurorionVidas;
import com.aurorion.vidas.config.LivesConfig;
import com.aurorion.vidas.lives.LivesManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * A conta de toda morte de jogador: aberta no inicio do evento, fechada quando a vida e cobrada.
 *
 * <h2>Por que existe</h2>
 *
 * <p>A vida e cobrada em {@code LOW} para que quem cancela a morte (revive, totem custom) fale antes.
 * Mas o barramento de eventos para no primeiro listener que lanca excecao, e a rede de seguranca do
 * {@code aurorion_essentials} ({@code DeathListenerGuardMixin}) engole essa excecao para o servidor
 * nao cair — com o efeito colateral de que nenhum listener depois do quebrado roda. Um mod com bug em
 * {@code NORMAL} bastava para alguem morrer <b>sem perder vida</b>, toda vez.
 *
 * <p>Nenhum dos dois lados pode conhecer o outro (o essentials nao depende deste mod), entao a
 * garantia mora aqui: {@code HIGHEST} abre a conta guardando o proprio evento, {@code LOW} fecha
 * antes de cobrar. Uma conta que sobra aberta no fim do tick com o evento <b>nao cancelado</b> e uma
 * morte que aconteceu sem passar pela cobranca — e ela e cobrada ali, com aviso para a staff.
 *
 * <h2>Por que nao cobra demais</h2>
 *
 * <ul>
 *   <li>Morte cancelada (revive, Finale, totem) deixa o evento cancelado: a conta e descartada.</li>
 *   <li>Excecao <em>depois</em> do {@code LOW}: a conta ja foi fechada e a cobranca ja aconteceu.</li>
 *   <li>Sem o essentials, a excecao derruba o servidor antes do fim do tick: nada a acertar.</li>
 * </ul>
 *
 * <p>O acerto tambem roda no {@code PlayerRespawnPositionEvent}, antes de o destino do respawn ser
 * escolhido: com {@code doImmediateRespawn}, o pedido de respawn de uma morte vinda de pacote pode
 * chegar antes do fim do tick, e a ultima vida precisa estar cobrada para o exilio valer ja nele.
 *
 * <p>Custo: um {@code put} e um {@code remove} por morte de jogador, e um {@code isEmpty} por tick.
 */
final class DeathLedger {
    private static final Map<UUID, LivingDeathEvent> OPEN = new HashMap<>();

    private DeathLedger() {
    }

    static void open(ServerPlayer player, LivingDeathEvent event) {
        OPEN.put(player.getUUID(), event);
    }

    static void close(ServerPlayer player) {
        OPEN.remove(player.getUUID());
    }

    /** Fim do tick: acerta toda conta que ficou aberta. */
    static void settleAll(MinecraftServer server) {
        if (OPEN.isEmpty()) return;
        for (Iterator<Map.Entry<UUID, LivingDeathEvent>> it = OPEN.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, LivingDeathEvent> entry = it.next();
            it.remove();
            settle(server, entry.getKey(), entry.getValue());
        }
    }

    /** Antes do respawn deste jogador: a vida da morte que o trouxe ate aqui ja precisa estar cobrada. */
    static void settle(ServerPlayer player) {
        LivingDeathEvent event = OPEN.remove(player.getUUID());
        if (event != null) settle(player.server, player.getUUID(), event);
    }

    private static void settle(MinecraftServer server, UUID id, LivingDeathEvent event) {
        if (event.isCanceled()) return;
        if (!(event.getEntity() instanceof ServerPlayer dead)) return;
        if (LivesConfig.IGNORE_CREATIVE.get() && (dead.isCreative() || dead.isSpectator())) return;

        // Depois do respawn a entidade e outra; a mensagem e o HUD vao para a que esta conectada.
        ServerPlayer online = server.getPlayerList().getPlayer(id);
        ServerPlayer target = online != null ? online : dead;
        int left = LivesManager.loseLife(target);
        AurorionVidas.LOGGER.warn("Vidas: a morte de {} ({}) nao chegou ao listener que cobra a vida — um listener"
                        + " anterior de LivingDeathEvent lancou excecao (ver o erro do aurorion_essentials logo"
                        + " antes). A vida foi cobrada pela rede de seguranca; restam {}.",
                dead.getGameProfile().getName(), id, left);
    }

    static void reset() {
        OPEN.clear();
    }
}
