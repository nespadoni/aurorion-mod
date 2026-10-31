package com.aurorion.menu.client;

import com.aurorion.core.client.menu.CharacterMenu;
import com.aurorion.core.client.menu.MenuUi;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A janela ○/× sozinha, sem o menu: um cartão no meio da visão, no mundo, para responder apontando (o
 * convite de duelo, por exemplo). Nasce para onde a pessoa olha e segue a cabeça só quando ela vira muito.
 * Clique esquerdo com a mira no ○ aceita, no × recusa; o prazo correndo recusa sozinho.
 *
 * <p>Com o menu aberto, a janela vai dentro dele ({@code MenuView.prompt}), não aqui.
 */
final class MenuPrompt {
    private static final int W = 300, H = 150;
    private static final float WIDTH = 1.15f, DIST = 1.5f, FOLLOW = 30f;
    private static final float R = 13;
    private static final WorldCanvas CANVAS = new WorldCanvas(W, H);
    private static final float[] HIT = new float[2];

    @Nullable private static CharacterMenu.Modal current;
    private static long shownAt, until;
    private static boolean anchorPending;
    private static float anchorYaw, anchorPitch;
    private static float aimX = Float.NaN, aimY = Float.NaN;

    private MenuPrompt() {
    }

    static void show(CharacterMenu.Modal modal) {
        show(modal, modal.seconds() > 0 ? Util.getMillis() + modal.seconds() * 1000L : 0);
    }

    /** {@code deadline}: hora absoluta do fim do prazo (0 = sem prazo), para a janela não ganhar tempo ao mudar de lugar. */
    static void show(CharacterMenu.Modal modal, long deadline) {
        current = modal;
        shownAt = Util.getMillis();
        until = deadline;
        anchorPending = true;
        CANVAS.reset();
        MenuView.play(SoundEvents.AMETHYST_BLOCK_RESONATE, 1.5f);
    }

    static long until() {
        return until;
    }

    static boolean active() {
        return current != null;
    }

    static boolean aiming() {
        return current != null && !Float.isNaN(aimX);
    }

    static void clear() {
        current = null;
        aimX = aimY = Float.NaN;
        CANVAS.release();
    }

    /** Passa a janela para dentro do menu que acabou de abrir. */
    @Nullable
    static CharacterMenu.Modal take() {
        CharacterMenu.Modal m = current;
        clear();
        return m;
    }

    private static void answer(boolean yes) {
        CharacterMenu.Modal m = current;
        clear();
        if (m == null) return;
        MenuView.play(SoundEvents.UI_BUTTON_CLICK.value(), yes ? 1.8f : 1.2f);
        if (yes) m.yes().run();
        else if (m.no() != null) m.no().run();
    }

    /** Clique com a mira no cartão: ○ ou ×; fora dos dois não faz nada. */
    static void click() {
        float bx = W / 2f, by = H - 24;
        if (Math.hypot(aimX - (bx - 30), aimY - by) <= R + 2) answer(true);
        else if (Math.hypot(aimX - (bx + 30), aimY - by) <= R + 2) answer(false);
    }

    static void paint(GuiGraphics g) {
        CharacterMenu.Modal m = current;
        if (m == null) return;
        long now = Util.getMillis();
        if (until > 0 && now >= until) {
            answer(false);
            return;
        }
        CANVAS.paint(g, gg -> card(gg, m, now));
    }

