package com.aurorion.ficha.client;

import com.aurorion.ficha.AurorionFicha;
import com.aurorion.ficha.config.FichaClientConfig;
import com.aurorion.ficha.config.FichaClientConfig.Ancora;
import com.aurorion.ficha.sheet.RollCard;
import com.aurorion.ficha.sheet.RollResult;
import com.aurorion.ficha.sheet.Texts;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.Util;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.neoforged.neoforge.client.settings.KeyModifier;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

/**
 * Os cartões da ficha no HUD. Onde cada bloco fica, o tamanho, o tempo e o que esconde vêm de
 * {@code ficha-client.toml} ({@link FichaClientConfig}).
 *
 * <ul>
 *   <li><b>Rolagens</b> (padrão: à direita, perto do meio da altura), abaixo dos ícones de efeito, das
 *       barras de chefe e do minimapa do JourneyMap ({@link HudCompat}): a sua (ou a que o mestre
 *       pediu e chegou a ele) num cartão grande com o d20 rolando, assentando e revelando o resultado,
 *       sempre na ponta da pilha; as dos vizinhos em cartões menores depois. Entram deslizando da
 *       borda mais perto, somem sozinhos; os outros andam quando um some. Nas âncoras {@code *_BAIXO}
 *       a pilha cresce para cima.</li>
 *   <li><b>Pedido do mestre</b> (padrão: no centro, abaixo do Jade): quem pediu, o teste, a CD (se
 *       visível) e o motivo, como responder ({@link FichaKeys#answerHint}) e o tempo que falta (número e
 *       barra, que ficam âmbar nos últimos 20 s). Um pedido por vez (o mais novo, com "+N" se houver
 *       outros); o anterior volta se este sumir.</li>
 *   <li><b>Aviso</b> (alt sem ficha, ficha antiga), no mesmo lugar, quando não há pedido. O tempo dele só começa a
 *       contar quando aparece de fato (chegou com uma tela aberta: espera).</li>
 *   <li><b>"Abrindo…"</b>, logo acima do lugar do pedido, entre a tecla da ficha e a tela chegar.</li>
 * </ul>
 *
 * <p>Respeita F1 (nada), F3 (só o pedido, que tem prazo), a ficha/mesa aberta (que mostram o
 * resultado elas mesmas; o tempo dos cartões corre), a barra de itens e o chat. Com o modo streamer
 * do Essentials (ou {@code ocultarSegredos}) os cartões mostram só o que um vizinho veria.
 *
 * <p>Custo: com nada na fila o render sai na primeira linha. Com cartões, alguns {@code fill} e
 * textos por cartão; os textos (cortados, medidos, quebrados) são montados uma vez por cartão ou
 * pedido e guardados nele — por frame só se desenha. O minimapa e o modo streamer são conferidos
 * em intervalos ({@link HudCompat}), não a cada frame.
 */
public final class FichaHud implements LayeredDraw.Layer {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(AurorionFicha.MOD_ID, "cartoes");

    private static final int MAX_ASKS = 4;
    private static final long FADE_MS = 500L;
    private static final long ENTER_MS = 220L;
    private static final long LEAVE_MS = 180L;
    /** Quanto tempo a borda do pedido pulsa forte ao chegar; depois fica firme até a reta final. */
    private static final long ASK_CALL_MS = 3_500L;
    private static final long URGENT_SECONDS = 20L;
    /** Sem resposta do servidor depois de responder (roda, botão ou tecla de pedido): volta a mostrar a dica. */
    private static final long ANSWER_TIMEOUT_MS = 12_000L;
    /** "Abrindo…" só aparece se a tela demorar (resposta da cópia em cache chega antes) e some sozinho. */
    private static final long LOADING_DELAY_MS = 180L;
    private static final long LOADING_MAX_MS = 10_000L;
    private static final long CRIT_GLOW_MS = 1_400L;
    private static final int BIG_W = 180, BIG_H = 50;
    private static final int SMALL_W = 152, SMALL_H = 24;
    private static final int BIG_TEXT_W = BIG_W - 47 - 44 - 4;
    private static final int SMALL_TEXT_W = SMALL_W - 27 - 38;
    private static final int ASK_W = 190;
    private static final int MARGIN = 6, GAP = 4;
    /** Topo mínimo do bloco no centro: abaixo do Jade (topo central, ~2–3 linhas). */
    private static final int CENTER_TOP = 56;
    /** Âncoras {@code *_MEIO}: a pilha começa nesta fração da altura (abaixo de um minimapa de 30 %). */
    private static final float MIDDLE = 0.42f;
    private static final int LOADING_H = 14;
    private static final int BG = 0xF0120822;
    private static final String[] ROLLING = {"Rolando", "Rolando.", "Rolando..", "Rolando..."};
    /** O girador do "Abrindo…": oito pontos num círculo. */
    private static final int[] SPIN_X = {0, 2, 3, 2, 0, -2, -3, -2};
    private static final int[] SPIN_Y = {-3, -2, 0, 2, 3, 2, 0, -2};

    /** Um cartão na pilha; {@code y} é a posição desenhada, que desliza até a de destino. */
    private static final class Shown {
        final RollCard card;
        final long start;
        final long life;
        final boolean big;
        final long seed;
        float y = Float.NaN;
        // Textos prontos, medidos na primeira vez que o cartão aparece (e de novo se o modo streamer mudar).
        boolean prepared;
        /** Os textos foram montados escondendo segredos? */
        boolean preparedHide;
        /** Rolagem secreta com segredos escondidos: o cartão diz só "Rolagem secreta", sem dado nem número. */
        boolean masked;
        String kicker = "", label = "", line = "", outcome = "", total = "";
        int accent, totalW, outcomeW;
        float totalScale = 1f;

        Shown(RollCard card, long start, long life, boolean big, long seed) {
            this.card = card;
            this.start = start;
            this.life = life;
            this.big = big;
            this.seed = seed;
        }

