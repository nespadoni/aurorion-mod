package com.aurorion.essentials.mixin;

import com.aurorion.essentials.voice.VoiceNameSync;
import de.maxhenkel.voicechat.voice.common.PlayerState;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Todo "estado" de jogador do Simple Voice Chat nasce em {@code defaultDisconnectedState}, com o
 * nick da Mojang. Aqui ele nasce com o nome do personagem — o porque esta em {@link VoiceNameSync}.
 *
 * <p>So o texto exibido muda: o Voice Chat identifica todo mundo por UUID, e o nome nao e usado pelo
 * servidor para nada. No {@code aurorion_essentials.voice.mixins.json}, nao obrigatorio: se o Voice
 * Chat nao estiver instalado ou mudar este metodo, o servidor sobe com o nick nos menus dele.
 */
@Pseudo
@Mixin(targets = "de.maxhenkel.voicechat.voice.server.PlayerStateManager", remap = false)
public abstract class VoicechatPlayerNameMixin {
    @Inject(method = "defaultDisconnectedState", at = @At("RETURN"), require = 0)
    private static void aurorion_essentials$characterName(ServerPlayer player, CallbackInfoReturnable<PlayerState> cir) {
        PlayerState state = cir.getReturnValue();
        if (state != null && player != null) state.setName(VoiceNameSync.displayName(player));
    }
}
