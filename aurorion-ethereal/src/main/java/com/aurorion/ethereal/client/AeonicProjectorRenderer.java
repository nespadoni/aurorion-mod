package com.aurorion.ethereal.client;

import com.aurorion.ethereal.block.entity.AeonicProjectorBlockEntity;
import com.aurorion.ethereal.ranking.BoardLine;
import com.aurorion.ethereal.ranking.BoardMode;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * O holograma do Projetor Aeonico.
 *
 * <p>Isto e a peca inteira do mod de referencia trazida como estava: moldura pulsante, varredura
 * subindo, faiscas correndo o perimetro, runas flutuando, cabecalho com sombra e as cinco posicoes.
 * A geometria e os tempos sao os mesmos.
 *
 * <p>Duas diferencas, e as duas por performance:
 *
 * <ul>
 *   <li><b>O corpo do projetor nao passa por aqui.</b> No original ele era um modelo animado
 *       desenhado por frame; aqui e o modelo JSON do bloco, que o chunk desenha em batch junto com o
 *       resto do mundo.</li>
 *   <li><b>O texto chega pronto do servidor.</b> Este caminho nao ordena nada, nao consulta casa
 *       nenhuma e nao monta string — mede e desenha (SDD §4.2).</li>
 * </ul>
 *
 * <p>O que continua custando por frame e o que faz o painel parecer vivo, e isso e o ponto do bloco.
 * Por isso o detalhe caro (varredura, faiscas e runas) e cortado a partir de 26 blocos: de longe
 * ninguem le uma runa de 9 milimetros, mas todo mundo paga por ela.
 */
public final class AeonicProjectorRenderer implements BlockEntityRenderer<AeonicProjectorBlockEntity> {
    /** Altura em que o painel nasce, acima da base do bloco. */
    private static final float PANEL_Y = 1.05F;

    /** Escala do texto dentro do painel. */
    private static final float TEXT_SCALE = 0.013F;

    private static final int VIEW_DISTANCE = 48;

    /** Distancia (ao quadrado) a partir da qual os enfeites animados somem. */
    private static final double DETAIL_DISTANCE_SQR = 26.0 * 26.0;

    private static final String[] RUNES = {"ᚠ", "ᚢ", "ᚦ", "ᚨ", "ᚱ", "ᚲ", "ᚷ", "ᚹ", "ᚺ", "ᚾ", "♦", "•"};
    private static final int RUNE_COUNT = 10;
    private static final int RUNE_COLUMNS = 5;

    /** Cor por posicao no ranking. Uma linha que trouxer cor propria (casa) ignora esta tabela. */
    private static final int[] RANK_COLORS = {0xFFFFDD33, 0xFFCCDDFF, 0xFFAABBEE, 0xFF8899DD, 0xFF7788CC};

    private static final int COLOR_HEADER = 0xFFFFD700;
    private static final int COLOR_HEADER_SHADOW = 0x70604000;
    private static final int COLOR_LINE_SHADOW = 0x40000000;
    private static final int COLOR_EMPTY = 0xFF777788;
    private static final int RGB_SEPARATOR = 0x2266BB;
    private static final int RGB_RUNE = 0x55AAFF;

    private static final String SEPARATOR = "===================";

    /** Altura de uma linha do ranking, na escala do texto do painel. */
    private static final float LINE_HEIGHT = 14.0F;

    /** O painel e luz: ele nao escurece de noite nem dentro de uma sala sem tocha. */
    private static final int LIGHT = 0x00F000F0;

    private static final RenderType HOLOGRAM = HologramShard.create();

    private final Font font;

    /**
     * O cabecalho de cada meta, montado uma vez.
     *
     * <p>Sem isto, cada frame de cada projetor visivel fazia tres alocacoes so para escrever o
     * titulo: {@code Component#getString}, {@code toUpperCase} e a concatenacao com o simbolo. O
     * texto so muda quando a meta muda ou quando o jogador troca o idioma do jogo — nunca por frame.
     */
    private final String[] headers = new String[BoardMode.values().length];

