package com.aurorion.economia.mixin;

import com.aurorion.economia.money.Money;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.ArrayList;
import java.util.List;

/** Ajustes de legibilidade para a tela do banco opcional do telefone. */
@Pseudo
@Mixin(targets = "com.mattupolis.phone.client.gui.PhoneBankScreen", remap = false)
public abstract class PhoneBankClientMixin {
    @Shadow private EditBox targetInput;
    @Shadow private EditBox amountInput;
    @Shadow private EditBox noteInput;
    @Shadow private String toastMessage;
    @Shadow private boolean awaitingConfirmation;
    @Shadow private String pendingTarget;
    @Shadow private long pendingAmount;
    @Shadow private String pendingAmountLabel;

    private List<String> aurorion$confirmationNoteLines = List.of();

    @Redirect(method = "sendTransfer", at = @At(value = "INVOKE",
            target = "Ljava/lang/Long;parseLong(Ljava/lang/String;)J"))
    private long aurorion$parseObolos(String text) {
        // The phone packet carries the wallet's smallest unit. 12 becomes 120 fragments,
        // while 12.5 becomes 125, so the server never has to parse user text.
        return Money.parse(text);
    }

    @Inject(method = "sendTransfer", at = @At("TAIL"))
    private void aurorion$prepareConfirmation(CallbackInfo ci) {
        if (!this.awaitingConfirmation || this.pendingAmount <= 0L) return;

        this.pendingAmountLabel = Money.describe(this.pendingAmount);
        String note = this.noteInput == null ? "" : this.noteInput.getValue().trim();
        this.aurorion$confirmationNoteLines = wrap(aurorion$font(), note, 132);
    }

    @Inject(method = "updateInputPositions", at = @At("TAIL"))
    private void aurorion$fixInputLayout(int phoneX, int phoneY, CallbackInfo ci) {
        // Keep the vanilla widget a few pixels inside the painted field. The original widget
        // starts on the border, which is especially visible with the pixel font.
        position(this.targetInput, phoneX + 21, phoneY + 167, 124);
        position(this.amountInput, phoneX + 21, phoneY + 191, 124);
        position(this.noteInput, phoneX + 21, phoneY + 215, 124);
    }

    private void position(EditBox input, int x, int y, int width) {
        if (input == null) return;
        input.setX(x);
        input.setY(y);
        input.setWidth(width);
        if (this.awaitingConfirmation) input.visible = false;
    }

