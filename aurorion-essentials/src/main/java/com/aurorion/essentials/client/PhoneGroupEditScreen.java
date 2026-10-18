package com.aurorion.essentials.client;

import com.aurorion.essentials.compat.PhoneGroupBook;
import com.aurorion.essentials.network.PhoneGroupPayloads.Member;
import com.aurorion.essentials.network.PhoneGroupPayloads.View;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Novo grupo (nome + contatos) ou "adicionar membros" a um grupo existente. Os contatos sao a agenda
 * do proprio telefone (as conversas de jogador), mostrados pelo nome do personagem; o que vai ao
 * servidor e o nick, como em todo o telefone.
 */
public final class PhoneGroupEditScreen extends PhoneGroupScreen {
    private static final int ROW = 24;

    /** {@code null} ao criar. */
    @Nullable
    private final UUID group;
    private final Set<String> selected = new LinkedHashSet<>();
    private List<String> contacts = List.of();
    private String name = "";
    @Nullable
    private EditBox nameBox;
    /** Quando o pedido de criar saiu: o botao espera a resposta, sem travar se ela nao vier. */
    private long sentAt = Long.MIN_VALUE / 2;

    private PhoneGroupEditScreen(@Nullable UUID group) {
        super(Component.translatable(group == null
                ? "aurorion_essentials.phone_group.new_group" : "aurorion_essentials.phone_group.add_members"));
        this.group = group;
    }

    public static PhoneGroupEditScreen create() {
        return new PhoneGroupEditScreen(null);
    }

    public static PhoneGroupEditScreen addTo(UUID group) {
        return new PhoneGroupEditScreen(group);
    }

    @Override
    protected void init() {
        super.init();
        contacts = available();
        selected.retainAll(contacts);
        nameBox = null;
        if (group == null) {
            nameBox = new EditBox(font, px + 16, py + 59, W - 32, 14,
                    Component.translatable("aurorion_essentials.phone_group.name_hint"));
            nameBox.setMaxLength(PhoneGroupBook.MAX_NAME);
            nameBox.setBordered(false);
            nameBox.setTextColor(color("getReadableInputTextColor", 0xFF111111));
            nameBox.setValue(name);
            nameBox.setResponder(value -> name = value);
            addRenderableWidget(nameBox);
            setInitialFocus(nameBox);
        }
    }

    /** Contatos que ainda podem entrar: fora quem ja esta no grupo. */
    private List<String> available() {
        List<String> all = PhoneGroupsClient.contacts();
        if (group == null) return all;
        View view = PhoneGroupsClient.view(group);
        if (view == null) return all;
        List<String> result = new ArrayList<>();
        for (String nick : all) {
            if (view.members().stream().noneMatch(member -> member.nick().equalsIgnoreCase(nick))) result.add(nick);
        }
        return result;
    }

    /** Quantos ainda cabem no grupo, contando o dono. */
    private int room() {
        if (group == null) return PhoneGroupBook.MAX_MEMBERS - 1;
        View view = PhoneGroupsClient.view(group);
        return PhoneGroupBook.MAX_MEMBERS - (view == null ? 1 : view.members().size());
    }

    @Override
    protected String heading() {
        if (group == null) return Component.translatable("aurorion_essentials.phone_group.new_group").getString();
        View view = PhoneGroupsClient.view(group);
        return view != null ? view.name() : Component.translatable("aurorion_essentials.phone_group.add_members").getString();
    }

    @Override
    protected String subheading() {
        return Component.translatable("aurorion_essentials.phone_group.selected", selected.size(), Math.max(0, room())).getString();
    }

    @Override
    protected void back() {
        if (group == null) PhoneGroupsClient.openMessages();
        else Minecraft.getInstance().setScreen(new PhoneGroupInfoScreen(group));
    }

    @Nullable
    @Override
    protected HeaderAction headerAction() {
        boolean ready = Util.getMillis() - sentAt > 2000L && !selected.isEmpty() && selected.size() <= room()
                && (group != null || PhoneGroupBook.cleanName(name) != null);
        String label = Component.translatable(group == null
                ? "aurorion_essentials.phone_group.create" : "aurorion_essentials.phone_group.add").getString();
        return new HeaderAction(label, ready, this::submit);
    }

