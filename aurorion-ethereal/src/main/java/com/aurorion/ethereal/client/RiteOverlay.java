package com.aurorion.ethereal.client;

import com.aurorion.ethereal.ceremony.BindingRite;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * O texto do rito, para quem esta passando por ele.
 *
 * <h2>Sobreposicao, e nao tela</h2>
 *
 * <p>A revelacao antiga abria uma {@code Screen} por cima de tudo. O problema nao era a tela: era que
 * a cena boa acontece <b>atras</b> dela — a luz, as particulas, o simbolo girando sobre o proprio
 * corpo. Uma tela modal cobre exatamente aquilo que se quer que a pessoa veja, e ainda a tira do
 * mundo num momento em que outros jogadores estao olhando para ela.
 *
 * <p>Entao o nome e o lema sao desenhados por cima do HUD, sem roubar o controle e sem escurecer o
 * mundo. O unico momento em que a tela inteira e tomada e o estouro, e ele dura meio segundo.
 *
 * <p>So aparece para quem esta sendo vinculado. Quem assiste ve a luz, as particulas e o simbolo —
 * o nome da casa chega no anuncio do chat, no fim, como sempre chegou.
 */
public final class RiteOverlay {
    private static final int NAME_DELAY = 8;
    private static final int MOTTO_DELAY = 24;
    private static final int FADE_TICKS = 14;
    private static final int FLASH_TICKS = 12;

    private RiteOverlay() {
    }

    public static void render(GuiGraphics graphics) {
        RiteClient.Rite rite = RiteClient.ofSelf();
        if (rite == null) return;

        // Sem parcial de quadro de proposito: tudo aqui e transicao de opacidade ao longo de mais de
        // dez ticks, e a diferenca entre 20 e 60 passos numa transicao dessas nao existe para o olho.
        // Em troca, nao dependemos do formato do tempo que cada evento de HUD resolve entregar.
        float life = rite.tick();
        float since = rite.sinceReveal();
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        int color = rite.color() & 0xFFFFFF;

        if (since < 0.0F) {
            // Antes do estouro a casa e segredo: a tela so vai ficando mais clara, sem cor nenhuma.
            float build = Mth.clamp((life - BindingRite.RISE_TICKS) / BindingRite.GATHER_TICKS, 0.0F, 1.0F);
            graphics.fill(0, 0, width, height, alpha(0xFFFFFF, build * 0.12F));
            return;
        }

        if (since < FLASH_TICKS) {
            float fade = 1.0F - since / FLASH_TICKS;
            graphics.fill(0, 0, width, height, alpha(0xFFFFFF, fade * fade * 0.85F));
        }

        float leaving = leaving(since);
        float nameAlpha = Math.min(fadeIn(since - NAME_DELAY), leaving);
        float mottoAlpha = Math.min(fadeIn(since - MOTTO_DELAY), leaving);

        if (nameAlpha > 0.02F) {
            graphics.fill(0, 0, width, height, alpha(color, nameAlpha * 0.10F));
            title(graphics, rite.houseName(), width, Math.round(height * 0.30F), color, nameAlpha);
        }
        if (mottoAlpha > 0.02F) {
            graphics.drawCenteredString(Minecraft.getInstance().font, rite.motto(),
                    width / 2, Math.round(height * 0.30F) + 26, alpha(0xE8E8E8, mottoAlpha));
        }
    }

    /** O nome da casa em corpo grande. O {@code scale} da pilha e o unico jeito de crescer a fonte. */
    private static void title(GuiGraphics graphics, Component name, int width, int y, int color, float alpha) {
        float scale = 2.6F;
        graphics.pose().pushPose();
        graphics.pose().translate(width / 2.0F, y, 0.0F);
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.drawCenteredString(Minecraft.getInstance().font, name, 0, 0, alpha(color, alpha));
        graphics.pose().popPose();
    }

    private static float fadeIn(float since) {
        return Mth.clamp(since / FADE_TICKS, 0.0F, 1.0F);
    }

    /** Some junto com o simbolo, nos ultimos vinte ticks da coroacao. */
    private static float leaving(float since) {
        int start = BindingRite.BURST_TICKS + BindingRite.CROWN_TICKS - 20;
        return since < start ? 1.0F : Math.max(0.0F, 1.0F - (since - start) / 20.0F);
    }

    /**
     * Alfa zero e tratado como "nao desenhe" pela fonte, o que faria o texto piscar ao aparecer.
     * O piso de 4 mantem o primeiro quadro invisivel a olho nu e visivel para o desenhista.
     */
    private static int alpha(int rgb, float alpha) {
        int value = Mth.clamp(Math.round(alpha * 255.0F), 4, 255);
        return value << 24 | rgb & 0xFFFFFF;
    }
}
