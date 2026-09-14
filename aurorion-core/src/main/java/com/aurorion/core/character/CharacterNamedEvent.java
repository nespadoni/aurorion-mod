package com.aurorion.core.character;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.Event;

/**
 * The identity is already published when this fires: the account is playable again.
 *
 * <p>Presentation belongs here — display name, greeting, starting kit. Anything that must survive
 * a crash belongs in {@link CharacterResetEvent}, which runs inside the journalled transaction.
 */
public final class CharacterNamedEvent extends Event {
    private final ServerPlayer player;
    private final CharacterData.Character character;
    private final boolean replacement;

    public CharacterNamedEvent(ServerPlayer player, CharacterData.Character character, boolean replacement) {
        this.player = player;
        this.character = character;
        this.replacement = replacement;
    }

    public ServerPlayer player() { return player; }
    public CharacterData.Character character() { return character; }
    /** False on the first character of an account: nothing was reset, progression is intact. */
    public boolean replacement() { return replacement; }
}
