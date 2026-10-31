package com.aurorion.ficha.client;

import com.aurorion.ficha.AurorionFicha;
import com.tesseraui.TesseraInputState;
import com.tesseraui.TesseraModel;
import com.tesseraui.TesseraPanel;
import com.tesseraui.TesseraTemplate;
import com.tesseraui.TesseraTemplateRenderer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * O que as telas e o HUD da ficha dividem: montar painéis do Tessera em posição fixa, escapar texto,
 * cortar e quebrar linhas na medida da fonte do Tessera, e o desenho direto (barras, retrato, texto
 * em escala) que fica por cima das molduras.
 *
 * <h2>Por que painéis soltos</h2>
 * O Tessera 1.1 não soma a altura de uma coluna dentro de uma linha nem respeita espaçador ao lado de
 * texto longo (ver {@code NpcScreen} do {@code aurorion-profissoes}). Cada bloco aqui é um painel
 * próprio, posicionado pelo Java: o layout não depende de nenhum dos dois.
 *
 * <h2>Escala da fonte</h2>
 * No Tessera, {@code font-size: 7px} é a fonte do Minecraft em escala 1 (a escala é
 * {@code tamanho / 7}). Um texto em 6px ocupa 6/7 da largura medida pelo {@link Font}.
 */
final class FichaUi {
    static final int GOLD = 0xFFD7A857;
    static final int GOLD_HI = 0xFFFFE9A7;
    static final int INK = 0xFFF3EAD8;
    static final int SOFT = 0xFFBFB3D6;
    static final int FAINT = 0xFF8C8098;
    static final int VIOLET = 0xFF8B5CF6;
    static final int OK = 0xFF4ADE80;
    static final int BAD = 0xFFF87171;
    static final int WARN = 0xFFFBBF24;
    static final int CRIT = 0xFFFFD76A;

    /** Recursos: cor da barra e o brilho da borda superior. */
    static final int VIDA = 0xFFDC3C50, VIDA_HI = 0xFFFF8A95;
    static final int STAMINA = 0xFFE0A030, STAMINA_HI = 0xFFFFD27A;
    static final int SANIDADE = 0xFF6D7CF5, SANIDADE_HI = 0xFFB4BEFF;
    static final int PE = 0xFF2BB8C8, PE_HI = 0xFF8EF0F7;
    static final int TEMP = 0xFFFFE0E6;

    private static String css = "";

    private FichaUi() {
    }

    static String css() {
        if (css.isEmpty()) css = resource("ui/ficha.css");
        return css;
    }

    /** Recarrega o CSS (hot reload do Tessera chama o {@code init} de novo). */
    static void reloadCss() {
        css = "";
    }

    /** Um painel Tessera em posição fixa dentro de {@code parent}, com o CSS já interpretado ({@link #template}). */
    static void add(TesseraPanel parent, String html, Map<String, Runnable> clicks, Map<String, Consumer<String>> submits,
                    Map<String, TesseraInputState> inputs, int x, int y, int w, int h) {
        if (w <= 0 || h <= 0) return;
        TesseraPanel panel = TesseraTemplateRenderer.build(template(html), TesseraModel.EMPTY,
                clicks, submits, inputs, x, y, w, h);
        panel.layout();
        // TesseraUI 1.1: addAbsolute(widget, top, left, right, bottom); MIN_VALUE deixa right/bottom sem âncora.
        parent.addAbsolute(panel, y, x, Integer.MIN_VALUE, Integer.MIN_VALUE);
    }

