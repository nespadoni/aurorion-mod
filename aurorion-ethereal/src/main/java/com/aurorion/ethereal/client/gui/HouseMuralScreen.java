package com.aurorion.ethereal.client.gui;

import com.aurorion.core.text.TimeFormat;
import com.aurorion.ethereal.network.HouseMuralPayloads;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

/**
 * Painel coletivo da Casa em três abas: o cofre (depositar e sacar), as melhorias de capacidade e a
 * área proibida do Protetor.
 *
 * <p>A tela não decide nada: cada botão manda um pedido e o servidor responde com o estado inteiro de
 * novo, que {@link #update} aplica sem fechar a tela nem perder o valor digitado. Contagens (salário,
 * "há X") andam sozinhas a partir do instante em que o estado chegou, sem pacote por segundo.</p>
 */
public final class HouseMuralScreen extends Screen {
    private static final int PANEL_W = 344;
    private static final int PANEL_H = 276;
    private static final int PAD = 14;
    private static final int TAB_Y = 40;
    private static final int TAB_H = 16;
    private static final int CONTENT_Y = 64;
    private static final int MOVEMENT_ROWS = 4;
    /** Mesmo alcance que o servidor aceita: longe disso toda ação seria recusada. */
    private static final double MAX_DISTANCE_SQR = 64.0D;
    private static final long NOTICE_MILLIS = 4_000L;
    private static final long NOTICE_FADE_MILLIS = 800L;

    private static final int C_OVERLAY = 0xB4000000;
    private static final int C_SHADOW = 0x66000000;
    private static final int C_PANEL_TOP = 0xFF13141B;
    private static final int C_PANEL_BOTTOM = 0xFF08090D;
    private static final int C_CARD = 0xFF171A23;
    private static final int C_CARD_EDGE = 0xFF262A36;
    private static final int C_ROW_ALT = 0xFF12141B;
    private static final int C_TAB = 0xFF15171F;
    private static final int C_TAB_HOVER = 0xFF1E212B;
    private static final int C_TRACK = 0xFF0C0D12;
    private static final int C_FORBIDDEN = 0xFF060207;
    private static final int C_RED = 0xFF8D2639;
    private static final int C_TEXT = 0xFFE6E0D8;
    private static final int C_MUTED = 0xFF8A8590;
    private static final int C_GOOD = 0xFF6FCF8A;
    private static final int C_BAD = 0xFFE07A7A;
    private static final int C_GOLD = 0xFFE8C15A;

    private HouseMuralPayloads.Open data;
    private long receivedAt = Util.getMillis();
    private int tab;
    private int left;
    private int top;
    private String amountText = "";
    private int confirmingLevel = -1;
    private EditBox amountInput;

    private Component notice = Component.empty();
    private boolean noticeError;
    private long noticeAt;

    public HouseMuralScreen(HouseMuralPayloads.Open data) {
        super(Component.translatable("gui.aurorion_ethereal.mural.title"));
        this.data = data;
        this.tab = data.tab();
        showNotice(data.notice(), data.noticeError(), false);
    }

    public boolean showsMural(BlockPos pos) {
        return data.pos().equals(pos);
    }

    /** Novo estado vindo do servidor depois de uma ação. */
    public void update(HouseMuralPayloads.Open next) {
        this.data = next;
        this.receivedAt = Util.getMillis();
        this.tab = next.tab();
        this.confirmingLevel = -1;
        if (!next.noticeError() && next.tab() == HouseMuralPayloads.TAB_VAULT) amountText = "";
        showNotice(next.notice(), next.noticeError(), true);
        rebuildWidgets();
    }

