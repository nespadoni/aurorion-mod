package com.aurorion.core.client.menu;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Desenho liso do menu do personagem (e de quem desenha no mesmo estilo: HUD do duelo, seções da ficha):
 * formas e ícones brancos em alta resolução (gerados por {@code aurorion-core/tools/generate_ui.py},
 * amostrados com filtro linear pelo {@code "blur"} do .mcmeta), tingidos na hora, em coordenadas
 * fracionárias — a animação desliza em vez de pular de pixel em pixel. Texto na fonte TTF
 * {@code aurorion_core:menu} (Rajdhani, OFL), não na fonte pixelada.
 *
 * <p>Só cliente: nada aqui pode ser tocado pelo servidor dedicado.
 *
 * <p>Paleta Aurorion (a mesma da ficha): violeta profundo, marfim e dourado.
 */
public final class MenuUi {
    private static float canvasScale;
    private static int canvasHeight;
    public static final ResourceLocation FONT = ResourceLocation.fromNamespaceAndPath("aurorion_core", "menu");

    public static final int IVORY = 0xF3EAD8, IVORY_HI = 0xFFFAEE, VIOLET = 0x3A1F68, VIOLET_DEEP = 0x1A0B2E,
            VIOLET_HI = 0xC59AFF, GOLD = 0xD7A857, GOLD_HI = 0xFFD76A, INK = 0x2A1748, FAINT = 0x8C8098,
            HP_OK = 0x5BE38A, HP_MID = 0xFFC94A, HP_LOW = 0xFF5A5A, PE = 0x6FA8FF;

    public static final ResourceLocation CIRCLE = tex("circle"), RING = tex("ring"), GLOW = tex("glow"), PANEL = tex("panel"),
            PANEL_LINE = tex("panel_line"), POINTER = tex("pointer"), HP_FILL = tex("hp_fill"), HP_FRAME = tex("hp_frame"),
            HOLO = tex("holo");

    private MenuUi() {
    }

    public static ResourceLocation tex(String name) {
        return ResourceLocation.fromNamespaceAndPath("aurorion_core", "textures/gui/menu/" + name + ".png");
    }

    public static ResourceLocation icon(String name) {
        return tex("icon_" + name);
    }

    // --- primitivas --------------------------------------------------------------------------------

    /** Textura inteira esticada em {@code w×h} na posição fracionária, com a cor (ARGB) por cima. */
    public static void image(GuiGraphics g, ResourceLocation texture, float x, float y, float w, float h, int texW, int texH, int argb) {
        region(g, texture, x, y, w, h, 0, 0, texW, texH, texW, texH, argb);
    }

