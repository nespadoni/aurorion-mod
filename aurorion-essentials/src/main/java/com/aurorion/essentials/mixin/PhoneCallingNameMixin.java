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
@Mixin(targets = "com.mattupolis.phone.client.gui.PhoneCallingScreen", remap = false)
public abstract class PhoneCallingNameMixin {
    @Shadow @Final private String targetName;

    @Redirect(method = "drawCalling", at = @At(value = "FIELD",
            target = "Lcom/mattupolis/phone/client/gui/PhoneCallingScreen;targetName:Ljava/lang/String;"))
    private String aurorion_essentials$targetName(@Coerce Object screen) {
        return MattupolisPhoneNames.callLabel(targetName);
    }
}