    @Inject(method = "drawConfirmation", at = @At("HEAD"), cancellable = true)
    private void aurorion$drawReadableConfirmation(GuiGraphics guiGraphics, int phoneX, int phoneY,
                                                    CallbackInfo ci) {
        if (!this.awaitingConfirmation) return;

        guiGraphics.fill(phoneX, phoneY, phoneX + 170, phoneY + 285, 0x99000000);
        int x = phoneX + 9;
        int y = phoneY + 64;
        int w = 152;
        List<String> lines = this.aurorion$confirmationNoteLines.isEmpty()
                ? List.of("Sem descricao") : this.aurorion$confirmationNoteLines;
        int lineCount = Math.min(lines.size(), 4);
        int buttonY = y + 88 + lineCount * 11;
        int h = buttonY - y + 27;

        this.aurorion$drawRoundRect(guiGraphics, x + 1, y + 2, w, h, 8, 0x22000000);
        this.aurorion$drawRoundRect(guiGraphics, x, y, w, h, 8, 0xFFFFFFFF);
        this.aurorion$drawRoundRect(guiGraphics, x + 10, y + 10, 17, 17, 6, 0xFFE5E5E5);
        Font font = aurorion$font();
        guiGraphics.drawString(font, ">", x + 16, y + 15, 0xFF007A5A, false);
        guiGraphics.drawString(font, "Confirmar transferencia", x + 33, y + 13, 0xFF101820, false);
        guiGraphics.fill(x + 10, y + 36, x + w - 10, y + 37, 0xFFD9D9D9);
        guiGraphics.drawString(font, clip(font, this.pendingTarget, w - 20), x + 10, y + 44,
                0xFF6A6F7A, false);
        guiGraphics.drawString(font, clip(font, this.pendingAmountLabel, w - 20), x + 10, y + 57,
                0xFF007A5A, false);
        guiGraphics.drawString(font, "Descricao", x + 10, y + 72, 0xFF6A6F7A, false);

        for (int i = 0; i < lineCount; i++) {
            guiGraphics.drawString(font, lines.get(i), x + 10, y + 86 + i * 11,
                    0xFF101820, false);
        }

        this.aurorion$drawRoundRect(guiGraphics, x + 10, buttonY, 58, 18, 6, 0xFFE5E5E5);
        this.aurorion$drawRoundRect(guiGraphics, x + w - 68, buttonY, 58, 18, 6, 0xFF008C87);
        guiGraphics.drawString(font, "Cancelar", x + 17, buttonY + 6, 0xFF101820, false);
        guiGraphics.drawString(font, "Enviar", x + w - 58, buttonY + 6, 0xFFFFFFFF, false);
        ci.cancel();
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void aurorion$handleConfirmationClick(double mouseX, double mouseY, int button,
                                                    CallbackInfoReturnable<Boolean> cir) {
        if (!this.awaitingConfirmation) return;

        Screen screen = (Screen)(Object)this;
        int phoneX = (screen.width - 170) / 2;
        int phoneY = (screen.height - 285) / 2;
        int x = phoneX + 9;
        int y = phoneY + 64;
        int lineCount = Math.min(Math.max(this.aurorion$confirmationNoteLines.size(), 1), 4);
        int buttonY = y + 88 + lineCount * 11;

        if (inside(mouseX, mouseY, x + 10, buttonY, 58, 18)) {
            this.awaitingConfirmation = false;
            cir.setReturnValue(true);
        } else if (inside(mouseX, mouseY, x + 84, buttonY, 58, 18)) {
            this.aurorion$confirmTransfer();
            cir.setReturnValue(true);
        } else {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "drawToast", at = @At("HEAD"), cancellable = true)
    private void aurorion$drawToastAfterWidgets(GuiGraphics guiGraphics, int phoneX, int phoneY,
                                                 CallbackInfo ci) {
        // The toast is redrawn after super.render so EditBox widgets cannot cover it.
        ci.cancel();
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void aurorion$renderToastOnTop(GuiGraphics guiGraphics, int mouseX, int mouseY,
                                           float partialTick, CallbackInfo ci) {
        if (this.awaitingConfirmation || this.toastMessage == null || this.toastMessage.isEmpty()) return;

        Screen screen = (Screen)(Object)this;
        int phoneX = (screen.width - 170) / 2;
        int phoneY = (screen.height - 285) / 2;
        int boxX = phoneX + 12;
        int boxY = phoneY + 285 - 76;
        this.aurorion$drawRoundRect(guiGraphics, boxX, boxY, 146, 26, 7, 0xDD20252B);
        Font font = aurorion$font();
        guiGraphics.drawString(font, clip(font, this.toastMessage, 136), boxX + 6, boxY + 9,
                0xFFFFFFFF, false);
    }

    private static Font aurorion$font() {
        return Minecraft.getInstance().font;
    }

    private static List<String> wrap(Font font, String text, int maxWidth) {
        if (text == null || text.isBlank()) return List.of();

        List<String> lines = new ArrayList<>();
        String remaining = text.trim();
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

    private static String clip(Font font, String text, int maxWidth) {
        if (text == null) return "";
        return font.width(text) <= maxWidth ? text : font.plainSubstrByWidth(text, maxWidth - 3) + "...";
    }

    private static boolean inside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }

    @Invoker("drawRoundRect")
    abstract void aurorion$drawRoundRect(GuiGraphics guiGraphics, int x, int y, int width, int height,
                                         int radius, int color);

    @Invoker("confirmTransfer")
    abstract void aurorion$confirmTransfer();
}