        /**
         * {@code hide}: mostrar só o que um vizinho veria (modo streamer / {@code ocultarSegredos}) —
         * secreta sem nada, sem a CD que o mestre escondeu, bônus como "?" (pode ser número de NPC: o
         * cartão não diz se é; rolagem livre não tem bônus de ficha e fica inteira).
         */
        void prepare(Font font, boolean hide, String reason) {
            if (prepared && preparedHide == hide) return;
            prepared = true;
            preparedHide = hide;
            masked = hide && card.secret();
            if (masked) {
                accent = FichaUi.VIOLET;
                total = "";
                totalW = 0;
                outcome = "";
                outcomeW = 0;
                String what = card.requestId().isEmpty() ? "SECRETO" : "SECRETO · PEDIDO DO MESTRE";
                kicker = FichaUi.fitScaled(font, what, big ? BIG_TEXT_W : SMALL_TEXT_W, 0.62f);
                label = FichaUi.fitScaled(font, "Rolagem secreta", big ? BIG_TEXT_W + 40 : SMALL_TEXT_W + 30, big ? 1f : 0.8f);
                line = FichaUi.fitScaled(font, "resultado oculto · " + reason, BIG_TEXT_W + 40, 0.7f);
                return;
            }
            RollResult roll = card.roll();
            boolean hideBonus = hide && !"free".equals(roll.kind());
            if (hide) roll = roll.redacted(hideBonus, card.dcHidden());
            accent = FichaUi.outcomeColor(roll);
            total = roll.repeated() ? Texts.totals(roll) : D20.num(roll.total());
            totalW = font.width(total);
            if (big) {
                kicker = FichaUi.fitScaled(font, kicker(card), BIG_TEXT_W, 0.62f);
                label = FichaUi.fitScaled(font, roll.label(), BIG_TEXT_W, 1f);
                line = FichaUi.fitScaled(font, Texts.breakdown(roll, card.bonusHidden() || hideBonus, card.dcHidden()), BIG_TEXT_W, 0.7f);
                outcome = FichaUi.fitScaled(font, Texts.outcome(roll), BIG_TEXT_W, 0.8f);
                outcomeW = Math.round(font.width(outcome) * 0.8f);
                totalScale = roll.repeated() ? 1.1f : total.length() >= 3 ? 1.6f : 2f;
            } else {
                kicker = FichaUi.fitScaled(font, card.displayName() + (card.secret() ? " · secreto" : ""), SMALL_TEXT_W, 0.62f);
                label = FichaUi.fitScaled(font, roll.label(), SMALL_TEXT_W, 0.8f);
                outcome = shortOutcome(roll);
                outcomeW = Math.round(font.width(outcome) * 0.55f);
                totalScale = roll.repeated() ? 0.9f : 1.3f;
            }
        }
    }

    /**
     * Pedido no HUD. {@code shownAt}: quando chegou; {@code expiresAt}: quando vence.
     * {@code local}: veio do {@code /ficha teste} (responde localmente).
     */
    record Ask(String id, String gmName, String title, String test, String detail, long shownAt, long expiresAt, boolean local) {
    }

    /** Os textos de um pedido já cortados e medidos; o relógio só remonta quando muda o segundo. */
    private static final class AskView {
        final Ask ask;
        int more = -1;
        long firstDrawn;
        String kicker = "", gm = "", test = "";
        int gmW, h;
        List<String> detail = List.of();
        long clockFor = -1;
        String clock = "";
        int clockW;

        AskView(Ask ask) {
            this.ask = ask;
        }

        void prepare(Font font, int others) {
            if (more == others) return;
            boolean first = more < 0;
            more = others;
            gm = ask.gmName().isEmpty() ? "" : FichaUi.fitScaled(font, ask.gmName(), 70, 0.62f);
            gmW = gm.isEmpty() ? 0 : Math.round(font.width(gm) * 0.62f);
            String top = ask.title().toUpperCase(Locale.ROOT) + (others > 0 ? "  ·  +" + others + (others > 1 ? " PEDIDOS" : " PEDIDO") : "");
            kicker = FichaUi.fitScaled(font, top, ASK_W - 16 - gmW - 6, 0.62f);
            if (!first) return;
            // "Teste de Furtividade" ou, num pedido de dados, "Rolar 2d6".
            test = FichaUi.fitScaled(font, Texts.askHeadline(ask.test()), ASK_W - 16, 1.1f);
            detail = ask.detail().isEmpty() ? List.of() : FichaUi.wrap(font, ask.detail(), ASK_W - 16, 6, 2);
            // wrap mede na régua do Tessera (6px = 6/7); aqui o texto sai em 0,75 — sobra folga.
            h = 50 + detail.size() * 8;
        }

        String clock(Font font, long seconds) {
            if (seconds != clockFor) {
                clockFor = seconds;
                clock = seconds / 60 + ":" + (seconds % 60 < 10 ? "0" : "") + seconds % 60;
                clockW = Math.round(font.width(clock) * 0.75f);
            }
            return clock;
        }
    }

    /** O nome da tecla configurada, refeito só quando a tecla (ou o idioma) muda. */
    private static final class KeyLabel {
        final KeyMapping key;
        @Nullable
        InputConstants.Key last;
        @Nullable
        KeyModifier lastModifier;
        @Nullable
        String lastLanguage;
        String text = "";
        int w;

        KeyLabel(KeyMapping key) {
            this.key = key;
        }

        /** Vazio se a tecla estiver sem atribuição. */
        String get(Font font) {
            String language = Minecraft.getInstance().getLanguageManager().getSelected();
            if (key.getKey() != last || key.getKeyModifier() != lastModifier || !language.equals(lastLanguage)) {
                last = key.getKey();
                lastModifier = key.getKeyModifier();
                lastLanguage = language;
                String name = FichaUi.keyName(key);
                text = name.isEmpty() ? "" : FichaUi.fitScaled(font, name, 40, 0.75f);
                w = Math.round(font.width(text) * 0.75f) + 7;
            }
            return text;
        }
    }

    private static final class Notice {
        final String title, text;
        final int seconds;
        /** 0 = ainda não apareceu (chegou com uma tela por cima). */
        long shownAt;
        long until;
        @Nullable
        List<String> lines;
        String titleUpper = "";
        int h;

        Notice(String title, String text, int seconds) {
            this.title = title;
            this.text = text;
            this.seconds = seconds;
        }
    }

    private static final List<Shown> CARDS = new ArrayList<>();
    private static final Deque<Ask> ASKS = new ArrayDeque<>();
    private static final KeyLabel OPEN_KEY = new KeyLabel(FichaKeys.OPEN);
    /** A dica de como responder (base do cartão do pedido), de {@link FichaKeys#answerHint}, já cortada. */
    private static String hintRaw = "", hintFit = "";
    /** O vencimento mais próximo da fila de pedidos: antes dele, conferir a fila é uma comparação. */
    private static long nextAskExpiry = Long.MAX_VALUE;
    @Nullable
    private static AskView view;
    /** O último pedido saindo (fade para a esquerda) quando não sobrou outro. */
    @Nullable
    private static AskView leaving;
    private static long leftAt;
    @Nullable
    private static Notice notice;
    /** Pedido que já foi respondido com a tecla e espera o resultado. */
    @Nullable
    private static String answering;
    private static long answeringSince;
    /** "Abrindo…": o contexto do pedido ({@code abrir}/{@code mestre}) e o texto. */
    @Nullable
    private static String loadingContext;
    private static String loadingText = "";
    private static long loadingSince;
    private static int loadingW;
    private static long lastFrame;

