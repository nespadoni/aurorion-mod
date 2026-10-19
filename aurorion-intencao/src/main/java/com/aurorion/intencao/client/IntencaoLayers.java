package com.aurorion.intencao.client;

import com.aurorion.core.client.ScreenFog;
import com.aurorion.intencao.AurorionIntencao;
import com.aurorion.intencao.network.ScenePayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

import java.util.List;

/**
 * As duas camadas de tela da batalha. Nenhuma e {@code Screen}: a pessoa continua com chat, voz e
 * camera — so o mundo em volta muda.
 *
 * <ul>
 *   <li>{@link Fog}, abaixo do HUD: o ar do plano, para quem usa shader pack (sem shader, a neblina
 *       do vanilla ja faz isso).</li>
 *   <li>{@link Scene}, acima de tudo: a linha discreta do topo, o chamado, a frase do fragmento, o
 *       veredito e as palpebras.</li>
 * </ul>
 */
public final class IntencaoLayers {
    private IntencaoLayers() {
    }

    public static final class Fog implements LayeredDraw.Layer {
        public static final ResourceLocation ID = AurorionIntencao.id("neblina");

        @Override
        public void render(GuiGraphics graphics, DeltaTracker delta) {
            if (!ClientIntencao.inPlane() || ClientIntencao.scene == null) return;
            ScreenFog.draw(graphics, ClientIntencao.scene.fog(), ScreenFog.weightFor(ClientIntencao.screenFogStrength()));
        }
    }

    public static final class Scene implements LayeredDraw.Layer {
        public static final ResourceLocation ID = AurorionIntencao.id("cena");
        private static final int PALE = 0xE6E1FF;

        @Override
        public void render(GuiGraphics graphics, DeltaTracker delta) {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player == null || minecraft.options.hideGui && ClientIntencao.call == null
                    && ClientIntencao.verdict == null) return;
            float partial = delta.getGameTimeDeltaPartialTick(false);
            float now = ClientIntencao.ticks + partial;
            Font font = minecraft.font;
            int width = graphics.guiWidth(), height = graphics.guiHeight();

            if (ClientIntencao.inPlane() && ClientIntencao.verdict == null && !minecraft.options.hideGui) hud(graphics, font, now);
            ClientIntencao.Moment phrase = ClientIntencao.phrase;
            if (phrase != null) phrase(graphics, font, phrase, now - phrase.start(), width, height);
            ClientIntencao.Moment call = ClientIntencao.call;
            if (call != null && now - call.start() < ClientIntencao.CALL_TICKS + 4) call(graphics, font, call, now - call.start(), width, height);
            ClientIntencao.Moment verdict = ClientIntencao.verdict;
            if (verdict != null && now - verdict.start() < ClientIntencao.VERDICT_TICKS + 4) {
                verdict(graphics, font, verdict, now - verdict.start(), width, height);
            }
            eyelids(graphics, ClientIntencao.curtain(partial), width, height);
        }

        /** Topo da tela: quem esta na mesa e ha quanto tempo. Nada de placar — as regras ficam na mesa. */
        private static void hud(GuiGraphics graphics, Font font, float now) {
            ClientIntencao.Scene scene = ClientIntencao.scene;
            if (scene == null) return;
            int width = graphics.guiWidth();
            float appear = Mth.clamp((now - scene.startTick()) / 40F, 0, 1);
            graphics.drawCenteredString(font, scene.heading(), width / 2, 5, alpha(0xCFC6FF, .85F * appear));
            graphics.drawCenteredString(font, scene.names(), width / 2, 16, alpha(0xFFFFFF, appear));
            long seconds = Math.max(0, (long) (now - scene.startTick()) / 20);
            Component footer = Component.translatable("aurorion_intencao.hud.mesa",
                    String.format("%d:%02d", seconds / 60, seconds % 60));
            graphics.drawCenteredString(font, footer, width / 2, 27, alpha(0x8E89A8, .9F * appear));
        }