    private void showNotice(Component text, boolean error, boolean withSound) {
        if (text.getString().isEmpty()) return;
        notice = text;
        noticeError = error;
        noticeAt = Util.getMillis();
        if (withSound && minecraft != null) {
            minecraft.getSoundManager().play(error
                    ? SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_BASS.value(), 0.7F, 0.6F)
                    : SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.4F, 0.35F));
        }
    }

    @Override
    protected void init() {
        left = (width - PANEL_W) / 2;
        top = (height - PANEL_H) / 2;
        amountInput = null;

        if (tab == HouseMuralPayloads.TAB_VAULT) initVault();
        else if (tab == HouseMuralPayloads.TAB_UPGRADES) initUpgrades();
        else initProtector();

        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> onClose())
                .bounds(left + PANEL_W / 2 - 55, top + PANEL_H - 26, 110, 20).build());
    }

    private void initVault() {
        if (!data.economyAvailable() || !data.member()) return;
        int y = top + CONTENT_Y + 84;
        amountInput = new EditBox(font, left + PAD + 1, y + 1, 116, 18,
                Component.translatable("gui.aurorion_ethereal.mural.vault.amount"));
        amountInput.setMaxLength(16);
        amountInput.setFilter(text -> text.chars().allMatch(c -> Character.isDigit(c) || c == ',' || c == '.'));
        amountInput.setHint(Component.translatable("gui.aurorion_ethereal.mural.vault.amount_hint"));
        amountInput.setValue(amountText);
        addRenderableWidget(amountInput);
        setInitialFocus(amountInput);

        addRenderableWidget(Button.builder(Component.translatable("gui.aurorion_ethereal.mural.vault.deposit"),
                button -> send(true)).bounds(left + 138, y, 94, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.aurorion_ethereal.mural.vault.withdraw"),
                button -> send(false)).bounds(left + PANEL_W - PAD - 94, y, 94, 20).build());
    }

    private void send(boolean deposit) {
        amountText = amountInput.getValue();
        long amount = HouseMuralPayloads.parseMoney(amountText);
        if (amount <= 0L) {
            showNotice(Component.translatable("aurorion_ethereal.mural.vault.error.invalid"), true, true);
            return;
        }
        PacketDistributor.sendToServer(new HouseMuralPayloads.VaultTransfer(data.pos(), deposit, amount));
    }

    private void initUpgrades() {
        if (!data.economyAvailable()) return;
        int next = data.vaultLevel() + 1;
        if (next > data.tiers().size()) return;
        HouseMuralPayloads.Tier tier = data.tiers().get(next - 1);
        boolean confirming = confirmingLevel == next;
        Button buy = addRenderableWidget(Button.builder(Component.translatable(confirming
                        ? "gui.aurorion_ethereal.mural.upgrade.confirm" : "gui.aurorion_ethereal.mural.upgrade.buy"),
                button -> {
                    if (confirmingLevel != next) {
                        confirmingLevel = next;
                        rebuildWidgets();
                        return;
                    }
                    PacketDistributor.sendToServer(new HouseMuralPayloads.BuyVaultUpgrade(data.pos(), next, tier.price()));
                }).bounds(left + PANEL_W - PAD - 86, tierY(next - 1) + 8, 78, 20).build());
        buy.active = data.member() && tier.price() >= 0L && data.balance() >= tier.price();
    }

    private void initProtector() {
        Component protectorLabel = data.protectorLevel() <= 0
                ? Component.translatable("gui.aurorion_ethereal.mural.protector.scrambled")
                : Component.translatable("gui.aurorion_ethereal.mural.protector.open");
        Button protector = addRenderableWidget(Button.builder(protectorLabel, button ->
                        PacketDistributor.sendToServer(new HouseMuralPayloads.OpenProtector(data.pos())))
                .bounds(left + 30, top + CONTENT_Y + 118, PANEL_W - 60, 20).build());
        protector.active = data.member() && data.protectorLevel() > 0
                && data.remainingMillis() <= 0L && data.livesAvailable();
    }

    /** Longe do cofre toda ação seria recusada no servidor; fechar é mais honesto que falhar calado. */
    @Override
    public void tick() {
        super.tick();
        if (minecraft != null && minecraft.player != null
                && minecraft.player.distanceToSqr(Vec3.atCenterOf(data.pos())) > MAX_DISTANCE_SQR) {
            onClose();
        }
    }

    // --- Abas -------------------------------------------------------------------------------------

    private int tabX(int index) {
        return left + PAD + index * ((PANEL_W - 2 * PAD) / 3);
    }

    private int tabWidth() {
        return (PANEL_W - 2 * PAD) / 3 - 4;
    }

    private int tabAt(double mouseX, double mouseY) {
        if (mouseY < top + TAB_Y || mouseY >= top + TAB_Y + TAB_H) return -1;
        for (int i = 0; i < 3; i++) {
            if (mouseX >= tabX(i) && mouseX < tabX(i) + tabWidth()) return i;
        }
        return -1;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int clicked = tabAt(mouseX, mouseY);
        if (button == 0 && clicked >= 0 && clicked != tab) {
            if (amountInput != null) amountText = amountInput.getValue();
            tab = clicked;
            confirmingLevel = -1;
            notice = Component.empty();
            playClick();
            rebuildWidgets();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void playClick() {
        if (minecraft != null) minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    private void renderTabs(GuiGraphics graphics, int mouseX, int mouseY) {
        int hovered = tabAt(mouseX, mouseY);
        String[] keys = {"gui.aurorion_ethereal.mural.tab.vault", "gui.aurorion_ethereal.mural.tab.upgrades",
                "gui.aurorion_ethereal.mural.tab.protector"};
        for (int i = 0; i < 3; i++) {
            int x = tabX(i);
            int w = tabWidth();
            boolean selected = i == tab;
            graphics.fill(x, top + TAB_Y, x + w, top + TAB_Y + TAB_H,
                    selected ? C_CARD : hovered == i ? C_TAB_HOVER : C_TAB);
            if (selected) {
                graphics.fill(x, top + TAB_Y + TAB_H - 2, x + w, top + TAB_Y + TAB_H,
                        i == HouseMuralPayloads.TAB_PROTECTOR ? C_RED : houseColor());
            }
            int color = i == HouseMuralPayloads.TAB_PROTECTOR ? C_RED : selected ? C_TEXT : C_MUTED;
            String label = clip(Component.translatable(keys[i]).getString(), w - 8);
            graphics.drawCenteredString(font, label, x + w / 2, top + TAB_Y + 4, color);
        }
        graphics.fill(left + PAD, top + TAB_Y + TAB_H, left + PANEL_W - PAD, top + TAB_Y + TAB_H + 1, C_CARD_EDGE);
    }

    // --- Desenho ----------------------------------------------------------------------------------

    /**
     * Todo o painel é desenhado aqui, e não em {@code render}: no 1.21 o {@code Screen.render} chama
     * {@code renderBackground} de novo, e o desfoque dessa segunda chamada borrava o texto desenhado
     * antes dela. Só os botões, que vêm depois, ficavam nítidos.
     */
    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        int right = left + PANEL_W;
        int bottom = top + PANEL_H;
        int house = houseColor();

        graphics.fill(0, 0, width, height, C_OVERLAY);
        graphics.fill(left + 4, top + 5, right + 4, bottom + 5, C_SHADOW);
        graphics.fill(left - 1, top - 1, right + 1, bottom + 1, dim(house, 0.45F));
        graphics.fillGradient(left, top, right, bottom, C_PANEL_TOP, C_PANEL_BOTTOM);
        graphics.fill(left, top, right, top + 3, house);
        // Cantos curtos dão acabamento de moldura sem textura.
        graphics.fill(left, bottom - 2, left + 14, bottom, house);
        graphics.fill(right - 14, bottom - 2, right, bottom, house);

        graphics.drawCenteredString(font, title, left + PANEL_W / 2, top + 8, C_MUTED);
        drawScaledCentered(graphics, data.houseName(), left + PANEL_W / 2, top + 20, 1.5F, house);

        renderTabs(graphics, mouseX, mouseY);
        if (tab == HouseMuralPayloads.TAB_VAULT) renderVault(graphics);
        else if (tab == HouseMuralPayloads.TAB_UPGRADES) renderUpgrades(graphics);
        else renderProtector(graphics);
        renderNotice(graphics);
    }

    private void renderVault(GuiGraphics graphics) {
        int x = left + PAD;
        int right = left + PANEL_W - PAD;
        int y = top + CONTENT_Y;
        card(graphics, x, y, right, y + 64);
        graphics.drawString(font, Component.translatable("gui.aurorion_ethereal.mural.vault"), x + 10, y + 8, C_MUTED, false);
        if (!data.economyAvailable()) {
            graphics.drawString(font, Component.translatable("gui.aurorion_ethereal.mural.vault.unavailable"),
                    x + 10, y + 28, C_BAD, false);
            return;
        }

        pill(graphics, Component.translatable("gui.aurorion_ethereal.mural.vault.level", data.vaultLevel()),
                right - 8, y + 6, houseColor());

        String balance = HouseMuralPayloads.formatMoney(data.balance());
        drawScaled(graphics, Component.literal(balance), x + 10, y + 20, 2.0F, C_TEXT);
        graphics.drawString(font, Component.translatable("gui.aurorion_ethereal.mural.vault.of",
                HouseMuralPayloads.formatMoney(data.capacity())), x + 16 + font.width(balance) * 2, y + 27, C_MUTED, false);

        double fill = data.capacity() <= 0L ? 0.0D : Math.min(1.0D, data.balance() / (double) data.capacity());
        String percent = Math.round(fill * 100.0D) + "%";
        graphics.drawString(font, percent, right - 10 - font.width(percent), y + 27, fill >= 1.0D ? C_BAD : C_MUTED, false);
        bar(graphics, x + 10, y + 40, right - 10, fill, fill >= 1.0D ? C_BAD : houseColor());

        String salary = salaryLine().getString();
        graphics.drawString(font, clip(salary, right - x - 20), x + 10, y + 51,
                data.salary() > 0L ? C_GOLD : C_MUTED, false);

        int walletY = y + 71;
        Component walletLabel = Component.translatable("gui.aurorion_ethereal.mural.vault.wallet");
        graphics.drawString(font, walletLabel, x + 1, walletY, C_MUTED, false);
        graphics.drawString(font, HouseMuralPayloads.formatMoney(data.wallet()),
                x + 5 + font.width(walletLabel), walletY, C_GOLD, false);

        if (!data.member()) {
            graphics.drawCenteredString(font, Component.translatable("gui.aurorion_ethereal.mural.vault.members_only"),
                    left + PANEL_W / 2, y + 90, C_MUTED);
        }

        renderHistory(graphics, x, right, y + 124);
    }

    private void renderHistory(GuiGraphics graphics, int x, int right, int y) {
        graphics.drawString(font, Component.translatable("gui.aurorion_ethereal.mural.vault.history"), x + 1, y, C_MUTED, false);
        graphics.fill(x, y + 10, right, y + 11, C_CARD_EDGE);
        List<HouseMuralPayloads.Movement> movements = data.movements();
        if (movements.isEmpty()) {
            graphics.drawString(font, Component.translatable("gui.aurorion_ethereal.mural.vault.history.empty"),
                    x + 4, y + 16, C_MUTED, false);
            return;
        }
        long elapsed = elapsed();
        for (int i = 0; i < Math.min(MOVEMENT_ROWS, movements.size()); i++) {
            HouseMuralPayloads.Movement movement = movements.get(i);
            int rowY = y + 13 + i * 11;
            if (i % 2 == 1) graphics.fill(x, rowY - 1, right, rowY + 10, C_ROW_ALT);

            String key;
            String icon;
            int color;
            switch (movement.kind()) {
                case 0 -> { key = "deposit"; icon = "+"; color = C_GOOD; }
                case 1 -> { key = "withdraw"; icon = "-"; color = C_BAD; }
                default -> { key = "upgrade"; icon = "*"; color = C_GOLD; }
            }
            String ago = Component.translatable("gui.aurorion_ethereal.mural.movement.ago",
                    TimeFormat.duration(movement.agoMillis() + elapsed)).getString();
            int agoX = right - 4 - font.width(ago);
            graphics.drawString(font, icon, x + 4, rowY + 1, color, false);
            String line = Component.translatable("gui.aurorion_ethereal.mural.movement." + key,
                    movement.actor(), HouseMuralPayloads.formatMoney(movement.amount())).getString();
            graphics.drawString(font, clip(line, agoX - (x + 14) - 6), x + 14, rowY + 1, C_TEXT, false);
            graphics.drawString(font, ago, agoX, rowY + 1, C_MUTED, false);
        }
    }

    private Component salaryLine() {
        if (data.salary() <= 0L) return Component.translatable("gui.aurorion_ethereal.mural.vault.salary.none");
        return Component.translatable("gui.aurorion_ethereal.mural.vault.salary",
                HouseMuralPayloads.formatMoney(data.salary()), data.salaryDays(),
                TimeFormat.duration(Math.max(0L, data.nextSalaryMillis() - elapsed())));
    }

    private void renderUpgrades(GuiGraphics graphics) {
        int x = left + PAD;
        int right = left + PANEL_W - PAD;
        int y = top + CONTENT_Y;
        graphics.drawString(font, Component.translatable("gui.aurorion_ethereal.mural.upgrade.title"), x + 1, y, C_TEXT, false);
        if (!data.economyAvailable()) {
            graphics.drawString(font, Component.translatable("gui.aurorion_ethereal.mural.vault.unavailable"),
                    x + 1, y + 14, C_BAD, false);
            return;
        }
        String subtitle = Component.translatable("gui.aurorion_ethereal.mural.upgrade.subtitle",
                HouseMuralPayloads.formatMoney(data.capacity()), HouseMuralPayloads.formatMoney(data.balance())).getString();
        graphics.drawString(font, clip(subtitle, right - x - 2), x + 1, y + 12, C_MUTED, false);

        List<HouseMuralPayloads.Tier> tiers = data.tiers();
        long maxCapacity = tiers.isEmpty() ? 1L : Math.max(1L, tiers.get(tiers.size() - 1).capacity());
        for (int i = 0; i < tiers.size(); i++) {
            int level = i + 1;
            HouseMuralPayloads.Tier tier = tiers.get(i);
            int cardY = tierY(i);
            boolean owned = level <= data.vaultLevel();
            boolean next = level == data.vaultLevel() + 1;
            int accent = owned ? houseColor() : next ? C_GOLD : C_CARD_EDGE;

            card(graphics, x, cardY, right, cardY + 36);
            graphics.fill(x, cardY, x + 3, cardY + 36, accent);
            graphics.drawString(font, Component.translatable("gui.aurorion_ethereal.mural.upgrade.tier",
                    level, HouseMuralPayloads.formatMoney(tier.capacity())), x + 10, cardY + 6,
                    owned || next ? C_TEXT : C_MUTED, false);

            Component status;
            int color;
            if (owned) {
                status = Component.translatable("gui.aurorion_ethereal.mural.upgrade.owned");
                color = C_GOOD;
            } else if (tier.price() < 0L) {
                status = Component.translatable("gui.aurorion_ethereal.mural.upgrade.disabled");
                color = C_MUTED;
            } else if (next) {
                boolean affordable = data.balance() >= tier.price();
                status = Component.translatable(affordable ? "gui.aurorion_ethereal.mural.upgrade.price"
                        : "gui.aurorion_ethereal.mural.upgrade.price_missing", HouseMuralPayloads.formatMoney(tier.price()));
                color = affordable ? C_GOLD : C_BAD;
            } else {
                status = Component.translatable("gui.aurorion_ethereal.mural.upgrade.locked",
                        HouseMuralPayloads.formatMoney(tier.price()));
                color = C_MUTED;
            }
            int textRight = next ? right - 96 : right - 10;
            graphics.drawString(font, clip(status.getString(), textRight - x - 10), x + 10, cardY + 17, color, false);
            // A barra compara com o maior nível: dá para ver quanto cada compra cresce o cofre.
            bar(graphics, x + 10, cardY + 29, textRight, tier.capacity() / (double) maxCapacity,
                    owned ? houseColor() : dim(accent, 0.6F));
        }
    }

    private void renderProtector(GuiGraphics graphics) {
        int x = left + PAD;
        int right = left + PANEL_W - PAD;
        int y = top + CONTENT_Y;
        graphics.fill(x, y, right, y + 146, C_FORBIDDEN);
        // Linhas de varredura fracas: a seção parece um registro corrompido, não um cartão comum.
        for (int line = y + 2; line < y + 146; line += 3) graphics.fill(x, line, right, line + 1, 0x14FF2040);
        graphics.fill(x, y, x + 4, y + 146, C_RED);

        Component section = data.protectorLevel() <= 0
                ? Component.translatable("gui.aurorion_ethereal.mural.protector.scrambled")
                : Component.translatable("gui.aurorion_ethereal.mural.protector.level", data.protectorLevel());
        // Tremor raro e de um pixel; parado a maior parte do tempo para continuar legível.
        int jitter = (Util.getMillis() / 90L) % 23L == 0L ? 1 : 0;
        graphics.drawCenteredString(font, section, left + PANEL_W / 2 + jitter, y + 32, C_RED);
        graphics.drawCenteredString(font, protectorState(), left + PANEL_W / 2, y + 62,
                data.remainingMillis() > 0L ? C_MUTED : C_TEXT);
    }

    private Component protectorState() {
        if (data.protectorLevel() <= 0) {
            return Component.translatable("gui.aurorion_ethereal.mural.protector.locked");
        }
        if (!data.livesAvailable()) {
            return Component.translatable("gui.aurorion_ethereal.mural.protector.no_lives_mod");
        }
        long remaining = Math.max(0L, data.remainingMillis() - elapsed());
        if (remaining > 0L) {
            return Component.translatable("gui.aurorion_ethereal.mural.protector.remaining", TimeFormat.duration(remaining));
        }
        return Component.translatable("gui.aurorion_ethereal.mural.protector.ready");
    }

    /** Aviso em pílula; some sozinho depois de alguns segundos. */
    private void renderNotice(GuiGraphics graphics) {
        if (notice.getString().isEmpty()) return;
        long age = Util.getMillis() - noticeAt;
        if (age > NOTICE_MILLIS + NOTICE_FADE_MILLIS) return;
        float alpha = age <= NOTICE_MILLIS ? 1.0F : 1.0F - (age - NOTICE_MILLIS) / (float) NOTICE_FADE_MILLIS;
        if (alpha < 0.1F) return;

        String text = clip(notice.getString(), PANEL_W - 2 * PAD - 16);
        int w = font.width(text) + 16;
        int cx = left + PANEL_W / 2;
        // No cofre fica entre os botoes e o historico; nas outras abas, logo acima do Concluido.
        int y = tab == HouseMuralPayloads.TAB_VAULT ? top + CONTENT_Y + 107 : top + PANEL_H - 44;
        int accent = noticeError ? C_BAD : C_GOOD;
        graphics.fill(cx - w / 2, y, cx + w / 2, y + 14, withAlpha(0xFF101218, alpha * 0.92F));
        graphics.fill(cx - w / 2, y, cx - w / 2 + 2, y + 14, withAlpha(accent, alpha));
        graphics.drawCenteredString(font, text, cx + 1, y + 3, withAlpha(accent, alpha));
    }

    // --- Utilitários de desenho -------------------------------------------------------------------

    private void card(GuiGraphics graphics, int x0, int y0, int x1, int y1) {
        graphics.fill(x0, y0, x1, y1, C_CARD_EDGE);
        graphics.fill(x0 + 1, y0 + 1, x1 - 1, y1 - 1, C_CARD);
    }

    private void bar(GuiGraphics graphics, int x0, int y, int x1, double fill, int color) {
        graphics.fill(x0, y, x1, y + 4, C_TRACK);
        int filled = (int) Math.round((x1 - x0) * Math.max(0.0D, Math.min(1.0D, fill)));
        if (filled <= 0) return;
        graphics.fill(x0, y, x0 + filled, y + 4, color);
        graphics.fill(x0, y, x0 + filled, y + 1, brighten(color));
    }

    /** Etiqueta alinhada pela direita em {@code rightX}. */
    private void pill(GuiGraphics graphics, Component text, int rightX, int y, int color) {
        int w = font.width(text) + 10;
        graphics.fill(rightX - w, y, rightX, y + 12, dim(color, 0.35F));
        graphics.drawString(font, text, rightX - w + 5, y + 2, brighten(color), false);
    }

    private void drawScaled(GuiGraphics graphics, Component text, int x, int y, float scale, int color) {
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0.0F);
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.drawString(font, text, 0, 0, color, true);
        graphics.pose().popPose();
    }

    private void drawScaledCentered(GuiGraphics graphics, Component text, int centerX, int y, float scale, int color) {
        drawScaled(graphics, text, Math.round(centerX - font.width(text) * scale / 2.0F), y, scale, color);
    }

    private String clip(String text, int maxWidth) {
        if (maxWidth <= 0) return "";
        if (font.width(text) <= maxWidth) return text;
        return font.plainSubstrByWidth(text, Math.max(0, maxWidth - font.width("..."))) + "...";
    }

    private int tierY(int index) {
        return top + CONTENT_Y + 26 + index * 42;
    }

    private long elapsed() {
        return Math.max(0L, Util.getMillis() - receivedAt);
    }

    private int houseColor() {
        return 0xFF000000 | data.houseColor();
    }

    private static int dim(int color, float factor) {
        int r = (int) (((color >> 16) & 0xFF) * factor);
        int g = (int) (((color >> 8) & 0xFF) * factor);
        int b = (int) ((color & 0xFF) * factor);
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    private static int brighten(int color) {
        int r = Math.min(255, ((color >> 16) & 0xFF) + 60);
        int g = Math.min(255, ((color >> 8) & 0xFF) + 60);
        int b = Math.min(255, (color & 0xFF) + 60);
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    private static int withAlpha(int color, float alpha) {
        // Fonte trata alfa quase zero como opaco; o chamador ja corta abaixo de 10%.
        int a = Math.max(0x10, Math.min(0xFF, (int) (((color >>> 24) & 0xFF) * alpha)));
        return a << 24 | (color & 0xFFFFFF);
    }

    @Override public boolean isPauseScreen() { return false; }
}
