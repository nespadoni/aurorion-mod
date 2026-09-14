package com.aurorion.ethereal.client.gui;

import com.aurorion.ethereal.ceremony.CeremonyQuestionView;
import com.aurorion.ethereal.network.CeremonyAnswerPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A Cerimonia de Vinculacao: uma pergunta por vez, alternativas como botoes.
 *
 * <p>As perguntas ja vieram todas no pacote de abertura, entao trocar de pergunta e instantaneo — o
 * jogador nunca fica olhando uma tela parada esperando a rede. O servidor continua sendo a
 * autoridade: cada resposta vai com o indice da pergunta, e uma resposta fora de ordem e descartada
 * do outro lado.
 *
 * <p>Esc fecha, de proposito. A cerimonia continua viva no servidor e clicar no altar de novo devolve
 * o jogador exatamente a pergunta em que ele parou; prender a tela so criaria um jeito de alguem
 * ficar travado por causa de um telefone que tocou.
 */
public class CeremonyScreen extends Screen {
    private static final int PANEL_WIDTH = 320;
    private static final int OPTION_HEIGHT = 20;
    private static final int OPTION_GAP = 4;
    private static final int QUESTION_TOP = 74;

    private static final int COLOR_TITLE = 0xFFFFD98A;
    private static final int COLOR_QUESTION = 0xFFFFFFFF;
    private static final int COLOR_HINT = 0xFF9A9AB0;
    private static final int COLOR_PROGRESS_ON = 0xFFFFD98A;
    private static final int COLOR_PROGRESS_OFF = 0x40FFFFFF;

    private static final String[] LETTERS = {"A", "B", "C", "D", "E", "F", "G", "H"};

    private final List<CeremonyQuestionView> questions;
    private int index;

    /** Preenchido quando o servidor fecha a cerimonia; a tela vira o aviso de "aguarde". */
    @Nullable
    private Component closingMessage;

    /** O aviso de fechamento ja quebrado em linhas — o texto nao muda enquanto ele esta na tela. */
    private List<FormattedCharSequence> wrappedClosing = List.of();

    /**
     * Quebra de linha do enunciado atual, refeita so na troca de pergunta.
     *
     * <p>Mesmo motivo do {@code aurorion-talk} (SDD §4.2): {@code Font#split} a 60 fps para um texto
     * que so muda quando alguem clica e trabalho jogado fora.
     */
    private List<FormattedCharSequence> wrapped = List.of();

    public CeremonyScreen(List<CeremonyQuestionView> questions, int startAt) {
        super(Component.translatable("gui.aurorion_ethereal.ceremony.title"));
        this.questions = questions;
        this.index = Math.max(0, Math.min(startAt, Math.max(0, questions.size() - 1)));
    }

    @Override
    protected void init() {
        int left = this.width / 2 - PANEL_WIDTH / 2;

        if (closingMessage != null) {
            // Aqui e nao no render: o aviso fica parado na tela, e refazer a quebra de linha dele a
            // 60 fps e o mesmo desperdicio que esta classe evita para o enunciado das perguntas.
            wrappedClosing = this.font.split(closingMessage, PANEL_WIDTH);

            addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> onClose())
                    .bounds(this.width / 2 - 60, this.height / 2 + 30, 120, 20)
                    .build());
            return;
        }

        if (questions.isEmpty()) {
            return;
        }

        CeremonyQuestionView question = questions.get(index);
        wrapped = this.font.split(question.question(), PANEL_WIDTH - 16);

        int y = QUESTION_TOP + wrapped.size() * 12 + 14;
        for (int i = 0; i < question.options().size(); i++) {
            int option = i;
            Component label = Component.literal(letter(i) + ")  ").append(question.options().get(i));

            addRenderableWidget(Button.builder(label, button -> answer(option))
                    .bounds(left, y, PANEL_WIDTH, OPTION_HEIGHT)
                    .build());
            y += OPTION_HEIGHT + OPTION_GAP;
        }
    }

    private static String letter(int index) {
        return index < LETTERS.length ? LETTERS[index] : String.valueOf(index + 1);
    }

    /**
     * Manda a resposta e avanca sem esperar confirmacao.
     *
     * <p>Avancar na hora e seguro porque o cliente nao decide nada: se o servidor recusar a resposta
     * (indice fora de ordem, cerimonia ja encerrada), ele simplesmente nao registra, e a cerimonia
     * nao fecha. O que o jogador ve avancando e a leitura dele, nao o estado do servidor.
     */
    private void answer(int option) {
        PacketDistributor.sendToServer(new CeremonyAnswerPayload(index, option));

        if (index + 1 < questions.size()) {
            index++;
            rebuildWidgets();
        } else {
            // Ultima respondida: a tela espera o servidor confirmar o fechamento.
            clearWidgets();
        }
    }

    /** Chamada quando o servidor encerra a cerimonia — por conclusao ou por cancelamento da staff. */
    public void close(Component message) {
        this.closingMessage = message;
        rebuildWidgets();
    }

    /**
     * Os botoes sao desenhados aqui, e nao por {@code super.render}: {@code Screen#render} pinta o
     * fundo do vanilla <b>antes</b> dos widgets, e ele apagaria o fundo proprio desta tela.
     */
    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackdrop(graphics);
        drawContent(graphics);

        for (Renderable widget : this.renderables) {
            widget.render(graphics, mouseX, mouseY, partialTick);
        }
    }

    private void drawContent(GuiGraphics graphics) {
        if (closingMessage != null) {
            graphics.drawCenteredString(this.font, this.title, this.width / 2, this.height / 2 - 30, COLOR_TITLE);

            int y = this.height / 2;
            for (FormattedCharSequence line : wrappedClosing) {
                graphics.drawString(this.font, line, this.width / 2 - this.font.width(line) / 2,
                        y, COLOR_QUESTION, false);
                y += 12;
            }
            return;
        }

        if (questions.isEmpty()) {
            return;
        }

        graphics.drawCenteredString(this.font, this.title, this.width / 2, 26, COLOR_TITLE);
        graphics.drawCenteredString(this.font,
                Component.translatable("gui.aurorion_ethereal.ceremony.subtitle"), this.width / 2, 40, COLOR_HINT);

        drawProgress(graphics, 54);

        int y = QUESTION_TOP;
        for (FormattedCharSequence line : wrapped) {
            graphics.drawString(this.font, line, this.width / 2 - this.font.width(line) / 2, y, COLOR_QUESTION, false);
            y += 12;
        }
    }

    /** Uma marca por pergunta: mostra o tamanho do ritual sem numero nenhum na frente. */
    private void drawProgress(GuiGraphics graphics, int y) {
        int total = questions.size();
        int pipWidth = 14;
        int left = this.width / 2 - (total * pipWidth) / 2;

        for (int i = 0; i < total; i++) {
            int x = left + i * pipWidth;
            graphics.fill(x + 2, y, x + pipWidth - 2, y + 2, i <= index ? COLOR_PROGRESS_ON : COLOR_PROGRESS_OFF);
        }
    }

    /** Escurece o mundo atras sem o desfoque do menu: a cerimonia acontece no altar, nao numa pausa. */
    private void renderBackdrop(GuiGraphics graphics) {
        graphics.fillGradient(0, 0, this.width, this.height, 0xE0090912, 0xF0050508);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
