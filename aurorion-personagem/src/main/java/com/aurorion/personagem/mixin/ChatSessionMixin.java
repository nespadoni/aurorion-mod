package com.aurorion.personagem.mixin;

import com.aurorion.personagem.alt.AltLogin;
import net.minecraft.network.protocol.game.ServerboundChatSessionUpdatePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Ignora a sessao de chat assinada de quem esta como o segundo personagem.
 *
 * <p>A chave de chat que o cliente manda foi emitida pela Mojang para o UUID <b>real</b> da conta. O
 * vanilla a valida contra o perfil do jogador — que agora e o do alt — e, quando nao bate,
 * <b>desconecta</b>. Sem sessao, o chat do alt segue como mensagem nao assinada, o que exige
 * {@code enforce-secure-profile=false} no {@code server.properties} (o mesmo que o NoChatReports
 * pede).
 *
 * <p>Roda na thread de rede, antes do {@code ensureRunningOnSameThread}: por isso a consulta e o
 * conjunto concorrente do {@link AltLogin}, e nao o {@code SavedData}.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ChatSessionMixin {
    @Shadow
    public ServerPlayer player;

    @Inject(method = "handleChatSessionUpdate", at = @At("HEAD"), cancellable = true)
    private void aurorion$ignoreAltChatSession(ServerboundChatSessionUpdatePacket packet, CallbackInfo ci) {
        if (player != null && AltLogin.isOnlineAlt(player.getUUID())) ci.cancel();
    }
}
