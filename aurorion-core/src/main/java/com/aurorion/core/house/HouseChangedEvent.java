package com.aurorion.core.house;

import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.Event;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * A casa de alguem mudou, ou o catalogo de casas foi recarregado. Postado pelo dono das casas
 * ({@code aurorion-ethereal}) no {@code NeoForge.EVENT_BUS}, na thread do servidor; quem mostra algo
 * derivado da casa (a cor do nome na tab, no {@code aurorion-essentials}) atualiza so neste momento, sem
 * conferir nada por tick.
 */
public final class HouseChangedEvent extends Event {
    private final MinecraftServer server;
    @Nullable private final UUID player;

    public HouseChangedEvent(MinecraftServer server, @Nullable UUID player) {
        this.server = server;
        this.player = player;
    }

    public MinecraftServer server() { return server; }

    /** A conta cuja casa mudou; {@code null} quando o catalogo inteiro foi recarregado ({@code /reload}). */
    @Nullable
    public UUID player() { return player; }
}
