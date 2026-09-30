package com.aurorion.essentials.mixin;

import com.aurorion.essentials.client.MattupolisPhoneNames;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * A letra no circulo do avatar (conversas, agenda, historico de chamadas, Twitter) e a primeira do
 * nick: {@code title().substring(0, 1)}. Com o nome ja trocado ao lado, "S" ao lado de "Arthur"
 * entregava o nick. So a chamada {@code substring(0, 1)} muda; qualquer outro corte no mesmo metodo
 * segue com o texto original, porque os indices dele foram calculados sobre o nick.
 */
@Pseudo
@Mixin(targets = {
        "com.mattupolis.phone.client.gui.PhoneMessagesScreen",
        "com.mattupolis.phone.client.gui.PhoneMessageChatScreen",
        "com.mattupolis.phone.client.gui.PhoneContactsScreen",
        "com.mattupolis.phone.client.gui.PhoneCallHistoryScreen",
        "com.mattupolis.phone.client.gui.PhoneTwitterScreen"
}, remap = false)
public abstract class PhoneAvatarInitialMixin {
    @Redirect(method = {"drawThreadRow", "drawTopBar", "drawContactRow", "drawHistoryRow", "drawProfileAvatar", "drawAvatar"},
            at = @At(value = "INVOKE", target = "Ljava/lang/String;substring(II)Ljava/lang/String;"), require = 0)
    private String aurorion_essentials$characterInitial(String text, int begin, int end) {
        if (begin == 0 && end == 1) {
            String shown = MattupolisPhoneNames.display(text);
            if (shown != null && !shown.isEmpty()) return shown.substring(0, 1);
        }
        return text.substring(begin, end);
    }
}