    private void submit() {
        if (PhoneUi.airplaneMode()) {
            PhoneUi.playError();
            toast(PhoneUi.text("airplane_on_short", "Modo avião ativo."));
            return;
        }
        PhoneUi.playClick();
        List<String> nicks = List.copyOf(selected);
        if (group == null) {
            // A conversa abre sozinha quando o servidor confirmar (PhoneGroupsClient.applySync).
            if (PhoneGroupsClient.create(name, nicks)) sentAt = Util.getMillis();
        } else if (PhoneGroupsClient.add(group, nicks)) {
            Minecraft.getInstance().setScreen(new PhoneGroupInfoScreen(group));
        }
    }

    @Override
    protected void drawContent(GuiGraphics graphics, int mouseX, int mouseY) {
        int top = py + 50;
        if (nameBox != null) {
            graphics.fill(px + 10, py + 54, px + W - 10, py + 74, color("getReadableInputColor", 0xFFFFFFFF));
            graphics.fill(px + 10, py + 73, px + W - 10, py + 74, color("getReadableInputBorderColor", 0xFFD0D5DD));
            if (name.isEmpty() && !nameBox.isFocused()) {
                graphics.drawString(font, Component.translatable("aurorion_essentials.phone_group.name_hint").getString(),
                        px + 16, py + 59, color("getReadableInputPlaceholderColor", 0xFF8A8F99), false);
            }
            top = py + 80;
        }
        graphics.drawString(font, Component.translatable("aurorion_essentials.phone_group.contacts").getString(),
                px + 12, top + 2, color("getSubTextColor", 0xFF6B7280), false);
        top += 14;

        int bottom = py + H - 20;
        listHeight = bottom - top;
        contentHeight = contacts.size() * ROW;
        scroll = Math.max(0, Math.min(scroll, Math.max(0, contentHeight - listHeight)));
        if (contacts.isEmpty()) {
            graphics.drawCenteredString(font, Component.translatable(group == null
                            ? "aurorion_essentials.phone_group.no_contacts" : "aurorion_essentials.phone_group.no_more_contacts").getString(),
                    px + W / 2, top + 20, color("getMutedTextColor", 0xFF8A8F99));
            return;
        }

        int card = color("getCardColor", 0xFFFFFFFF);
        int accent = color("getAccentColor", 0xFF2F80ED);
        int text = color("getTextColor", 0xFF111111);
        graphics.enableScissor(px, top, px + W, bottom);
        for (int i = 0; i < contacts.size(); i++) {
            int y = top + i * ROW - scroll;
            if (y + ROW < top || y > bottom) continue;
            String nick = contacts.get(i);
            String shown = PhoneGroupsClient.display(new Member(nick, ""));
            boolean on = selected.contains(nick);
            boolean hover = inside(mouseX, mouseY, px, Math.max(y, top), W, Math.min(y + ROW, bottom) - Math.max(y, top));
            graphics.fill(px, y, px + W, y + ROW, hover ? color("getSecondaryCardColor", 0xFFF3F4F6) : card);
            graphics.fill(px + 36, y + ROW - 1, px + W - 8, y + ROW, color("getSeparatorColor", 0xFFE3E3E8));
            avatar(graphics, px + 10, y + 3, 18, 0xFF8B5CF6, shown);
            graphics.drawString(font, trim(shown, W - 70), px + 36, y + 8, text, false);
            int box = px + W - 22;
            graphics.fill(box, y + 6, box + 12, y + 18, on ? accent : color("getBorderColor", 0xFFD8D8DE));
            if (!on) graphics.fill(box + 1, y + 7, box + 11, y + 17, card);
            else graphics.drawString(font, "✓", box + 3, y + 8, 0xFFFFFFFF, false);
            int visibleTop = Math.max(y, top);
            int visibleBottom = Math.min(y + ROW, bottom);
            if (visibleBottom > visibleTop) hit(px, visibleTop, W, visibleBottom - visibleTop, () -> toggle(nick));
        }
        graphics.disableScissor();
    }

    private void toggle(String nick) {
        if (selected.remove(nick)) {
            PhoneUi.playClick();
            return;
        }
        if (selected.size() >= room()) {
            PhoneUi.playError();
            toast(Component.translatable("aurorion_essentials.phone_group.result.group_full").getString());
            return;
        }
        PhoneUi.playClick();
        selected.add(nick);
    }
}
