package com.aurorion.essentials.client;

import com.aurorion.essentials.compat.PhoneGroupBook;
import com.aurorion.essentials.network.PhoneGroupPayloads.Member;
import com.aurorion.essentials.network.PhoneGroupPayloads.View;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.UUID;

/**
 * Detalhes do grupo, aberto ao tocar no topo da conversa: membros, adicionar e remover (so o
 * criador), silenciar, sair. Le o estado do {@link PhoneGroupsClient} a cada quadro — uma mudanca
 * vinda do servidor (alguem saiu, o jogador foi removido) aparece sem reabrir a tela.
 */
public final class PhoneGroupInfoScreen extends PhoneGroupScreen {
    private static final int ROW = 22;

    private final UUID group;

    public PhoneGroupInfoScreen(UUID group) {
        super(Component.translatable("aurorion_essentials.phone_group.info"));
        this.group = group;
    }

    @Override
    protected String heading() {
        View view = PhoneGroupsClient.view(group);
        return view != null ? view.name() : Component.translatable("aurorion_essentials.phone_group.info").getString();
    }

    @Override
    protected String subheading() {
        View view = PhoneGroupsClient.view(group);
        return view != null ? PhoneGroupsClient.membersLabel(view)
                : Component.translatable("aurorion_essentials.phone_group.gone").getString();
    }

    @Override
    protected void back() {
        PhoneGroupsClient.openChat(group);
    }

    @Override
    protected void drawContent(GuiGraphics graphics, int mouseX, int mouseY) {
        View view = PhoneGroupsClient.view(group);
        String threadId = PhoneGroupsClient.threadId(group);
        int footer = py + H - 22;

        if (view == null) {
            listHeight = contentHeight = 0;
            graphics.drawCenteredString(font, Component.translatable("aurorion_essentials.phone_group.gone_detail").getString(),
                    px + W / 2, py + 80, color("getMutedTextColor", 0xFF8A8F99));
            wideButton(graphics, footer - 20, Component.translatable("aurorion_essentials.phone_group.delete").getString(), RED, true,
                    mouseX, mouseY, () -> confirm(Component.translatable("aurorion_essentials.phone_group.delete_confirm").getString(),
                            Component.translatable("aurorion_essentials.phone_group.delete").getString(), () -> {
                                PhoneGroupsClient.deleteConversation(group);
                                PhoneGroupsClient.openMessages();
                            }));
            return;
        }

        boolean owner = PhoneGroupsClient.isOwner(view);
        boolean canAdd = owner && view.members().size() < PhoneGroupBook.MAX_MEMBERS;
        int buttons = 2 + (canAdd ? 1 : 0);
        int top = py + 52;
        int bottom = footer - buttons * 20 - 4;
        listHeight = bottom - top;
        contentHeight = view.members().size() * ROW;
        scroll = Math.max(0, Math.min(scroll, Math.max(0, contentHeight - listHeight)));
        drawMembers(graphics, view, owner, top, bottom, mouseX, mouseY);

        int y = footer - buttons * 20;
        if (canAdd) {
            wideButton(graphics, y, Component.translatable("aurorion_essentials.phone_group.add_members").getString(),
                    color("getAccentColor", 0xFF2F80ED), true, mouseX, mouseY, () -> {
                        PhoneUi.playClick();
                        Minecraft.getInstance().setScreen(PhoneGroupEditScreen.addTo(group));
                    });
            y += 20;
        }
        boolean muted = PhoneGroupsClient.muted(threadId);
        wideButton(graphics, y, Component.translatable(muted ? "aurorion_essentials.phone_group.unmute"
                        : "aurorion_essentials.phone_group.mute").getString(),
                color("getAccentColor", 0xFF2F80ED), true, mouseX, mouseY, () -> {
                    PhoneUi.playClick();
                    PhoneGroupsClient.toggleMuted(threadId);
                });
        y += 20;
        wideButton(graphics, y, Component.translatable("aurorion_essentials.phone_group.leave").getString(), RED, true,
                mouseX, mouseY, () -> confirm(Component.translatable("aurorion_essentials.phone_group.leave_confirm").getString(),
                        Component.translatable("aurorion_essentials.phone_group.leave").getString(),
                        () -> PhoneGroupsClient.leave(group)));
    }

    private void drawMembers(GuiGraphics graphics, View view, boolean owner, int top, int bottom, int mouseX, int mouseY) {
        int card = color("getCardColor", 0xFFFFFFFF);
        int text = color("getTextColor", 0xFF111111);
        int muted = color("getMutedTextColor", 0xFF8A8F99);
        List<Member> members = view.members();
        graphics.enableScissor(px, top, px + W, bottom);
        for (int i = 0; i < members.size(); i++) {
            int y = top + i * ROW - scroll;
            if (y + ROW < top || y > bottom) continue;
            Member member = members.get(i);
            boolean self = PhoneGroupsClient.isSelf(member.nick());
            String shown = PhoneGroupsClient.display(member);
            graphics.fill(px, y, px + W, y + ROW, card);
            graphics.fill(px + 34, y + ROW - 1, px + W - 8, y + ROW, color("getSeparatorColor", 0xFFE3E3E8));
            avatar(graphics, px + 10, y + 3, 16, i == 0 ? PhoneGroupsClient.COLOR : 0xFF8B5CF6, shown);

            String tag = self ? Component.translatable("aurorion_essentials.phone_group.you").getString()
                    : i == 0 ? Component.translatable("aurorion_essentials.phone_group.owner").getString() : "";
            if (self && i == 0) tag = tag + " · " + Component.translatable("aurorion_essentials.phone_group.owner").getString();

            int right = px + W - 8;
            if (owner && !self) {
                String label = Component.translatable("aurorion_essentials.phone_group.remove").getString();
                int width = font.width(label) + 6;
                int left = right - width;
                boolean hover = inside(mouseX, mouseY, left, y + 4, width, 14) && y + 4 >= top && y + 18 <= bottom;
                graphics.fill(left, y + 4, right, y + 18, hover ? 0x33E5484D : 0x1AE5484D);
                graphics.drawString(font, label, left + 3, y + 7, RED, false);
                if (y + 4 >= top && y + 18 <= bottom && !member.nick().isBlank()) {
                    hit(left, y + 4, width, 14, () -> confirm(
                            Component.translatable("aurorion_essentials.phone_group.remove_confirm", shown).getString(),
                            label, () -> PhoneGroupsClient.remove(group, member.nick())));
                }
                right = left - 4;
            }
            if (!tag.isEmpty()) {
                right -= font.width(tag);
                graphics.drawString(font, tag, right, y + 7, muted, false);
                right -= 4;
            }
            graphics.drawString(font, trim(shown, right - (px + 32)), px + 32, y + 7, text, false);
        }
        graphics.disableScissor();
    }

    /** Pergunta antes de algo sem volta, com o menu de opcoes do proprio celular. */
    private void confirm(String question, String yes, Runnable action) {
        PhoneUi.playClick();
        Minecraft.getInstance().setScreen(new PhoneChoiceSheet(this, question, List.of(
                new PhoneChoiceSheet.Option(yes, () -> {
                    Minecraft.getInstance().setScreen(this);
                    action.run();
                }))));
    }
}
