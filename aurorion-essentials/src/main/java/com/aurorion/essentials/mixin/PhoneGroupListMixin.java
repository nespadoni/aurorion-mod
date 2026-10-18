package com.aurorion.essentials.mixin;

import com.aurorion.essentials.client.PhoneGroupsClient;
import com.aurorion.essentials.client.PhoneUi;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * O "+ Adicionar" da lista de conversas do Mensagens abre um menu com "Nova conversa" (a tela de
 * sempre do telefone) e "Novo grupo". Nao desenha nada: o botao e o do proprio telefone, e o menu e o
 * mesmo do "+" do Gram ({@code PhoneChoiceSheet}).
 *
 * <p>Sem os grupos no servidor (essentials antigo do outro lado), o clique segue para o original.
 * A area e a mesma que o telefone testa: x+W-55, y+30, 45 x 32.
 */
@Pseudo
@Mixin(targets = "com.mattupolis.phone.client.gui.PhoneMessagesScreen", remap = false)
public abstract class PhoneGroupListMixin {
    @Inject(method = "mouseClicked(DDI)Z", at = @At("HEAD"), cancellable = true, require = 0)
    private void aurorion_essentials$newMenu(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        if (button != 0) return;
        Screen self = (Screen) (Object) this;
        int x = PhoneUi.phoneLeft(self) + PhoneUi.PHONE_WIDTH - 55;
        int y = PhoneUi.phoneTop(self) + 30;
        if (mouseX < x || mouseX > x + 45 || mouseY < y || mouseY > y + 32) return;
        if (PhoneGroupsClient.openNewMenu(self)) cir.setReturnValue(true);
    }
}
