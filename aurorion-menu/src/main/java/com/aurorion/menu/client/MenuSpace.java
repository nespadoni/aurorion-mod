package com.aurorion.menu.client;

import com.aurorion.menu.AurorionMenu;
import com.aurorion.menu.config.MenuClientConfig;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import org.lwjgl.glfw.GLFW;

/**
 * O menu como um painel de verdade na frente da pessoa, no mundo, em primeira e em terceira pessoa.
 *
 * <ol>
 *   <li><b>Desenho</b> (camada do HUD): o {@link MenuView} é pintado numa {@link WorldCanvas} de
 *   {@value MenuView#WIDTH}×{@value MenuView#HEIGHT} unidades.</li>
 *   <li><b>No mundo</b> ({@code RenderLevelStageEvent}, fim do nível): à frente dos olhos, um pouco abaixo,
 *   de frente para eles. O painel segue a pessoa: anda junto e, quando a cabeça vira além de
 *   {@code grausAteSeguir}, gira junto.</li>
 *   <li><b>Apontar</b>: a mira (a linha da câmera) cruza o painel; o ponto vira a posição do "mouse" no
 *   menu. Clique esquerdo escolhe, direito volta, a rodinha rola o cartão, Enter confirma a janela, Esc
 *   fecha. Fora do painel, os cliques voltam a ser do jogo.</li>
 * </ol>
 * Os avisos ({@link MenuNotices}) e a janela ○/× sem menu ({@link MenuPrompt}) usam o mesmo caminho, cada um
 * numa tela própria. Fechado, nada disto roda: cada
 * evento sai na primeira linha.
 */
@EventBusSubscriber(modid = AurorionMenu.MOD_ID, value = Dist.CLIENT)
public final class MenuSpace {
    static final ResourceLocation LAYER = AurorionMenu.id("menu_painel");
    /** O painel fica um pouco abaixo da linha dos olhos (blocos), para o mundo continuar à vista. */
    private static final float DROP = 0.22f;

    private static final WorldCanvas CANVAS = new WorldCanvas(MenuView.WIDTH, MenuView.HEIGHT);
    private static final float[] HIT = new float[2];
    private static boolean anchorPending;
    private static float anchorYaw;
    /** Onde a mira toca o menu, em unidades da tela dele; {@code NaN} quando não toca. */
    private static float aimX = Float.NaN, aimY = Float.NaN;

    private MenuSpace() {
    }

    /** Abriu: o painel nasce à frente de para onde a pessoa olha agora. */
    /** O menu fechou: a textura dele sai da memória de vídeo. */
    static void closed() {
        CANVAS.release();
        aimX = aimY = Float.NaN;
    }

    static void opened() {
        anchorPending = true;
        CANVAS.reset();
        aimX = aimY = Float.NaN;
    }

    /** Camada do HUD que não desenha no HUD: pinta o menu e o aviso nas telas do mundo. */
    static final class CanvasLayer implements LayeredDraw.Layer {
        @Override
        public void render(GuiGraphics g, DeltaTracker delta) {
            if (Minecraft.getInstance().options.hideGui) return;
            MenuView view = MenuView.current();
            if (view != null) CANVAS.paint(g, gg -> {
                try {
                    view.render(gg, aimX, aimY);
                } catch (RuntimeException e) {
                    // Uma seção quebrada não pode derrubar o HUD: o menu fecha e o erro fica no log, uma vez.
                    com.aurorion.menu.AurorionMenu.LOGGER.error("Menu do personagem falhou ao desenhar; fechado", e);
                    com.mojang.blaze3d.systems.RenderSystem.disableScissor();
                    MenuView.close(false);
                }
            });
            MenuPrompt.paint(g);
            MenuNotices.paint(g);
        }
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return;
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        MenuNotices.render(event, player, partial);
        MenuPrompt.render(event, player, partial);
        if (!MenuView.isOpen() || mc.options.hideGui) {
            aimX = aimY = Float.NaN;
            return;
        }
        float headYaw = player.getViewYRot(partial);
        if (anchorPending) {
            anchorYaw = headYaw;
            anchorPending = false;
        }
        float limit = MenuClientConfig.FOLLOW_DEGREES.get().floatValue();
        float delta = Mth.wrapDegrees(headYaw - anchorYaw);
        if (Math.abs(delta) > limit) anchorYaw += delta - Math.signum(delta) * limit;
        float dist = MenuClientConfig.DISTANCE.get().floatValue();
        float pitch = (float) Math.toDegrees(Math.atan2(DROP, dist));
        CANVAS.place(player.getEyePosition(partial), event.getCamera().getPosition(), anchorYaw, pitch,
                (float) Math.sqrt(dist * dist + DROP * DROP), MenuClientConfig.WIDTH.get().floatValue());
        if (CANVAS.hit(event.getCamera().getLookVector(), HIT)) {
            aimX = HIT[0];
            aimY = HIT[1];
        } else {
            aimX = aimY = Float.NaN;
        }
        CANVAS.draw(event.getModelViewMatrix(), event.getProjectionMatrix());
    }

    private static boolean aiming() {
        return !Float.isNaN(aimX);
    }

    @SubscribeEvent
    public static void onMouse(InputEvent.MouseButton.Pre event) {
        MenuView view = MenuView.current();
        Minecraft mc = Minecraft.getInstance();
        // A janela ○/× sozinha (convite): com a mira nela, o clique é dela.
        if (mc.screen == null && MenuPrompt.aiming() && event.getAction() == GLFW.GLFW_PRESS && mc.mouseHandler.isMouseGrabbed()) {
            event.setCanceled(true);
            if (event.getButton() == GLFW.GLFW_MOUSE_BUTTON_LEFT) MenuPrompt.click();
            return;
        }
        // Só o apertar: soltar sempre passa, para nenhuma tecla do jogo ficar presa.
        if (view == null || mc.screen != null || mc.player == null || !aiming() || event.getAction() != GLFW.GLFW_PRESS
                || !mc.mouseHandler.isMouseGrabbed()) return;
        // Apontando para o painel o clique é do menu, nunca um ataque ou um uso no mundo.
        event.setCanceled(true);
        if (event.getButton() == GLFW.GLFW_MOUSE_BUTTON_LEFT) view.click(aimX, aimY, 0);
        else if (event.getButton() == GLFW.GLFW_MOUSE_BUTTON_RIGHT) view.back();
    }

    @SubscribeEvent
    public static void onScroll(InputEvent.MouseScrollingEvent event) {
        MenuView view = MenuView.current();
        if (view == null || Minecraft.getInstance().screen != null || !aiming()) return;
        if (view.scroll(aimX, aimY, event.getScrollDeltaY())) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onKey(InputEvent.Key event) {
        MenuView view = MenuView.current();
        if (view == null || Minecraft.getInstance().screen != null || event.getAction() != GLFW.GLFW_PRESS) return;
        view.key(event.getKey(), event.getScanCode(), event.getModifiers());
    }

    /** Esc (a tela de pausa tentando abrir) fecha o menu; outra tela qualquer também o fecha. */
    @SubscribeEvent
    public static void onScreen(ScreenEvent.Opening event) {
        if (!MenuView.isOpen() || event.getNewScreen() == null) return;
        if (event.getNewScreen() instanceof PauseScreen && Minecraft.getInstance().screen == null) event.setCanceled(true);
        MenuView.close(true);
    }

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        if (!MenuView.isOpen()) return;
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || !player.isAlive() || player.isSpectator()) MenuView.close(false);
    }
}
