package com.aurorion.essentials.mixin;

import com.aurorion.essentials.fakename.FakeNameRegistry;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * {@code getTabListDisplayName()} so existe em {@code ServerPlayer} (nao em {@code Player}, ao
 * contrario de {@code getName()}) e nao chama {@code getName()} internamente — por isso precisa
 * do proprio override, separado de {@link PlayerNameMixin}.
 */
@Mixin(ServerPlayer.class)
public abstract class TabListNameMixin {

    @Inject(method = "getTabListDisplayName", at = @At("HEAD"), cancellable = true)
    private void aurorion_essentials$fakeTabListName(CallbackInfoReturnable<Component> cir) {
        Component fakeName = FakeNameRegistry.getDisplayName(((ServerPlayer) (Object) this).getUUID());
        if (fakeName != null) cir.setReturnValue(fakeName);
    }
}
