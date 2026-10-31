package com.aurorion.menu.client;

import com.aurorion.core.client.menu.MenuUi;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;

import java.util.function.Consumer;

/**
 * Uma "tela" pendurada no mundo: desenha-se nela com o {@link MenuUi} como num HUD de {@code width×height}
 * unidades (fundo transparente), e ela aparece como um quad na frente dos olhos. O menu e os avisos usam uma
 * cada.
 *
 * <p>A textura sai pré-multiplicada (o {@code MenuUi} acumula o alfa) e vai ao mundo com {@code ONE, 1 - alfa},
 * por cima de tudo: parede perto, a própria pessoa em terceira pessoa ou um mob no meio não a escondem.
 */
final class WorldCanvas {
    final int width, height;
    /** Os cantos do último quadro, relativos à câmera. */
    final Vector3f tl = new Vector3f(), tr = new Vector3f(), bl = new Vector3f(), br = new Vector3f();
    private final Matrix4f ortho;
    private final Vector3f u = new Vector3f(), v = new Vector3f(), n = new Vector3f(), tmp = new Vector3f();
    @Nullable private TextureTarget target;
    private boolean drawn;

    WorldCanvas(int width, int height) {
        this.width = width;
        this.height = height;
        this.ortho = new Matrix4f().setOrtho(0, width, height, 0, 1000, 21000);
    }

    /** Pinta (na camada do HUD): limpa a textura, desenha com {@code painter} e volta para a janela. */
    void paint(GuiGraphics g, Consumer<GuiGraphics> painter) {
        Minecraft mc = Minecraft.getInstance();
        int scale = mc.getWindow().getWidth() >= 2200 ? 3 : 2;
        int pw = width * scale, ph = height * scale;
        g.flush();
        if (target == null) {
            target = new TextureTarget(pw, ph, true, Minecraft.ON_OSX);
            target.setFilterMode(GL11.GL_LINEAR);
        } else if (target.width != pw || target.height != ph) {
            target.resize(pw, ph, Minecraft.ON_OSX);
        }
        target.setClearColor(0, 0, 0, 0);
        target.clear(Minecraft.ON_OSX);
        target.bindWrite(true);
        RenderSystem.backupProjectionMatrix();
        RenderSystem.setProjectionMatrix(ortho, VertexSorting.ORTHOGRAPHIC_Z);
        // O NeoForge empurra cada camada do HUD +200 em z; com muitas camadas o desenho sairia do volume do ortho
        // da tela. Aqui a pose e a visão começam do zero, no meio do volume.
        Matrix4fStack view = RenderSystem.getModelViewStack();
        view.pushMatrix();
        view.translation(0, 0, -11000);
        RenderSystem.applyModelViewMatrix();
        g.pose().pushPose();
        g.pose().setIdentity();
        MenuUi.beginCanvas(scale, ph);
        try {
            painter.accept(g);
            g.flush();
            drawn = true;
        } catch (RuntimeException e) {
            // Um erro no desenho (o boneco de um mod de cosméticos, por exemplo) não derruba o jogo nem deixa
            // estado de GL preso: recorte, sombra e luz voltam ao normal, e esta tela some até a próxima vez.
            com.aurorion.menu.AurorionMenu.LOGGER.error("Tela do menu no mundo falhou ao desenhar", e);
            RenderSystem.disableScissor();
            mc.getEntityRenderDispatcher().setRenderShadow(true);
            com.mojang.blaze3d.platform.Lighting.setupFor3DItems();
            drawn = false;
        } finally {
            MenuUi.endCanvas();
            g.pose().popPose();
            view.popMatrix();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.restoreProjectionMatrix();
            mc.getMainRenderTarget().bindWrite(true);
        }
    }

    void reset() {
        drawn = false;
    }

    /** Some da memória de vídeo (a textura do menu chega a ~20 MB); recriada na próxima pintura. */
    void release() {
        drawn = false;
        if (target != null) {
            target.destroyBuffers();
            target = null;
        }
    }

    /**
     * Posiciona o quad: centro a {@code dist} dos olhos na direção ({@code yaw}, {@code pitch}) em graus,
     * perpendicular a essa direção (de frente para os olhos), {@code w} blocos de largura.
     */
    void place(Vec3 eye, Vec3 camera, float yaw, float pitch, float dist, float w) {
        float y = yaw * Mth.DEG_TO_RAD, p = pitch * Mth.DEG_TO_RAD;
        float fx = -Mth.sin(y) * Mth.cos(p), fy = -Mth.sin(p), fz = Mth.cos(y) * Mth.cos(p);
        float rx = -Mth.cos(y), rz = -Mth.sin(y);
        // cima = direita × frente
        float ux = -rz * fy, uy = rz * fx - rx * fz, uz = rx * fy;
        float hw = w / 2, hh = w * height / width / 2;
        float cx = (float) (eye.x - camera.x) + fx * dist;
        float cy = (float) (eye.y - camera.y) + fy * dist;
        float cz = (float) (eye.z - camera.z) + fz * dist;
        tl.set(cx - rx * hw + ux * hh, cy + uy * hh, cz - rz * hw + uz * hh);
        tr.set(cx + rx * hw + ux * hh, cy + uy * hh, cz + rz * hw + uz * hh);
        bl.set(cx - rx * hw - ux * hh, cy - uy * hh, cz - rz * hw - uz * hh);
        br.set(cx + rx * hw - ux * hh, cy - uy * hh, cz + rz * hw - uz * hh);
    }

    /**
     * Onde a linha {@code look} (saindo da câmera) toca a tela, em unidades dela; {@code null} fora.
     * Escreve em {@code out} para não alocar.
     */
    boolean hit(Vector3f look, float[] out) {
        tr.sub(tl, u);
        bl.sub(tl, v);
        u.cross(v, n);
        float denom = look.dot(n);
        if (Math.abs(denom) < 1e-6f) return false;
        float t = tl.dot(n) / denom;
        if (t <= 0) return false;
        look.mul(t, tmp).sub(tl);
        float s = tmp.dot(u) / u.lengthSquared();
        float q = tmp.dot(v) / v.lengthSquared();
        if (s < 0 || s > 1 || q < 0 || q > 1) return false;
        out[0] = s * width;
        out[1] = q * height;
        return true;
    }

    /** Desenha o quad no mundo (fim do nível), com as matrizes do quadro. */
    void draw(Matrix4f modelViewMatrix, Matrix4f projection) {
        if (!drawn || target == null) return;
        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        modelView.set(modelViewMatrix);
        RenderSystem.applyModelViewMatrix();
        RenderSystem.backupProjectionMatrix();
        RenderSystem.setProjectionMatrix(projection, VertexSorting.DISTANCE_TO_ORIGIN);
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, target.getColorTextureId());
        RenderSystem.setShaderColor(1, 1, 1, 1);
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        RenderSystem.disableCull();
        RenderSystem.disableDepthTest();
        BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        // A textura do framebuffer é de baixo para cima: o topo da tela é v = 1.
        b.addVertex(tl.x, tl.y, tl.z).setUv(0, 1);
        b.addVertex(bl.x, bl.y, bl.z).setUv(0, 0);
        b.addVertex(br.x, br.y, br.z).setUv(1, 0);
        b.addVertex(tr.x, tr.y, tr.z).setUv(1, 1);
        BufferUploader.drawWithShader(b.buildOrThrow());
        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.restoreProjectionMatrix();
        modelView.popMatrix();
        RenderSystem.applyModelViewMatrix();
    }
}