    /** Escapa texto vindo de fora (nomes, títulos, descrições) antes de entrar no HTML do Tessera. */
    static String esc(String text) {
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '&' -> out.append("&amp;");
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                case '"' -> out.append("&quot;");
                case '\'' -> out.append("&#39;");
                case '{' -> out.append("&#123;");
                case '}' -> out.append("&#125;");
                default -> {
                    if (!Character.isISOControl(c)) out.append(c);
                }
            }
        }
        return out.toString();
    }

    /** Largura, em px de GUI, de um texto do Tessera em {@code size}px. */
    static int width(Font font, String text, int size) {
        return (font.width(text) * size + 6) / 7;
    }

    /** Corta o texto para caber em {@code width} px de GUI com a fonte do Tessera em {@code size}px. */
    static String fit(Font font, String text, int width, int size) {
        int room = Math.max(1, width * 7 / Math.max(1, size));
        if (font.width(text) <= room) return text;
        return font.plainSubstrByWidth(text, Math.max(1, room - font.width("…"))) + "…";
    }

    /** Quebra em linhas que cabem em {@code width}; a última leva "…" se sobrou texto. */
    static List<String> wrap(Font font, String text, int width, int size, int maxLines) {
        int room = Math.max(20, width * 7 / Math.max(1, size));
        List<String> out = new ArrayList<>();
        List<FormattedCharSequence> lines = font.split(Component.literal(text), room);
        for (int i = 0; i < lines.size() && out.size() < maxLines; i++) out.add(plain(lines.get(i)));
        if (lines.size() > maxLines && !out.isEmpty()) {
            String last = out.remove(out.size() - 1);
            out.add(fit(font, last + "…", width, size));
        }
        return out;
    }

    /**
     * Largura que o Tessera dá a um botão: o texto medido na escala da fonte mais o padding
     * horizontal (a borda é desenhada por dentro). {@code upper}: a classe tem
     * {@code text-transform: uppercase}; {@code bold}: {@code font-weight: bold}.
     */
    static int buttonWidth(Font font, String text, int size, int padX, boolean upper, boolean bold) {
        String shown = upper ? text.toUpperCase(java.util.Locale.ROOT) : text;
        int raw = bold ? font.width(Component.literal(shown).withStyle(ChatFormatting.BOLD)) : font.width(shown);
        return (raw * size + 6) / 7 + 2 * padX;
    }

    /** Soma de botões lado a lado numa {@code row} com {@code gap}. */
    static int rowWidth(int gap, int... widths) {
        int total = 0;
        for (int w : widths) total += w;
        return total + Math.max(0, widths.length - 1) * gap;
    }

    /** Corta para caber em {@code width} px de GUI quando desenhado em {@code scale} (desenho direto). */
    static String fitScaled(Font font, String text, float width, float scale) {
        int room = Math.max(1, (int) (width / scale));
        if (font.width(text) <= room) return text;
        return font.plainSubstrByWidth(text, Math.max(1, room - font.width("…"))) + "…";
    }

    /** O nome da tecla como o jogador configurou ("Y", "Shift Esq."); vazio se estiver sem tecla. */
    static String keyName(KeyMapping key) {
        return key.isUnbound() ? "" : key.getTranslatedKeyMessage().getString();
    }

    static String plain(FormattedCharSequence sequence) {
        StringBuilder out = new StringBuilder();
        sequence.accept((index, style, codePoint) -> {
            out.appendCodePoint(codePoint);
            return true;
        });
        return out.toString();
    }

    // ── Desenho direto ──────────────────────────────────────────────────────────

    /** Retângulo com os cantos aparados em 1px: lê como arredondado em qualquer escala de GUI. */
    static void rounded(GuiGraphics g, int x, int y, int w, int h, int color) {
        if (w <= 2 || h <= 2) {
            g.fill(x, y, x + w, y + h, color);
            return;
        }
        g.fill(x + 1, y, x + w - 1, y + h, color);
        g.fill(x, y + 1, x + 1, y + h - 1, color);
        g.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
    }

    /** Moldura arredondada: borda de 1px e o miolo. */
    static void card(GuiGraphics g, int x, int y, int w, int h, int fill, int border) {
        rounded(g, x, y, w, h, border);
        rounded(g, x + 1, y + 1, w - 2, h - 2, fill);
    }

    /** Texto em escala (1 = fonte do Minecraft). */
    static void text(GuiGraphics g, Font font, String text, float x, float y, int color, float scale, boolean shadow) {
        if (text.isEmpty() || (color >>> 24) < 8) return;
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(scale, scale, 1);
        g.drawString(font, text, 0, 0, color, shadow);
        g.pose().popPose();
    }

    static void textRight(GuiGraphics g, Font font, String text, float right, float y, int color, float scale, boolean shadow) {
        text(g, font, text, right - font.width(text) * scale, y, color, scale, shadow);
    }

    static void textCenter(GuiGraphics g, Font font, String text, float cx, float y, int color, float scale, boolean shadow) {
        text(g, font, text, cx - font.width(text) * scale / 2f, y, color, scale, shadow);
    }

    /**
     * Uma barra de recurso: trilho escuro, preenchimento com brilho na borda superior, a parte de
     * Vida temporária por cima em tom claro, e marcas a cada 25%.
     */
    static void bar(GuiGraphics g, int x, int y, int w, int h, int value, int max, int temp, int color, int highlight) {
        card(g, x, y, w, h, 0xE00A0614, 0xFF2A1E44);
        int inner = w - 2;
        if (max > 0 && inner > 0) {
            int filled = (int) Math.round(inner * Math.min(1.0, Math.max(0.0, value / (double) max)));
            if (filled > 0) {
                g.fill(x + 1, y + 1, x + 1 + filled, y + h - 1, color);
                g.fill(x + 1, y + 1, x + 1 + filled, y + 2, highlight);
            }
            if (temp > 0) {
                int extra = (int) Math.round(inner * Math.min(1.0, temp / (double) max));
                int start = Math.min(x + 1 + filled, x + w - 1 - extra);
                g.fill(start, y + 1, Math.min(x + w - 1, start + extra), y + 2 + Math.max(1, (h - 3) / 2), TEMP);
            }
            for (int i = 1; i < 4; i++) {
                int tx = x + 1 + inner * i / 4;
                g.fill(tx, y + 1, tx + 1, y + h - 1, 0x40000000);
            }
        }
    }

    /** A cor do resultado: crítico dourado, falha vermelha, sucesso verde; sem CD, o dourado claro. */
    static int outcomeColor(com.aurorion.ficha.sheet.RollResult roll) {
        String outcome = com.aurorion.ficha.sheet.Texts.outcome(roll);
        if (outcome.startsWith("CRÍTICO")) return CRIT;
        if (outcome.contains("FALHA")) return BAD;
        if (outcome.equals("SUCESSO")) return OK;
        // Repetição com resultado misto ("1 DE 2 SUCESSOS"): nem verde nem vermelho.
        return outcome.isEmpty() ? GOLD_HI : WARN;
    }

    static int alpha(int color, float alpha) {
        int a = Math.round(((color >>> 24) & 0xFF) * Math.max(0f, Math.min(1f, alpha)));
        return (a << 24) | (color & 0xFFFFFF);
    }

    /** O rosto da skin do jogador; quem não está na lista de jogadores usa a skin padrão do UUID. */
    static void face(GuiGraphics g, @Nullable UUID player, int x, int y, int size) {
        Minecraft minecraft = Minecraft.getInstance();
        PlayerSkin skin;
        if (player == null) {
            skin = minecraft.player != null ? minecraft.player.getSkin() : DefaultPlayerSkin.get(UUID.randomUUID());
        } else {
            PlayerInfo info = minecraft.getConnection() == null ? null : minecraft.getConnection().getPlayerInfo(player);
            skin = info != null ? info.getSkin() : DefaultPlayerSkin.get(player);
        }
        PlayerFaceRenderer.draw(g, skin, x, y, size);
    }

    static String resource(String path) {
        ResourceLocation location = ResourceLocation.fromNamespaceAndPath(AurorionFicha.MOD_ID, path);
        return Minecraft.getInstance().getResourceManager().getResource(location).map(resource -> {
            try (BufferedReader reader = resource.openAsReader()) {
                return reader.lines().collect(Collectors.joining("\n"));
            } catch (IOException e) {
                AurorionFicha.LOGGER.warn("Ficha: não consegui ler {}", location, e);
                return "";
            }
        }).orElse("");
    }

    // ── Folha de estilo lida uma vez (acrescentado pela mesa do mestre) ────────

    /*
     * TesseraTemplate.fromString(html, css) passa o CSS inteiro pelo parser a cada chamada: ~0,8 ms
     * por painel com o ficha.css, e uma tela monta dezenas de painéis por rebuild (a mesa, ~70 —
     * 50–70 ms de engasgo por clique ou resposta chegando). Aqui o CSS vira TesseraStyleSheet uma vez
     * (com as folhas globais do Tessera, como o fromString faz) e cada painel só lê o próprio HTML.
     * O construtor (nó, folha) do TesseraTemplate é privado no 1.1: vai por reflexão, e se ela falhar
     * (outra versão do Tessera) cai no fromString de sempre — mais lento, mas igual na tela.
     */
    private static String sheetSource;
    private static com.tesseraui.TesseraStyleSheet sheet;
    private static java.lang.reflect.Constructor<TesseraTemplate> templateCtor;
    private static boolean templateCtorFailed;

    /** O template de {@code html} com o ficha.css já interpretado; o mesmo resultado do {@code fromString}. */
    static TesseraTemplate template(String html) {
        String source = css();
        if (!templateCtorFailed) {
            try {
                if (templateCtor == null) {
                    java.lang.reflect.Constructor<TesseraTemplate> ctor = TesseraTemplate.class.getDeclaredConstructor(
                            com.tesseraui.TesseraNode.class, com.tesseraui.TesseraStyleSheet.class);
                    ctor.setAccessible(true);
                    templateCtor = ctor;
                }
                // Identidade de propósito: reloadCss() relê o arquivo e troca a String.
                if (sheet == null || sheetSource != source) {
                    sheet = TesseraTemplate.fromString("<col></col>", source).styleSheet();
                    sheetSource = source;
                }
            } catch (ReflectiveOperationException | RuntimeException e) {
                fallBack(e);
            }
        }
        if (!templateCtorFailed) {
            // O HTML é lido fora do try da reflexão: um painel com HTML ruim falha igual ao fromString,
            // sem desligar o atalho para todos os outros.
            com.tesseraui.TesseraNode node = com.tesseraui.TesseraHtmlParser.parseWithComponents(html);
            try {
                return templateCtor.newInstance(node, sheet);
            } catch (ReflectiveOperationException e) {
                fallBack(e);
            }
        }
        return TesseraTemplate.fromString(html, source);
    }

    private static void fallBack(Exception e) {
        templateCtorFailed = true;
        AurorionFicha.LOGGER.warn("Ficha: sem atalho do CSS no Tessera ({}); usando o caminho padrão.", e.toString());
    }


    // ── Tooltip e animação (acrescentado pela tela da ficha) ────────────────────

    /*
     * Por que tooltip próprio: no Tessera 1.1 o atributo tooltip só vale em filhos (a raiz de um
     * painel — o <col>/<row> com onclick — o ignora), sai numa linha só sem limite de largura e é
     * desenhado no meio da montagem, por baixo do que o Java pinta depois. Aqui: várias linhas,
     * quebradas na largura, presas à tela e por cima de tudo.
     */

    /** Linhas de um tooltip já quebradas; {@code title} é quantas linhas o primeiro parágrafo ocupa. */
    record Tooltip(List<FormattedCharSequence> lines, int title) {
    }

    /** Quebra {@code text} (parágrafos separados por {@code \n}) em linhas de até {@code maxWidth} px. */
    static Tooltip tooltip(Font font, String text, int maxWidth) {
        List<FormattedCharSequence> lines = new ArrayList<>();
        int title = 0;
        String[] parts = text.split("\n");
        for (int i = 0; i < parts.length; i++) {
            if (parts[i].isEmpty()) continue;
            lines.addAll(font.split(Component.literal(parts[i]), Math.max(40, maxWidth)));
            if (i == 0) title = lines.size();
        }
        return new Tooltip(lines, title);
    }

    /**
     * Desenha o tooltip perto do mouse, virando para o outro lado quando não cabe. O primeiro
     * parágrafo em dourado, o resto em lilás claro; z alto para ficar acima de painéis e textos.
     */
    static void drawTooltip(GuiGraphics g, Font font, Tooltip tip, int mouseX, int mouseY, int screenW, int screenH) {
        if (tip.lines().isEmpty()) return;
        int w = 0;
        for (FormattedCharSequence line : tip.lines()) w = Math.max(w, font.width(line));
        int lineH = 10;
        int h = tip.lines().size() * lineH - 1 + (tip.title() > 0 && tip.title() < tip.lines().size() ? 2 : 0);
        int x = mouseX + 10;
        int y = mouseY - 6;
        if (x + w + 8 > screenW) x = Math.max(2, mouseX - w - 14);
        if (y + h + 8 > screenH) y = Math.max(2, screenH - h - 8);
        y = Math.max(2, y);
        g.pose().pushPose();
        g.pose().translate(0, 0, 400);
        card(g, x - 4, y - 4, w + 8, h + 8, 0xF0120822, 0xFF6D4BC4);
        g.fill(x - 3, y - 3, x + w + 3, y - 2, 0x55D7A857);
        int ty = y;
        for (int i = 0; i < tip.lines().size(); i++) {
            if (i == tip.title() && i > 0) ty += 2;
            g.drawString(font, tip.lines().get(i), x, ty, i < tip.title() ? GOLD_HI : SOFT, false);
            ty += lineH;
        }
        g.pose().popPose();
    }

    /** Ficha e mesa: o tooltip espera um instante (passar o mouse por uma lista não pisca caixas). */
    static final long TIP_DELAY_MS = 200L;
    /** Ficha e mesa: o clarão de "pressionado" (o Tessera 1.1 ignora {@code :active}). */
    static final long PRESS_MS = 160L;

    /** O clarão do clique em ({@code x}, {@code y}), {@code age} ms depois; o mesmo nas duas telas. */
    static void press(GuiGraphics g, int x, int y, int w, int h, long age) {
        if (age < 0 || age >= PRESS_MS) return;
        rounded(g, x, y, w, h, alpha(0xFFFFF4DA, 0.3f * (1f - age / (float) PRESS_MS)));
    }

    /** Desaceleração cúbica: rápido no começo, assentando no fim (0..1 → 0..1). */
    static float easeOut(float t) {
        float c = Math.max(0f, Math.min(1f, t));
        float inv = 1f - c;
        return 1f - inv * inv * inv;
    }

    // ── Ícones de item e cores vindas do servidor (acrescentado pela aba Habilidades) ──

    /** id do item → a pilha pronta; o registro é consultado uma vez por id, não por frame nem por remontagem. */
    private static final Map<String, ItemStack> ICONS = new java.util.HashMap<>();

    /** A pilha do item {@code id} ({@code minecraft:blaze_powder}); id vazio, torto ou de mod ausente → vazia. */
    static ItemStack icon(String id) {
        if (id == null || id.isEmpty()) return ItemStack.EMPTY;
        ItemStack cached = ICONS.get(id);
        if (cached != null) return cached;
        ResourceLocation location = ResourceLocation.tryParse(id.toLowerCase(java.util.Locale.ROOT));
        ItemStack stack = location == null || !BuiltInRegistries.ITEM.containsKey(location) ? ItemStack.EMPTY
                : new ItemStack(BuiltInRegistries.ITEM.get(location));
        if (ICONS.size() >= 256) ICONS.clear(); // teto: ids vêm do servidor
        ICONS.put(id, stack);
        return stack;
    }

    /**
     * {@code #RRGGBB} → ARGB opaco; vazio ou torto → {@code fallback}. Cor escura demais para ler sobre o
     * fundo violeta é clareada (mistura com branco) — o servidor escolhe a cor, a tela garante o contraste.
     */
    static int hexColor(String hex, int fallback) {
        if (hex == null || hex.length() != 7 || hex.charAt(0) != '#') return fallback;
        int rgb;
        try {
            rgb = Integer.parseInt(hex.substring(1), 16);
        } catch (NumberFormatException e) {
            return fallback;
        }
        int r = rgb >> 16 & 0xFF, g = rgb >> 8 & 0xFF, b = rgb & 0xFF;
        float luma = (0.299f * r + 0.587f * g + 0.114f * b) / 255f;
        if (luma < 0.5f) {
            float mix = Math.min(0.6f, (0.5f - luma) * 1.4f);
            r = Math.round(r + (255 - r) * mix);
            g = Math.round(g + (255 - g) * mix);
            b = Math.round(b + (255 - b) * mix);
        }
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    /** Três pontos pulsando em sequência ("carregando…"), centrados em {@code cx}. */
    static void dots(GuiGraphics g, int cx, int cy, long now, int color) {
        for (int i = 0; i < 3; i++) {
            double phase = (now / 160.0 - i * 0.9) % (Math.PI * 2);
            float a = 0.35f + 0.65f * (float) Math.max(0, Math.sin(phase));
            int x = cx - 5 + i * 4;
            g.fill(x, cy, x + 2, cy + 2, alpha(color, a));
        }
    }
}
