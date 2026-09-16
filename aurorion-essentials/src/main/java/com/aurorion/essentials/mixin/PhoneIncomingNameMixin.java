package com.aurorion.essentials.mixin;

import com.aurorion.essentials.client.MattupolisPhoneNames;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Redirect;

@Pseudo
@Mixin(targets = "com.mattupolis.phone.client.gui.PhoneIncomingCallScreen", remap = false)
public abstract class PhoneIncomingNameMixin {
    @Shadow @Final private String callerName;

    @Redirect(method = "drawIncomingCall", at = @At(value = "FIELD",
            target = "Lcom/mattupolis/phone/client/gui/PhoneIncomingCallScreen;callerName:Ljava/lang/String;"))
    private String aurorion_essentials$callerName(@Coerce Object screen) {
        return MattupolisPhoneNames.callLabel(callerName);
    }
}
