package com.aurorion.core.character;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.Event;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Erase everything the previous character owned. One handler per mod that stores per-player state.
 *
 * <p>Fired on the server thread, inside a journalled transaction: the new identity is already
 * reserved on disk but is <b>not</b> published yet, and the account still counts as dead. Throwing
 * aborts the activation and leaves the reservation in place, so the next login retries — which is
 * why every handler must be idempotent.
 *
 * <p>Never reset account authentication, OP/whitelist or moderation permissions here: those belong
 * to the person, not to the character.
 */
public final class CharacterResetEvent extends Event {
    private final MinecraftServer server;
    private final CharacterData.Pending transaction;

    public CharacterResetEvent(MinecraftServer server, CharacterData.Pending transaction) {
        this.server = server;
        this.transaction = transaction;
    }

    public MinecraftServer server() { return server; }
    public UUID account() { return transaction.account(); }
    /** The character being retired. Progression keyed by character ID can be dropped by this. */
    public UUID previousCharacterId() { return transaction.previousId(); }
    public CharacterData.Pending transaction() { return transaction; }

    /** Present in the normal flow; absent when a crashed reset is retried before the owner logs in. */
    @Nullable public ServerPlayer player() { return server.getPlayerList().getPlayer(account()); }
}