        /** "A Intencao te chama": escurece, os nomes surgem frente a frente, o coracao acelera. */
        private static void call(GuiGraphics graphics, Font font, ClientIntencao.Moment call, float t, int width, int height) {
            float dim = Mth.clamp(t / 50F, 0, 1);
            ScreenFog.draw(graphics, 0x07040F, .9F * dim);
            graphics.fill(0, 0, width, height, alpha(0x05030A, .45F * dim));
            float text = Mth.clamp((t - 10) / 20F, 0, 1);
            if (text > .02F) {
                graphics.drawCenteredString(font, Component.translatable(call.text()).withStyle(ChatFormatting.ITALIC),
                        width / 2, (int) (height * .28F), alpha(PALE, text));
            }
            float names = Mth.clamp((t - 28) / 18F, 0, 1);
            if (names <= .02F) return;
            List<ScenePayload.Member> members = call.members();
            int y = (int) (height * .45F);
            if (members.size() == 2) {
                // Quem recebe fica sempre a esquerda: "eu" contra "o outro".
                int self = Math.max(0, call.self());
                ScenePayload.Member left = members.get(self), right = members.get(1 - Math.min(1, self));
                float slide = (1 - names) * 30;
                big(graphics, font, Component.literal(left.name()), (int) (width * .28F - slide), y, left.color(), names, width * .4F);
                big(graphics, font, Component.literal(right.name()), (int) (width * .72F + slide), y, right.color(), names, width * .4F);
                graphics.drawCenteredString(font, Component.literal("×"), width / 2, y + 2, alpha(0x8E89A8, names));
            } else {
                MutableComponent all = Component.empty();
                for (int i = 0; i < members.size(); i++) {
                    if (i > 0) all.append(Component.literal("  ·  ").withStyle(ChatFormatting.DARK_GRAY));
                    all.append(Component.literal(members.get(i).name()).withColor(members.get(i).color()));
                }
                big(graphics, font, all, width / 2, y, 0xFFFFFF, names, width - 32F);
            }
        }

        private static void phrase(GuiGraphics graphics, Font font, ClientIntencao.Moment phrase, float t, int width, int height) {
            float a = Math.min(Mth.clamp(t / 10F, 0, 1), Mth.clamp((100 - t) / 20F, 0, 1));
            if (a <= .02F) return;
            List<FormattedCharSequence> lines = font.split(Component.translatable(phrase.text())
                    .withStyle(ChatFormatting.ITALIC), (int) (width * .7F));
            int y = (int) (height * .68F);
            for (FormattedCharSequence line : lines) {
                graphics.drawCenteredString(font, line, width / 2, y, alpha(0xE8E0FF, a));
                y += font.lineHeight + 2;
            }
        }

        /** O veredito, visto de dentro: clarao na cor da Casa, vidro rachando, ou tudo virando cinza. */
        private static void verdict(GuiGraphics graphics, Font font, ClientIntencao.Moment verdict, float t, int width, int height) {
            int self = verdict.self();
            int color = self >= 0 && self < verdict.members().size() ? verdict.members().get(self).color() : 0xFFFFFF;
            int titleColor;
            switch (verdict.outcome()) {
                case WIN -> {
                    if (t < 14) {
                        float flash = 1 - t / 14;
                        graphics.fill(0, 0, width, height, alpha(color, flash * flash * .65F));
                    }
                    graphics.fill(0, 0, width, height, alpha(color, .07F));
                    titleColor = ClientIntencao.lerp(color, 0xFFFFFF, .35F);
                }
                case LOSE -> {
                    graphics.fill(0, 0, width, height, alpha(0x0A0A12, .3F));
                    cracks(graphics, width, height, Mth.clamp(t / 8F, 0, 1), verdict.start());
                    titleColor = 0xA8A8B4;
                }
                default -> {
                    float ash = Mth.clamp(t / 40F, 0, 1);
                    graphics.fill(0, 0, width, height, alpha(0x3A3A40, .35F * ash));
                    ashes(graphics, width, height, t, verdict.start());
                    titleColor = 0xC9C6CF;
                }
            }
            float a = Mth.clamp((t - 8) / 14F, 0, 1);
            if (a > .02F) {
                big(graphics, font, Component.translatable(verdict.text()), width / 2, (int) (height * .40F), titleColor, a, width - 32F);
            }
        }

        /** Um traco da rachadura: de (x0, y0), {@code length} pixels no {@code angle}; aparece quando o crescimento passa de {@code at}. */
        private record Crack(float x0, float y0, float angle, float length, float at) {
        }

        private static List<Crack> cracks = List.of();
        private static long cracksSeed = Long.MIN_VALUE;
        private static int cracksWidth, cracksHeight;

        /**
         * Rachaduras saindo de um ponto perto do centro. A geometria e sorteada uma vez por veredito (e por
         * tamanho de tela), nao a cada quadro: assim ela nao treme, e o quadro so desenha.
         */
        private static void cracks(GuiGraphics graphics, int width, int height, float grow, long seed) {
            if (seed != cracksSeed || width != cracksWidth || height != cracksHeight) {
                cracks = buildCracks(width, height, seed);
                cracksSeed = seed;
                cracksWidth = width;
                cracksHeight = height;
            }
            int line = alpha(0xEDEDF5, .85F), shade = alpha(0x000000, .5F);
            for (Crack crack : cracks) {
                if (crack.at() >= grow) continue;
                segment(graphics, crack, 1, shade);
                segment(graphics, crack, 0, line);
            }
        }