    /** Em que idioma {@link #headers} foi montado. Trocar o idioma no jogo invalida o cache. */
    @Nullable
    private Language headerLanguage;

    public AeonicProjectorRenderer(BlockEntityRendererProvider.Context context) {
        font = context.getFont();
    }

    @Override
    public void render(AeonicProjectorBlockEntity projector, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int packedLight, int packedOverlay) {
        float width = projector.holoWidth();
        float height = projector.holoHeight();

        // Relogio de parede, e nao tick do jogo: as animacoes do painel continuam iguais com o
        // servidor engasgando, que e justamente quando alguem esta olhando para ele.
        double time = System.currentTimeMillis() / 1000.0;
        float pulse = (float) (0.65 + 0.35 * Math.sin(time * 1.6));
        float pulseSlow = (float) (0.5 + 0.5 * Math.sin(time * 2.9 + 1.0));
        float blink = (float) (0.5 + 0.5 * Math.sin(time * 4.5 + 0.5));

        pose.pushPose();
        pose.translate(0.5, PANEL_Y, 0.5);

        boolean detailed = billboard(projector, pose, height);

        float left = -width / 2.0F;
        VertexConsumer consumer = buffers.getBuffer(HOLOGRAM);
        Matrix4f matrix = pose.last().pose();

        drawGlow(consumer, matrix, left, width, height, pulse);
        drawBackground(consumer, matrix, left, width, height, pulse);
        if (detailed) {
            drawScanline(consumer, matrix, left, width, height, time, pulse);
        }
        drawEdges(consumer, matrix, left, width, height, pulseSlow);
        drawBorder(consumer, matrix, left, width, height, pulse);
        drawCorners(consumer, matrix, left, width, height, blink);

        if (detailed) {
            float perimeter = 2.0F * (width + height);
            spark(consumer, matrix, left, width, height, (float) (time * 0.65 % perimeter), alpha(185, pulseSlow));
            spark(consumer, matrix, left, width, height,
                    (float) ((time * 0.65 + perimeter * 0.5) % perimeter), alpha(130, pulseSlow));
            drawRunes(pose, buffers, left, width, height, time);
        }

        drawText(projector, pose, buffers, width, height, pulse);
        pose.popPose();
    }