    // Barras de chefe deste frame (CustomizeGuiOverlayEvent.BossEventProgress, antes da nossa camada).
    private static int frame;
    private static int bossFrame = -1;
    private static int bossLeft, bossRight, bossBottom;
    // Ícones de efeito: conferidos a cada 250 ms, não a cada frame.
    private static long effectsCheckedAt;
    private static int effectsTop = MARGIN;
    // O bloco do pedido/aviso neste frame (GUI), para a pilha de rolagens desviar dele. blockX1 0 = nenhum.
    private static int blockX0, blockX1, blockY0, blockY1;
    // Os segredos escondidos neste frame (modo streamer / ocultarSegredos) e por quê.
    private static boolean hideSecrets;
    private static String hideReason = "";

    // ── Estado (thread do cliente) ──────────────────────────────────────────────

    static void addCard(RollCard card) {
        long now = Util.getMillis();
        boolean big = card.own() || (card.gm() && !card.requestId().isEmpty());
        // "Rolagens dos outros" na config: os seus e as respostas aos seus pedidos sempre entram.
        FichaClientConfig.Outros others = FichaClientConfig.get(FichaClientConfig.OTHERS);
        boolean show = big || others == FichaClientConfig.Outros.TODAS
                || (others == FichaClientConfig.Outros.PEDIDAS && !card.requestId().isEmpty());
        if (show) {
            // O mesmo cartão de novo (reenvio do mesmo command_id): fica um só.
            if (!card.id().isEmpty()) CARDS.removeIf(s -> s.card.id().equals(card.id()));
            long life = 1000L * FichaClientConfig.get(big ? FichaClientConfig.OWN_SECONDS : FichaClientConfig.OTHER_SECONDS);
            CARDS.add(0, new Shown(card, now, life, big, card.id().hashCode() * 31L + now));
            int max = FichaClientConfig.get(FichaClientConfig.MAX_CARDS);
            while (CARDS.size() > max) CARDS.remove(CARDS.size() - 1);
        }
        if (!card.requestId().isEmpty()) {
            if (card.requestId().equals(answering)) answering = null;
            // O resultado do pedido chegou: o cartão do pedido sai (o servidor também manda fechar).
            if (card.own()) closeAsk(card.requestId());
        }
    }

    /** Põe o pedido no topo. Devolve {@code true} se é novo (não a reedição de um que já estava). */
    static boolean ask(Ask ask) {
        boolean known = false;
        for (Iterator<Ask> it = ASKS.iterator(); it.hasNext(); ) {
            if (it.next().id().equals(ask.id())) {
                it.remove();
                known = true;
            }
        }
        ASKS.addFirst(ask);
        while (ASKS.size() > MAX_ASKS) ASKS.removeLast();
        leaving = null;
        recomputeExpiry();
        return !known;
    }

    static void closeAsk(String id) {
        boolean removed = false;
        for (Iterator<Ask> it = ASKS.iterator(); it.hasNext(); ) {
            if (it.next().id().equals(id)) {
                it.remove();
                removed = true;
            }
        }
        if (id.equals(answering)) answering = null;
        if (!removed) return;
        recomputeExpiry();
        // Saiu o último: ele desliza para fora em vez de sumir de uma vez.
        if (ASKS.isEmpty() && view != null && view.ask.id().equals(id) && view.firstDrawn > 0) {
            leaving = view;
            leftAt = Util.getMillis();
        }
    }

    /** O pedido do topo que ainda vale, ou {@code null}. */
    @Nullable
    static Ask currentAsk() {
        pruneAsks(Util.getMillis());
        return ASKS.peekFirst();
    }

    /** Há um pedido no HUD? (Contexto das teclas de pedido, sem tecla de fábrica: chamado a cada tecla, precisa ser barato.) */
    static boolean hasAsk() {
        return currentAsk() != null;
    }

    private static void pruneAsks(long now) {
        if (now < nextAskExpiry) return;
        for (Iterator<Ask> it = ASKS.iterator(); it.hasNext(); ) {
            Ask a = it.next();
            if (a.expiresAt() <= now) {
                it.remove();
                if (a.id().equals(answering)) answering = null;
            }
        }
        recomputeExpiry();
    }

    private static void recomputeExpiry() {
        long next = Long.MAX_VALUE;
        for (Ask a : ASKS) next = Math.min(next, a.expiresAt());
        nextAskExpiry = next;
    }

    static void answering(@Nullable String id) {
        answering = id;
        answeringSince = Util.getMillis();
    }

    static void notice(String title, String text, int seconds) {
        notice = new Notice(title, text, Math.max(3, Math.min(60, seconds)));
    }

    /**
     * A dica de {@link FichaKeys#answerHint} ("Segure ' para responder", "Y rola · U recusa"…) pronta
     * para desenhar (0,75), cortada para {@code width}. O {@code answerHint} devolve a mesma instância
     * até a tecla ou o idioma mudar: por quadro é uma comparação de referência; cortar, só quando muda.
     */
    private static String answerHint(Font font, int width) {
        String raw = FichaKeys.answerHint();
        if (raw == null) raw = "";
        if (raw != hintRaw) {
            hintRaw = raw;
            hintFit = raw.isEmpty() ? "" : FichaUi.fitScaled(font, raw, width, 0.75f);
        }
        return hintFit;
    }

    /** "Abrindo…" para o pedido {@code context}, até {@link #loadingDone} ou {@link #LOADING_MAX_MS}. */
    static void loading(String context, String text) {
        loadingContext = context;
        loadingText = text;
        loadingSince = Util.getMillis();
        loadingW = Minecraft.getInstance().font.width(text);
    }

    static void loadingDone(String context) {
        if (context.equals(loadingContext)) loadingContext = null;
    }

    /** Uma barra de chefe desenhada neste frame em ({@code x}, {@code y}), com {@code increment} de altura. */
    static void bossBar(int x, int y, int increment) {
        if (bossFrame != frame) {
            bossFrame = frame;
            bossLeft = x;
            bossRight = x + 182;
            bossBottom = 0;
        }
        bossLeft = Math.min(bossLeft, x);
        bossRight = Math.max(bossRight, x + 182);
        // A barra ocupa y..y+5 e o nome da próxima começa em y+increment-9; barra de mod pode ser mais alta.
        bossBottom = Math.max(bossBottom, y + Math.max(5, increment - 10) + 2);
    }

