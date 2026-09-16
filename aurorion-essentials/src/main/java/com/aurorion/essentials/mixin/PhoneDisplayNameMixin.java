package com.aurorion.essentials.mixin;

import com.aurorion.essentials.client.MattupolisPhoneNames;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Substitui antes do corte por pixels, sem truncar a identidade para os 16 caracteres de um nick. */
@Pseudo
@Mixin(targets = {
        "com.mattupolis.phone.client.gui.PhoneHomeScreen",
        "com.mattupolis.phone.client.gui.PhoneLockScreen",
        "com.mattupolis.phone.client.gui.PhoneSettingsScreen",
        "com.mattupolis.phone.client.gui.PhoneContactsScreen",
        "com.mattupolis.phone.client.gui.PhoneContactRequestScreen",
        "com.mattupolis.phone.client.gui.PhoneMessagesScreen",
        "com.mattupolis.phone.client.gui.PhoneMessageChatScreen",
        "com.mattupolis.phone.client.gui.PhoneMessageInfoScreen",
        "com.mattupolis.phone.client.gui.PhoneNewMessageScreen",
        "com.mattupolis.phone.client.gui.PhoneCallScreen",
        "com.mattupolis.phone.client.gui.PhoneCallHistoryScreen",
        "com.mattupolis.phone.client.gui.PhoneActiveCallScreen",
        "com.mattupolis.phone.client.gui.PhoneBankScreen",
        "com.mattupolis.phone.client.gui.PhoneBankHistoryScreen",
        "com.mattupolis.phone.client.gui.PhoneBankTransactionDetailScreen",
        "com.mattupolis.phone.client.gui.PhoneBankAdminScreen"
}, remap = false)
public abstract class PhoneDisplayNameMixin {
    @ModifyVariable(method = "trimText", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private String aurorion_essentials$displayCharacterName(String text) {
        return MattupolisPhoneNames.display(text);
    }
}
