package com.aurorion.talk.mixin;

import com.aurorion.talk.server.ProximityChat;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Troca o broadcast global da fala pela entrega por proximidade ({@link ProximityChat}).
 *
 * <p>Sao as duas sobrecargas publicas, e nao o metodo privado que as duas chamam: a de
 * {@link ServerPlayer} e a do chat comum (o {@code ServerGamePacketListenerImpl} chama ela depois do
 * {@code ServerChatEvent}); a de {@link CommandSourceStack} e a do {@code /me} e do {@code /say}.
 * Mirar as publicas deixa o descritor estavel e o predicado de filtro igual ao que o vanilla montaria.
 *
 * <p>{@code @Inject} cancelavel no HEAD, e nao {@code @Redirect}: outro mod do pack pode mexer no
 * mesmo metodo, e injecoes se somam — dois redirects no mesmo ponto derrubam o servidor no boot.
 * Quando {@link ProximityChat#handles} diz nao, o vanilla segue intocado.
 */
@Mixin(PlayerList.class)
public abstract class PlayerListChatMixin {

    @Inject(
            method = "broadcastChatMessage(Lnet/minecraft/network/chat/PlayerChatMessage;Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/network/chat/ChatType$Bound;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void aurorion_talk$proximityFromPlayer(PlayerChatMessage message, ServerPlayer sender,
                                                   ChatType.Bound bound, CallbackInfo ci) {
        if (!ProximityChat.handles(sender, bound)) return;

        ProximityChat.deliver(message, sender::shouldFilterMessageTo, sender, bound);
        ci.cancel();
    }

    @Inject(
            method = "broadcastChatMessage(Lnet/minecraft/network/chat/PlayerChatMessage;Lnet/minecraft/commands/CommandSourceStack;Lnet/minecraft/network/chat/ChatType$Bound;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void aurorion_talk$proximityFromCommand(PlayerChatMessage message, CommandSourceStack source,
                                                    ChatType.Bound bound, CallbackInfo ci) {
        // Console, bloco de comando e funcao nao tem posicao de "quem fala": seguem globais.
        ServerPlayer sender = source.getPlayer();
        if (!ProximityChat.handles(sender, bound)) return;

        ProximityChat.deliver(message, source::shouldFilterMessageTo, sender, bound);
        ci.cancel();
    }
}
