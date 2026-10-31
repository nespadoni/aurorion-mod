package com.aurorion.menu.client;

import com.aurorion.core.client.menu.CharacterMenu;
import com.aurorion.core.client.menu.MenuUi;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * O menu do personagem: a coluna de círculos (as seções que os mods registram no {@code CharacterMenu}),
 * cada escolha abrindo uma lista de cartões ao lado, e um cartão grande à esquerda para as seções e itens
 * com conteúdo próprio (Perfil, Perícias...). Janelas de confirmação com ○ e ×.
 *
 * <p>Não é uma tela: é desenhado numa tela de {@value #WIDTH}×{@value #HEIGHT} unidades que o
 * {@link MenuSpace} pendura no mundo, na frente da pessoa. Quem aponta é a mira (o centro da visão), e o
 * clique chega por aqui com a posição na tela do menu. A câmera e o andar continuam livres.
 *
 * <p>Ao abrir, o menu se materializa: cresce de leve, um risco de luz desce, e os círculos caem um a um.
 */
final class MenuView {
    static final int WIDTH = 800, HEIGHT = 400;

    private record Hit(int level, int index, float x, float y, float w, float h) {
    }

    private static final float CIRCLE_R = 11, ROOT_GAP = 30, PILL_W = 140, PILL_H = 18, PILL_GAP = 21, COLUMN_GAP = 152;

    @Nullable private static MenuView current;

    private final List<CharacterMenu.Entry> roots = new ArrayList<>();
    private final List<CharacterMenu.Section> rootSections = new ArrayList<>();
    private final List<List<CharacterMenu.Entry>> levels = new ArrayList<>();
    private final List<Integer> selected = new ArrayList<>();
    private final List<Long> openedAt = new ArrayList<>();
    private final List<Hit> hits = new ArrayList<>();
    private final List<Hit> modalHits = new ArrayList<>();
    private final long openTime = Util.getMillis();
    private final Font font = MenuUi.font();
    @Nullable private CharacterMenu.Panel panel;
    private long panelSince;
    private float panelX, panelY, panelW, panelH;
    @Nullable private CharacterMenu.Modal modal;
    private long modalUntil;

    private MenuView(@Nullable String section) {
        rebuildRoots();
        levels.add(roots);
        selected.add(-1);
        openedAt.add(openTime);
        play(SoundEvents.AMETHYST_BLOCK_CHIME, 1.7f);
        if (section != null) openSection(section);
    }

    // --- estado global (um menu por cliente) -------------------------------------------------------

    @Nullable
    static MenuView current() {
        return current;
    }

    static boolean isOpen() {
        return current != null;
    }

    /** Abre (ou, já aberto, só vai para a seção). */
    static void open(@Nullable String section) {
        if (current == null) {
            current = new MenuView(section);
            MenuSpace.opened();
        } else if (section != null) {
            current.refresh();
            current.openSection(section);
        }
    }

    static void close(boolean sound) {
        MenuView view = current;
        if (view == null) return;
        current = null;
        if (view.panel != null) view.panel.closed();
        if (view.modal != null && (view.modalUntil == 0 || view.modalUntil > Util.getMillis())) {
            MenuPrompt.show(view.modal, view.modalUntil);
        }
        if (sound) play(SoundEvents.AMETHYST_BLOCK_STEP, 1.3f);
        MenuSpace.closed();
        MenuHost.closed();
    }

    private void openSection(String id) {
        for (int i = 0; i < rootSections.size(); i++) {
            if (rootSections.get(i) != null && rootSections.get(i).id().equals(id)) {
                if (selected.get(0) != i) select(0, i, false);
                return;
            }
        }
    }

    private void rebuildRoots() {
        roots.clear();
        rootSections.clear();
        for (CharacterMenu.Section section : CharacterMenu.sections()) {
            rootSections.add(section);
            roots.add(new CharacterMenu.Entry(section.title(), section.icon(), "", true, null, null, null));
        }
        rootSections.add(null);
        roots.add(CharacterMenu.Entry.action("Fechar", "no", "", true, () -> close(true)));
    }

    void prompt(CharacterMenu.Modal value) {
        prompt(value, value.seconds() > 0 ? Util.getMillis() + value.seconds() * 1000L : 0);
    }

    /** Com prazo absoluto: a janela que veio da frente da pessoa não ganha tempo novo. */
    void prompt(CharacterMenu.Modal value, long until) {
        modal = value;
        modalUntil = until;
        play(SoundEvents.AMETHYST_BLOCK_RESONATE, 1.5f);
    }

    void dismissModal() {
        modal = null;
    }

    private boolean dirty;

    /** Os dados mudaram: a coluna é refeita no próximo quadro, nunca no meio de uma escolha. */
    void markDirty() {
        dirty = true;
    }

    /** Os dados de alguma seção mudaram: reconstrói as listas abertas mantendo as escolhas. */
    void refresh() {
        int[] path = selected.stream().mapToInt(Integer::intValue).toArray();
        // A coluna de seções também muda (Ações aparece na sua vez e some depois): a escolha segue o id.
        CharacterMenu.Section was = path[0] >= 0 && path[0] < rootSections.size() ? rootSections.get(path[0]) : null;
        rebuildRoots();
        path[0] = was == null ? -1 : rootSections.indexOf(was);
        if (path[0] < 0 && was != null) {
            if (panel != null) panel.closed();
            panel = null;
        }
        while (levels.size() > 1) {
            levels.remove(levels.size() - 1);
            selected.remove(selected.size() - 1);
            openedAt.remove(openedAt.size() - 1);
        }
        selected.set(0, path[0]);
        for (int level = 0; level < path.length && path[level] >= 0; level++) {
            if (level >= levels.size() || path[level] >= levels.get(level).size()) break;
            selected.set(level, path[level]);
            List<CharacterMenu.Entry> children = childrenOf(level, path[level]);
            if (children == null || children.isEmpty()) break;
            levels.add(children);
            selected.add(-1);
            openedAt.add(0L);
        }
    }

    @Nullable
    private List<CharacterMenu.Entry> childrenOf(int level, int index) {
        if (level == 0) {
            CharacterMenu.Section section = rootSections.get(index);
            return section == null ? null : safe(section::entries);
        }
        CharacterMenu.Entry entry = levels.get(level).get(index);
        return entry.children() == null ? null : safe(entry.children());
    }

    private static List<CharacterMenu.Entry> safe(Supplier<List<CharacterMenu.Entry>> supplier) {
        try {
            List<CharacterMenu.Entry> list = supplier.get();
            return list == null ? List.of() : list;
        } catch (RuntimeException e) {
            return List.of(CharacterMenu.Entry.disabled("Indisponível agora", "no"));
        }
    }

    // --- navegação ---------------------------------------------------------------------------------

    private void select(int level, int index, boolean sound) {
        if (!levels.get(level).get(index).enabled()) return;
        while (levels.size() > level + 1) {
            levels.remove(levels.size() - 1);
            selected.remove(selected.size() - 1);
            openedAt.remove(openedAt.size() - 1);
        }
        CharacterMenu.Entry entry = levels.get(level).get(index);
        if (!entry.enabled()) return;
        selected.set(level, index);
        if (sound) play(SoundEvents.UI_BUTTON_CLICK.value(), 1.8f);
        if (entry.action() != null) {
            entry.action().run();
            return;
        }
        if (level == 0) {
            CharacterMenu.Section section = rootSections.get(index);
            if (section != null) {
                section.opened();
                showPanel(section.panel());
            }
        } else if (entry.panel() != null) {
            showPanel(entry.panel().get());
        }
        List<CharacterMenu.Entry> children = childrenOf(level, index);
        if (children != null && !children.isEmpty()) {
            levels.add(children);
            selected.add(-1);
            openedAt.add(Util.getMillis());
        }
    }

    private void showPanel(@Nullable CharacterMenu.Panel next) {
        if (panel != null && panel != next) panel.closed();
        if (next == null && panel != null && selected.get(0) >= 0) {
            // Item sem cartão próprio: mantém o cartão da seção.
            CharacterMenu.Section section = rootSections.get(selected.get(0));
            if (section != null && section.panel() == panel) return;
        }
        panel = next;
        panelSince = Util.getMillis();
    }

    private void confirmModal(boolean yes) {
        CharacterMenu.Modal m = modal;
        modal = null;
        if (m == null) return;
        if (yes) m.yes().run();
        else if (m.no() != null) m.no().run();
    }

    /** Volta um passo: fecha a janela, a última lista, o cartão; no fim, o menu. */
    void back() {
        if (modal != null) {
            confirmModal(false);
            return;
        }
        play(SoundEvents.UI_BUTTON_CLICK.value(), 1.4f);
        if (levels.size() > 1) {
            int last = levels.size() - 1;
            levels.remove(last);
            selected.remove(last);
            openedAt.remove(last);
            selected.set(last - 1, -1);
            if (last == 1) {
                if (panel != null) panel.closed();
                panel = null;
            }
            return;
        }
        close(true);
    }

    // --- entrada (posição já na tela do menu) ------------------------------------------------------

    boolean click(double x, double y, int button) {
        if (modal != null) {
            for (Hit hit : modalHits) {
                if (inside(hit, x, y)) {
                    confirmModal(hit.index() == 0);
                    return true;
                }
            }
            return true;
        }
        if (panel != null && x >= panelX && x <= panelX + panelW && y >= panelY && y <= panelY + panelH) {
            try {
                if (panel.mouseClicked(x, y, button)) return true;
            } catch (RuntimeException ignored) {
                // um cartão quebrado não derruba o menu
            }
            return true;
        }
        for (Hit hit : hits) {
            if (inside(hit, x, y)) {
                select(hit.level(), hit.index(), true);
                return true;
            }
        }
        return false;
    }

    boolean scroll(double x, double y, double delta) {
        return modal == null && panel != null && panel.mouseScrolled(x, y, delta);
    }

    /** Tecla com o menu aberto (sem tela): Enter confirma a janela; o cartão pode querer outras. */
    boolean key(int key, int scan, int modifiers) {
        if (modal != null) {
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                confirmModal(true);
                return true;
            }
            return false;
        }
        return panel != null && panel.keyPressed(key, scan, modifiers);
    }

    private static boolean inside(Hit hit, double x, double y) {
        return x >= hit.x() && x <= hit.x() + hit.w() && y >= hit.y() && y <= hit.y() + hit.h();
    }

    // --- desenho -----------------------------------------------------------------------------------

    /** {@code mouseX/Y}: onde a mira toca a tela do menu ({@code NaN} fora dela). */
    void render(GuiGraphics g, float mouseX, float mouseY) {
        if (dirty) {
            dirty = false;
            refresh();
        }
        int width = WIDTH, height = HEIGHT;
        long now = Util.getMillis();
        if (Float.isNaN(mouseX)) {
            mouseX = -1000;
            mouseY = -1000;
        }
        float appear = MenuUi.easeOut((now - openTime) / 260f);
        var pose = g.pose();
        pose.pushPose();
        float s = 0.94f + 0.06f * appear;
        pose.translate(width / 2f * (1 - s), height / 2f * (1 - s), 0);
        pose.scale(s, s, 1);
        hits.clear();

        float cx = width / 2f, cy = height / 2f;
        float rootX = cx + 30;
        float panelWanted = panel != null ? panel.width() : 0;
        float right = rootX + CIRCLE_R + 18 + (levels.size() - 1) * COLUMN_GAP;
        if (right > width - 8) rootX -= Math.min(right - (width - 8), rootX - CIRCLE_R - 8);
        if (panel != null && rootX - CIRCLE_R - 16 - panelWanted < 8) rootX = Math.min(width - 60, 8 + panelWanted + 16 + CIRCLE_R);
        float rootTop = cy - (roots.size() - 1) * ROOT_GAP / 2f;

        for (int i = 0; i < roots.size(); i++) {
            CharacterMenu.Entry node = roots.get(i);
            float p = (now - openTime - i * 55) / 280f;
            float drop = (1 - MenuUi.easeOutBack(p)) * -26;
            float alpha = MenuUi.easeOut(p);
            float y = rootTop + i * ROOT_GAP + drop;
            boolean sel = selected.get(0) == i;
            boolean hover = modal == null && Math.hypot(mouseX - rootX, mouseY - y) <= CIRCLE_R + 2;
            if (sel || hover) MenuUi.glow(g, rootX, y, CIRCLE_R * 2.1f, MenuUi.argb(sel ? MenuUi.GOLD_HI : MenuUi.VIOLET_HI, alpha * .55f));
            int fill = !node.enabled() ? MenuUi.FAINT : sel ? MenuUi.GOLD : hover ? MenuUi.IVORY_HI : MenuUi.IVORY;
            MenuUi.circle(g, rootX, y, CIRCLE_R, MenuUi.argb(fill, alpha * .95f));
            MenuUi.iconAt(g, node.icon(), rootX, y, CIRCLE_R * 1.15f, MenuUi.argb(sel ? MenuUi.VIOLET_DEEP : MenuUi.VIOLET, alpha));
            if (hover && !sel) MenuUi.draw(g, MenuUi.text(node.label()), rootX + CIRCLE_R + 6, y - 4, MenuUi.IVORY, alpha);
            hits.add(new Hit(0, i, rootX - CIRCLE_R, y - CIRCLE_R, CIRCLE_R * 2, CIRCLE_R * 2));
        }

        if (panel != null) {
            float pw = panel.width(), ph = Math.min(panel.height(), height - 16);
            float a = MenuUi.easeOut((now - panelSince) / 220f);
            panelW = pw;
            panelH = ph;
            panelX = rootX - CIRCLE_R - 16 - pw + (1 - a) * 12;
            panelY = Math.max(8, cy - ph / 2);
            MenuUi.panel(g, panelX, panelY, pw, ph, 9, MenuUi.argb(MenuUi.IVORY, a * .95f));
            try {
                panel.render(g, panelX, panelY, pw, ph, (int) mouseX, (int) mouseY, a);
            } catch (RuntimeException e) {
                MenuUi.drawCentered(g, MenuUi.text("Não deu para mostrar isto agora."), panelX + pw / 2, panelY + ph / 2, MenuUi.INK, a);
            }
        }

        float anchorX = rootX + CIRCLE_R + 4;
        float anchorY = selected.get(0) >= 0 ? rootTop + selected.get(0) * ROOT_GAP : cy;
        for (int level = 1; level < levels.size(); level++) {
            List<CharacterMenu.Entry> nodes = levels.get(level);
            // anchorX já anda uma coluna por nível: somar o nível de novo jogava a lista para fora do painel.
            float x = anchorX + 14;
            float top = anchorY - (nodes.size() - 1) * PILL_GAP / 2f - PILL_H / 2f;
            top = Math.max(8, Math.min(height - 8 - nodes.size() * PILL_GAP, top));
            long opened = openedAt.get(level);
            float t = opened == 0 ? 1 : (now - opened) / 200f;
            MenuUi.pointer(g, x - 11, anchorY, 9, MenuUi.argb(MenuUi.IVORY, MenuUi.easeOut(t) * .9f));
            MenuUi.line(g, x - 3, top + 2, x - 3, top + (nodes.size() - 1) * PILL_GAP + PILL_H - 2, 1, MenuUi.argb(MenuUi.VIOLET_HI, MenuUi.easeOut(t) * .6f));
            for (int i = 0; i < nodes.size(); i++) {
                CharacterMenu.Entry node = nodes.get(i);
                float p = opened == 0 ? 1 : (now - opened - i * 28) / 190f;
                float alpha = MenuUi.easeOut(p);
                float px = x + (1 - MenuUi.easeOut(p)) * -14;
                float py = top + i * PILL_GAP;
                boolean sel = selected.get(level) == i;
                boolean hover = modal == null && mouseX >= px && mouseX <= px + PILL_W && mouseY >= py && mouseY <= py + PILL_H;
                pill(g, node, px, py, sel, hover, alpha);
                hits.add(new Hit(level, i, px, py, PILL_W, PILL_H));
                if (sel) anchorY = py + PILL_H / 2f;
            }
            anchorX = x + COLUMN_GAP - 14;
        }

        // O risco de luz descendo na abertura.
        float sweep = (now - openTime) / 340f;
        if (sweep < 1) {
            float sy = height * MenuUi.easeOut(sweep);
            MenuUi.line(g, 0, sy, width, sy, 1.5f, MenuUi.argb(MenuUi.GOLD_HI, (1 - sweep) * .7f));
            MenuUi.glow(g, cx, sy, 60, MenuUi.argb(MenuUi.VIOLET_HI, (1 - sweep) * .35f));
        }
        pose.popPose();

        if (modal != null) modal(g, mouseX, mouseY, now);

        // Onde a mira toca: um ponto de luz, para saber o que o clique vai escolher.
        if (mouseX >= 0 && mouseX <= width && mouseY >= 0 && mouseY <= height) {
            MenuUi.glow(g, mouseX, mouseY, 7, MenuUi.argb(MenuUi.GOLD_HI, .55f));
            MenuUi.circle(g, mouseX, mouseY, 1.6f, MenuUi.argb(MenuUi.IVORY_HI, .95f));
        }
    }

    private void pill(GuiGraphics g, CharacterMenu.Entry node, float x, float y, boolean sel, boolean hover, float alpha) {
        float a = alpha * (node.enabled() ? 1 : .55f);
        int back = sel ? MenuUi.GOLD : hover ? MenuUi.IVORY_HI : MenuUi.IVORY;
        MenuUi.panel(g, x, y, PILL_W, PILL_H, 6, MenuUi.argb(back, a * .94f));
        if (sel || hover) MenuUi.outline(g, x, y, PILL_W, PILL_H, 6, MenuUi.argb(MenuUi.GOLD_HI, a));
        MenuUi.circle(g, x + 9.5f, y + PILL_H / 2f, 6.5f, MenuUi.argb(sel ? MenuUi.VIOLET_DEEP : MenuUi.VIOLET, a));
        MenuUi.iconAt(g, node.icon(), x + 9.5f, y + PILL_H / 2f, 8, MenuUi.argb(sel ? MenuUi.GOLD_HI : MenuUi.IVORY, a));
        float detailW = node.detail().isEmpty() ? 0 : font.width(MenuUi.text(node.detail())) + 6;
        MenuUi.draw(g, MenuUi.text(MenuUi.fit(node.label(), PILL_W - 26 - detailW)), x + 20, y + 5, sel ? MenuUi.VIOLET_DEEP : MenuUi.INK, a);
        if (!node.detail().isEmpty()) {
            var detail = MenuUi.text(node.detail());
            MenuUi.draw(g, detail, x + PILL_W - 6 - font.width(detail), y + 5, sel ? MenuUi.VIOLET : MenuUi.FAINT, a);
        }
    }

    private void modal(GuiGraphics g, float mouseX, float mouseY, long now) {
        CharacterMenu.Modal m = modal;
        if (m == null) return;
        if (modalUntil > 0 && now >= modalUntil) {
            confirmModal(false);
            return;
        }
        modalHits.clear();
        float w = 230, h = 104, x = WIDTH / 2f - w / 2, y = HEIGHT / 2f - h / 2;
        MenuUi.glow(g, WIDTH / 2f, HEIGHT / 2f, 150, MenuUi.argb(MenuUi.VIOLET_DEEP, .45f));
        MenuUi.panel(g, x, y, w, h, 9, MenuUi.argb(MenuUi.IVORY, .97f));
        MenuUi.panel(g, x, y, w, 20, 9, MenuUi.argb(MenuUi.VIOLET, .95f));
        MenuUi.drawCentered(g, MenuUi.text(m.title()), x + w / 2, y + 6, MenuUi.GOLD_HI, 1);
        List<FormattedCharSequence> lines = font.split(MenuUi.text(m.body()), (int) w - 24);
        float ty = y + 28;
        for (FormattedCharSequence line : lines.subList(0, Math.min(3, lines.size()))) {
            g.pose().pushPose();
            g.pose().translate(x + w / 2f - font.width(line) / 2f, ty, 0);
            g.drawString(font, line, 0, 0, 0xFF000000 | m.bodyColor(), false);
            g.pose().popPose();
            ty += 10;
        }
        if (modalUntil > 0) MenuUi.drawCentered(g, MenuUi.text(Math.max(0, (modalUntil - now) / 1000) + " s"), x + w / 2, y + 60, MenuUi.FAINT, 1);
        float by = y + h - 18;
        button(g, x + w / 2 - 26, by, "yes", MenuUi.GOLD, mouseX, mouseY, 0);
        button(g, x + w / 2 + 26, by, "no", MenuUi.IVORY_HI, mouseX, mouseY, 1);
    }

    private void button(GuiGraphics g, float cx, float cy, String icon, int color, float mouseX, float mouseY, int index) {
        float r = 11;
        boolean hover = Math.hypot(mouseX - cx, mouseY - cy) <= r + 1;
        if (hover) MenuUi.glow(g, cx, cy, r * 2.1f, MenuUi.argb(index == 0 ? MenuUi.GOLD_HI : MenuUi.VIOLET_HI, .6f));
        MenuUi.circle(g, cx, cy, r, MenuUi.argb(color, 1));
        MenuUi.ring(g, cx, cy, r, MenuUi.argb(MenuUi.VIOLET, .35f));
        MenuUi.iconAt(g, icon, cx, cy, r * 1.1f, MenuUi.argb(MenuUi.VIOLET, 1));
        modalHits.add(new Hit(-1, index, cx - r, cy - r, r * 2, r * 2));
    }

    static void play(SoundEvent sound, float pitch) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, .55f));
    }
}