    /** Um pedaço {@code (u, v, uw, vh)} da textura esticado em {@code w×h}. */
    public static void region(GuiGraphics g, ResourceLocation texture, float x, float y, float w, float h,
                              int u, int v, int uw, int vh, int texW, int texH, int argb) {
        if (w <= 0 || h <= 0 || FastColor.ARGB32.alpha(argb) == 0) return;
        RenderSystem.enableBlend();
        // O alfa acumula (ONE, 1 - alfa) em vez de ser trocado: na textura do painel 3D o resultado sai
        // pré-multiplicado e as bordas lisas não viram buracos. Na tela comum o alfa nem é lido.
        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        RenderSystem.setShaderColor(FastColor.ARGB32.red(argb) / 255f, FastColor.ARGB32.green(argb) / 255f,
                FastColor.ARGB32.blue(argb) / 255f, FastColor.ARGB32.alpha(argb) / 255f);
        var pose = g.pose();
        pose.pushPose();
        pose.translate(x, y, 0);
        pose.scale(w / uw, h / vh, 1);
        g.blit(texture, 0, 0, u, v, uw, vh, texW, texH);
        pose.popPose();
        RenderSystem.setShaderColor(1, 1, 1, 1);
        // Devolve o estado padrão: o HUD e as telas de outros mods desenham depois de nós.
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

    public static void circle(GuiGraphics g, float cx, float cy, float radius, int argb) {
        image(g, CIRCLE, cx - radius, cy - radius, radius * 2, radius * 2, 128, 128, argb);
    }

    public static void ring(GuiGraphics g, float cx, float cy, float radius, int argb) {
        image(g, RING, cx - radius, cy - radius, radius * 2, radius * 2, 128, 128, argb);
    }

    public static void glow(GuiGraphics g, float cx, float cy, float radius, int argb) {
        image(g, GLOW, cx - radius, cy - radius, radius * 2, radius * 2, 128, 128, argb);
    }

    public static void iconAt(GuiGraphics g, String name, float cx, float cy, float size, int argb) {
        image(g, icon(name), cx - size / 2, cy - size / 2, size, size, 128, 128, argb);
    }

    /** Cartão arredondado (9-slice da textura 64x64 com cantos de 24). {@code r} é o raio na tela. */
    public static void panel(GuiGraphics g, float x, float y, float w, float h, float r, int argb) {
        nine(g, PANEL, x, y, w, h, r, argb);
    }

    public static void outline(GuiGraphics g, float x, float y, float w, float h, float r, int argb) {
        nine(g, PANEL_LINE, x, y, w, h, r, argb);
    }

    private static void nine(GuiGraphics g, ResourceLocation tex, float x, float y, float w, float h, float r, int argb) {
        r = Math.min(r, Math.min(w, h) / 2);
        final int c = 24, mid = 64 - 2 * c, t = 64;
        float iw = w - 2 * r, ih = h - 2 * r;
        region(g, tex, x, y, r, r, 0, 0, c, c, t, t, argb);
        region(g, tex, x + r, y, iw, r, c, 0, mid, c, t, t, argb);
        region(g, tex, x + w - r, y, r, r, t - c, 0, c, c, t, t, argb);
        region(g, tex, x, y + r, r, ih, 0, c, c, mid, t, t, argb);
        region(g, tex, x + r, y + r, iw, ih, c, c, mid, mid, t, t, argb);
        region(g, tex, x + w - r, y + r, r, ih, t - c, c, c, mid, t, t, argb);
        region(g, tex, x, y + h - r, r, r, 0, t - c, c, c, t, t, argb);
        region(g, tex, x + r, y + h - r, iw, r, c, t - c, mid, c, t, t, argb);
        region(g, tex, x + w - r, y + h - r, r, r, t - c, t - c, c, c, t, t, argb);
    }

    public static void pointer(GuiGraphics g, float x, float y, float size, int argb) {
        image(g, POINTER, x, y - size / 2, size, size, 32, 32, argb);
    }

    /** Linha fina (horizontal ou vertical) com espessura fracionária. */
    public static void line(GuiGraphics g, float x1, float y1, float x2, float y2, float thickness, int argb) {
        float x = Math.min(x1, x2), y = Math.min(y1, y2);
        float w = Math.max(thickness, Math.abs(x2 - x1)), h = Math.max(thickness, Math.abs(y2 - y1));
        region(g, PANEL, x, y, w, h, 24, 24, 16, 16, 64, 64, argb);
    }

    /**
     * Barra de vida no formato da interface (paralelogramo). {@code frac} 0–1 enche da esquerda; a moldura
     * vem por cima.
     */
    public static void bar(GuiGraphics g, float x, float y, float w, float h, float frac, int fill, int back, int frame) {
        image(g, HP_FILL, x, y, w, h, 512, 48, back);
        frac = Math.max(0, Math.min(1, frac));
        if (frac > 0) {
            int cut = Math.max(1, Math.round(512 * frac));
            region(g, HP_FILL, x, y, w * frac, h, 0, 0, cut, 48, 512, 48, fill);
        }
        image(g, HP_FRAME, x, y, w, h, 512, 48, frame);
    }

    /** Barra de recurso reta e arredondada (vida, stamina, sanidade, PE na ficha). */
    public static void resource(GuiGraphics g, float x, float y, float w, float h, float frac, int fill, float alpha) {
        panel(g, x, y, w, h, h / 2, argb(0x000000, .35f * alpha));
        frac = Math.max(0, Math.min(1, frac));
        if (frac > 0) panel(g, x, y, Math.max(h, w * frac), h, h / 2, argb(fill, alpha));
    }

    /** Selo: texto curto num cartão pequeno com a cor dada. Devolve a largura usada. */
    public static float chip(GuiGraphics g, String label, float x, float y, int back, int ink, float alpha) {
        var c = text(label);
        float w = font().width(c) + 10;
        panel(g, x, y, w, 12, 6, argb(back, alpha * .9f));
        draw(g, c, x + 5, y + 2.5f, ink, alpha);
        return w;
    }

    /** Recorta o desenho seguinte a um retângulo (listas com rolagem). Feche com {@link #endClip}. */
    public static void clip(GuiGraphics g, float x, float y, float w, float h) {
        if (canvasScale > 0) {
            // Desenhando na textura do painel: o recorte é em pixels dela (origem embaixo), não da janela.
            RenderSystem.enableScissor((int) Math.floor(x * canvasScale), (int) Math.floor(canvasHeight - (y + h) * canvasScale),
                    (int) Math.ceil(w * canvasScale), (int) Math.ceil(h * canvasScale));
            return;
        }
        g.enableScissor((int) Math.floor(x), (int) Math.floor(y), (int) Math.ceil(x + w), (int) Math.ceil(y + h));
    }

    public static void endClip(GuiGraphics g) {
        if (canvasScale > 0) RenderSystem.disableScissor();
        else g.disableScissor();
    }

    /**
     * Daqui até {@link #endCanvas} o desenho vai para a textura do painel 3D ({@code scale} pixels por unidade,
     * {@code pixelHeight} de altura), e não para a janela. Só o recorte precisa saber.
     */
    public static void beginCanvas(float scale, int pixelHeight) {
        canvasScale = scale;
        canvasHeight = pixelHeight;
    }

    public static void endCanvas() {
        canvasScale = 0;
    }

    /** Desenhando na textura do painel (o menu no mundo), e não na janela? */
    public static boolean onCanvas() {
        return canvasScale > 0;
    }

    public static String fit(String text, float maxWidth) {
        var font = font();
        if (font.width(text(text)) <= maxWidth) return text;
        String s = text;
        while (s.length() > 1 && font.width(text(s + "…")) > maxWidth) s = s.substring(0, s.length() - 1);
        return s + "…";
    }

    /**
     * O boneco do personagem (o modelo de verdade, com a skin e o que veste) de pé no retângulo dado, virando
     * o rosto para {@code (lookX, lookY)} como no inventário. Sem recorte da janela: funciona também na
     * textura do painel 3D.
     */
    public static void character(GuiGraphics g, LivingEntity entity, float x1, float y1, float x2, float y2, float scale,
                                 float lookX, float lookY) {
        float cx = (x1 + x2) / 2f, cy = (y1 + y2) / 2f;
        float ax = (float) Math.atan((cx - lookX) / 40f), ay = (float) Math.atan((cy - lookY) / 40f);
        Quaternionf pose = new Quaternionf().rotateZ((float) Math.PI);
        Quaternionf camera = new Quaternionf().rotateX(ay * 20 * ((float) Math.PI / 180f));
        pose.mul(camera);
        float bodyRot = entity.yBodyRot, yRot = entity.getYRot(), xRot = entity.getXRot();
        float headO = entity.yHeadRotO, head = entity.yHeadRot;
        entity.yBodyRot = 180 + ax * 20;
        entity.setYRot(180 + ax * 40);
        entity.setXRot(-ay * 20);
        entity.yHeadRot = entity.getYRot();
        entity.yHeadRotO = entity.getYRot();
        try {
            float size = entity.getScale();
            Vector3f offset = new Vector3f(0, entity.getBbHeight() / 2f + 0.0625f * size, 0);
            InventoryScreen.renderEntityInInventory(g, cx, cy, scale / size, offset, pose, camera, entity);
        } finally {
            // Se uma camada de cosmético lançar no meio, o vanilla não desfaz isto sozinho.
            Minecraft.getInstance().getEntityRenderDispatcher().setRenderShadow(true);
            entity.yBodyRot = bodyRot;
            entity.setYRot(yRot);
            entity.setXRot(xRot);
            entity.yHeadRotO = headO;
            entity.yHeadRot = head;
        }
    }

    // --- texto -------------------------------------------------------------------------------------

    public static MutableComponent text(String s) {
        return Component.literal(s).withStyle(Style.EMPTY.withFont(FONT));
    }

    public static MutableComponent text(Component c) {
        return c.copy().withStyle(st -> st.withFont(FONT));
    }

    public static Font font() {
        return Minecraft.getInstance().font;
    }

    public static void draw(GuiGraphics g, Component c, float x, float y, int rgb, float alpha) {
        int a = Math.round(Math.max(0, Math.min(1, alpha)) * 255);
        if (a < 5) return;
        var pose = g.pose();
        pose.pushPose();
        pose.translate(x, y, 0);
        g.drawString(font(), c, 0, 0, (a << 24) | (rgb & 0xFFFFFF), false);
        pose.popPose();
    }

    public static void drawCentered(GuiGraphics g, Component c, float cx, float y, int rgb, float alpha) {
        draw(g, c, cx - font().width(c) / 2f, y, rgb, alpha);
    }

    public static int argb(int rgb, float alpha) {
        return (Math.round(Math.max(0, Math.min(1, alpha)) * 255) << 24) | (rgb & 0xFFFFFF);
    }

    public static int mix(int a, int b, float t) {
        t = Math.max(0, Math.min(1, t));
        int r = Math.round(FastColor.ARGB32.red(a) + (FastColor.ARGB32.red(b) - FastColor.ARGB32.red(a)) * t);
        int gg = Math.round(FastColor.ARGB32.green(a) + (FastColor.ARGB32.green(b) - FastColor.ARGB32.green(a)) * t);
        int bl = Math.round(FastColor.ARGB32.blue(a) + (FastColor.ARGB32.blue(b) - FastColor.ARGB32.blue(a)) * t);
        return (r << 16) | (gg << 8) | bl;
    }

    /** Desaceleração com um leve passo além (o "pouso" dos círculos). */
    public static float easeOutBack(float t) {
        t = Math.max(0, Math.min(1, t));
        float c1 = 1.4f, c3 = c1 + 1;
        return 1 + c3 * (float) Math.pow(t - 1, 3) + c1 * (float) Math.pow(t - 1, 2);
    }

    public static float easeOut(float t) {
        t = Math.max(0, Math.min(1, t));
        return 1 - (1 - t) * (1 - t) * (1 - t);
    }
}
