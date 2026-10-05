package com.aurorion.essentials.mixin;

import com.aurorion.essentials.client.PhoneIcons;
import com.aurorion.essentials.client.PhonePhotoPicker;
import com.aurorion.essentials.client.PhoneUi;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Barra de anexos da conversa do Mensagens. O telefone desenha dois botoes de 20px com o texto
 * "Phot"/"Loc" cortado; no mesmo espaco (de x+4 ate x+54, antes do campo de texto em x+56) entram tres
 * botoes de icone: clipe (foto da galeria), camera e localizacao.
 *
 * <p>Os botoes originais deixam de aparecer porque, so dentro de {@code drawInputBackground}, a cor
 * deles vira transparente e o rotulo vira vazio — esses metodos so sao chamados ali para esses dois
 * botoes. O clique nos tres botoes e tratado antes do original, que nunca ve a faixa.
 */
@Pseudo
@Mixin(targets = "com.mattupolis.phone.client.gui.PhoneMessageChatScreen", remap = false)
public abstract class PhoneMessageAttachMixin {
    @Unique private static final int AURORION$BUTTON = 15;
    @Unique private static final int AURORION$GAP = 2;

    @Redirect(method = "drawInputBackground", at = @At(value = "INVOKE",
            target = "Lcom/mattupolis/phone/client/gui/PhoneTheme;getAttachmentButtonColor()I"), require = 0)
    private int aurorion_essentials$hideOldButton() {
        return 0;
    }

    @Redirect(method = "drawInputBackground", at = @At(value = "INVOKE",
            target = "Lcom/mattupolis/phone/client/gui/PhoneTheme;getAttachmentButtonHoverColor()I"), require = 0)
    private int aurorion_essentials$hideOldButtonHover() {
        return 0;
    }

    @Redirect(method = "drawInputBackground", at = @At(value = "INVOKE",
            target = "Lcom/mattupolis/phone/client/gui/PhoneLanguageStore;t(Ljava/lang/String;)Ljava/lang/String;"),
            require = 0)
    private String aurorion_essentials$hideOldLabel(String key) {
        return "";
    }

    @Inject(method = "drawInputBackground", at = @At("TAIL"), require = 0)
    private void aurorion_essentials$drawIcons(GuiGraphics graphics, int x, int y, int width, int height,
                                               int mouseX, int mouseY, CallbackInfo ci) {
        int top = y + height - 50 + 4;
        String[][] icons = {PhoneIcons.CLIP, PhoneIcons.CAMERA, PhoneIcons.LOCATION};
        for (int i = 0; i < icons.length; i++) {
            int left = aurorion$buttonLeft(x, i);
            PhoneIcons.button(graphics, icons[i], left, top, AURORION$BUTTON, 20,
                    PhoneIcons.inside(mouseX, mouseY, left, top, AURORION$BUTTON, 20));
        }
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true, require = 0)
    private void aurorion_essentials$clickIcons(double mouseX, double mouseY, int button,
                                                CallbackInfoReturnable<Boolean> cir) {
        Screen self = (Screen) (Object) this;
        int x = PhoneUi.phoneLeft(self);
        int y = PhoneUi.phoneTop(self);
        int top = y + PhoneUi.PHONE_HEIGHT - 50 + 4;
        if (!PhoneIcons.inside(mouseX, mouseY, x + 4, top, 52, 20)) return;
        cir.setReturnValue(true);
        for (int i = 0; i < 3; i++) {
            if (!PhoneIcons.inside(mouseX, mouseY, aurorion$buttonLeft(x, i), top, AURORION$BUTTON, 20)) continue;
            switch (i) {
                case 0 -> aurorion$openGallery(self);
                case 1 -> PhoneUi.callPrivate(self, "openCamera");
                default -> PhoneUi.callPrivate(self, "sendLocation");
            }
            return;
        }
    }

    @Unique
    private static int aurorion$buttonLeft(int phoneX, int index) {
        return phoneX + 4 + index * (AURORION$BUTTON + AURORION$GAP);
    }

    @Unique
    private static void aurorion$openGallery(Screen chat) {
        if (PhoneUi.airplaneMode()) {
            PhoneUi.playError();
            PhoneUi.toast(PhoneUi.text("airplane_no_photo", "Modo aviao ativo."));
            return;
        }
        String threadId = PhoneUi.stringField(chat, "threadId");
        if (threadId == null) return;
        PhoneUi.playClick();
        PhonePhotoPicker.open(new PhonePhotoPicker.Messages(threadId));
    }
}
