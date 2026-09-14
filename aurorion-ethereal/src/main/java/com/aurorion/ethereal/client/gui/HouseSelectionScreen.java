package com.aurorion.ethereal.client.gui;

import com.aurorion.ethereal.house.HouseOption;
import com.aurorion.ethereal.network.ChooseHousePayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * A grade de casas.
 *
 * <p>Ela tem dois papeis, e o {@code locked} decide qual: com a cerimonia ligada (o padrao) ela e a
 * <b>vitrine</b> — quem ja tem casa consulta a sua e as das outras no altar, sem poder trocar. Com a
 * cerimonia desligada ela volta a ser a <b>escolha direta</b>, em duas etapas (clicar no card,
 * depois confirmar), porque a escolha e definitiva e um clique solitario num card e perto demais de
 * um clique errado.
 *
 * <p>A tela nao decide nada: ela desenha o que o servidor mandou e devolve um id. Lotacao, trava de
 * re-escolha, permissao de escolha direta e ate a existencia da casa sao reconferidas la — o que
 * aparece aqui e so o retrato do instante em que o altar foi clicado.
 */
public class HouseSelectionScreen extends Screen {
    private static final int CARD_WIDTH = 116;
    private static final int CARD_HEIGHT = 146;
    private static final int GAP = 10;
    private static final int GRID_TOP = 48;
    private static final int BUTTON_WIDTH = 150;

    private static final int COLOR_HINT = 0xFFA0A0A0;
    private static final int COLOR_ERROR = 0xFFFF6B6B;

    private final List<HouseOption> options;
    @Nullable
    private final ResourceLocation current;
    private final boolean locked;

    private final List<HouseCardWidget> cards = new ArrayList<>();

    @Nullable
    private Button confirm;
    @Nullable
    private ResourceLocation selected;
    @Nullable
    private Component feedback;

    /** True entre mandar a escolha e o servidor responder: trava a tela para nao mandar duas vezes. */
    private boolean awaiting;

    private int buttonsY;

    public HouseSelectionScreen(List<HouseOption> options, @Nullable ResourceLocation current, boolean locked) {
        super(Component.translatable(locked
                ? "gui.aurorion_ethereal.house.title_locked"
                : "gui.aurorion_ethereal.house.title"));
        this.options = options;
        this.current = current;
        this.locked = locked;
    }

    @Override
    protected void init() {
        cards.clear();
        confirm = null;

        int columns = Math.max(1, Math.min(options.size(), (this.width - 40) / (CARD_WIDTH + GAP)));
        int rows = (options.size() + columns - 1) / columns;
        int gridWidth = columns * CARD_WIDTH + (columns - 1) * GAP;
        int left = (this.width - gridWidth) / 2;

        for (int i = 0; i < options.size(); i++) {
            HouseOption option = options.get(i);
            ResourceLocation id = option.house().id();

            HouseCardWidget card = new HouseCardWidget(
                    left + (i % columns) * (CARD_WIDTH + GAP),
                    GRID_TOP + (i / columns) * (CARD_HEIGHT + GAP),
                    CARD_WIDTH, CARD_HEIGHT,
                    this.font, option,
                    id.equals(current),
                    !locked && !awaiting,
                    this::pick);

            card.setSelected(id.equals(selected));
            cards.add(card);
            addRenderableWidget(card);
        }

        buttonsY = Math.min(GRID_TOP + rows * (CARD_HEIGHT + GAP) + 10, this.height - 28);

        if (locked) {
            addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> onClose())
                    .bounds(this.width / 2 - BUTTON_WIDTH / 2, buttonsY, BUTTON_WIDTH, 20)
                    .build());
            return;
        }

        confirm = addRenderableWidget(Button.builder(
                        Component.translatable("gui.aurorion_ethereal.house.confirm"), button -> submit())
                .bounds(this.width / 2 - BUTTON_WIDTH - 2, buttonsY, BUTTON_WIDTH, 20)
                .build());
        confirm.active = selected != null && !awaiting;

        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, button -> onClose())
                .bounds(this.width / 2 + 2, buttonsY, BUTTON_WIDTH, 20)
                .build());
    }

    // Os tres metodos abaixo mexem no estado dos widgets em vez de chamar rebuildWidgets(): eles
    // rodam de dentro do clique, e refazer a lista de filhos no meio do proprio evento e a receita
    // de deixar o foco apontando para um widget que nao existe mais.

    private void pick(HouseOption option) {
        if (locked || awaiting) {
            return;
        }
        selected = option.house().id();
        feedback = null;

        for (HouseCardWidget card : cards) {
            card.setSelected(card.option().house().id().equals(selected));
        }
        if (confirm != null) {
            confirm.active = true;
        }
    }

    private void submit() {
        if (selected == null || awaiting) {
            return;
        }
        awaiting = true;
        feedback = Component.translatable("gui.aurorion_ethereal.house.sending");
        setInteractive(false);

        PacketDistributor.sendToServer(new ChooseHousePayload(selected));
    }

    /**
     * Veredito do servidor. Uma tela aberta vale exatamente uma tentativa, portanto qualquer
     * resposta fecha a tela; para tentar novamente o jogador precisa clicar no altar de novo.
     */
    public void onResult(boolean ignoredSuccess, Component message) {
        if (this.minecraft != null && this.minecraft.player != null) {
            // Actionbar, nao chat: com anuncio ligado o chat ja pode receber a mesma noticia.
            this.minecraft.player.displayClientMessage(message, true);
        }
        onClose();
    }

    private void setInteractive(boolean interactive) {
        for (HouseCardWidget card : cards) {
            card.setEnabled(interactive && !locked);
        }
        if (confirm != null) {
            confirm.active = interactive && selected != null;
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        graphics.drawCenteredString(this.font, this.title, this.width / 2, 16, 0xFFFFFFFF);

        Component subtitle = locked
                ? Component.translatable("gui.aurorion_ethereal.house.subtitle_locked")
                : Component.translatable("gui.aurorion_ethereal.house.subtitle");
        graphics.drawCenteredString(this.font, subtitle, this.width / 2, 30, COLOR_HINT);

        if (feedback != null) {
            graphics.drawCenteredString(this.font, feedback, this.width / 2, buttonsY - 14,
                    awaiting ? COLOR_HINT : COLOR_ERROR);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
