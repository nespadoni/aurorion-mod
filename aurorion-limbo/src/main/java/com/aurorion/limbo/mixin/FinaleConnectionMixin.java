package com.aurorion.limbo.mixin;

import com.aurorion.limbo.finale.FinaleManager;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Runs AFTER vanilla moves packet handling to the server thread, never reads SavedData on Netty.
 *
 * <p>Chat e comandos ficam fora daqui de proposito: {@code handleChat}/{@code handleChatCommand} nao
 * chamam {@code ensureRunningOnSameThread} (o vanilla agenda o trabalho por conta propria), entao este
 * ponto de injecao nao existe neles e o {@code defaultRequire = 1} derrubaria o boot. Quem tranca o
 * morto e o {@code LimboServerEvents}: {@code ServerChatEvent} para o chat e {@code CommandEvent} para
 * os comandos, os dois ja na thread do servidor.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class FinaleConnectionMixin {
    @Shadow public ServerPlayer player;
    @Inject(method = {"handleMovePlayer", "handleTeleportToEntity", "handlePlayerAction", "handleContainerClick",
            "handleClientCommand", "handleInteract", "handleUseItem", "handleUseItemOn"},
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/server/level/ServerLevel;)V",
                    shift = At.Shift.AFTER), cancellable = true)
    private void aurorion$lockDeadCharacter(CallbackInfo ci) {
        if (FinaleManager.isDead(player)) ci.cancel();
    }
}