    /**
     * Gira o painel para encarar a camera e responde se vale desenhar os enfeites.
     *
     * <p>Girar aqui, e nao a partir de uma propriedade do bloco, e o que faz o painel ser legivel de
     * qualquer lado — foi assim no original, e e por isso que o bloco nao tem direcao.
     */
    private static boolean billboard(AeonicProjectorBlockEntity projector, PoseStack pose, float height) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.gameRenderer == null) {
            return true;
        }

        Vec3 camera = minecraft.gameRenderer.getMainCamera().getPosition();
        BlockPos pos = projector.getBlockPos();
        double dx = camera.x - (pos.getX() + 0.5);
        double dy = camera.y - (pos.getY() + PANEL_Y + height * 0.5);
        double dz = camera.z - (pos.getZ() + 0.5);

        pose.mulPose(Axis.YP.rotationDegrees((float) Math.toDegrees(Math.atan2(dx, dz))));
        return dx * dx + dy * dy + dz * dz <= DETAIL_DISTANCE_SQR;
    }

    /** Duas auras concentricas atras do painel. */
    private static void drawGlow(VertexConsumer consumer, Matrix4f matrix,
                                 float left, float width, float height, float pulse) {
        float outer = 0.18F;
        quad(consumer, matrix, left - outer, -outer, -0.012F, width + outer * 2.0F, height + outer * 2.0F,
                10, 50, 180, alpha(14, pulse));

        float inner = 0.08F;
        quad(consumer, matrix, left - inner, -inner, -0.008F, width + inner * 2.0F, height + inner * 2.0F,
                20, 80, 220, alpha(30, pulse));
    }

    private static void drawBackground(VertexConsumer consumer, Matrix4f matrix,
                                       float left, float width, float height, float pulse) {
        int a = alpha(178, 0.9 + 0.1 * pulse);
        quadGradientV(consumer, matrix, left, 0.0F, -0.004F, width, height, 4, 8, 30, a, 9, 18, 58, a);
    }

    /** A varredura que sobe pelo painel e reaparece embaixo. */
    private static void drawScanline(VertexConsumer consumer, Matrix4f matrix,
                                     float left, float width, float height, double time, float pulse) {
        float y = (float) (time * 0.22 % (height + 0.25)) - 0.05F;
        float top = Math.max(0.0F, y);
        float bottom = Math.min(height, y + 0.13F);
        if (top < bottom) {
            quad(consumer, matrix, left, top, -0.002F, width, bottom - top, 80, 190, 255, alpha(28, pulse));
        }
    }

    /** Duas faixas laterais que somem para o meio. */
    private static void drawEdges(VertexConsumer consumer, Matrix4f matrix,
                                  float left, float width, float height, float pulse) {
        float w = 0.045F;
        int a = alpha(55, pulse);
        quadGradientH(consumer, matrix, left, 0.0F, -0.001F, w, height, 40, 130, 255, a, 40, 130, 255, 0);
        quadGradientH(consumer, matrix, left + width - w, 0.0F, -0.001F, w, height, 40, 130, 255, 0, 40, 130, 255, a);
    }

    private static void drawBorder(VertexConsumer consumer, Matrix4f matrix,
                                   float left, float width, float height, float pulse) {
        float thickness = 0.021F;
        int a = alpha(218, 0.75 + 0.25 * pulse);
        quad(consumer, matrix, left, 0.0F, 0.0F, width, thickness, 55, 145, 255, a);
        quad(consumer, matrix, left, height - thickness, 0.0F, width, thickness, 55, 145, 255, a);
        quad(consumer, matrix, left, 0.0F, 0.0F, thickness, height, 55, 145, 255, a);
        quad(consumer, matrix, left + width - thickness, 0.0F, 0.0F, thickness, height, 55, 145, 255, a);

        float hair = 0.006F;
        int inner = alpha(80, pulse);
        quad(consumer, matrix, left + thickness, thickness, 0.0F,
                width - thickness * 2.0F, hair, 140, 215, 255, inner);
        quad(consumer, matrix, left + thickness, height - thickness - hair, 0.0F,
                width - thickness * 2.0F, hair, 140, 215, 255, inner);
        quad(consumer, matrix, left + thickness, thickness, 0.0F,
                hair, height - thickness * 2.0F, 140, 215, 255, inner);
        quad(consumer, matrix, left + width - thickness - hair, thickness, 0.0F,
                hair, height - thickness * 2.0F, 140, 215, 255, inner);
    }

    /** Os quatro cantos reforcados, piscando fora de fase com a moldura. */
    private static void drawCorners(VertexConsumer consumer, Matrix4f matrix,
                                    float left, float width, float height, float blink) {
        float size = 0.07F;
        float thickness = 0.021F;
        float bleed = 0.003F;
        int a = alpha(255, 0.7 + 0.3 * blink);
        float right = left + width;

        quad(consumer, matrix, left - bleed, -bleed, 0.0F, size + bleed, thickness + bleed * 2.0F, 210, 240, 255, a);
        quad(consumer, matrix, left - bleed, -bleed, 0.0F, thickness + bleed * 2.0F, size + bleed, 210, 240, 255, a);
        quad(consumer, matrix, right - size - bleed, -bleed, 0.0F, size + bleed, thickness + bleed * 2.0F, 210, 240, 255, a);
        quad(consumer, matrix, right - thickness, -bleed, 0.0F, thickness + bleed * 2.0F, size + bleed, 210, 240, 255, a);
        quad(consumer, matrix, left - bleed, height - thickness, 0.0F, size + bleed, thickness + bleed * 2.0F, 210, 240, 255, a);
        quad(consumer, matrix, left - bleed, height - size - bleed, 0.0F, thickness + bleed * 2.0F, size + bleed, 210, 240, 255, a);
        quad(consumer, matrix, right - size - bleed, height - thickness, 0.0F, size + bleed, thickness + bleed * 2.0F, 210, 240, 255, a);
        quad(consumer, matrix, right - thickness, height - size - bleed, 0.0F, thickness + bleed * 2.0F, size + bleed, 210, 240, 255, a);
    }

    /** Runas subindo em cinco colunas, cada uma no seu ritmo, sumindo nas pontas. */
    private void drawRunes(PoseStack pose, MultiBufferSource buffers,
                           float left, float width, float height, double time) {
        pose.pushPose();
        pose.translate(0.0F, 0.0F, 0.003F);
        float scale = TEXT_SCALE * 0.68F;

        for (int i = 0; i < RUNE_COUNT; i++) {
            double speed = 0.038 + i * 0.0065;
            double fraction = (time * speed + i * 0.19) % 1.2;
            float y = (float) (fraction * (height + 0.2)) - 0.1F;
            if (y < -0.05F || y > height + 0.05F) {
                continue;
            }

            float fade = Mth.clamp(Math.min(y > 0.0F ? y / 0.18F : 0.0F, (height - y) / 0.18F), 0.0F, 1.0F);
            int a = (int) (fade * (70.0 + 45.0 * Math.sin(time * 1.4 + i * 0.9)));
            if (a <= 0) {
                continue;
            }

            float x = left + 0.07F + (i % RUNE_COLUMNS) * ((width - 0.14F) / (RUNE_COLUMNS - 1));
            pose.pushPose();
            pose.translate(x, y, 0.0F);
            pose.scale(scale, -scale, scale);
            String rune = RUNES[(i + (int) (time * 0.12)) % RUNES.length];
            font.drawInBatch(rune, 0.0F, 0.0F, clampAlpha(a) << 24 | RGB_RUNE, false,
                    pose.last().pose(), buffers, Font.DisplayMode.NORMAL, 0, LIGHT);
            pose.popPose();
        }
        pose.popPose();
    }

    /** Cabecalho, separador e as cinco posicoes. */
    private void drawText(AeonicProjectorBlockEntity projector, PoseStack pose, MultiBufferSource buffers,
                          float width, float height, float pulse) {
        pose.pushPose();
        pose.translate(0.0F, height - 0.09F, 0.005F);
        pose.scale(TEXT_SCALE, -TEXT_SCALE, TEXT_SCALE);
        Matrix4f matrix = pose.last().pose();

        int maxWidth = (int) ((width - 0.1F) / TEXT_SCALE);
        BoardMode mode = projector.mode();

        String header = fit(header(mode), maxWidth);
        float headerX = -font.width(header) / 2.0F;
        font.drawInBatch(header, headerX + 0.5F, 0.5F, COLOR_HEADER_SHADOW, false,
                matrix, buffers, Font.DisplayMode.NORMAL, 0, LIGHT);
        font.drawInBatch(header, headerX, 0.0F, COLOR_HEADER, false,
                matrix, buffers, Font.DisplayMode.NORMAL, 0, LIGHT);

        String separator = fit(SEPARATOR, maxWidth);
        int separatorAlpha = clampAlpha(150 + (int) (80.0F * pulse));
        font.drawInBatch(separator, -font.width(separator) / 2.0F, 13.0F,
                separatorAlpha << 24 | RGB_SEPARATOR, false, matrix, buffers, Font.DisplayMode.NORMAL, 0, LIGHT);

        List<BoardLine> lines = projector.renderedLines();
        if (lines.isEmpty()) {
            Component empty = Component.translatable("gui.aurorion_ethereal.projector.empty");
            drawCentered(empty.getVisualOrderText(), 28.0F, COLOR_EMPTY, matrix, buffers, false);
        } else {
            float y = 28.0F;
            for (int i = 0; i < lines.size(); i++) {
                BoardLine line = lines.get(i);
                // Cor propria vence a da posicao: e assim que o ranking de casas sai com a cor de
                // cada casa e o de jogadores continua com o degrade de ouro para azul.
                int color = line.color() == 0 ? RANK_COLORS[Math.min(i, RANK_COLORS.length - 1)] : line.color();

                drawCentered(fit(line.text(), maxWidth), y, color, matrix, buffers, true);
                y += LINE_HEIGHT;
            }
        }
        pose.popPose();
    }

    private void drawCentered(FormattedCharSequence text, float y, int color,
                              Matrix4f matrix, MultiBufferSource buffers, boolean shadow) {
        float x = -font.width(text) / 2.0F;
        if (shadow) {
            font.drawInBatch(text, x + 0.4F, y + 0.4F, COLOR_LINE_SHADOW, false,
                    matrix, buffers, Font.DisplayMode.NORMAL, 0, LIGHT);
        }
        font.drawInBatch(text, x, y, color, false, matrix, buffers, Font.DisplayMode.NORMAL, 0, LIGHT);
    }

    /** O cabecalho da meta, do cache. Ver {@link #headers}. */
    private String header(BoardMode mode) {
        Language language = Language.getInstance();
        if (headerLanguage != language) {
            Arrays.fill(headers, null);
            headerLanguage = language;
        }

        String cached = headers[mode.ordinal()];
        if (cached == null) {
            // Locale.ROOT e nao o do sistema: com a JVM em turco, "MISSOES" viraria "MISSOES" com i
            // sem ponto, e o texto do jogo nao tem nada a ver com o locale da maquina.
            cached = mode.icon() + " " + mode.displayName().getString().toUpperCase(Locale.ROOT);
            headers[mode.ordinal()] = cached;
        }
        return cached;
    }

    /** Devolve o proprio texto quando ele cabe — sem alocar nada no caso comum. */
    private String fit(String text, int maxWidth) {
        return font.width(text) > maxWidth ? font.plainSubstrByWidth(text, maxWidth - 8) + ".." : text;
    }

    /**
     * A mesma poda do cabecalho, para um texto formatado.
     *
     * <p>Ela existe porque a linha e um {@link Component} traduzido no cliente: um nome longo num
     * idioma qualquer nao pode vazar da moldura, e cortar por largura de pixel e a unica medida que
     * vale para uma fonte proporcional.
     *
     * <p>O caminho comum (a linha cabe) sai por {@code getVisualOrderText()}, que o proprio
     * {@code MutableComponent} guarda ate o idioma mudar. Chamar
     * {@code Language.getInstance().getVisualOrder(...)} direto, como estava, pulava esse cache e
     * decompunha o texto de novo a cada frame.
     */
    private FormattedCharSequence fit(Component text, int maxWidth) {
        if (font.width(text) <= maxWidth) {
            return text.getVisualOrderText();
        }
        return Language.getInstance().getVisualOrder(
                FormattedText.composite(font.substrByWidth(text, maxWidth - 8), FormattedText.of("..")));
    }

    // --- Quads ----------------------------------------------------------------------------------

    private static void quad(VertexConsumer consumer, Matrix4f matrix, float x, float y, float z,
                             float w, float h, int r, int g, int b, int a) {
        if (a <= 0) {
            return;
        }
        consumer.addVertex(matrix, x, y, z).setColor(r, g, b, a);
        consumer.addVertex(matrix, x + w, y, z).setColor(r, g, b, a);
        consumer.addVertex(matrix, x + w, y + h, z).setColor(r, g, b, a);
        consumer.addVertex(matrix, x, y + h, z).setColor(r, g, b, a);
    }

    private static void quadGradientV(VertexConsumer consumer, Matrix4f matrix, float x, float y, float z,
                                      float w, float h,
                                      int r0, int g0, int b0, int a0, int r1, int g1, int b1, int a1) {
        consumer.addVertex(matrix, x, y, z).setColor(r0, g0, b0, a0);
        consumer.addVertex(matrix, x + w, y, z).setColor(r0, g0, b0, a0);
        consumer.addVertex(matrix, x + w, y + h, z).setColor(r1, g1, b1, a1);
        consumer.addVertex(matrix, x, y + h, z).setColor(r1, g1, b1, a1);
    }

    private static void quadGradientH(VertexConsumer consumer, Matrix4f matrix, float x, float y, float z,
                                      float w, float h,
                                      int r0, int g0, int b0, int a0, int r1, int g1, int b1, int a1) {
        consumer.addVertex(matrix, x, y, z).setColor(r0, g0, b0, a0);
        consumer.addVertex(matrix, x + w, y, z).setColor(r1, g1, b1, a1);
        consumer.addVertex(matrix, x + w, y + h, z).setColor(r1, g1, b1, a1);
        consumer.addVertex(matrix, x, y + h, z).setColor(r0, g0, b0, a0);
    }

    /** Um ponto de luz correndo o perimetro do painel, com halo. */
    private static void spark(VertexConsumer consumer, Matrix4f matrix,
                              float left, float width, float height, float position, int a) {
        if (a <= 0) {
            return;
        }
        float perimeter = 2.0F * (width + height);
        float p = (position % perimeter + perimeter) % perimeter;

        float x;
        float y;
        if (p <= width) {
            x = left + p;
            y = 0.0F;
        } else if (p <= width + height) {
            x = left + width;
            y = p - width;
        } else if (p <= 2.0F * width + height) {
            x = left + width - (p - width - height);
            y = height;
        } else {
            x = left;
            y = height - (p - 2.0F * width - height);
        }

        float core = 0.042F;
        quad(consumer, matrix, x - core, y - core, 0.0F, core * 2.0F, core * 2.0F, 255, 255, 255, a);
        float halo = core * 2.4F;
        quad(consumer, matrix, x - halo, y - halo, -0.001F, halo * 2.0F, halo * 2.0F, 90, 190, 255, a / 4);
    }

    private static int alpha(int base, double factor) {
        return clampAlpha((int) (base * factor));
    }

    private static int clampAlpha(int a) {
        return Mth.clamp(a, 0, 255);
    }

    /**
     * O holograma sobe acima do bloco e e mais largo que ele, entao a caixa padrao de um block entity
     * (o proprio bloco) faria o painel sumir quando o cubo saisse da tela — olhando para os pes dele,
     * por exemplo.
     */
    @Override
    public AABB getRenderBoundingBox(AeonicProjectorBlockEntity projector) {
        BlockPos pos = projector.getBlockPos();
        double half = projector.holoWidth() / 2.0 + 0.3;
        double top = PANEL_Y + projector.holoHeight() + 0.3;

        return new AABB(
                pos.getX() + 0.5 - half, pos.getY(), pos.getZ() + 0.5 - half,
                pos.getX() + 0.5 + half, pos.getY() + top, pos.getZ() + 0.5 + half);
    }

    @Override
    public int getViewDistance() {
        return VIEW_DISTANCE;
    }

    /**
     * O {@code RenderType} do painel: cor pura, translucido, sem cull e sem escrever profundidade
     * de forma a cortar a si mesmo.
     *
     * <p>A subclasse existe so para alcancar as constantes protegidas de {@link RenderStateShard} —
     * e o mesmo truque do mod de referencia, e nao ha caminho publico para elas.
     */
    private static final class HologramShard extends RenderStateShard {
        private HologramShard() {
            super("aurorion_ethereal_hologram", () -> {
            }, () -> {
            });
        }

        static RenderType create() {
            RenderType.CompositeState state = RenderType.CompositeState.builder()
                    .setShaderState(POSITION_COLOR_SHADER)
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setDepthTestState(LEQUAL_DEPTH_TEST)
                    .setCullState(NO_CULL)
                    .setWriteMaskState(COLOR_DEPTH_WRITE)
                    .createCompositeState(false);

            return RenderType.create("aurorion_ethereal:hologram", DefaultVertexFormat.POSITION_COLOR,
                    VertexFormat.Mode.QUADS, 2048, false, true, state);
        }
    }
}
