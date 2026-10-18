package com.aurorion.essentials.client;

import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Base das telas de grupo: o mesmo celular das telas do telefone (170 x 285, capinha, tema claro e
 * escuro, barra de status, brilho) para que criar e ver um grupo pareca parte do app Mensagens.
 *
 * <p>Nao estende a base do telefone (ele nao esta no classpath de compilacao); a escala de interface
 * vem do {@link EssentialsPhoneScreen}. Cliques sao areas registradas no proprio desenho, como no app
 * Servicos: o que aparece na tela e exatamente o que pode ser tocado.
 */
abstract class PhoneGroupScreen extends Screen implements EssentialsPhoneScreen {
    static final int W = PhoneUi.PHONE_WIDTH;
    static final int H = PhoneUi.PHONE_HEIGHT;
    static final int RED = 0xFFE5484D;
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm");

    private record Hit(int x, int y, int w, int h, Runnable action) {
        boolean inside(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    /** Resolvidos uma vez: sao pedidos a cada quadro. */
    private static Method shell;
    private static boolean shellResolved;
    private static Method brightness;
    private static boolean brightnessResolved;

    private final List<Hit> hits = new ArrayList<>();
    private String toast = "";
    private long toastUntil;
    protected int px;
    protected int py;
    protected int scroll;
    /** Altura total da lista e area visivel dela, para limitar a rolagem. */
    protected int contentHeight;
    protected int listHeight;

    protected PhoneGroupScreen(Component title) {
        super(title);
    }

    // ---- o que cada tela define ----------------------------------------------------------------

    protected abstract String heading();

    protected abstract String subheading();

    protected abstract void back();

    protected abstract void drawContent(GuiGraphics graphics, int mouseX, int mouseY);

    /** Botao do canto direito do cabecalho ("Criar"), ou {@code null}. */
    @Nullable
    protected HeaderAction headerAction() {
        return null;
    }

    protected record HeaderAction(String label, boolean enabled, Runnable action) {
    }

    // ---- ciclo da tela -------------------------------------------------------------------------

    @Override
    protected void init() {
        px = PhoneUi.phoneLeft(this);
        py = PhoneUi.phoneTop(this);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0x33000000);
        hits.clear();
        drawShell(graphics);
        drawStatusBar(graphics);
        drawHeader(graphics, mouseX, mouseY);
        drawContent(graphics, mouseX, mouseY);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        if (!toast.isEmpty() && Util.getMillis() < toastUntil) {
            int top = py + H - 76;
            graphics.fill(px + 12, top, px + W - 12, top + 26, 0xDD000000);
            graphics.drawCenteredString(font, font.plainSubstrByWidth(toast, W - 32), px + W / 2, top + 9, 0xFFFFFFFF);
        }
        int overlay = brightnessOverlay();
        if (overlay != 0) graphics.fill(px, py, px + W, py + H, overlay);
        graphics.fill(px + 55, py + H - 12, px + W - 55, py + H - 9, PhoneUi.themeColor("getHomeIndicatorColor", 0xFF111111));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        if (button != 0) return false;
        for (Hit hit : List.copyOf(hits)) {
            if (hit.inside(mouseX, mouseY)) {
                hit.action().run();
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scroll = Math.max(0, Math.min(Math.max(0, contentHeight - listHeight), scroll - (int) Math.round(scrollY * 18)));
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) {
            // ESC volta um passo, como o "<", em vez de fechar o celular inteiro.
            back();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    void toast(String message) {
        toast = message == null ? "" : message;
        toastUntil = Util.getMillis() + 2250L;
    }

    // ---- pecas de desenho ----------------------------------------------------------------------

    protected void hit(int x, int y, int w, int h, Runnable action) {
        hits.add(new Hit(x, y, w, h, action));
    }

    protected static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    protected int color(String getter, int fallback) {
        return PhoneUi.themeColor(getter, fallback);
    }

    /** Circulo do avatar (quadrado, como o telefone desenha) com a inicial do nome mostrado. */
    protected void avatar(GuiGraphics graphics, int x, int y, int size, int background, String shown) {
        graphics.fill(x, y, x + size, y + size, background);
        String initial = shown == null || shown.isBlank() ? "?" : shown.substring(0, 1).toUpperCase();
        graphics.drawString(font, initial, x + (size - font.width(initial)) / 2, y + (size - 8) / 2, 0xFFFFFFFF, false);
    }

    /** Botao largo do rodape, no estilo das linhas de ajuste do telefone. */
    protected void wideButton(GuiGraphics graphics, int y, String label, int textColor, boolean enabled,
                              int mouseX, int mouseY, Runnable action) {
        int left = px + 12;
        int width = W - 24;
        boolean hover = enabled && inside(mouseX, mouseY, left, y, width, 18);
        graphics.fill(left, y, left + width, y + 18, hover
                ? color("getReadableInputHoverColor", 0xFFE5E7EB)
                : color("getSecondaryCardColor", 0xFFF3F4F6));
        graphics.drawCenteredString(font, label, px + W / 2, y + 5, enabled ? textColor : color("getMutedTextColor", 0xFF8A8F99));
        if (enabled) hit(left, y, width, 18, action);
    }

    protected String trim(String text, int width) {
        if (text == null) return "";
        return font.width(text) <= width ? text : font.plainSubstrByWidth(text, width - font.width("...")) + "...";
    }

    private void drawShell(GuiGraphics graphics) {
        int screen = color("getAppBackgroundColor", 0xFFF2F2F7);
        if (!shellResolved) {
            shellResolved = true;
            try {
                shell = Class.forName(PhoneUi.GUI + "PhoneCaseStore").getDeclaredMethod("drawPhoneShell",
                        GuiGraphics.class, int.class, int.class, int.class, int.class, int.class);
                shell.setAccessible(true);
            } catch (ReflectiveOperationException | RuntimeException e) {
                shell = null;
            }
        }
        if (shell != null) {
            try {
                shell.invoke(null, graphics, px, py, W, H, screen);
                return;
            } catch (ReflectiveOperationException | RuntimeException e) {
                shell = null;
            }
        }
        graphics.fill(px - 10, py - 8, px + W + 10, py + H + 10, 0x66000000);
        graphics.fill(px - 6, py - 6, px + W + 6, py + H + 6, 0xFF030303);
        graphics.fill(px - 3, py - 3, px + W + 3, py + H + 3, 0xFF1F2937);
        graphics.fill(px, py, px + W, py + H, screen);
    }

    private void drawStatusBar(GuiGraphics graphics) {
        int text = color("getTextColor", 0xFF111111);
        graphics.fill(px, py, px + W, py + 22, color("getCardColor", 0xFFFFFFFF));
        graphics.drawString(font, LocalTime.now().format(CLOCK), px + 8, py + 7, text, false);
        int notch = px + W / 2 - 18;
        graphics.fill(notch, py + 4, notch + 36, py + 14, 0x22000000);
        String network = PhoneUi.airplaneMode() ? PhoneUi.text("airplane", "Avião") : "5G";
        graphics.drawString(font, network, px + W - 8 - font.width(network), py + 7, text, false);
    }

    private void drawHeader(GuiGraphics graphics, int mouseX, int mouseY) {
        int y = py + 22;
        int text = color("getTextColor", 0xFF111111);
        int accent = color("getAccentColor", 0xFF2F80ED);
        graphics.fill(px, y, px + W, y + 28, color("getCardColor", 0xFFFFFFFF));
        graphics.fill(px, y + 27, px + W, y + 28, color("getBorderColor", 0xFFD8D8DE));
        boolean backHover = inside(mouseX, mouseY, px + 4, y + 4, 20, 20);
        graphics.drawString(font, "<", px + 10, y + 10, backHover ? accent : text, false);
        hit(px + 4, y + 4, 20, 20, () -> {
            PhoneUi.playBack();
            back();
        });

        HeaderAction action = headerAction();
        int titleWidth = W - 30;
        if (action != null) {
            int buttonWidth = font.width(action.label()) + 10;
            int left = px + W - 6 - buttonWidth;
            boolean hover = action.enabled() && inside(mouseX, mouseY, left, y + 6, buttonWidth, 16);
            graphics.fill(left, y + 6, left + buttonWidth, y + 22, action.enabled()
                    ? (hover ? color("getAccentSoftColor", 0xFFD6E4FF) : color("getSecondaryCardColor", 0xFFF3F4F6))
                    : color("getSecondaryCardColor", 0xFFF3F4F6));
            graphics.drawString(font, action.label(), left + 5, y + 10,
                    action.enabled() ? accent : color("getMutedTextColor", 0xFF8A8F99), false);
            if (action.enabled()) hit(left, y + 6, buttonWidth, 16, action.action());
            titleWidth = left - (px + 24) - 4;
        }
        graphics.drawString(font, trim(heading(), titleWidth), px + 24, y + 5, text, false);
        graphics.drawString(font, trim(subheading(), titleWidth), px + 24, y + 16, color("getMutedTextColor", 0xFF8A8F99), false);
    }

    private static int brightnessOverlay() {
        if (!brightnessResolved) {
            brightnessResolved = true;
            try {
                brightness = Class.forName(PhoneUi.GUI + "PhoneSettingsStore").getDeclaredMethod("getBrightnessOverlayColor");
                brightness.setAccessible(true);
            } catch (ReflectiveOperationException | RuntimeException e) {
                brightness = null;
            }
        }
        if (brightness == null) return 0;
        try {
            return (int) brightness.invoke(null);
        } catch (ReflectiveOperationException | RuntimeException e) {
            brightness = null;
            return 0;
        }
    }
}
