package com.aurorion.essentials.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Accessor;

@Pseudo
@Mixin(targets = "com.mattupolis.phone.client.gui.PhoneTwitterStore$Tweet", remap = false)
public interface PhoneTweetDisplayNameAccessor {
    @Accessor("displayName")
    String aurorion_essentials$storedDisplayName();
}
