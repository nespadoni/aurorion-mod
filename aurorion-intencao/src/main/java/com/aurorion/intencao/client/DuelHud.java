package com.aurorion.intencao.client;

import com.aurorion.intencao.AurorionIntencao;
import com.aurorion.core.client.menu.MenuUi;
import com.aurorion.intencao.network.DuelPayloads;
import net.minecraft.Util;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;

import java.util.List;

/**
 * O HUD do duelo, no estilo de menu de MMO e liso ({@link MenuUi}): a sua barra grande no alto à esquerda
 * (nome, vida em paralelogramo, PE), os outros duelistas menores embaixo, a marca do limite combinado em
 * cada barra, de quem é a vez e a última linha do que aconteceu no topo, e o anel da reação no centro.
 * Só desenha com duelo aberto.
 */
final class DuelHud implements LayeredDraw.Layer {
    static final ResourceLocation ID = AurorionIntencao.id("duelo");

    @Override
    public void render(GuiGraphics g, DeltaTracker delta) {
        DuelPayloads.State state = ClientDuel.state;
        Minecraft minecraft = Minecraft.getInstance();
        int width = g.guiWidth(), height = g.guiHeight();
        long now = Util.getMillis();
        // "LUTEM!" continua alguns instantes depois de a contagem acabar, por cima do primeiro turno.
        if (state == null || minecraft.options.hideGui) {
            lastPhase = -1;
            return;
        }
        if (lastPhase == 0 && state.phase() != 0) {
            fightAt = now;
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.TRIDENT_THUNDER.value(), 1.1f, 0.6f));
        }
        lastPhase = state.phase();
        if (state.phase() == 0) {
            countdown(g, state, width, height, now);
            return;
        }

        float y = 8;
        if (state.self() >= 0 && state.self() < state.fighters().size()) {
            fighter(g, state, state.self(), 8, y, 190, true);
            y += 40;
        }
        for (int i = 0; i < state.fighters().size(); i++) {
            if (i == state.self()) continue;
            fighter(g, state, i, 8, y, 140, false);
            y += 28;
        }

        String header = state.paused() ? "Pausado pelo mestre" : switch (state.phase()) {
            case 1 -> state.actor() == state.self() ? "Sua vez" : "Vez de " + name(state, state.actor());
            default -> "Rodada " + (state.round() + 1);
        };
        float hw = Math.max(120, MenuUi.font().width(MenuUi.text(header)) + 24);
        MenuUi.panel(g, width / 2f - hw / 2, 6, hw, 15, 7, MenuUi.argb(MenuUi.VIOLET_DEEP, .78f));
        MenuUi.drawCentered(g, MenuUi.text(header), width / 2f, 10, state.phase() == 1 && state.actor() == state.self() ? MenuUi.GOLD_HI : MenuUi.IVORY, 1);
        if (!state.log().isEmpty()) MenuUi.drawCentered(g, MenuUi.text(state.log()), width / 2f, 25, MenuUi.IVORY, .95f);
        if (now - fightAt < FIGHT_MS) fight(g, width, height, now);

        DuelPayloads.React react = ClientDuel.react;
        if (react != null) ring(g, react, width / 2f, height / 2f);
    }

    // --- contagem ----------------------------------------------------------------------------------

    private static final long POP_MS = 380, FIGHT_MS = 1100;
    private static int lastPhase = -1, lastSeconds = -1;
    private static long secondAt, fightAt;

    /**
     * A abertura: faixas de cinema, os nomes com o VS dourado e o limite, e o número que salta a cada segundo
     * dentro de um anel que se fecha. Cada segundo toca uma nota que sobe; o último é mais grave.
     */
    private static void countdown(GuiGraphics g, DuelPayloads.State state, int width, int height, long now) {
        int seconds = Math.max(0, state.seconds());
        if (seconds != lastSeconds) {
            lastSeconds = seconds;
            secondAt = now;
            if (seconds > 0) {
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_PLING.value(),
                        seconds <= 1 ? 1.5f : 0.9f + 0.1f * Math.max(0, 5 - seconds), 0.7f));
            }
        }
        float since = (now - secondAt) / 1000f;
        float enter = MenuUi.easeOut(Math.min(1, since * 3 + (seconds < 5 ? 1 : 0)));

        // Faixas de cinema.
        float bar = 26 * enter;
        g.fill(0, 0, width, (int) bar, MenuUi.argb(MenuUi.VIOLET_DEEP, .88f));
        g.fill(0, height - (int) bar, width, height, MenuUi.argb(MenuUi.VIOLET_DEEP, .88f));
        MenuUi.line(g, 0, bar, width, bar, 1, MenuUi.argb(MenuUi.GOLD, .7f));
        MenuUi.line(g, 0, height - bar, width, height - bar, 1, MenuUi.argb(MenuUi.GOLD, .7f));

        // Os nomes, o VS e o limite.
        float cx = width / 2f, ny = height * .24f;
        List<DuelPayloads.Fighter> fighters = state.fighters();
        if (fighters.size() == 2) {
            var left = MenuUi.text(fighters.get(0).name()).withStyle(s -> s.withBold(true));
            var right = MenuUi.text(fighters.get(1).name()).withStyle(s -> s.withBold(true));
            float slide = (1 - enter) * 40;
            float lw = MenuUi.font().width(left) * 1.4f;
            scaled(g, left, cx - 34 - lw - slide, ny, 1.4f, MenuUi.IVORY, enter, false);
            scaled(g, right, cx + 34 + slide, ny, 1.4f, MenuUi.IVORY, enter, false);
            MenuUi.circle(g, cx - 28 - lw - slide - 6, ny + 5, 3.5f, MenuUi.argb(fighters.get(0).color(), enter));
            MenuUi.circle(g, cx + 28 + slide + 2, ny + 5, 3.5f, MenuUi.argb(fighters.get(1).color(), enter));
        } else {
            scaled(g, MenuUi.text(fighters.size() + " duelistas"), cx, ny, 1.4f, MenuUi.IVORY, enter, true);
        }
        MenuUi.glow(g, cx, ny + 6, 26, MenuUi.argb(MenuUi.GOLD_HI, .45f * enter));
        scaled(g, MenuUi.text("VS").withStyle(s -> s.withBold(true)), cx, ny - 3, 2.2f, MenuUi.GOLD_HI, enter, true);
        boolean death = state.limit() == 0;
        String limit = state.limit() < 0 ? "PRIMEIRO SANGUE" : death ? "ATÉ A MORTE" : "ATÉ " + state.limit() + "% DA VIDA";
        scaled(g, MenuUi.text(limit), cx, ny + 22, 1.1f, death ? 0xFF5040 : MenuUi.VIOLET_HI, enter, true);

        // O número.
        float ny2 = height * .52f;
        float pop = MenuUi.easeOut(Math.min(1, (now - secondAt) / (float) POP_MS));
        float ringR = Mth.lerp(Math.min(1, since), 48, 30);
        MenuUi.glow(g, cx, ny2, 70, MenuUi.argb(death ? 0xFF3020 : MenuUi.VIOLET_HI, .35f));
        MenuUi.ring(g, cx, ny2, ringR, MenuUi.argb(MenuUi.GOLD_HI, .9f * (1 - Math.min(1, since)) + .1f));
        MenuUi.ring(g, cx, ny2, 30, MenuUi.argb(MenuUi.IVORY, .35f));
        scaled(g, MenuUi.text("PREPAREM-SE"), cx, ny2 - 46, 0.9f, MenuUi.IVORY, .85f, true);
        if (seconds > 0) {
            float scale = 4.2f * (1 + 0.7f * (1 - pop));
            scaled(g, MenuUi.text(String.valueOf(seconds)).withStyle(s -> s.withBold(true)), cx, ny2 - 4.5f * scale, scale,
                    seconds <= 1 ? 0xFF6A50 : MenuUi.GOLD_HI, pop, true);
        }
    }

    /** "LUTEM!": cresce e esmaece, com um risco de luz atravessando a tela. */
    private static void fight(GuiGraphics g, int width, int height, long now) {
        float t = (now - fightAt) / (float) FIGHT_MS;
        float cx = width / 2f, cy = height * .42f;
        float alpha = 1 - t * t;
        float scale = 5.5f - 2f * MenuUi.easeOut(Math.min(1, t * 2.5f));
        MenuUi.glow(g, cx, cy, 120 * (0.6f + t), MenuUi.argb(MenuUi.GOLD_HI, .4f * alpha));
        float sweep = width * MenuUi.easeOut(Math.min(1, t * 1.6f));
        MenuUi.line(g, cx - sweep / 2, cy + 22, cx + sweep / 2, cy + 22, 1.5f, MenuUi.argb(MenuUi.GOLD_HI, alpha));
        scaled(g, MenuUi.text("LUTEM!").withStyle(s -> s.withBold(true)), cx, cy - 4.5f * scale, scale, MenuUi.GOLD_HI, alpha, true);
    }

    private static void scaled(GuiGraphics g, net.minecraft.network.chat.Component text, float x, float y, float scale, int rgb,
                               float alpha, boolean centered) {
        if (alpha <= 0.02f) return;
        var pose = g.pose();
        pose.pushPose();
        pose.translate(x, y, 0);
        pose.scale(scale, scale, 1);
        if (centered) MenuUi.drawCentered(g, text, 0, 0, rgb, alpha);
        else MenuUi.draw(g, text, 0, 0, rgb, alpha);
        pose.popPose();
    }

    private static void fighter(GuiGraphics g, DuelPayloads.State state, int index, float x, float y, float w, boolean big) {
        DuelPayloads.Fighter f = state.fighters().get(index);
        boolean down = (f.flags() & 2) != 0;
        boolean actor = index == state.actor();
        float barH = big ? 14 : 9;
        float panelH = big ? 36 : 25;
        MenuUi.panel(g, x - 4, y - 3, w + 40, panelH, 6, MenuUi.argb(MenuUi.VIOLET_DEEP, .62f));
        if (actor) MenuUi.outline(g, x - 4, y - 3, w + 40, panelH, 6, MenuUi.argb(MenuUi.GOLD_HI, .9f));
        MenuUi.circle(g, x + 3, y + 3, 3, MenuUi.argb(f.color(), 1));
        String flags = ((f.flags() & 1) != 0 ? "  · em guarda" : "") + ((f.flags() & 8) != 0 ? "  · exausto" : "");
        MenuUi.draw(g, MenuUi.text(f.name() + flags), x + 10, y - 1, down ? MenuUi.FAINT : MenuUi.IVORY, 1);
        float frac = f.hpMax() <= 0 ? 0 : f.hp() / (float) f.hpMax();
        int fill = down ? MenuUi.FAINT : frac > .5f ? MenuUi.HP_OK : frac > .25f ? MenuUi.HP_MID : MenuUi.HP_LOW;
        float by = y + 9;
        MenuUi.bar(g, x, by, w, barH, frac, MenuUi.argb(fill, .95f), MenuUi.argb(0x000000, .45f), MenuUi.argb(MenuUi.IVORY, .85f));
        if (f.stopHp() > 0 && f.hpMax() > 0) {
            float mark = x + w * f.stopHp() / f.hpMax();
            MenuUi.line(g, mark, by + 1, mark, by + barH - 1, 1.2f, MenuUi.argb(MenuUi.GOLD_HI, .95f));
        }
        MenuUi.draw(g, MenuUi.text(f.hp() + "/" + f.hpMax()), x + w + 4, by + (barH - 8) / 2, MenuUi.IVORY, 1);
        if (big && f.peMax() > 0) {
            float py = by + barH + 2;
            float pw = w * .6f;
            MenuUi.panel(g, x + 6, py, pw, 4, 2, MenuUi.argb(0x000000, .45f));
            MenuUi.panel(g, x + 6, py, pw * Mth.clamp(f.pe() / (float) f.peMax(), 0, 1), 4, 2, MenuUi.argb(MenuUi.PE, .95f));
            MenuUi.draw(g, MenuUi.text(f.pe() + " PE"), x + 10 + pw, py - 2, 0x9FC8FF, 1);
        }
    }

    private static void ring(GuiGraphics g, DuelPayloads.React react, float cx, float cy) {
        long now = Util.getMillis();
        long total = react.ticks() * 50L;
        float progress = Mth.clamp((now - ClientDuel.reactShownAt) / (float) total, 0, 1.2f);
        float radius = Mth.lerp(Math.min(progress, 1f), 64, 13);
        long off = Math.abs(now - ClientDuel.impactAt);
        int color = off <= react.perfectMs() ? MenuUi.GOLD_HI : off <= react.windowMs() ? MenuUi.HP_OK : MenuUi.IVORY;
        if (ClientDuel.reacted) color = ClientDuel.lastReaction == 1 ? MenuUi.VIOLET_HI : MenuUi.HP_OK;
        MenuUi.glow(g, cx, cy, 30, MenuUi.argb(MenuUi.VIOLET_HI, .35f));
        MenuUi.ring(g, cx, cy, 13, MenuUi.argb(MenuUi.IVORY, .55f));
        MenuUi.ring(g, cx, cy, radius, MenuUi.argb(color, .95f));
        MenuUi.drawCentered(g, MenuUi.text(react.attacker() + " — " + react.label()), cx, cy - 82, MenuUi.IVORY, 1);
        MenuUi.drawCentered(g, MenuUi.text("Esquerdo: aparar   ·   Direito: esquivar"), cx, cy + 76, MenuUi.IVORY, .9f);
    }

    private static String name(DuelPayloads.State state, int index) {
        return index >= 0 && index < state.fighters().size() ? state.fighters().get(index).name() : "?";
    }
}