        private static List<Crack> buildCracks(int width, int height, long seed) {
            RandomSource random = RandomSource.create(seed * 31 + 7);
            float cx = width * (.42F + random.nextFloat() * .16F), cy = height * (.38F + random.nextFloat() * .2F);
            int rays = 9;
            List<Crack> out = new java.util.ArrayList<>(rays * 14);
            for (int r = 0; r < rays; r++) {
                double angle = (r + random.nextFloat() * .6) / rays * Math.PI * 2;
                float x = cx, y = cy;
                int segments = 7 + random.nextInt(5);
                for (int s = 0; s < segments; s++) {
                    angle += (random.nextFloat() - .5F) * .7F;
                    float length = 10 + random.nextFloat() * 22;
                    float at = s / (float) segments;
                    out.add(new Crack(x, y, (float) angle, length, at));
                    float nx = x + (float) Math.cos(angle) * length, ny = y + (float) Math.sin(angle) * length;
                    if (random.nextFloat() < .3F) {
                        out.add(new Crack(nx, ny, (float) (angle + (random.nextBoolean() ? .9 : -.9)), length * .5F, at));
                    }
                    x = nx;
                    y = ny;
                }
            }
            return List.copyOf(out);
        }

        /** Um retangulo de 1 px girado no angulo do traco: uma chamada de {@code fill}, nao uma por pixel. */
        private static void segment(GuiGraphics graphics, Crack crack, float offset, int color) {
            graphics.pose().pushPose();
            graphics.pose().translate(crack.x0() + offset, crack.y0() + offset, 0);
            graphics.pose().mulPose(com.mojang.math.Axis.ZP.rotation(crack.angle()));
            graphics.fill(0, 0, Math.round(crack.length()), 1, color);
            graphics.pose().popPose();
        }

        /** Cinzas caindo devagar: o plano se desfaz para os dois. */
        private static void ashes(GuiGraphics graphics, int width, int height, float t, long seed) {
            RandomSource random = RandomSource.create(seed * 17 + 3);
            for (int i = 0; i < 70; i++) {
                float x = random.nextFloat() * width;
                float speed = 1.2F + random.nextFloat() * 2.4F;
                float y = (random.nextFloat() * height + t * speed) % height;
                int size = random.nextFloat() < .2F ? 2 : 1;
                float a = .35F + random.nextFloat() * .4F;
                graphics.fill((int) x, (int) y, (int) x + size, (int) y + size, alpha(0xB9B6C2, a * Mth.clamp(t / 20F, 0, 1)));
            }
        }

        /** Palpebras: duas faixas pretas que se encontram no meio, com borda suave. */
        private static void eyelids(GuiGraphics graphics, float closure, int width, int height) {
            if (closure <= .003F) return;
            float eased = closure * closure * (3 - 2 * closure);
            int lid = (int) (height * .5F * eased);
            int feather = Math.max(4, height / 12);
            graphics.fill(0, 0, width, lid, 0xFF000000);
            graphics.fill(0, height - lid, width, height, 0xFF000000);
            graphics.fillGradient(0, lid, width, Math.min(height, lid + feather), alpha(0, eased), 0);
            graphics.fillGradient(0, Math.max(0, height - lid - feather), width, height - lid, 0, alpha(0, eased));
            if (eased > .98F) graphics.fill(0, 0, width, height, 0xFF000000);
        }

        private static void big(GuiGraphics graphics, Font font, Component text, int x, int y, int color, float a, float maxWidth) {
            float scale = Math.min(2.2F, maxWidth / Math.max(1, font.width(text)));
            graphics.pose().pushPose();
            graphics.pose().translate(x, y, 0);
            graphics.pose().scale(scale, scale, 1);
            graphics.drawCenteredString(font, text, 0, -font.lineHeight / 2, alpha(color, a));
            graphics.pose().popPose();
        }

        private static int alpha(int rgb, float value) {
            // Texto com alfa abaixo de 4 sai opaco no vanilla; 4 e o minimo "quase invisivel".
            return Mth.clamp(Math.round(value * 255), 4, 255) << 24 | rgb & 0xFFFFFF;
        }
    }
}
