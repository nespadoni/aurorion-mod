package com.aurorion.menu.client;

import com.aurorion.core.client.menu.CharacterMenu;
import com.aurorion.core.client.menu.MenuUi;
import net.minecraft.Util;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementType;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Avisos na frente da pessoa: um cartão branco no estilo do "Result" (título, linhas finas, rótulo e valor)
 * com o personagem dela ao lado, flutuando no mundo um pouco acima da linha dos olhos. Segue a cabeça com
 * atraso (desliza atrás do olhar, não fica grudado na tela), entra crescendo e some esmaecendo.
 *
 * <p>Vêm por {@code CharacterMenu.notice} (a ficha manda os avisos dela) e das conquistas do vanilla, que
 * deixam de usar o aviso do canto ({@code ToastComponentMixin}). Um de cada vez; até {@value #QUEUE} na fila.
 */
public final class MenuNotices {
    private static final int W = 330, H = 200, QUEUE = 6;
    private static final long IN_MS = 260, OUT_MS = 420;
    private static final float WIDTH = 0.95f, DIST = 1.55f, ABOVE = 8f;
    private static final WorldCanvas CANVAS = new WorldCanvas(W, H);
    private static final Deque<CharacterMenu.Notice> QUEUED = new ArrayDeque<>();

    @Nullable private static CharacterMenu.Notice current;
    private static long shownAt;
    private static float yaw, pitch;
    private static long lastFrame;

    private MenuNotices() {
    }

    static boolean offer(CharacterMenu.Notice notice) {
        if (Minecraft.getInstance().player == null) return false;
        if (QUEUED.size() >= QUEUE) QUEUED.pollFirst();
        QUEUED.addLast(notice);
        return true;
    }

    /** A conquista do vanilla, no cartão. {@code false} sem o que mostrar (o aviso do canto segue). */
    public static boolean advancement(AdvancementHolder holder) {
        if (!com.aurorion.menu.config.MenuClientConfig.ADVANCEMENTS.get()) return false;
        DisplayInfo display = holder.value().display().orElse(null);
        if (display == null) return false;
        String kicker = display.getType() == AdvancementType.CHALLENGE ? "DESAFIO CONCLUÍDO"
                : display.getType() == AdvancementType.GOAL ? "OBJETIVO ALCANÇADO" : "CONQUISTA";
        List<String[]> rows = new ArrayList<>();
        String about = display.getDescription().getString();
        if (!about.isBlank()) rows.add(new String[]{about, ""});
        return offer(new CharacterMenu.Notice(kicker, display.getTitle().getString(), rows, display.getIcon(), 6));
    }

    static void clear() {
        QUEUED.clear();
        current = null;
        CANVAS.release();
    }

    private static float progress(long now) {
        if (current == null) return 0;
        long age = now - shownAt, total = Math.max(2, current.seconds()) * 1000L;
        if (age < IN_MS) return MenuUi.easeOutBack(age / (float) IN_MS);
        if (age > total - OUT_MS) return Math.max(0, (total - age) / (float) OUT_MS);
        return 1;
    }

    /** Na camada do HUD: troca o aviso quando o tempo acaba e pinta o atual na tela dele. */
    static void paint(GuiGraphics g) {
        long now = Util.getMillis();
        if (current != null && now - shownAt > Math.max(2, current.seconds()) * 1000L) {
            current = null;
            if (QUEUED.isEmpty()) CANVAS.release();
        }
        if (current == null && !QUEUED.isEmpty()) {
            current = QUEUED.pollFirst();
            shownAt = now;
            CANVAS.reset();
            boolean challenge = current.kicker().startsWith("DESAFIO");
            MenuView.play(challenge ? SoundEvents.UI_TOAST_CHALLENGE_COMPLETE : SoundEvents.AMETHYST_BLOCK_CHIME, challenge ? 1f : 1.4f);
        }
        CharacterMenu.Notice notice = current;
        if (notice == null) return;
        float a = progress(now);
        CANVAS.paint(g, gg -> card(gg, notice, a));
    }

    private static void card(GuiGraphics g, CharacterMenu.Notice n, float appear) {
        float alpha = Mth.clamp(appear, 0, 1);
        var pose = g.pose();
        pose.pushPose();
        float s = 0.85f + 0.15f * appear;
        pose.translate(W / 2f * (1 - s), H / 2f * (1 - s), 0);
        pose.scale(s, s, 1);
        // A altura acompanha as linhas (o resultado do duelo tem seis); o resto da tela fica transparente.
        float x = 6, y = 6, w = W - 12, h = Math.min(H - 12, Math.max(116, 60 + n.rows().size() * 17));
        MenuUi.glow(g, W / 2f, y + h / 2, W * .55f, MenuUi.argb(MenuUi.VIOLET_DEEP, .35f * alpha));
        MenuUi.panel(g, x, y, w, h, 8, MenuUi.argb(MenuUi.IVORY_HI, .97f * alpha));

        // O personagem, num círculo violeta à esquerda.
        float px = x + 46, py = y + h / 2;
        MenuUi.circle(g, px, py, 36, MenuUi.argb(MenuUi.VIOLET, .92f * alpha));
        MenuUi.ring(g, px, py, 36, MenuUi.argb(MenuUi.GOLD, alpha));
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null && alpha > .3f) {
            MenuUi.clip(g, px - 34, py - 34, 68, 68);
            MenuUi.character(g, player, px - 30, py - 40, px + 30, py + 38, 30, px + 40, py - 20);
            MenuUi.endClip(g);
        }

        // O texto à direita: linha pequena dourada, título, linhas finas, rótulo e valor.
        float tx = x + 94, tw = x + w - 12 - tx;
        float ty = y + 12;
        MenuUi.draw(g, MenuUi.text(n.kicker()), tx, ty, MenuUi.GOLD, alpha);
        if (n.item() != null && !n.item().isEmpty()) g.renderItem(n.item(), (int) (x + w - 28), (int) (y + 8));
        ty += 11;
        var font = MenuUi.font();
        pose.pushPose();
        pose.translate(tx, ty, 0);
        pose.scale(1.25f, 1.25f, 1);
        MenuUi.draw(g, MenuUi.text(MenuUi.fit(n.title(), tw / 1.25f)), 0, 0, MenuUi.VIOLET, alpha);
        pose.popPose();
        ty += 15;
        MenuUi.line(g, tx, ty, tx + tw, ty, 1, MenuUi.argb(MenuUi.FAINT, .7f * alpha));
        ty += 5;
        for (String[] row : n.rows()) {
            if (ty > y + h - 12) break;
            if (row.length < 2 || row[1].isEmpty()) {
                List<FormattedCharSequence> lines = font.split(FormattedText.of(row[0], MenuUi.text("").getStyle()), (int) tw);
                for (FormattedCharSequence line : lines) {
                    if (ty > y + h - 12) break;
                    g.drawString(font, line, (int) tx, (int) ty, MenuUi.argb(MenuUi.INK, alpha), false);
                    ty += 10;
                }
            } else {
                MenuUi.draw(g, MenuUi.text(row[0]), tx, ty, MenuUi.INK, alpha);
                var value = MenuUi.text(row[1]);
                MenuUi.draw(g, value, tx + tw - font.width(value), ty, MenuUi.VIOLET, alpha);
                ty += 10;
            }
            ty += 2;
            MenuUi.line(g, tx, ty, tx + tw, ty, 1, MenuUi.argb(MenuUi.FAINT, .45f * alpha));
            ty += 4;
        }
        pose.popPose();
    }

    /** No mundo: acima da linha dos olhos, seguindo o olhar com atraso. */
    static void render(RenderLevelStageEvent event, LocalPlayer player, float partial) {
        long now = Util.getMillis();
        float dt = lastFrame == 0 ? 0 : Math.min(0.25f, (now - lastFrame) / 1000f);
        lastFrame = now;
        float headYaw = player.getViewYRot(partial), headPitch = player.getViewXRot(partial) - ABOVE;
        if (current == null) {
            yaw = headYaw;
            pitch = headPitch;
            return;
        }
        float k = 1 - (float) Math.exp(-dt * 5);
        yaw += Mth.wrapDegrees(headYaw - yaw) * k;
        pitch += (headPitch - pitch) * k;
        if (Minecraft.getInstance().options.hideGui) return;
        CANVAS.place(player.getEyePosition(partial), event.getCamera().getPosition(), yaw, pitch, DIST, WIDTH);
        CANVAS.draw(event.getModelViewMatrix(), event.getProjectionMatrix());
    }
}
