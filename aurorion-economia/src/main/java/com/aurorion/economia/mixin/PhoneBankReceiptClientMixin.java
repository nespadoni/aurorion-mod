package com.aurorion.economia.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.ArrayList;
import java.util.List;

/** Permite abrir a descricao completa de um recibo sem aumentar o cartao principal. */
@Pseudo
@Mixin(targets = "com.mattupolis.phone.client.gui.PhoneBankTransactionDetailScreen", remap = false)
public abstract class PhoneBankReceiptClientMixin {
    private String aurorion$note = "";
    private List<String> aurorion$noteLines = List.of();
    private boolean aurorion$noteOpen;

    @Redirect(method = "drawDetail", at = @At(value = "INVOKE", target =
            "Lcom/mattupolis/phone/client/gui/PhoneBankTransactionDetailScreen;drawRow"
                    + "(Lnet/minecraft/client/gui/GuiGraphics;IIILjava/lang/String;Ljava/lang/String;I)V"))
    private void aurorion$captureNote(@Coerce Object screen, GuiGraphics guiGraphics, int x, int y, int w,
                                      String label, String value, int valueColor) {
        if (isNoteLabel(label)) this.aurorion$note = value == null ? "" : value;
        this.aurorion$drawRow(guiGraphics, x, y, w, label, value, valueColor);
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void aurorion$openNote(double mouseX, double mouseY, int button,
                                   CallbackInfoReturnable<Boolean> cir) {
        if (this.aurorion$noteOpen) {
            this.aurorion$noteOpen = false;
            cir.setReturnValue(true);
            return;
        }

        Screen screen = (Screen)(Object)this;
        int phoneX = (screen.width - 170) / 2;
        int phoneY = (screen.height - 285) / 2;
        if (isInside(mouseX, mouseY, phoneX + 12, phoneY + 190, 146, 22)
                && !this.aurorion$note.isBlank() && !"-".equals(this.aurorion$note)) {
            this.aurorion$noteLines = wrap(aurorion$font(), this.aurorion$note, 130);
            this.aurorion$noteOpen = true;
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void aurorion$renderNote(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick,
                                     CallbackInfo ci) {
        if (!this.aurorion$noteOpen) return;

        Screen screen = (Screen)(Object)this;
        int phoneX = (screen.width - 170) / 2;
        int phoneY = (screen.height - 285) / 2;
        int x = phoneX + 10;
        int y = phoneY + 70;
        int w = 150;
        int lines = Math.min(this.aurorion$noteLines.size(), 4);
        int closeY = y + 56 + lines * 11;
        int h = closeY - y + 27;

        guiGraphics.fill(phoneX, phoneY, phoneX + 170, phoneY + 285, 0x99000000);
        this.aurorion$drawRoundRect(guiGraphics, x + 1, y + 2, w, h, 8, 0x22000000);
        this.aurorion$drawRoundRect(guiGraphics, x, y, w, h, 8, 0xFFFFFFFF);
        Font font = aurorion$font();
        guiGraphics.drawString(font, "Descricao completa", x + 10, y + 12, 0xFF101820, false);
        guiGraphics.fill(x + 10, y + 30, x + w - 10, y + 31, 0xFFD9D9D9);
        for (int i = 0; i < lines; i++) {
            guiGraphics.drawString(font, this.aurorion$noteLines.get(i), x + 10, y + 41 + i * 11,
                    0xFF101820, false);
        }
        this.aurorion$drawRoundRect(guiGraphics, x + 10, closeY, w - 20, 18, 6, 0xFFE5E5E5);
        guiGraphics.drawString(font, "Fechar", x + 61, closeY + 6, 0xFF101820, false);
    }

    private static Font aurorion$font() {
        return Minecraft.getInstance().font;
    }

    private static boolean isNoteLabel(String label) {
        return "Note".equalsIgnoreCase(label) || "Aciklama".equalsIgnoreCase(label)
                || "Descricao".equalsIgnoreCase(label) || "Descrição".equalsIgnoreCase(label);
    }

    private static List<String> wrap(Font font, String text, int maxWidth) {
        List<String> lines = new ArrayList<>();
        String remaining = text == null ? "" : text.trim();
        while (!remaining.isEmpty() && lines.size() < 4) {
            if (font.width(remaining) <= maxWidth) {
                lines.add(remaining);
                break;
            }
            String candidate = font.plainSubstrByWidth(remaining, maxWidth);
            int breakAt = candidate.lastIndexOf(' ');
            if (breakAt <= 0) breakAt = candidate.length();
            lines.add(remaining.substring(0, breakAt).trim());
            remaining = remaining.substring(breakAt).trim();
        }
        return List.copyOf(lines);
    }

    private static boolean isInside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }

    @Invoker("drawRow")
    abstract void aurorion$drawRow(GuiGraphics guiGraphics, int x, int y, int w, String label, String value,
                                   int valueColor);

    @Invoker("drawRoundRect")
    abstract void aurorion$drawRoundRect(GuiGraphics guiGraphics, int x, int y, int width, int height,
                                         int radius, int color);
}
