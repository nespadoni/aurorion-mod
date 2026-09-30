package com.aurorion.essentials.mixin;

import com.aurorion.essentials.client.MattupolisPhoneNames;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * O nome do personagem nos pedacos do telefone que aparecem por cima do jogo: o painel de
 * notificacoes puxado de qualquer tela, a ilha dinamica (ligacao, GPS), o mini GPS e o HUD da
 * ligacao em andamento. Cada um tem o proprio {@code trim} estatico, fora do {@code trimText} das
 * telas que o {@link PhoneDisplayNameMixin} cobre.
 *
 * <p>Os helpers so cortam texto para caber: trocar o nick ali nao muda nada que volte ao servidor.
 */
@Pseudo
@Mixin(targets = {
        "com.mattupolis.phone.client.gui.PhoneNotificationPanelOverlay",
        "com.mattupolis.phone.client.gui.PhoneDynamicIslandHelper",
        "com.mattupolis.phone.client.gui.PhoneGpsCompactOverlay",
        "com.mattupolis.phone.client.gui.PhoneCallCompactOverlay"
}, remap = false)
public abstract class PhoneOverlayNamesMixin {
    @ModifyVariable(method = {"trimText", "trim", "trimSimple", "trimByWidth"}, at = @At("HEAD"),
            argsOnly = true, ordinal = 0, require = 0)
    private static String aurorion_essentials$overlayName(String text) {
        return MattupolisPhoneNames.displayOrReplace(text);
    }
}
