package com.aurorion.economia.api;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.Event;

/** Profession integrations explicitly authorize each server-side sale operation. Default: deny. */
public final class BrokerAuthorizationEvent extends Event {
    private final ServerPlayer player;
    private boolean authorized;
    public BrokerAuthorizationEvent(ServerPlayer player) { this.player = player; }
    public ServerPlayer player() { return player; }
    public boolean authorized() { return authorized; }
    public void authorize() { authorized = true; }
}