    private static void card(GuiGraphics g, CharacterMenu.Modal m, long now) {
        float appear = MenuUi.easeOutBack(Mth.clamp((now - shownAt) / 260f, 0, 1));
        var pose = g.pose();
        pose.pushPose();
        float s = 0.8f + 0.2f * appear;
        pose.translate(W / 2f * (1 - s), H / 2f * (1 - s), 0);
        pose.scale(s, s, 1);
        float x = 8, y = 8, w = W - 16, h = H - 16;
        MenuUi.glow(g, W / 2f, H / 2f, W * .6f, MenuUi.argb(MenuUi.VIOLET_DEEP, .4f));
        MenuUi.panel(g, x, y, w, h, 9, MenuUi.argb(MenuUi.IVORY, .97f));
        MenuUi.panel(g, x, y, w, 22, 9, MenuUi.argb(MenuUi.VIOLET, .96f));
        MenuUi.iconAt(g, "duel", x + 14, y + 11, 12, MenuUi.argb(MenuUi.GOLD_HI, 1));
        MenuUi.drawCentered(g, MenuUi.text(m.title()), W / 2f, y + 7, MenuUi.GOLD_HI, 1);

        var font = MenuUi.font();
        List<FormattedCharSequence> lines = font.split(FormattedText.of(m.body(), MenuUi.text("").getStyle()), (int) w - 28);
        float ty = y + 30;
        for (FormattedCharSequence line : lines.subList(0, Math.min(4, lines.size()))) {
            g.drawString(font, line, (int) (W / 2f - font.width(line) / 2f), (int) ty, 0xFF000000 | m.bodyColor(), false);
            ty += 10;
        }

        // O prazo: uma barra que encolhe e os segundos.
        if (until > 0) {
            float left = Mth.clamp((until - now) / (float) (until - shownAt), 0, 1);
            MenuUi.resource(g, x + 20, y + h - 46, w - 40, 3, left, left > .3f ? MenuUi.GOLD : 0xB3261E, 1);
            MenuUi.drawCentered(g, MenuUi.text(Math.max(0, (until - now + 999) / 1000) + " s"), W / 2f, y + h - 40, MenuUi.FAINT, 1);
        }
        float bx = W / 2f, by = H - 24;
        button(g, bx - 30, by, "yes", MenuUi.GOLD);
        button(g, bx + 30, by, "no", MenuUi.IVORY_HI);
        pose.popPose();

        if (!Float.isNaN(aimX)) {
            MenuUi.glow(g, aimX, aimY, 7, MenuUi.argb(MenuUi.GOLD_HI, .55f));
            MenuUi.circle(g, aimX, aimY, 1.6f, MenuUi.argb(MenuUi.IVORY_HI, .95f));
        }
    }

    private static void button(GuiGraphics g, float cx, float cy, String icon, int color) {
        boolean hover = !Float.isNaN(aimX) && Math.hypot(aimX - cx, aimY - cy) <= R + 2;
        if (hover) MenuUi.glow(g, cx, cy, R * 2.2f, MenuUi.argb(icon.equals("yes") ? MenuUi.GOLD_HI : MenuUi.VIOLET_HI, .65f));
        MenuUi.circle(g, cx, cy, R, MenuUi.argb(color, 1));
        MenuUi.ring(g, cx, cy, R, MenuUi.argb(MenuUi.VIOLET, hover ? .8f : .35f));
        MenuUi.iconAt(g, icon, cx, cy, R * 1.1f, MenuUi.argb(MenuUi.VIOLET, 1));
    }

    /** No mundo: no meio da visão de quando chegou; vira junto só quando a cabeça vai além de {@value #FOLLOW}°. */
    static void render(RenderLevelStageEvent event, LocalPlayer player, float partial) {
        if (current == null || Minecraft.getInstance().options.hideGui) {
            aimX = aimY = Float.NaN;
            return;
        }
        float headYaw = player.getViewYRot(partial);
        if (anchorPending) {
            anchorYaw = headYaw;
            anchorPitch = Mth.clamp(player.getViewXRot(partial), -35, 35);
            anchorPending = false;
        }
        float delta = Mth.wrapDegrees(headYaw - anchorYaw);
        if (Math.abs(delta) > FOLLOW) anchorYaw += delta - Math.signum(delta) * FOLLOW;
        CANVAS.place(player.getEyePosition(partial), event.getCamera().getPosition(), anchorYaw, anchorPitch, DIST, WIDTH);
        if (CANVAS.hit(event.getCamera().getLookVector(), HIT)) {
            aimX = HIT[0];
            aimY = HIT[1];
        } else {
            aimX = aimY = Float.NaN;
        }
        CANVAS.draw(event.getModelViewMatrix(), event.getProjectionMatrix());
    }
}
