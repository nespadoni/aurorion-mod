package com.aurorion.personagem.mixin;

import com.aurorion.personagem.creation.CreationManager;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Prende de verdade quem espera um personagem.
 *
 * <p>O espectador sozinho nao prende: no vanilla ele voa, atravessa parede, se teleporta ate
 * qualquer jogador pelo menu do espectador e gruda a camera em quem clicar — e o servidor carrega e
 * gera chunk onde ele estiver. Para quem ainda nao existe isso e um mapa ao vivo do servidor e de
 * onde cada um esta. Aqui o servidor simplesmente nao aceita esses pacotes: a posicao do lado do
 * servidor so muda por codigo do proprio servidor (staff, respawn), nunca pelo cliente.
 *
 * <p>O cliente ainda desenha o proprio movimento localmente; a varredura do
 * {@link CreationManager} devolve a camera dele ao ponto real uma vez por segundo, so quando ele de
 * fato tentou sair do lugar.
 *
 * <p>Roda <b>depois</b> do {@code ensureRunningOnSameThread}, como o mixin do Limbo: a consulta ao
 * {@link CreationManager} e feita na thread do servidor, nunca na de rede.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class HeldConnectionMixin {
    @Shadow
    public ServerPlayer player;

    @Inject(method = "handleMovePlayer",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/server/level/ServerLevel;)V",
                    shift = At.Shift.AFTER), cancellable = true)
    private void aurorion$holdPosition(ServerboundMovePlayerPacket packet, CallbackInfo ci) {
        if (player == null || !CreationManager.isHeld(player)) return;
        ci.cancel();

        if (!packet.hasPosition()) return;
        double dx = packet.getX(player.getX()) - player.getX();
        double dy = packet.getY(player.getY()) - player.getY();
        double dz = packet.getZ(player.getZ()) - player.getZ();
        // O lembrete de posicao que o cliente manda parado chega com a mesma coordenada; so um
        // deslocamento de verdade pede a correcao (e o pacote de teleporte que vem com ela).
        if (dx * dx + dy * dy + dz * dz > 1.0E-4) CreationManager.refusedMove(player);
    }

    @Inject(method = {"handleTeleportToEntity", "handleInteract"},
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/server/level/ServerLevel;)V",
                    shift = At.Shift.AFTER), cancellable = true)
    private void aurorion$holdSpectatorReach(CallbackInfo ci) {
        if (player != null && CreationManager.isHeld(player)) ci.cancel();
    }
}
