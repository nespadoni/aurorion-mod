package com.aurorion.essentials.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Read the immutable original account key without replacing the record's native username(). */
@Pseudo
@Mixin(targets = {
        "com.mattupolis.phone.client.gui.PhoneTwitterStore$Tweet",
        "com.mattupolis.phone.client.gui.PhoneTwitterStore$TweetComment",
        "com.mattupolis.phone.client.gui.PhoneInstagramStore$GramComment"
}, remap = false)
public interface PhoneSocialIdentityAccessor {
    @Accessor("username")
    String aurorion_essentials$rawUsername();
}
