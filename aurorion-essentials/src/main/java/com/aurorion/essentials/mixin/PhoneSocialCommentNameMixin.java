package com.aurorion.essentials.mixin;

import com.aurorion.essentials.client.MattupolisPhoneNames;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Comment authors bypass trimText: the phone concatenates author + body, then wraps the lines.
 * Replace only the author at those reads, including the corresponding height calculation, so a
 * longer character name wraps consistently. Deletion, likes, profile links and storage use the raw key.
 */
@Pseudo
@Mixin(targets = {
        "com.mattupolis.phone.client.gui.PhoneInstagramPostDetailScreen",
        "com.mattupolis.phone.client.gui.PhoneTwitterPostDetailScreen"
}, remap = false)
public abstract class PhoneSocialCommentNameMixin {
    @Redirect(method = {"drawWrappedCommentRow", "getCommentRowHeight"},
            at = @At(value = "INVOKE",
                    target = "Lcom/mattupolis/phone/client/gui/PhoneInstagramStore$GramComment;username()Ljava/lang/String;"),
            require = 0)
    private String aurorion_essentials$gramCommentAuthor(@Coerce Object comment) {
        return aurorion_essentials$author(comment);
    }

    @Redirect(method = {"drawCommentRow", "getCommentRowHeight"},
            at = @At(value = "INVOKE",
                    target = "Lcom/mattupolis/phone/client/gui/PhoneTwitterStore$TweetComment;username()Ljava/lang/String;"),
            require = 0)
    private String aurorion_essentials$twitterCommentAuthor(@Coerce Object comment) {
        return aurorion_essentials$author(comment);
    }

    @Unique
    private static String aurorion_essentials$author(Object comment) {
        return MattupolisPhoneNames.socialName(
                ((PhoneSocialIdentityAccessor) comment).aurorion_essentials$rawUsername());
    }
}
