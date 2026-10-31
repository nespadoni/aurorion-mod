package com.aurorion.ficha.client;

import com.aurorion.ficha.sheet.Texts;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A roda da ficha, na mesma linguagem da roda de passivas da magia ({@code PassiveWheelScreen}):
 * segurar a tecla da ficha abre, mover o mouse escolhe a fatia, soltar a tecla usa.
 *
 * <ul>
 *   <li><b>Ficha</b>, <b>Dados</b> (a ficha já no diálogo de rolagem livre), <b>Habilidades</b> (a ficha na
 *       aba 5) e, para o mestre, a <b>Mesa do mestre</b>.</li>
 *   <li>Com um pedido do mestre no HUD, o <b>miolo vira o cartão do pedido</b> (quem pediu, o teste, CD e
 *       motivo como o mestre mandou, o tempo que falta num arco) e aparecem <b>Rolar</b> (dourada, no
 *       alto) e <b>Recusar</b> (do lado oposto, longe de um escorregão do mouse).</li>
 *   <li>Soltar a tecla, clique esquerdo ou direito: escolhe. Teclas 1 a 9: direto pela posição. Enter:
 *       rola o pedido (sem pedido, abre a ficha). Esc, ou soltar no miolo: fecha sem fazer nada.</li>
 * </ul>
 *
 * <p>Não pausa o jogo: é uma escolha rápida, no meio da cena. O fundo só escurece, sem o desfoque das
 * telas do vanilla. Tudo é do cliente: a roda só existe enquanto aberta e não manda nada ao servidor
 * até a escolha.
 *
 * <p>As fatias não mudam de lugar debaixo do mouse: um pedido que chega (ou sai) com a roda aberta só
 * reorganiza a roda quando o mouse volta ao miolo; até lá o cartão aparece no centro e Rolar/Recusar,
 * se já estavam, ficam desabilitadas com o motivo. Trocar o pedido mostrado trava Rolar/Recusar por
 * {@value #ARM_MS} ms, para ninguém soltar a tecla no pedido de antes.
 */
public final class FichaRoda extends Screen {
    private enum Kind {
        ROLAR("Rolar", "", true),
        FICHA("Ficha", "minecraft:writable_book", false),
        DADOS("Dados", "", false),
        HABILIDADES("Habilidades", "minecraft:enchanted_book", false),
        MESA("Mesa do mestre", "minecraft:lectern", false),
        RECUSAR("Recusar", "minecraft:barrier", false);

        final String label;
        final String icon;
        final boolean gold;

        Kind(String label, String icon, boolean gold) {
            this.label = label;
            this.icon = icon;
            this.gold = gold;
        }
    }

    private static final int SEGMENTS_PER_SLICE = 24;
    private static final int SEGMENTS_CIRCLE = 64;
    private static final float GAP = 0.03F;
    private static final int SLICE = 0xC0140A26;
    private static final int SLICE_HOVER = 0xE03A2468;
    private static final int SLICE_GOLD = 0xC83A260A;
    private static final int SLICE_GOLD_HOVER = 0xF0705016;
    private static final int SLICE_OFF = 0x900E0818;
    private static final int RIM = 0x906D4BC4;
    private static final int RIM_OFF = 0x503A2E52;
    private static final int CORE = 0xEC120822;
    private static final int CORE_RIM = 0xFF6D4BC4;
    private static final int TRACK = 0x40D7A857;
    private static final long OPEN_MS = 150L;
    private static final long LIFT_MS = 70L;
    private static final long ARM_MS = 450L;
    private static final long DENY_MS = 260L;
    private static final long URGENT_SECONDS = 20L;
    // Paletas do d20 desenhado (base, claro, borda, número), as mesmas cores do D20 da ficha.
    private static final int[] DIE_VIOLET = {0xFF4A2F8F, 0xFF7C5CD6, 0xFF1E1240, 0xFFFFF4DA};
    private static final int[] DIE_GOLD = {0xFF9A6A12, 0xFFF2C14E, 0xFF4A3005, 0xFF2A1700};
    private static final String[] ROLLING = {"Rolando", "Rolando.", "Rolando..", "Rolando..."};

    /** Aberta segurando a tecla: soltar escolhe. Aberta pelo comando de teste: clique escolhe. */
    private final boolean holdMode;
    private final boolean test;
    private final boolean gm;
    private final long openedAt = Util.getMillis();

    private List<Kind> kinds = List.of();
    /** O desenho atual tem Rolar/Recusar (pode diferir do pedido vivo até o mouse voltar ao miolo). */
    private boolean layoutAsk;
    private float[] lift = new float[0];
    private float[] labelScale = new float[0];
    /** Largura que um rótulo pode ocupar na fatia (a corda no raio do meio, com folga). */
    private float labelRoom = 60;
    private int hovered = -1;
    private boolean done;
    private float outer = 100, inner = 46;
    private long lastFrame;
    private long deniedAt;
    private int denied = -1;

    // O pedido mostrado e os textos dele, cortados uma vez (não por quadro).
    @Nullable
    private FichaHud.Ask ask;
    private long askChangedAt;
    private String askKicker = "", askHeadline = "", askGm = "";
    private float headlineScale = 0.9F;
    private List<String> askDetail = List.of();
    private String rollLegend = "", declineLegend = "";
    private long clockFor = -1;
    private String clock = "";

    FichaRoda(boolean holdMode, boolean test, boolean gm) {
        super(Component.literal("Ficha"));
        this.holdMode = holdMode;
        this.test = test;
        this.gm = gm;
        this.ask = FichaHud.currentAsk();
        layout(ask != null);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partial) {
        graphics.fill(0, 0, width, height, 0x58000000);
    }

    // ── Montagem ────────────────────────────────────────────────────────────────

    /** As fatias que se aplicam agora; Recusar fica do lado oposto a Rolar. */
    private void layout(boolean withAsk) {
        List<Kind> out = new ArrayList<>();
        if (withAsk) out.add(Kind.ROLAR);
        out.add(Kind.FICHA);
        out.add(Kind.DADOS);
        out.add(Kind.HABILIDADES);
        if (gm) out.add(Kind.MESA);
        if (withAsk) out.add((out.size() + 2) / 2, Kind.RECUSAR);
        kinds = List.copyOf(out);
        layoutAsk = withAsk;
        lift = new float[kinds.size()];
        hovered = -1;
        measureLabels();
    }

    @Override
    protected void init() {
        // Recalculado a cada init: o vanilla chama de novo ao redimensionar a janela. A roda cabe com a
        // legenda embaixo (≈ 30 px) mesmo em 854x480 na escala 3.
        outer = Mth.clamp(Math.min(width, height - 44) * 0.42F, 56F, 122F);
        // Miolo largo: ele vira o cartão do pedido. A faixa das fatias ainda cabe ícone, nome e linha.
        inner = outer * 0.52F;
        measureLabels();
        prepareAsk();
    }

    /** Nome longo em fatia estreita: encolhe em vez de invadir a vizinha (uma vez por montagem). */
    private void measureLabels() {
        labelScale = new float[kinds.size()];
        if (font == null) return;
        float mid = (inner + outer) / 2;
        float room = (float) (2 * mid * Math.sin(Math.min(Math.PI / 2, slice() / 2))) - 6;
        room = Math.min(room, (outer - inner) * 1.9F);
        labelRoom = room;
        for (int i = 0; i < kinds.size(); i++) {
            labelScale[i] = Math.min(0.9F, room / Math.max(1, font.width(kinds.get(i).label)));
        }
    }

    /** O cartão do pedido no miolo: textos cortados para a corda do círculo em cada linha. */
    private void prepareAsk() {
        clockFor = -1;
        FichaHud.Ask a = ask;
        if (a == null || font == null) {
            askKicker = askHeadline = askGm = rollLegend = declineLegend = "";
            askDetail = List.of();
            return;
        }
        String headline = Texts.askHeadline(a.test());
        askKicker = FichaUi.fitScaled(font, (a.title().isEmpty() ? "O mestre pede" : a.title()).toUpperCase(Locale.ROOT), chord(-22), 0.62F);
        // O teste é o que mais importa: encolhe até 0,62 antes de cortar ("Teste de Furtividade" inteiro).
        headlineScale = Mth.clamp(chord(-12) / Math.max(1, font.width(headline)), 0.62F, 0.9F);
        askHeadline = FichaUi.fitScaled(font, headline, chord(-12), headlineScale);
        askGm = a.gmName().isEmpty() ? "" : FichaUi.fitScaled(font, "por " + a.gmName(), chord(-3), 0.62F);
        askDetail = wrap(a.detail(), chord(11), 0.62F, 2);
        rollLegend = "Rola " + lowerFirst(headline) + " agora: o resultado aparece no HUD.";
        declineLegend = "Recusa o pedido" + (a.gmName().isEmpty() ? "" : " de " + a.gmName()) + ": a mesa do mestre fica sabendo.";
    }

    /** Largura útil do miolo na altura {@code dy} (do centro), com folga da borda. */
    private float chord(float dy) {
        float r = inner - 5;
        return (float) (2 * Math.sqrt(Math.max(0, r * r - dy * dy)));
    }

    private List<String> wrap(String text, float width, float scale, int maxLines) {
        if (text.isEmpty()) return List.of();
        List<FormattedCharSequence> lines = font.split(Component.literal(text), Math.max(20, (int) (width / scale)));
        List<String> out = new ArrayList<>();
        for (int i = 0; i < lines.size() && i < maxLines; i++) out.add(FichaUi.plain(lines.get(i)));
        if (lines.size() > maxLines && !out.isEmpty()) {
            out.set(out.size() - 1, FichaUi.fitScaled(font, out.get(out.size() - 1) + "…", width, scale));
        }
        return out;
    }

    private static String lowerFirst(String text) {
        return text.isEmpty() ? text : text.substring(0, 1).toLowerCase(Locale.ROOT) + text.substring(1);
    }

    // ── Desenho ─────────────────────────────────────────────────────────────────

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partial) {
        long now = Util.getMillis();
        float dt = lastFrame == 0 ? 0 : Math.min(100, now - lastFrame);
        lastFrame = now;
        int cx = width / 2, cy = height / 2;

        // Uma comparação por quadro: o pedido do topo do HUD mudou (chegou, venceu, foi trocado)?
        FichaHud.Ask live = FichaHud.currentAsk();
        if (live != ask) {
            if (live != null && (ask == null || !live.id().equals(ask.id()))) askChangedAt = now;
            ask = live;
            prepareAsk();
        }
        hovered = sliceAt(mouseX - cx, mouseY - cy);
        // Reorganiza só com o mouse no miolo: nada troca de lugar debaixo do cursor.
        if (hovered < 0 && layoutAsk != (live != null)) layout(live != null);

        float open = FichaUi.easeOut((now - openedAt) / (float) OPEN_MS);
        float a = 0.25F + 0.75F * open;
        renderBackground(graphics, mouseX, mouseY, partial);
        graphics.pose().pushPose();
        float scale = 0.86F + 0.14F * open;
        graphics.pose().translate(cx, cy, 0);
        graphics.pose().scale(scale, scale, 1);
        graphics.pose().translate(-cx, -cy, 0);

        graphics.flush();
        int n = kinds.size();
        for (int i = 0; i < n; i++) {
            Kind kind = kinds.get(i);
            boolean on = enabled(kind, now);
            float target = i == hovered && on ? 1F : 0F;
            lift[i] += (target - lift[i]) * Math.min(1F, dt / LIFT_MS);
            float start = startAngle(i), end = start + slice();
            float o = outer + 4 * lift[i];
            int fill = !on ? SLICE_OFF : kind.gold ? (i == hovered ? SLICE_GOLD_HOVER : SLICE_GOLD) : i == hovered ? SLICE_HOVER : SLICE;
            if (i == denied && now - deniedAt < DENY_MS) fill = 0xD0602030;
            ring(graphics, cx, cy, inner, o, start + GAP, end - GAP, FichaUi.alpha(fill, a), SEGMENTS_PER_SLICE);
            // Borda de fora: dourada no destaque (Rolar respira devagar enquanto espera), violeta nas outras.
            int rim = !on ? RIM_OFF : i == hovered ? FichaUi.GOLD_HI
                    : kind.gold ? FichaUi.alpha(FichaUi.GOLD, 0.7F + 0.3F * (float) Math.sin(now / 320.0)) : RIM;
            ring(graphics, cx, cy, o - 2.5F, o, start + GAP, end - GAP, FichaUi.alpha(rim, a), SEGMENTS_PER_SLICE);
        }
        // O miolo: disco escuro com borda violeta; com pedido, o arco do tempo que falta por cima da borda.
        float core = inner - 3;
        ring(graphics, cx, cy, 0, core, 0, Mth.TWO_PI, FichaUi.alpha(CORE, a), SEGMENTS_CIRCLE);
        FichaHud.Ask shown = ask;
        if (shown != null) {
            long total = Math.max(1, shown.expiresAt() - shown.shownAt());
            float rest = Mth.clamp((shown.expiresAt() - now) / (float) total, 0F, 1F);
            boolean urgent = shown.expiresAt() - now <= URGENT_SECONDS * 1000;
            ring(graphics, cx, cy, core - 1.5F, core + 1, 0, Mth.TWO_PI, FichaUi.alpha(TRACK, a), SEGMENTS_CIRCLE);
            if (rest > 0) {
                float top = -Mth.HALF_PI;
                ring(graphics, cx, cy, core - 1.5F, core + 1, top, top + Mth.TWO_PI * rest,
                        FichaUi.alpha(urgent ? FichaUi.WARN : FichaUi.GOLD, a), SEGMENTS_CIRCLE);
            }
        } else {
            ring(graphics, cx, cy, core, core + 1, 0, Mth.TWO_PI, FichaUi.alpha(CORE_RIM, a), SEGMENTS_CIRCLE);
        }

        boolean icons = outer - inner >= 34;
        for (int i = 0; i < n; i++) label(graphics, i, cx, cy, icons, now, a);
        center(graphics, cx, cy, now, a);
        graphics.pose().popPose();
        legend(graphics, cx, cy, now);
    }

    private void label(GuiGraphics g, int i, int cx, int cy, boolean icons, long now, float a) {
        Kind kind = kinds.get(i);
        boolean on = enabled(kind, now);
        double angle = startAngle(i) + slice() / 2;
        float radius = (inner + outer) / 2 + 2 * lift[i];
        int x = cx + (int) Math.round(Math.cos(angle) * radius);
        int y = cy + (int) Math.round(Math.sin(angle) * radius);
        int top = icons ? y - 17 : y - 7;
        if (icons) {
            float pop = 1F + 0.12F * lift[i];
            g.pose().pushPose();
            g.pose().translate(x, top + 8, 0);
            g.pose().scale(pop, pop, 1);
            g.pose().translate(-x, -(top + 8), 0);
            if (kind == Kind.ROLAR || kind == Kind.DADOS) {
                D20.draw(g, font, x, top + 8, 8, kind == Kind.ROLAR && on ? DIE_GOLD : DIE_VIOLET, "20", 1F, false, on ? a : a * 0.5F);
            } else {
                ItemStack stack = FichaUi.icon(kind.icon);
                if (!stack.isEmpty()) g.renderItem(stack, x - 8, top);
            }
            g.pose().popPose();
            top += 18;
        }
        int nameColor = !on ? FichaUi.FAINT : kind.gold || i == hovered ? FichaUi.GOLD_HI : FichaUi.INK;
        FichaUi.textCenter(g, font, kind.label, x, top, FichaUi.alpha(nameColor, a), labelScale.length > i ? labelScale[i] : 0.9F, true);
        String sub = (i + 1) + " · " + sub(kind, now);
        int subColor = !on ? FichaUi.FAINT : kind == Kind.RECUSAR ? FichaUi.BAD : kind.gold ? FichaUi.GOLD : FichaUi.SOFT;
        float subScale = Math.max(0.5F, Math.min(0.62F, labelRoom / Math.max(1, font.width(sub))));
        FichaUi.textCenter(g, font, FichaUi.fitScaled(font, sub, labelRoom, subScale), x, top + 9, FichaUi.alpha(subColor, a), subScale, false);
    }

    /** O miolo: o cartão do pedido, ou o personagem e o que está apontado. */
    private void center(GuiGraphics g, int cx, int cy, long now, float a) {
        FichaHud.Ask shown = ask;
        if (shown != null) {
            FichaUi.textCenter(g, font, askKicker, cx, cy - 24, FichaUi.alpha(FichaUi.GOLD, a), 0.62F, false);
            FichaUi.textCenter(g, font, askHeadline, cx, cy - 15, FichaUi.alpha(FichaUi.GOLD_HI, a), headlineScale, true);
            if (!askGm.isEmpty()) FichaUi.textCenter(g, font, askGm, cx, cy - 5, FichaUi.alpha(FichaUi.FAINT, a), 0.62F, false);
            for (int i = 0; i < askDetail.size(); i++) {
                FichaUi.textCenter(g, font, askDetail.get(i), cx, cy + 3 + i * 7, FichaUi.alpha(FichaUi.SOFT, a), 0.62F, false);
            }
            long seconds = (Math.max(0, shown.expiresAt() - now) + 999) / 1000;
            String bottom;
            int color;
            if (FichaClient.isAnswering(shown.id())) {
                bottom = ROLLING[(int) ((now / 280) % ROLLING.length)];
                color = FichaUi.WARN;
            } else {
                if (seconds != clockFor) {
                    clockFor = seconds;
                    clock = seconds / 60 + ":" + (seconds % 60 < 10 ? "0" : "") + seconds % 60;
                }
                bottom = clock;
                color = seconds <= URGENT_SECONDS ? FichaUi.WARN : FichaUi.FAINT;
            }
            FichaUi.textCenter(g, font, bottom, cx, cy + 19, FichaUi.alpha(color, a), 0.75F, false);
            return;
        }
        FichaUi.face(g, null, cx - 8, cy - 29, 16);
        String name = FichaUi.fitScaled(font, FichaClient.characterName(), chord(-6), 0.9F);
        FichaUi.textCenter(g, font, name, cx, cy - 8, FichaUi.alpha(FichaUi.GOLD_HI, a), 0.9F, true);
        // A fatia apontada; sem nenhuma, "Mestre" para quem é (a fatia da mesa só aparece para ele).
        if (hovered >= 0) {
            FichaUi.textCenter(g, font, FichaUi.fitScaled(font, kinds.get(hovered).label, chord(6), 0.75F), cx, cy + 3,
                    FichaUi.alpha(FichaUi.INK, a), 0.75F, false);
        } else if (gm) {
            FichaUi.textCenter(g, font, "MESTRE", cx, cy + 4, FichaUi.alpha(FichaUi.GOLD, a), 0.62F, false);
        }
        FichaUi.textCenter(g, font, FichaUi.fitScaled(font, "Nenhum pedido", chord(15), 0.62F), cx, cy + 14,
                FichaUi.alpha(FichaUi.FAINT, a), 0.62F, false);
    }

    /** Embaixo da roda: o que a fatia apontada faz (ou por que está desabilitada) e como escolher. */
    private void legend(GuiGraphics g, int cx, int cy, long now) {
        int y = (int) Math.min(cy + outer + 9, height - 21);
        String line;
        int color = FichaUi.SOFT;
        if (hovered >= 0) {
            Kind kind = kinds.get(hovered);
            String why = disabledReason(kind, now);
            line = why.isEmpty() ? legendText(kind) : why;
            if (!why.isEmpty()) color = FichaUi.FAINT;
            else if (kind.gold) color = FichaUi.GOLD_HI;
        } else if (ask != null && !layoutAsk) {
            line = "Pedido novo do mestre: volte ao centro para responder.";
            color = FichaUi.GOLD_HI;
        } else if (ask != null) {
            line = "O mestre pede um teste: Rolar ou Recusar.";
            color = FichaUi.GOLD;
        } else {
            line = "Ficha do personagem";
        }
        FichaUi.textCenter(g, font, FichaUi.fitScaled(font, line, width - 16, 1F), cx, y, color, 1F, true);
        String hint = holdMode ? "Solte numa fatia para escolher · no centro ou Esc cancela"
                : "Clique numa fatia · 1–" + kinds.size() + " · Esc fecha";
        if (layoutAsk && ask != null) hint += " · Enter rola";
        FichaUi.textCenter(g, font, FichaUi.fitScaled(font, hint, width - 16, 0.75F), cx, y + 11, FichaUi.FAINT, 0.75F, false);
    }

    private String sub(Kind kind, long now) {
        return switch (kind) {
            case ROLAR -> ask == null ? "Encerrado" : FichaClient.isAnswering(ask.id()) ? "Rolando…" : "Responder";
            case RECUSAR -> ask == null ? "Encerrado" : "Não rolar";
            case FICHA -> "Abrir";
            case DADOS -> "Rolagem livre";
            case HABILIDADES -> "Aba 5";
            case MESA -> "Pedir testes";
        };
    }

    private String legendText(Kind kind) {
        return switch (kind) {
            case ROLAR -> rollLegend;
            case RECUSAR -> declineLegend;
            case FICHA -> {
                String key = FichaUi.keyName(FichaKeys.OPEN);
                yield "Abre a ficha do personagem" + (key.isEmpty() ? "." : " (o mesmo que tocar " + key + ").");
            }
            case DADOS -> "Abre a ficha já na rolagem livre: 2d6+3, 1d20+5, 4d6kh3…";
            case HABILIDADES -> "Abre a aba Habilidades: magias, profissão, Marca e passivas.";
            case MESA -> "Abre a mesa do mestre: pedir testes aos jogadores por perto.";
        };
    }

    /** Vazio = habilitada; senão, o motivo (vai para a legenda). */
    private String disabledReason(Kind kind, long now) {
        if (kind != Kind.ROLAR && kind != Kind.RECUSAR) return "";
        FichaHud.Ask shown = ask;
        if (shown == null) return "Este pedido terminou (respondido, cancelado ou vencido).";
        if (FichaClient.isAnswering(shown.id())) return "Rolando… o resultado chega ao HUD.";
        if (now - askChangedAt < ARM_MS) return "Pedido novo: confira o cartão no centro.";
        return "";
    }

    private boolean enabled(Kind kind, long now) {
        return disabledReason(kind, now).isEmpty();
    }

    /** Fatia sob o mouse, ou -1 no miolo (soltar ali fecha sem escolher). */
    private int sliceAt(double dx, double dy) {
        if (kinds.isEmpty() || dx * dx + dy * dy < inner * inner * 0.6F) return -1;
        double angle = Math.atan2(dy, dx) - startAngle(0);
        double turn = Math.PI * 2;
        angle = ((angle % turn) + turn) % turn;
        return Math.min(kinds.size() - 1, (int) (angle / slice()));
    }

    /** A primeira fatia começa no alto; com uma só, ela ocupa a roda inteira. */
    private float startAngle(int i) {
        return (float) (-Math.PI / 2 - slice() / 2 + i * slice());
    }

    private float slice() {
        return (float) (Math.PI * 2 / Math.max(1, kinds.size()));
    }

    /** Um arco cheio entre dois raios, em faixa de triângulos. A GUI não tem primitiva de arco. */
    private static void ring(GuiGraphics graphics, int cx, int cy, float inner, float outer, float from, float to, int argb,
                             int segments) {
        if ((argb >>> 24) < 4 || to <= from) return;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        // O culling fica ligado na GUI. Com o y para baixo, a faixa sai em sentido horário e seria
        // descartada como face de trás: desligado só durante o arco.
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        Matrix4f pose = graphics.pose().last().pose();
        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLE_STRIP, DefaultVertexFormat.POSITION_COLOR);
        for (int k = 0; k <= segments; k++) {
            float t = Mth.lerp(k / (float) segments, from, to);
            float cos = Mth.cos(t), sin = Mth.sin(t);
            buffer.addVertex(pose, cx + cos * inner, cy + sin * inner, 0).setColor(argb);
            buffer.addVertex(pose, cx + cos * outer, cy + sin * outer, 0).setColor(argb);
        }
        BufferUploader.drawWithShader(buffer.buildOrThrow());
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    // ── Entrada ─────────────────────────────────────────────────────────────────

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (holdMode && FichaKeys.OPEN.matches(keyCode, scanCode)) {
            choose(hovered, true);
            return true;
        }
        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        int digit = keyCode >= GLFW.GLFW_KEY_1 && keyCode <= GLFW.GLFW_KEY_9 ? keyCode - GLFW.GLFW_KEY_1
                : keyCode >= GLFW.GLFW_KEY_KP_1 && keyCode <= GLFW.GLFW_KEY_KP_9 ? keyCode - GLFW.GLFW_KEY_KP_1 : -1;
        if (digit >= 0 && digit < kinds.size()) {
            choose(digit, false);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            int index = kinds.indexOf(layoutAsk ? Kind.ROLAR : Kind.FICHA);
            if (index >= 0) choose(index, false);
            return true;
        }
        // Teclas de pedido atribuídas pelo jogador valem aqui também (as de jogo não chegam a telas).
        if (layoutAsk && !FichaKeys.ROLL_REQUEST.isUnbound() && FichaKeys.ROLL_REQUEST.matches(keyCode, scanCode)) {
            choose(kinds.indexOf(Kind.ROLAR), false);
            return true;
        }
        if (layoutAsk && !FichaKeys.DECLINE_REQUEST.isUnbound() && FichaKeys.DECLINE_REQUEST.matches(keyCode, scanCode)) {
            choose(kinds.indexOf(Kind.RECUSAR), false);
            return true;
        }
        // No modo de clique, apertar a tecla da ficha de novo fecha a roda.
        if (!holdMode && FichaKeys.OPEN.matches(keyCode, scanCode)) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        // Tecla da ficha trocada para um botão do mouse.
        if (holdMode && FichaKeys.OPEN.matchesMouse(button)) {
            choose(hovered, true);
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT || button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            choose(sliceAt(mouseX - width / 2.0, mouseY - height / 2.0), false);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /**
     * Escolhe a fatia {@code index} (-1 = miolo: fecha). Desabilitada: soltando a tecla, só fecha; num
     * clique ou número, a roda fica aberta, a fatia pisca e a legenda diz o porquê.
     */
    private void choose(int index, boolean release) {
        if (done) return;
        if (index < 0 || index >= kinds.size()) {
            done = true;
            onClose();
            return;
        }
        Kind kind = kinds.get(index);
        long now = Util.getMillis();
        if (!enabled(kind, now)) {
            if (release) {
                done = true;
                onClose();
                return;
            }
            denied = index;
            deniedAt = now;
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_BASS.value(), 0.8F, 0.4F));
            return;
        }
        done = true;
        FichaHud.Ask shown = ask;
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.1F, 0.35F));
        // Fecha antes: a ficha e a mesa abrem por cima do jogo, não por cima da roda.
        onClose();
        switch (kind) {
            case FICHA -> FichaClient.requestOpen(FichaClient.OPEN_SHEET, test);
            case DADOS -> FichaClient.requestOpen(FichaClient.OPEN_DICE, test);
            case HABILIDADES -> FichaClient.requestOpen(FichaClient.OPEN_SKILLS, test);
            case MESA -> {
                if (test) FichaTestSession.openTable();
                else FichaClient.requestTable();
            }
            case ROLAR -> FichaClient.answer(shown, true);
            case RECUSAR -> {
                if (FichaClient.answer(shown, false) && minecraft != null && minecraft.player != null && shown != null) {
                    minecraft.player.displayClientMessage(Component.literal("Pedido" + (shown.gmName().isEmpty() ? "" : " de " + shown.gmName())
                            + " recusado."), true);
                }
            }
        }
    }
}
