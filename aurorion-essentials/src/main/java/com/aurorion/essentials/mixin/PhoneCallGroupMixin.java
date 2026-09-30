package com.aurorion.essentials.mixin;

import com.aurorion.essentials.voice.PhoneCallGroups;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Ligacao do telefone em grupo <b>aberto</b> do Simple Voice Chat: quem esta perto de quem fala ao
 * telefone ouve o lado dela da conversa, como na vida real.
 *
 * <p>O telefone tenta criar o grupo como {@code ISOLATED} por reflexao, procurando um
 * {@code setType} que receba um <i>enum</i>. Na API do Voice Chat {@code Group.Type} e uma interface
 * com constantes, entao a busca falha em silencio e o grupo nasce {@code NORMAL} — o tipo em que
 * ninguem de fora ouve os membros. E o sintoma relatado: a pessoa fala no telefone e quem esta do
 * lado nao escuta nada.
 *
 * <p>Aqui o grupo inteiro e montado pela API tipada ({@link PhoneCallGroups}). Se isso nao for
 * possivel (Voice Chat ainda nao iniciou), o original roda como antes. O {@code Object} no retorno e
 * de proposito: esta classe e fundida no telefone e nao pode citar tipo do Voice Chat.
 */
@Pseudo
@Mixin(targets = "com.mattupolis.phone.voice.MattupolisVoiceCallManager", remap = false)
public abstract class PhoneCallGroupMixin {
    @Inject(method = "buildPhoneCallGroup", at = @At("HEAD"), cancellable = true, require = 0)
    private static void aurorion_essentials$openGroup(String callerName, String targetName,
                                                      CallbackInfoReturnable<Object> cir) {
        Object group = PhoneCallGroups.build();
        if (group != null) cir.setReturnValue(group);
    }
}
