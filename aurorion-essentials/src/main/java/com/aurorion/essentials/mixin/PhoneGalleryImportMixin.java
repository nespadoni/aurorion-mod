package com.aurorion.essentials.mixin;

import com.aurorion.essentials.client.PhoneGalleryImport;
import com.aurorion.essentials.client.PhoneIcons;
import com.aurorion.essentials.client.PhoneUi;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.nio.file.Path;
import java.util.List;

/**
 * Botao "importar do PC" no topo da galeria (a direita do titulo, onde o telefone nao desenha nada) e
 * suporte a arrastar imagens do Windows para a janela do jogo com a galeria aberta. A galeria relista
 * a pasta a cada quadro, entao a foto importada aparece sozinha.
 */
@Pseudo
@Mixin(targets = "com.mattupolis.phone.client.gui.PhoneGalleryScreen", remap = false)
public abstract class PhoneGalleryImportMixin {
    @Unique private static final int AURORION$SIZE = 22;

    @Inject(method = "drawTopBar", at = @At("TAIL"), require = 0)
    private void aurorion_essentials$drawImport(GuiGraphics graphics, int x, int y, int width,
                                                int mouseX, int mouseY, CallbackInfo ci) {
        int left = x + width - 10 - AURORION$SIZE;
        int top = y + 22 + 14;
        PhoneIcons.button(graphics, PhoneIcons.IMPORT, left, top, AURORION$SIZE, AURORION$SIZE,
                PhoneGalleryImport.busy() || PhoneIcons.inside(mouseX, mouseY, left, top, AURORION$SIZE, AURORION$SIZE));
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true, require = 0)
    private void aurorion_essentials$clickImport(double mouseX, double mouseY, int button,
                                                 CallbackInfoReturnable<Boolean> cir) {
        Screen self = (Screen) (Object) this;
        int x = PhoneUi.phoneLeft(self);
        int y = PhoneUi.phoneTop(self);
        int left = x + PhoneUi.PHONE_WIDTH - 10 - AURORION$SIZE;
        int top = y + 22 + 14;
        if (!PhoneIcons.inside(mouseX, mouseY, left, top, AURORION$SIZE, AURORION$SIZE)) return;
        PhoneUi.playClick();
        PhoneGalleryImport.chooseFromPc();
        cir.setReturnValue(true);
    }

    /** Sobrescreve {@code Screen#onFilesDrop}: arquivos soltos na janela entram na galeria. */
    public void onFilesDrop(List<Path> paths) {
        PhoneGalleryImport.importDropped(paths);
    }
}
