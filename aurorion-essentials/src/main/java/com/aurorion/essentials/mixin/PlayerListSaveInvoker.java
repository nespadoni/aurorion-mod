package com.aurorion.essentials.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Save only the recovered player; PlayerList#save is protected and saveAll would write 80+ players. */
@Mixin(PlayerList.class)
public interface PlayerListSaveInvoker {
    @Invoker("save")
    void aurorion_essentials$savePlayer(ServerPlayer player);
}
