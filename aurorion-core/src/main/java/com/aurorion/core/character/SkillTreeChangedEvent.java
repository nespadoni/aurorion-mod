package com.aurorion.core.character;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.Event;

/**
 * Os nós de alguém mudaram: compra, perda, respec, concessão da staff ou troca de personagem. Postado
 * pelo dono da árvore ({@code aurorion-trama}) no {@code NeoForge.EVENT_BUS}, na thread do servidor,
 * com o jogador online. Quem deriva algo dos nós (o nível arcano e a especialidade, na magia) recalcula
 * só aqui e no login.
 */
public final class SkillTreeChangedEvent extends Event {
    private final ServerPlayer player;

    public SkillTreeChangedEvent(ServerPlayer player) {
        this.player = player;
    }

    public ServerPlayer player() {
        return player;
    }
}