    static void clear() {
        CARDS.clear();
        ASKS.clear();
        nextAskExpiry = Long.MAX_VALUE;
        view = null;
        leaving = null;
        notice = null;
        answering = null;
        loadingContext = null;
        lastFrame = 0;
        HudCompat.previewStreamer(false);
    }

    // ── Desenho ─────────────────────────────────────────────────────────────────

    @Override
    public void render(GuiGraphics graphics, DeltaTracker delta) {
        boolean bosses = bossFrame == frame;
        frame++;
        if (CARDS.isEmpty() && ASKS.isEmpty() && notice == null && leaving == null && loadingContext == null) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.options.hideGui || minecraft.player == null) return;
        long now = Util.getMillis();
        float dt = lastFrame == 0 ? 0f : Math.min(100f, now - lastFrame);
        lastFrame = now;
        if (answering != null && now - answeringSince > ANSWER_TIMEOUT_MS) answering = null;
        if (loadingContext != null && now - loadingSince > LOADING_MAX_MS) loadingContext = null;
        // A ficha e a mesa mostram o resultado elas mesmas; o cartão por baixo do escuro só polui.
        boolean covered = minecraft.screen instanceof FichaScreen || minecraft.screen instanceof MestreScreen;
        // F3: o texto de depuração ocupa os dois lados. Fica só o pedido do mestre, que tem prazo.
        boolean debug = minecraft.getDebugOverlay().showDebugScreen();
        Font font = minecraft.font;
        blockX1 = 0;
        if (!covered) renderBlock(graphics, font, minecraft, now, bosses, debug);
        renderCards(graphics, font, minecraft, now, dt, covered || debug, bosses);
    }

    /** {@code y}, ou logo abaixo das barras de chefe se o retângulo {@code x..x+w} passar sob elas. */
    private static int belowBosses(boolean bosses, int x, int w, int y) {
        if (!bosses || x >= bossRight || x + w <= bossLeft) return y;
        return Math.max(y, bossBottom + GAP);
    }

    /** Topo da área do chat sem foco (na escala dele). */
    private static int chatTop(GuiGraphics g, Minecraft minecraft) {
        double scale = minecraft.options.chatScale().get();
        int chat = (int) Math.ceil(ChatComponent.getHeight(minecraft.options.chatHeightUnfocused().get()) * scale);
        return g.guiHeight() - 40 - chat;
    }

    /** O retângulo {@code x..x+w} passa sobre a barra de itens (com a mão secundária e o texto de ação)? */
    private static boolean overHotbar(GuiGraphics g, int x, int w) {
        int mid = g.guiWidth() / 2;
        return x < mid + 91 + 30 && x + w > mid - 91 - 30;
    }

    /**
     * Onde algo pode começar no topo do lado direito: abaixo das fileiras de ícones de efeito, do
     * minimapa do JourneyMap no canto direito (se houver) e das barras de chefe.
     */
    private static int rightTop(GuiGraphics g, Minecraft minecraft, long now, boolean bosses, int x, int w) {
        int y = effectsTop(minecraft, now);
        int map = HudCompat.minimapTopRightBottom(minecraft, g.guiWidth(), g.guiHeight());
        if (map > 0) y = Math.max(y, map + GAP);
        return belowBosses(bosses, x, w, y);
    }

    /** Até onde algo pode descer no lado direito: acima da barra de itens (se passar por ela) e do minimapa de baixo. */
    private static int rightBottom(GuiGraphics g, Minecraft minecraft, int x, int w) {
        int bottom = g.guiHeight() - (overHotbar(g, x, w) ? 64 : 40);
        return Math.min(bottom, HudCompat.minimapBottomRightTop(minecraft, g.guiWidth(), g.guiHeight()) - GAP);
    }

    /** O x de um bloco de largura {@code w} na âncora, com o deslocamento da config. */
    private static int anchorX(GuiGraphics g, Ancora anchor, int w, int offX) {
        int x = anchor.right() ? g.guiWidth() - MARGIN - w : anchor.center() ? (g.guiWidth() - w) / 2 : MARGIN;
        return x + offX;
    }

    /**
     * O topo do bloco do pedido/aviso ({@code w}×{@code h} já na escala) na âncora. As da esquerda
     * ficam acima do chat; o centro, abaixo do Jade e das barras de chefe, sem descer ao meio da tela
     * (a mira); as da direita, abaixo de efeitos, minimapa e chefes.
     */
    private static int blockY(GuiGraphics g, Minecraft minecraft, Ancora anchor, int x, int w, int h, long now, boolean bosses) {
        int gh = g.guiHeight();
        int chatTop = chatTop(g, minecraft);
        return switch (anchor) {
            // O lugar antigo: ~1/4 da tela, mas acima do chat.
            case ESQUERDA_TOPO -> belowBosses(bosses, x, w, Math.max(24, Math.min(Math.round(gh * 0.24f), chatTop - h - 6)));
            case ESQUERDA_MEIO -> belowBosses(bosses, x, w, Math.max(24, Math.min(Math.round(gh * MIDDLE), chatTop - h - 6)));
            case ESQUERDA_BAIXO -> Math.max(MARGIN, chatTop - h - 6);
            case CENTRO_TOPO -> {
                int y = belowBosses(bosses, x, w, Math.max(CENTER_TOP, Math.round(gh * 0.18f)));
                // Tela baixa: sobe em vez de cobrir a mira.
                yield y + h > gh / 2 - 10 ? Math.max(MARGIN, gh / 2 - 10 - h) : y;
            }
            case DIREITA_TOPO -> rightTop(g, minecraft, now, bosses, x, w);
            case DIREITA_MEIO -> Math.max(rightTop(g, minecraft, now, bosses, x, w), Math.round(gh * MIDDLE));
            case DIREITA_BAIXO -> Math.max(MARGIN, rightBottom(g, minecraft, x, w) - h);
        };
    }

    /** Desenha em ({@code x}, {@code y}) na escala {@code scale}: quem chama desenha em (0, 0). */
    private static void push(GuiGraphics g, float x, float y, float scale) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        if (scale != 1f) g.pose().scale(scale, scale, 1f);
    }

    /** Pedido do mestre, aviso e "Abrindo…", na âncora do pedido. Guarda o retângulo para a pilha desviar. */
    private static void renderBlock(GuiGraphics g, Font font, Minecraft minecraft, long now, boolean bosses, boolean debug) {
        Ancora anchor = FichaClientConfig.get(FichaClientConfig.ASK_ANCHOR);
        float scale = FichaClientConfig.scale(FichaClientConfig.ASK_SCALE);
        int offX = FichaClientConfig.get(FichaClientConfig.ASK_X), offY = FichaClientConfig.get(FichaClientConfig.ASK_Y);
        boolean loading = loadingContext != null && !debug && now - loadingSince >= LOADING_DELAY_MS;

        // O que o bloco mostra neste frame: o pedido do topo, o último saindo ou o aviso.
        Ask ask = currentAsk();
        AskView shown = null;
        float leaveT = -1f;
        Notice n = null;
        int contentH = 0;
        if (ask != null) {
            if (view == null || view.ask != ask) view = new AskView(ask);
            shown = view;
            shown.prepare(font, ASKS.size() - 1);
            if (shown.firstDrawn == 0) shown.firstDrawn = now;
            contentH = shown.h;
        } else if (leaving != null) {
            leaveT = (now - leftAt) / (float) LEAVE_MS;
            if (leaveT >= 1f) {
                leaving = null;
                leaveT = -1f;
            } else {
                shown = leaving;
                contentH = shown.h;
            }
        }
        if (shown == null && notice != null && !debug) {
            n = notice;
            if (n.shownAt == 0) {
                n.shownAt = now;
                n.until = now + n.seconds * 1000L;
            }
            if (n.until <= now) {
                notice = null;
                n = null;
            } else {
                if (n.lines == null) {
                    n.lines = FichaUi.wrap(font, n.text, ASK_W - 16, 6, 3);
                    n.titleUpper = FichaUi.fitScaled(font, n.title.toUpperCase(Locale.ROOT), ASK_W - 16, 0.62f);
                    n.h = 28 + n.lines.size() * 8;
                }
                contentH = n.h;
            }
        }
        if (!loading && contentH == 0) return;

        int loadW = Math.round((Math.round(loadingW * 0.75f) + 22) * scale), loadH = Math.round(LOADING_H * scale);
        int w = Math.round(ASK_W * scale), h = Math.round(contentH * scale);
        int bw = contentH > 0 ? w : loadW;
        // "Abrindo…" fica logo acima do cartão (ou sozinho no lugar dele).
        int bh = (loading ? loadH + (contentH > 0 ? GAP : 0) : 0) + h;
        int x = anchorX(g, anchor, bw, offX);
        int y = blockY(g, minecraft, anchor, x, bw, bh, now, bosses) + offY;

        if (loading) {
            int lx = anchor.right() ? x + bw - loadW : anchor.center() ? x + (bw - loadW) / 2 : x;
            push(g, lx, y, scale);
            drawLoading(g, font, now);
            g.pose().popPose();
        }
        if (contentH == 0) return;
        int cy = y + (loading ? loadH + GAP : 0);
        // Entra pela borda mais perto (o centro desce um pouco e acende); sai do mesmo jeito.
        int side = anchor.right() ? 1 : anchor.left() ? -1 : 0;
        float dx = 0f, dy = 0f, a = 1f;
        if (shown != null) {
            if (leaveT >= 0f) {
                a = 1f - leaveT;
                if (side == 0) dy = -leaveT * leaveT * 10;
                else dx = side * leaveT * leaveT * 28;
            } else {
                float e = ease(now - shown.firstDrawn, ENTER_MS);
                if (side == 0) {
                    dy = -(1f - e) * 10;
                    a = 0.2f + 0.8f * e;
                } else {
                    dx = side * (1f - e) * (w + MARGIN);
                }
            }
            push(g, x + dx, cy + dy, scale);
            drawAsk(g, font, shown, now, a);
            g.pose().popPose();
        } else if (n != null) {
            float e = ease(now - n.shownAt, ENTER_MS);
            if (side == 0) {
                dy = -(1f - e) * 10;
                a = 0.2f + 0.8f * e;
            } else {
                dx = side * (1f - e) * (w + MARGIN);
            }
            push(g, x + dx, cy + dy, scale);
            drawNotice(g, font, n, now, a);
            g.pose().popPose();
        }
        // O pedido que está saindo não segura a pilha.
        if (leaveT < 0f) {
            blockX0 = x;
            blockX1 = x + w;
            blockY0 = y;
            blockY1 = cy + h;
        }
    }

    /** Ease-out cúbico de 0 a 1 em {@code ms}. */
    private static float ease(long age, long ms) {
        if (age >= ms) return 1f;
        if (age <= 0) return 0f;
        float t = 1f - age / (float) ms;
        return 1f - t * t * t;
    }

    /** O "Abrindo a ficha…" com o girador, em (0, 0). */
    private static void drawLoading(GuiGraphics g, Font font, long now) {
        float a = ease(now - loadingSince - LOADING_DELAY_MS, 160L);
        int w = Math.round(loadingW * 0.75f) + 22, h = LOADING_H;
        FichaUi.card(g, 0, 0, w, h, FichaUi.alpha(BG, a), FichaUi.alpha(0xFF3A2766, a));
        int cx = 8, cy = 7;
        int head = (int) ((now / 90) % SPIN_X.length);
        for (int i = 0; i < SPIN_X.length; i++) {
            int dist = Math.floorMod(head - i, SPIN_X.length);
            float dot = dist == 0 ? 1f : dist == 1 ? 0.6f : dist == 2 ? 0.35f : 0.15f;
            g.fill(cx + SPIN_X[i], cy + SPIN_Y[i], cx + SPIN_X[i] + 1, cy + SPIN_Y[i] + 1, FichaUi.alpha(FichaUi.GOLD, dot * a));
        }
        FichaUi.text(g, font, loadingText, 15, 4, FichaUi.alpha(FichaUi.SOFT, a), 0.75f, false);
    }

    /** O cartão do pedido em (0, 0), com opacidade {@code a}. */
    private static void drawAsk(GuiGraphics g, Font font, AskView v, long now, float a) {
        Ask ask = v.ask;
        int x = 0, y = 0;
        int w = ASK_W, h = v.h;
        long age = now - v.firstDrawn;
        long seconds = (Math.max(0, ask.expiresAt() - now) + 999) / 1000;
        boolean urgent = seconds <= URGENT_SECONDS;
        boolean busy = ask.id().equals(answering);
        // Chama a atenção ao chegar, fica firme, e volta a pulsar (âmbar, mais rápido) na reta final.
        float border = busy ? 0.6f
                : age < ASK_CALL_MS ? 0.55f + 0.45f * (float) Math.sin(now / 240.0)
                : urgent ? 0.6f + 0.4f * (float) Math.sin(now / 160.0) : 0.8f;
        int edge = urgent && !busy ? FichaUi.WARN : FichaUi.GOLD;
        FichaUi.card(g, x, y, w, h, FichaUi.alpha(BG, a), FichaUi.alpha(edge, border * a));
        g.fill(x + 1, y + 1, x + 3, y + h - 1, FichaUi.alpha(edge, a));

        FichaUi.text(g, font, v.kicker, x + 8, y + 5, FichaUi.alpha(FichaUi.GOLD, a), 0.62f, false);
        if (v.gmW > 0) FichaUi.text(g, font, v.gm, x + w - 7 - v.gmW, y + 5, FichaUi.alpha(FichaUi.FAINT, a), 0.62f, false);
        FichaUi.text(g, font, v.test, x + 8, y + 13, FichaUi.alpha(FichaUi.GOLD_HI, a), 1.1f, true);
        for (int i = 0; i < v.detail.size(); i++) {
            FichaUi.text(g, font, v.detail.get(i), x + 8, y + 26 + i * 8, FichaUi.alpha(FichaUi.SOFT, a), 0.75f, false);
        }

        int keyY = y + h - 17;
        String clock = v.clock(font, seconds);
        if (busy) {
            FichaUi.text(g, font, ROLLING[(int) ((now / 280) % ROLLING.length)], x + 8, keyY + 2.5f, FichaUi.alpha(FichaUi.WARN, a), 0.75f, false);
        } else {
            // A dica nunca vem vazia: sem tecla nenhuma, answerHint já diz "Use /ficha para responder".
            String hint = answerHint(font, ASK_W - 16 - 30);
            if (!hint.isEmpty()) {
                // Um losango pequeno marca a dica como ação (sem caixa de tecla: o texto já diz qual).
                int mx = x + 9, my = keyY + 5;
                g.fill(mx, my - 1, mx + 1, my + 2, FichaUi.alpha(FichaUi.OK, a));
                g.fill(mx - 1, my, mx + 2, my + 1, FichaUi.alpha(FichaUi.OK, a));
                FichaUi.text(g, font, hint, x + 13, keyY + 2.5f, FichaUi.alpha(FichaUi.OK, a), 0.75f, false);
            }
        }
        FichaUi.text(g, font, clock, x + w - 7 - v.clockW, keyY + 2.5f, FichaUi.alpha(urgent ? FichaUi.WARN : FichaUi.FAINT, a), 0.75f, false);

        // A barra do tempo que falta, na base do cartão.
        long total = Math.max(1, ask.expiresAt() - ask.shownAt());
        float rest = Math.max(0f, Math.min(1f, (ask.expiresAt() - now) / (float) total));
        int barW = Math.round((w - 8) * rest);
        g.fill(x + 4, y + h - 4, x + w - 4, y + h - 3, FichaUi.alpha(0x30D7A857, a));
        if (barW > 0) g.fill(x + 4, y + h - 4, x + 4 + barW, y + h - 3, FichaUi.alpha(urgent ? FichaUi.WARN : FichaUi.GOLD, 0.85f * a));
    }

    /** "[K] Abrir a ficha": a tecla numa caixinha e o rótulo. Devolve onde terminou. */
    private static int keyHint(GuiGraphics g, Font font, KeyLabel key, String label, int x, int y, int color, float a) {
        String k = key.get(font);
        int kw = key.w;
        FichaUi.card(g, x, y, kw, 11, FichaUi.alpha(0xFF1E1240, a), FichaUi.alpha(color, 0.8f * a));
        FichaUi.text(g, font, k, x + 4, y + 2.5f, FichaUi.alpha(FichaUi.INK, a), 0.75f, false);
        FichaUi.text(g, font, label, x + kw + 3, y + 2.5f, FichaUi.alpha(color, a), 0.75f, false);
        return x + kw + 3 + labelWidth(font, label);
    }

    private static int openLabelW = -1;

    /** Largura (em 0,75) de um rótulo de tecla; o fixo do aviso é medido uma vez. */
    private static int labelWidth(Font font, String label) {
        if ("Abrir a ficha".equals(label)) return openLabelW >= 0 ? openLabelW : (openLabelW = Math.round(font.width(label) * 0.75f));
        return Math.round(font.width(label) * 0.75f);
    }

    /** O aviso em (0, 0); {@code enter}: a opacidade da entrada (1 nas âncoras laterais, que deslizam). */
    private static void drawNotice(GuiGraphics g, Font font, Notice n, long now, float enter) {
        int x = 0, y = 0;
        int w = ASK_W, h = n.h;
        float fade = Math.min(1f, (n.until - now) / (float) FADE_MS) * enter;
        FichaUi.card(g, x, y, w, h, FichaUi.alpha(BG, fade), FichaUi.alpha(FichaUi.WARN, 0.7f * fade));
        g.fill(x + 1, y + 1, x + 3, y + h - 1, FichaUi.alpha(FichaUi.WARN, fade));
        FichaUi.text(g, font, n.titleUpper, x + 8, y + 5, FichaUi.alpha(FichaUi.WARN, fade), 0.62f, false);
        List<String> lines = n.lines;
        if (lines != null) {
            for (int i = 0; i < lines.size(); i++) {
                FichaUi.text(g, font, lines.get(i), x + 8, y + 13 + i * 8, FichaUi.alpha(FichaUi.INK, fade), 0.75f, false);
            }
        }
        if (OPEN_KEY.get(font).isEmpty()) {
            FichaUi.text(g, font, "Use /ficha para abrir a ficha", x + 8, y + h - 11, FichaUi.alpha(FichaUi.GOLD, fade), 0.75f, false);
        } else {
            keyHint(g, font, OPEN_KEY, "Abrir a ficha", x + 8, y + h - 14, FichaUi.GOLD, fade);
        }
    }

    /** Onde a pilha começa: abaixo das fileiras de ícones de efeito que estiverem na tela. */
    private static int effectsTop(Minecraft minecraft, long now) {
        if (now - effectsCheckedAt < 250L) return effectsTop;
        effectsCheckedAt = now;
        boolean good = false, bad = false;
        if (minecraft.player != null) {
            for (MobEffectInstance effect : minecraft.player.getActiveEffects()) {
                if (!effect.showIcon()) continue;
                if (effect.getEffect().value().isBeneficial()) good = true;
                else bad = true;
            }
        }
        effectsTop = bad ? 56 : good ? 30 : MARGIN;
        return effectsTop;
    }

    /**
     * A pilha na âncora da config. Nas âncoras de topo e meio ela desce de {@code start} até
     * {@code limit}; nas {@code *_BAIXO} sobe de {@code start} (a base) até {@code limit} (o teto). O
     * que não cabe fica escondido até abrir espaço (o tempo dele corre).
     */
    private static void renderCards(GuiGraphics g, Font font, Minecraft minecraft, long now, float dt, boolean hidden, boolean bosses) {
        if (CARDS.isEmpty()) return;
        Ancora anchor = FichaClientConfig.get(FichaClientConfig.CARDS_ANCHOR);
        float scale = FichaClientConfig.scale(FichaClientConfig.CARDS_SCALE);
        int offX = FichaClientConfig.get(FichaClientConfig.CARDS_X), offY = FichaClientConfig.get(FichaClientConfig.CARDS_Y);
        hideSecrets = HudCompat.hideSecrets();
        hideReason = hideSecrets ? HudCompat.hideReason() : "";
        int gh = g.guiHeight();
        int bigW = Math.round(BIG_W * scale);
        int sx = anchorX(g, anchor, bigW, offX);
        boolean up = anchor.bottom();
        int start = 0, limit = gh;
        if (!hidden && up) {
            start = (anchor.right() ? rightBottom(g, minecraft, sx, bigW)
                    : Math.min(chatTop(g, minecraft) - 6, overHotbar(g, sx, bigW) ? gh - 64 : gh)) + offY;
            limit = anchor.right() ? rightTop(g, minecraft, now, bosses, sx, bigW) : belowBosses(bosses, sx, bigW, MARGIN);
        } else if (!hidden) {
            start = switch (anchor) {
                case DIREITA_TOPO -> rightTop(g, minecraft, now, bosses, sx, bigW);
                case DIREITA_MEIO -> Math.max(rightTop(g, minecraft, now, bosses, sx, bigW), Math.round(gh * MIDDLE));
                case ESQUERDA_MEIO -> belowBosses(bosses, sx, bigW, Math.round(gh * MIDDLE));
                case CENTRO_TOPO -> belowBosses(bosses, sx, bigW, Math.max(CENTER_TOP, Math.round(gh * 0.18f)));
                default -> belowBosses(bosses, sx, bigW, MARGIN);
            } + offY;
            limit = anchor.right() ? rightBottom(g, minecraft, sx, bigW)
                    : anchor.center() ? gh / 2 - 12
                    : Math.min(chatTop(g, minecraft) - 6, overHotbar(g, sx, bigW) ? gh - 64 : gh);
        }
        int side = anchor.right() ? 1 : anchor.left() ? -1 : 0;
        int cursor = start;
        float follow = dt <= 0 ? 1f : 1f - (float) Math.exp(-dt / 70.0);
        // Duas passadas: os cartões grandes (seus) sempre na ponta da pilha, os dos vizinhos depois.
        for (int pass = 0; pass < 2; pass++) {
            boolean bigPass = pass == 0;
            for (int i = 0; i < CARDS.size(); i++) {
                Shown s = CARDS.get(i);
                if (s.big != bigPass) continue;
                long age = now - s.start;
                if (age >= s.life) {
                    CARDS.remove(i--);
                    continue;
                }
                if (hidden) {
                    s.y = Float.NaN;
                    continue;
                }
                int w = Math.round((s.big ? BIG_W : SMALL_W) * scale);
                int h = Math.round((s.big ? BIG_H : SMALL_H) * scale);
                int x0 = anchor.right() ? sx + bigW - w : anchor.center() ? sx + (bigW - w) / 2 : sx;
                int y = up ? cursor - h : cursor;
                // A pilha pula o pedido/aviso em vez de cobri-lo (mesma âncora, ou tela estreita).
                if (blockX1 > 0 && x0 < blockX1 + GAP && x0 + w > blockX0 - GAP && y < blockY1 + GAP && y + h > blockY0 - GAP) {
                    y = up ? blockY0 - GAP - h : blockY1 + GAP;
                }
                if (up ? y < limit : y + h > limit) {
                    s.y = Float.NaN;
                    continue;
                }
                s.prepare(font, hideSecrets, hideReason);
                s.y = Float.isNaN(s.y) ? y : s.y + (y - s.y) * follow;
                float enter = ease(age, ENTER_MS);
                float fade = age > s.life - FADE_MS ? (s.life - age) / (float) FADE_MS : 1f;
                float a = fade * (0.3f + 0.7f * enter);
                float dx = side * (Math.round((1f - enter) * 40) + Math.round((1f - fade) * 14));
                float dy = side == 0 ? -(1f - enter) * 8 : 0f;
                push(g, x0 + dx, Math.round(s.y) + dy, scale);
                if (s.big) drawBig(g, font, s, 0, 0, age, a);
                else drawSmall(g, font, s, 0, 0, age, a);
                g.pose().popPose();
                cursor = up ? y - GAP : y + h + GAP;
            }
        }
    }

    /** O tipo do teste no alto do cartão: "TESTE · COM VANTAGEM", "SECRETO · PEDIDO DO MESTRE". */
    private static String kicker(RollCard card) {
        RollResult roll = card.roll();
        String what;
        if (card.gm() && !card.own()) what = card.displayName().toUpperCase(Locale.ROOT);
        else if (!card.requestId().isEmpty()) what = "PEDIDO DO MESTRE";
        else what = switch (roll.kind()) {
            case "free" -> "ROLAGEM LIVRE";
            case "attribute" -> "TESTE DE ATRIBUTO";
            default -> "TESTE";
        };
        String mode = Texts.modeLabel(roll.mode()).toUpperCase(Locale.ROOT);
        return (card.secret() ? "SECRETO · " : "") + what + (mode.isEmpty() ? "" : " · " + mode);
    }

    /**
     * Rolagem secreta com segredos escondidos: d20 apagado com "?", "Rolagem secreta" e o motivo —
     * nem natural, nem total, nem a cor do resultado (que diria sucesso ou falha).
     */
    private static void drawMasked(GuiGraphics g, Font font, Shown s, int x, int y, long age, float a) {
        int w = s.big ? BIG_W : SMALL_W, h = s.big ? BIG_H : SMALL_H;
        FichaUi.card(g, x, y, w, h, FichaUi.alpha(s.big ? BG : 0xE0120822, a), FichaUi.alpha(0xFF3A2766, a));
        g.fill(x + 1, y + 1, x + 3, y + h - 1, FichaUi.alpha(FichaUi.VIOLET, 0.7f * a));
        if (s.big) {
            D20.draw(g, font, x + 24, y + h / 2, 17, D20.dim(), "?", 1f, false, a);
            int tx = x + 47;
            FichaUi.text(g, font, s.kicker, tx, y + 6, FichaUi.alpha(FichaUi.FAINT, a), 0.62f, false);
            FichaUi.text(g, font, s.label, tx, y + 14, FichaUi.alpha(FichaUi.SOFT, a), 1f, true);
            FichaUi.text(g, font, s.line, tx, y + 27, FichaUi.alpha(FichaUi.FAINT, a), 0.7f, false);
            int barW = Math.round((w - 8) * Math.max(0f, 1f - age / (float) s.life));
            if (barW > 0) g.fill(x + 4, y + h - 3, x + 4 + barW, y + h - 2, FichaUi.alpha(FichaUi.VIOLET, 0.4f * a));
        } else {
            D20.draw(g, font, x + 14, y + h / 2, 9, D20.dim(), "?", 1f, false, a);
            FichaUi.text(g, font, s.kicker, x + 27, y + 4, FichaUi.alpha(FichaUi.FAINT, a), 0.62f, false);
            FichaUi.text(g, font, s.label, x + 27, y + 12, FichaUi.alpha(FichaUi.SOFT, a), 0.8f, false);
        }
    }

    private static void drawBig(GuiGraphics g, Font font, Shown s, int x, int y, long age, float a) {
        if (s.masked) {
            drawMasked(g, font, s, x, y, age, a);
            return;
        }
        RollResult roll = s.card.roll();
        int w = BIG_W, h = BIG_H;
        boolean landed = !D20.spinning(age);
        float reveal = D20.reveal(age);
        int accent = landed ? s.accent : FichaUi.VIOLET;
        boolean crit = landed && D20.critical(roll);
        long after = age - D20.SPIN_MS;
        // Crítico: a borda pulsa um pouco ao pousar e depois fica acesa (pulsar 9 s cansa).
        float glow = crit ? (after < CRIT_GLOW_MS ? 0.7f + 0.3f * (float) Math.sin(after / 110.0) : 1f) : 0.75f;
        FichaUi.card(g, x, y, w, h, FichaUi.alpha(BG, a), FichaUi.alpha(accent, glow * a));
        // Ao pousar, um clarão rápido da cor do resultado no fundo.
        if (after >= 0 && after < 260) {
            g.fill(x + 1, y + 1, x + w - 1, y + h - 1, FichaUi.alpha(accent, 0.18f * (1f - after / 260f) * a));
        }
        if (crit) g.fill(x + 3, y + 1, x + w - 3, y + 2, FichaUi.alpha(FichaUi.CRIT, 0.8f * a));

        D20.drawRoll(g, font, roll, x + 24, y + h / 2, 17, age, s.seed, a);

        int tx = x + 47;
        FichaUi.text(g, font, s.kicker, tx, y + 6, FichaUi.alpha(FichaUi.FAINT, a), 0.62f, false);
        FichaUi.text(g, font, s.label, tx, y + 14, FichaUi.alpha(FichaUi.GOLD_HI, a), 1f, true);
        int cx = x + w - 4 - 22;
        if (!landed) {
            FichaUi.text(g, font, "rolando…", tx, y + 26, FichaUi.alpha(FichaUi.SOFT, a), 0.7f, false);
            FichaUi.textCenter(g, font, "…", cx, y + h / 2f - 7, FichaUi.alpha(FichaUi.FAINT, a), 1.5f, false);
        } else {
            // Revelar: a linha e o resultado sobem 2 px enquanto aparecem; o total encolhe até o tamanho.
            float ra = a * reveal;
            float lift = (1f - reveal) * 2f;
            FichaUi.text(g, font, s.line, tx, y + 26 + lift, FichaUi.alpha(FichaUi.SOFT, ra), 0.7f, false);
            if (!s.outcome.isEmpty()) {
                g.fill(tx - 2, y + 35, tx + s.outcomeW + 2, y + 43, FichaUi.alpha(accent, 0.16f * ra));
                FichaUi.text(g, font, s.outcome, tx, y + 36 + lift, FichaUi.alpha(accent, ra), 0.8f, false);
            }
            float scale = s.totalScale * (1f + 0.3f * (1f - reveal));
            FichaUi.text(g, font, s.total, cx - s.totalW * scale / 2f, y + h / 2f - 4.5f * scale - 2, FichaUi.alpha(accent, ra), scale, true);
            FichaUi.text(g, font, "TOTAL", cx - totalLabelW(font) / 2f, y + h - 12, FichaUi.alpha(FichaUi.FAINT, ra), 0.55f, false);
        }

        // O tempo de vida do cartão, discreto, na base.
        int barW = Math.round((w - 8) * Math.max(0f, 1f - age / (float) s.life));
        if (barW > 0) g.fill(x + 4, y + h - 3, x + 4 + barW, y + h - 2, FichaUi.alpha(accent, 0.4f * a));
    }

    private static float totalLabelW = -1f;

    private static float totalLabelW(Font font) {
        if (totalLabelW < 0) totalLabelW = font.width("TOTAL") * 0.55f;
        return totalLabelW;
    }

    /** O resultado em uma palavra, para o cartão pequeno. */
    private static String shortOutcome(RollResult roll) {
        String outcome = Texts.outcome(roll);
        if (outcome.startsWith("CRÍTICO")) return "CRÍTICO";
        if (outcome.equals("FALHA CRÍTICA")) return "FALHA CRÍT.";
        return outcome;
    }

    private static void drawSmall(GuiGraphics g, Font font, Shown s, int x, int y, long age, float a) {
        if (s.masked) {
            drawMasked(g, font, s, x, y, age, a);
            return;
        }
        RollResult roll = s.card.roll();
        int w = SMALL_W, h = SMALL_H;
        boolean landed = !D20.spinning(age);
        int accent = landed ? s.accent : FichaUi.VIOLET;
        FichaUi.card(g, x, y, w, h, FichaUi.alpha(0xE0120822, a), FichaUi.alpha(0xFF3A2766, a));
        g.fill(x + 1, y + 1, x + 3, y + h - 1, FichaUi.alpha(accent, a));
        D20.drawRoll(g, font, roll, x + 14, y + h / 2, 9, age, s.seed, a);
        int tx = x + 27;
        FichaUi.text(g, font, s.kicker, tx, y + 4, FichaUi.alpha(FichaUi.FAINT, a), 0.62f, false);
        FichaUi.text(g, font, s.label, tx, y + 12, FichaUi.alpha(FichaUi.INK, a), 0.8f, false);
        if (landed) {
            float ra = a * D20.reveal(age);
            float right = x + w - 6;
            FichaUi.text(g, font, s.total, right - s.totalW * 1.3f, y + (s.outcome.isEmpty() ? 7 : 3), FichaUi.alpha(accent, ra), 1.3f, true);
            if (!s.outcome.isEmpty()) FichaUi.text(g, font, s.outcome, right - s.outcomeW, y + 16, FichaUi.alpha(accent, ra), 0.55f, false);
        }
    }
}
