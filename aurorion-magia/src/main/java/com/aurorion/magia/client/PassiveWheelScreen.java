package com.aurorion.magia.client;

import com.aurorion.magia.network.PassiveMenuPayloads;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * A roda de passivas, no espirito da roda de magias do Iron's: segurar a tecla abre, mover o mouse
 * escolhe a fatia, soltar a tecla usa. As de interruptor (Presenca Aterradora) mostram se estao
 * ligadas e alternam; as de usar (Salto Quantico, Olhar Anacronico, Bolso do Vazio) fazem a acao.
 *
 * <ul>
 *   <li>Soltar a tecla, ou clique esquerdo: escolhe. Com Shift, ou clique direito: a acao alternativa.</li>
 *   <li>Teclas 1 a 9: escolhe direto pela posicao.</li>
 *   <li>Esc, ou soltar no meio da roda: fecha sem fazer nada.</li>
 * </ul>
 *
 * <p>Nao pausa o jogo: e uma escolha rapida, no meio da cena. O fundo so escurece, sem o desfoque das
 * telas do vanilla, para o mundo continuar a vista.
 */
public final class PassiveWheelScreen extends Screen {
    private static final int SEGMENTS_PER_SLICE = 24;
    private static final float GAP = 0.03F;
    private static final int SLICE = 0xB0141020;
    private static final int SLICE_HOVER = 0xD8463470;
    private static final int RIM_ON = 0xFFF2C94C;
    private static final int RIM_OFF = 0x60FFFFFF;

    private final List<PassiveMenuPayloads.Entry> entries;
    /** Aberta segurando a tecla: soltar escolhe. Aberta depois de soltar (atraso de rede): clique escolhe. */
    private final boolean holdMode;
    private int hovered = -1;
    private boolean done;
    private float outer = 100, inner = 38;

    PassiveWheelScreen(List<PassiveMenuPayloads.Entry> entries, boolean holdMode) {
        super(Component.translatable("aurorion_magia.roda.titulo"));
        this.entries = List.copyOf(entries);
        this.holdMode = holdMode;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partial) {
        graphics.fill(0, 0, width, height, 0x50000000);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partial) {
        renderBackground(graphics, mouseX, mouseY, partial);
        int cx = width / 2, cy = height / 2;
        hovered = sliceAt(mouseX - cx, mouseY - cy);

        graphics.flush();
        int n = entries.size();
        for (int i = 0; i < n; i++) {
            float start = startAngle(i), end = start + slice();
            ring(graphics, cx, cy, inner, outer, start + GAP, end - GAP, i == hovered ? SLICE_HOVER : SLICE);
            PassiveMenuPayloads.Entry entry = entries.get(i);
            // Borda dourada: ligada, ou em uso agora (fora do corpo, dentro do bolso).
            ring(graphics, cx, cy, outer - 2.5F, outer, start + GAP, end - GAP, entry.active() ? RIM_ON : RIM_OFF);
        }

        for (int i = 0; i < n; i++) label(graphics, i, cx, cy, (inner + outer) / 2);

        // Centro: o nome da fatia apontada, ou o titulo da roda.
        Component center = hovered >= 0 ? name(entries.get(hovered)) : title;
        graphics.drawCenteredString(font, center, cx, cy - 4, 0xFFFFFFFF);
        Component hint = (holdMode
                ? Component.translatable("aurorion_magia.roda.dica_segurar", PassiveKey.USE.getTranslatedKeyMessage())
                : Component.translatable("aurorion_magia.roda.dica_clique")).withStyle(ChatFormatting.GRAY);
        graphics.drawCenteredString(font, hint, cx, (int) (cy + outer + 10), 0xFFFFFFFF);
    }

