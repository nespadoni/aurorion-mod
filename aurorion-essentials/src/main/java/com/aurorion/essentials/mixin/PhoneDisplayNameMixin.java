package com.aurorion.essentials.mixin;

import com.aurorion.essentials.client.MattupolisPhoneNames;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Substitui antes do corte por pixels, sem truncar a identidade para os 16 caracteres de um nick.
 *
 * <p>A lista e das telas do telefone que mostram nick de jogador, e cada uma foi conferida no jar
 * instalado — o {@code PhoneMixinContractTest} falha se alguma perder o {@code trimText}. Ficam de
 * fora as telas que so desenham texto do proprio dono (notas, musica, tempo, papel de parede) e o
 * Instagram/Twitter, onde o @ e um perfil escolhido pela pessoa e nao o nick vazando.</p>
 */
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
        "com.mattupolis.phone.client.gui.PhoneBankAdminScreen",
        "com.mattupolis.phone.client.gui.PhoneInstagramBlockedUsersScreen",
        "com.mattupolis.phone.client.gui.PhoneInstagramDmChatScreen",
        "com.mattupolis.phone.client.gui.PhoneInstagramFollowListScreen",
        "com.mattupolis.phone.client.gui.PhoneInstagramFollowRequestsScreen",
        "com.mattupolis.phone.client.gui.PhoneInstagramNotificationsScreen",
        "com.mattupolis.phone.client.gui.PhoneInstagramPhotoViewerScreen",
        "com.mattupolis.phone.client.gui.PhoneInstagramPostDetailScreen",
        "com.mattupolis.phone.client.gui.PhoneInstagramPostShareScreen",
        "com.mattupolis.phone.client.gui.PhoneInstagramProfileScreen",
        "com.mattupolis.phone.client.gui.PhoneInstagramScreen",
        "com.mattupolis.phone.client.gui.PhoneInstagramStoryScreen",
        "com.mattupolis.phone.client.gui.PhoneInstagramStoryViewersScreen",
        // Vendedor do anuncio, remetente do e-mail e alvo do GPS sao nick de jogador como os demais.
        "com.mattupolis.phone.client.gui.PhoneMarketplaceScreen",
        "com.mattupolis.phone.client.gui.PhoneMarketplaceDetailScreen",
        "com.mattupolis.phone.client.gui.PhoneMarketplaceSellScreen",
        "com.mattupolis.phone.client.gui.PhoneMailScreen",
        "com.mattupolis.phone.client.gui.PhoneMailDetailScreen",
        "com.mattupolis.phone.client.gui.PhoneGpsScreen"
}, remap = false)
public abstract class PhoneDisplayNameMixin {
    @ModifyVariable(method = "trimText", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private String aurorion_essentials$displayCharacterName(String text) {
        return MattupolisPhoneNames.display(text);
    }
}
