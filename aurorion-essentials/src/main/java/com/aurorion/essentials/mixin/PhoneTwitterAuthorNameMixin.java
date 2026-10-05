package com.aurorion.essentials.mixin;

import com.aurorion.essentials.client.MattupolisPhoneNames;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Old tweets keep their technical account key, but render the account's current character name. */
@Pseudo
@Mixin(targets = {
        "com.mattupolis.phone.client.gui.PhoneTwitterScreen",
        "com.mattupolis.phone.client.gui.PhoneTwitterPostDetailScreen"
}, remap = false)
public abstract class PhoneTwitterAuthorNameMixin {
    @Redirect(method = {"drawComposer", "drawQuoteCard"},
            at = @At(value = "INVOKE",
                    target = "Lcom/mattupolis/phone/client/gui/PhoneTwitterStore$Tweet;displayName()Ljava/lang/String;"),
            require = 0)
    private String aurorion_essentials$currentAuthor(@Coerce Object tweet) {
        return aurorion_essentials$displayAuthor(tweet);
    }

    // A primeira leitura passa o nick para drawAvatar, que busca a imagem pela chave da conta.
    // Troque somente a segunda, usada no texto do cabecalho; a inicial tem seu proprio mixin.
    @Redirect(method = {"drawTweet", "drawTweetDetail"},
            at = @At(value = "INVOKE",
                    target = "Lcom/mattupolis/phone/client/gui/PhoneTwitterStore$Tweet;displayName()Ljava/lang/String;",
                    ordinal = 1), require = 0)
    private String aurorion_essentials$currentHeaderAuthor(@Coerce Object tweet) {
        return aurorion_essentials$displayAuthor(tweet);
    }

    @Redirect(method = "drawTweetDetail",
            at = @At(value = "INVOKE",
                    target = "Lcom/mattupolis/phone/client/gui/PhoneTwitterStore$Tweet;displayName()Ljava/lang/String;",
                    ordinal = 2), require = 0)
    private String aurorion_essentials$currentQuotedAuthor(@Coerce Object tweet) {
        return aurorion_essentials$displayAuthor(tweet);
    }

    @Unique
    private static String aurorion_essentials$displayAuthor(Object tweet) {
        return MattupolisPhoneNames.socialAuthor(
                ((PhoneSocialIdentityAccessor) tweet).aurorion_essentials$rawUsername(),
                ((PhoneTweetDisplayNameAccessor) tweet).aurorion_essentials$storedDisplayName());
    }
}
