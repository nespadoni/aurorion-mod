package com.aurorion.ethereal.client;

import com.aurorion.ethereal.ceremony.BindingRite;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/** Revelacao pessoal sem Screen modal; a pessoa continua vendo o palco e a plateia. */
public final class RiteOverlay {
    private static final Component INVOCATION = Component.translatable("aurorion_ethereal.rite.invocation");
    private static final Component WELCOME = Component.translatable("aurorion_ethereal.rite.welcome");

    private RiteOverlay() {}

    public static void render(GuiGraphics graphics) {
        RiteClient.Rite rite = RiteClient.ofSelf();
        if (rite == null) return;
        int width = graphics.guiWidth(), height = graphics.guiHeight();
        float life = rite.tick(), since = rite.sinceReveal();
        float fade = rite.fade(0);
        int center = Math.round(height * .30F);
        var font = Minecraft.getInstance().font;

        if (since < 0) {
            float appear = Mth.clamp(life / BindingRite.INTRO_TICKS, 0, 1);
            // Fade escuro suave na entrada; a imagem volta antes da revelacao.
            float dim = appear * Mth.clamp(-since / 30, 0, 1);
            graphics.fill(0, 0, width, height, alpha(0x070B18, .18F * dim));
            if (life >= BindingRite.INTRO_TICKS) {
                graphics.drawCenteredString(font, INVOCATION, width / 2, center, alpha(0xE4EAFE, dim));
            }
            return;
        }

        if (since < 10) {
            float flash = 1 - since / 10;
            graphics.fill(0, 0, width, height, alpha(rite.accent, flash * flash * .35F));
        }
        float nameAlpha = Math.min(Mth.clamp((since - 6) / 16, 0, 1), fade);
        if (nameAlpha <= .02F) return;
        graphics.fill(0, 0, width, height, alpha(rite.color, nameAlpha * .06F));
        graphics.drawCenteredString(font, WELCOME, width / 2, center - 19, alpha(rite.accent, nameAlpha));
        float scale = Math.min(3.1F, (width - 32F) / Math.max(1, rite.titleWidth));
        graphics.pose().pushPose();
        graphics.pose().translate(width / 2F, center, 0);
        graphics.pose().scale(scale, scale, 1);
        graphics.drawCenteredString(font, rite.houseName(), 0, 0, alpha(rite.color, nameAlpha));
        graphics.pose().popPose();
        float mottoAlpha = Math.min(Mth.clamp((since - 24) / 16, 0, 1), fade);
        if (mottoAlpha > .02F) {
            // Lemas de datapack podem ser longos: uma escala limitada evita cortar em GUI grande.
            float mottoScale = Math.min(1F, (width - 28F) / Math.max(1, rite.mottoWidth));
            graphics.pose().pushPose();
            graphics.pose().translate(width / 2F, center + 38, 0);
            graphics.pose().scale(mottoScale, mottoScale, 1);
            graphics.drawCenteredString(font, rite.motto(), 0, 0, alpha(0xFFF5E3, mottoAlpha));
            graphics.pose().popPose();
        }
    }

    private static int alpha(int rgb, float value) {
        return Mth.clamp(Math.round(value * 255), 4, 255) << 24 | rgb & 0xFFFFFF;
    }
}
