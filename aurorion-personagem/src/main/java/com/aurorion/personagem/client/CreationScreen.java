package com.aurorion.personagem.client;

import com.aurorion.core.character.CharacterName;
import com.aurorion.personagem.network.OpenCreationPayload;
import com.aurorion.personagem.network.SubmitNamePayload;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Predicate;

/**
 * Nome e sobrenome, e nada mais.
 *
 * <h2>A tela nao decide nada</h2>
 *
 * <p>O filtro de letras e o limite de 24 caracteres nos campos sao <b>conforto</b>, para a recusa
 * chegar antes do envio. A regra mora no {@code CharacterName} do servidor, que reconfere tudo — um
 * cliente modificado que mande digito, nome de uma letra so ou um nome ja usado recebe a mesma
 * recusa que um clique no botao receberia.
 *
 * <p>Nao da para fechar: {@link #shouldCloseOnEsc()} e {@code false} e o {@code ClientCreation}
 * reabre no tick seguinte. Nao e teimosia de interface — e que atras dela nao existe jogo nenhum
 * para voltar, so um espectador parado que o servidor nao deixa jogar.
 */
public class CreationScreen extends Screen {
    private static final int PANEL_WIDTH = 320;
    private static final int FIELD_WIDTH = 150;
    private static final int LINE = 10;

    /** Mesmos caracteres que o {@code CharacterName} aceita dentro de uma parte do nome. */
    private static final Predicate<String> LETTERS = value -> value.chars().allMatch(
            c -> Character.isLetter(c) || c == ' ' || c == '-' || c == '\'' || c == '’');

    private final OpenCreationPayload data;
    private final boolean resuming;

    private EditBox firstName;
    private EditBox lastName;
    private Button confirm;
    @Nullable
    private Component error;
    private List<FormattedCharSequence> intro = List.of();
    private List<FormattedCharSequence> rules = List.of();

    public CreationScreen(OpenCreationPayload data) {
        super(Component.literal(data.title()));
        this.data = data;
        this.resuming = !data.reserved().isEmpty();
    }

    @Override
    protected void init() {
        intro = font.split(Component.literal(data.intro()), PANEL_WIDTH);
        rules = font.split(Component.literal(data.rules()), PANEL_WIDTH);

        int left = (width - PANEL_WIDTH) / 2;
        int y = top() + LINE * 2 + intro.size() * LINE + 12;

        if (!resuming) {
            firstName = field(left, y, "aurorion_personagem.criacao.nome");
            lastName = field(left + PANEL_WIDTH - FIELD_WIDTH, y, "aurorion_personagem.criacao.sobrenome");
            addRenderableWidget(firstName);
            addRenderableWidget(lastName);
            setInitialFocus(firstName);
            y += 24;
        } else {
            y += 14;
        }

        y += rules.size() * LINE + 14;

        confirm = Button.builder(Component.translatable(resuming
                        ? "aurorion_personagem.criacao.continuar"
                        : "aurorion_personagem.criacao.nascer"), button -> submit())
                .bounds(width / 2 - 75, Math.min(height - 28, y), 150, 20)
                .build();
        addRenderableWidget(confirm);
    }

    private EditBox field(int x, int y, String hintKey) {
        Component hint = Component.translatable(hintKey);
        EditBox box = new EditBox(font, x, y, FIELD_WIDTH, 20, hint);
        box.setMaxLength(CharacterName.PART_LIMIT);
        box.setFilter(LETTERS);
        box.setHint(hint.copy().withStyle(ChatFormatting.DARK_GRAY));
        box.setResponder(value -> error = null);
        return box;
    }

    private void submit() {
        confirm.active = false;
        error = null;
        PacketDistributor.sendToServer(resuming
                ? new SubmitNamePayload("", "")
                : new SubmitNamePayload(firstName.getValue().strip(), lastName.getValue().strip()));
    }

    /** O servidor recusou: a tela continua aberta, com o motivo onde a pessoa esta olhando. */
    public void refuse(Component reason) {
        error = reason.copy().withStyle(ChatFormatting.RED);
        if (confirm != null) confirm.active = true;
    }

    /** O personagem existe. Quem fecha e o cliente, depois que o servidor confirmou. */
    public void accepted() {
        onClose();
    }

    @Override
    public void onClose() {
        minecraft.setScreen(null);
    }

    private int top() {
        return Math.max(24, height / 2 - 78);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xEE000000);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);

        int left = (width - PANEL_WIDTH) / 2;
        int y = top();

        graphics.drawCenteredString(font, title, width / 2, y, 0xFFE7C77A);
        y += LINE * 2;

        for (FormattedCharSequence line : intro) {
            graphics.drawString(font, line, left, y, 0xFFBBBBBB);
            y += LINE;
        }
        y += 12;

        if (!resuming) {
            y += 24;
        } else {
            graphics.drawCenteredString(font, Component.literal(data.reserved()), width / 2, y, 0xFFFFFFFF);
            y += 14;
        }

        for (FormattedCharSequence line : rules) {
            graphics.drawString(font, line, left, y, 0xFF777777);
            y += LINE;
        }

        if (error != null) {
            graphics.drawCenteredString(font, error, width / 2, y + 2, 0xFFFF6B6B);
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        boolean enter = keyCode == InputConstants.KEY_RETURN || keyCode == InputConstants.KEY_NUMPADENTER;

        if (enter && confirm != null && confirm.active) {
            submit();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /** Nada atras desta tela esta jogando, e o servidor nao para: pausar seria so esconder o mundo. */
    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }
}
