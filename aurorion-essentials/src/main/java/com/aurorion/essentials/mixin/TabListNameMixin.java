package com.aurorion.essentials.mixin;

import com.aurorion.essentials.tab.TabNames;
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
 *
 * <p>No {@code RETURN}, e nao no {@code HEAD}: o evento {@code TabListNameFormat} do NeoForge roda
 * primeiro, e o que um mod de tab montar nele (prefixo de grupo, sufixo de vanish) e mantido — so o
 * trecho do nome vira o nome do personagem, na cor da casa. Ver {@link TabNames}.
 */
@Mixin(ServerPlayer.class)
public abstract class TabListNameMixin {

    @Inject(method = "getTabListDisplayName", at = @At("RETURN"), cancellable = true)
    private void aurorion_essentials$fakeTabListName(CallbackInfoReturnable<Component> cir) {
        ServerPlayer self = (ServerPlayer) (Object) this;
        Component incoming = cir.getReturnValue();
        Component decorated = TabNames.decorate(self.getUUID(), self.getGameProfile().getName(), incoming);
        if (decorated != incoming) cir.setReturnValue(decorated);
    }
}