    private void label(GuiGraphics graphics, int i, int cx, int cy, float radius) {
        PassiveMenuPayloads.Entry entry = entries.get(i);
        double angle = startAngle(i) + slice() / 2;
        int x = cx + (int) Math.round(Math.cos(angle) * radius);
        int y = cy + (int) Math.round(Math.sin(angle) * radius);
        Component name = name(entry);
        Component state = Component.translatable(entry.toggleable()
                ? (entry.active() ? "aurorion_magia.roda.ligada" : "aurorion_magia.roda.desligada")
                : (entry.active() ? "aurorion_magia.roda.em_uso" : "aurorion_magia.roda.usar"))
                .withStyle(entry.active() ? ChatFormatting.GOLD : ChatFormatting.GRAY);
        // Nome longo em fatia estreita: encolhe em vez de invadir a vizinha.
        float room = (float) (2 * radius * Math.sin(Math.min(Math.PI / 2, slice() / 2))) - 6;
        float scale = Math.min(1F, room / Math.max(1, font.width(name)));
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        graphics.pose().scale(scale, scale, 1);
        graphics.drawCenteredString(font, name, 0, -9, 0xFFFFFFFF);
        graphics.pose().popPose();
        graphics.drawCenteredString(font, Component.literal((i + 1) + " · ").withStyle(ChatFormatting.DARK_GRAY)
                .append(state), x, y + 2, 0xFFFFFFFF);
    }

    private static Component name(PassiveMenuPayloads.Entry entry) {
        return Component.translatable("passive.aurorion_magia." + entry.key()).withStyle(ChatFormatting.GOLD);
    }

    @Override
    protected void init() {
        // Recalculado a cada init: o vanilla chama de novo ao redimensionar a janela.
        outer = Math.min(110, Math.min(width, height) * .36F);
        inner = outer * .38F;
    }

    /** Fatia sob o mouse, ou -1 no miolo (soltar ali fecha sem escolher). */
    private int sliceAt(double dx, double dy) {
        if (entries.isEmpty() || dx * dx + dy * dy < inner * inner * .5F) return -1;
        double angle = Math.atan2(dy, dx) - startAngle(0);
        double turn = Math.PI * 2;
        angle = ((angle % turn) + turn) % turn;
        return Math.min(entries.size() - 1, (int) (angle / slice()));
    }

    /** A primeira fatia comeca no alto; com uma so, ela ocupa a roda inteira. */
    private float startAngle(int i) {
        return (float) (-Math.PI / 2 - slice() / 2 + i * slice());
    }

    private float slice() {
        return (float) (Math.PI * 2 / Math.max(1, entries.size()));
    }

    /** Um arco cheio entre dois raios, em faixa de triangulos. A GUI nao tem primitiva de arco. */
    private static void ring(GuiGraphics graphics, int cx, int cy, float inner, float outer, float from, float to, int argb) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        // O culling fica ligado na GUI. Com o y para baixo, a faixa sai em sentido horario e seria descartada
        // como face de tras: desligado so durante o arco, em vez de depender da ordem dos vertices.
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        Matrix4f pose = graphics.pose().last().pose();
        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLE_STRIP, DefaultVertexFormat.POSITION_COLOR);
        for (int k = 0; k <= SEGMENTS_PER_SLICE; k++) {
            float a = Mth.lerp(k / (float) SEGMENTS_PER_SLICE, from, to);
            float cos = Mth.cos(a), sin = Mth.sin(a);
            buffer.addVertex(pose, cx + cos * inner, cy + sin * inner, 0).setColor(argb);
            buffer.addVertex(pose, cx + cos * outer, cy + sin * outer, 0).setColor(argb);
        }
        BufferUploader.drawWithShader(buffer.buildOrThrow());
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    // --- Entrada --------------------------------------------------------------------------------

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (holdMode && PassiveKey.USE.matches(keyCode, scanCode)) {
            choose(hovered, hasShiftDown());
            return true;
        }
        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode >= GLFW.GLFW_KEY_1 && keyCode <= GLFW.GLFW_KEY_9) {
            int index = keyCode - GLFW.GLFW_KEY_1;
            if (index < entries.size()) {
                choose(index, hasShiftDown());
                return true;
            }
        }
        // No modo de clique, apertar a tecla de passiva de novo fecha a roda.
        if (!holdMode && PassiveKey.USE.matches(keyCode, scanCode)) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        // Tecla de passiva trocada para um botao do mouse.
        if (holdMode && PassiveKey.USE.matchesMouse(button)) {
            choose(hovered, hasShiftDown());
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT || button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            int index = sliceAt(mouseX - width / 2.0, mouseY - height / 2.0);
            choose(index, button == GLFW.GLFW_MOUSE_BUTTON_RIGHT || hasShiftDown());
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void choose(int index, boolean alternate) {
        if (done) return;
        done = true;
        if (index >= 0 && index < entries.size()) {
            PacketDistributor.sendToServer(new PassiveMenuPayloads.Select(entries.get(index).key(), alternate));
        }
        onClose();
    }
}
