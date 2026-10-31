package com.aurorion.ficha.client;

import com.aurorion.ficha.AurorionFicha;
import com.aurorion.ficha.config.FichaClientConfig;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * O que o HUD da ficha precisa saber de outros mods, sem depender deles: onde o minimapa do
 * JourneyMap está e se o modo streamer do Aurorion Essentials está ligado.
 *
 * <p>Os dois são lidos por reflexão, com o mod conferido no {@link ModList} antes: sem o mod (ou com
 * uma versão que mudou os nomes), a leitura falha uma vez, avisa no log e fica desligada — o HUD
 * segue com a config. Nada aqui roda por frame: o minimapa é conferido no máximo a cada 1 s e o modo
 * streamer a cada 500 ms, e só enquanto há cartão na tela.
 */
@EventBusSubscriber(modid = AurorionFicha.MOD_ID, value = Dist.CLIENT)
public final class HudCompat {
    private HudCompat() {
    }

    // ── Modo streamer (aurorion-essentials) ─────────────────────────────────────

    private static final String ESSENTIALS = "aurorion_essentials";
    /** {@code StreamerConfig.MODE}: um {@code EnumValue<StreamerMode>} (OFF, OPERATIONS, ALL_SYSTEM). */
    private static final String STREAMER_CONFIG = "com.aurorion.essentials.streamer.StreamerConfig";
    @Nullable
    private static ModConfigSpec.ConfigValue<?> streamerMode;
    private static boolean streamerTried;
    private static long streamerCheckedAt;
    private static boolean streamer;

    /** O modo streamer do Essentials está ligado neste cliente ({@code /streamer on} ou {@code total})? */
    static boolean streamer() {
        long now = Util.getMillis();
        if (streamerTried && now - streamerCheckedAt < 500L) return streamer;
        streamerCheckedAt = now;
        if (!streamerTried) {
            streamerTried = true;
            if (ModList.get() != null && ModList.get().isLoaded(ESSENTIALS)) {
                try {
                    Object value = Class.forName(STREAMER_CONFIG).getField("MODE").get(null);
                    if (value instanceof ModConfigSpec.ConfigValue<?> config) streamerMode = config;
                    else AurorionFicha.LOGGER.warn("Ficha: StreamerConfig.MODE do Essentials mudou de tipo; o HUD não segue o modo streamer (use ocultarSegredos)");
                } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
                    AurorionFicha.LOGGER.warn("Ficha: modo streamer do Essentials não encontrado ({}); o HUD não segue o /streamer (use ocultarSegredos)", e.toString());
                }
            }
        }
        ModConfigSpec.ConfigValue<?> config = streamerMode;
        boolean on = false;
        if (config != null) {
            try {
                Object mode = config.get();
                on = mode instanceof Enum<?> e ? !"OFF".equals(e.name()) : mode != null && !"OFF".equals(String.valueOf(mode));
            } catch (IllegalStateException e) {
                on = false;
            }
        }
        streamer = on;
        return on;
    }

    /** {@code /ficha teste}: simula o modo streamer sem o Essentials (só nesta sessão; {@code false} desfaz). */
    private static boolean preview;

    static void previewStreamer(boolean on) {
        preview = on;
    }

    /** Os cartões escondem segredos agora? (modo streamer seguido pela config, ou {@code ocultarSegredos}). */
    static boolean hideSecrets() {
        if (preview || FichaClientConfig.get(FichaClientConfig.HIDE_SECRETS)) return true;
        return FichaClientConfig.get(FichaClientConfig.FOLLOW_STREAMER) && streamer();
    }

    /** Por que os cartões escondem: "modo streamer" ou "HUD sem segredos" (ou vazio). */
    static String hideReason() {
        if (preview || FichaClientConfig.get(FichaClientConfig.FOLLOW_STREAMER) && streamer()) return "modo streamer";
        return FichaClientConfig.get(FichaClientConfig.HIDE_SECRETS) ? "HUD sem segredos" : "";
    }

    // ── Minimapa do JourneyMap ──────────────────────────────────────────────────

    private static final String JOURNEYMAP = "journeymap";
    private static boolean mapTried, mapBroken;
    @Nullable
    private static Object mapUi;
    @Nullable
    private static Method mapGetMinimap, mapGetProps;
    @Nullable
    private static Field fEnabled, fPosition, fSize, fPosX, fPosY;
    private static final Field[] F_LABELS = new Field[4];
    private static final Field[] F_LABEL_POS = new Field[4];
    private static long mapCheckedAt;
    /** O último estado lido: posição ({@code TopRight}…), % da altura, rótulos acima/abaixo, centro do Custom. */
    private static String mapPosition = "";
    private static int mapSizePercent, mapLabelsTop, mapLabelsBottom;
    private static float mapX, mapY;

    /** Lê o minimapa ativo do JourneyMap (no máximo a cada 1 s). Falso se não há minimapa ligado ou legível. */
    private static boolean readMinimap() {
        if (!FichaClientConfig.get(FichaClientConfig.AVOID_MINIMAP)) return false;
        long now = Util.getMillis();
        if (mapTried && now - mapCheckedAt < 1_000L) return !mapPosition.isEmpty();
        mapCheckedAt = now;
        if (!mapTried) {
            mapTried = true;
            mapBroken = ModList.get() == null || !ModList.get().isLoaded(JOURNEYMAP) || !bindMinimap();
        }
        if (mapBroken) {
            mapPosition = "";
            return false;
        }
        try {
            Object minimap = mapGetMinimap.invoke(mapUi);
            Object props = minimap == null ? null : mapGetProps.invoke(minimap);
            if (props == null || !Boolean.TRUE.equals(value(fEnabled, props))) {
                mapPosition = "";
                return false;
            }
            Object position = value(fPosition, props);
            mapPosition = position instanceof Enum<?> e ? e.name() : String.valueOf(position);
            mapSizePercent = value(fSize, props) instanceof Number n ? n.intValue() : 30;
            mapX = value(fPosX, props) instanceof Number n ? n.floatValue() : 0.9f;
            mapY = value(fPosY, props) instanceof Number n ? n.floatValue() : 0.25f;
            int top = 0, bottom = 0;
            for (int i = 0; i < 4; i++) {
                Object label = F_LABELS[i] == null ? null : value(F_LABELS[i], props);
                if (label == null || String.valueOf(label).endsWith(".blank")) continue;
                Object where = F_LABEL_POS[i] == null ? null : value(F_LABEL_POS[i], props);
                if (where != null && String.valueOf(where).equalsIgnoreCase("Top")) top++;
                else bottom++;
            }
            mapLabelsTop = top;
            mapLabelsBottom = bottom;
            return true;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            mapBroken = true;
            mapPosition = "";
            AurorionFicha.LOGGER.warn("Ficha: não deu para ler o minimapa do JourneyMap ({}); a pilha segue só a config", e.toString());
            return false;
        }
    }

    /** {@code UIManager.INSTANCE.getMiniMap().getCurrentMinimapProperties()} e os campos que importam. */
    private static boolean bindMinimap() {
        try {
            Class<?> ui = Class.forName("journeymap.client.ui.UIManager");
            mapUi = ui.getField("INSTANCE").get(null);
            mapGetMinimap = ui.getMethod("getMiniMap");
            Class<?> minimap = Class.forName("journeymap.client.ui.minimap.MiniMap");
            mapGetProps = minimap.getMethod("getCurrentMinimapProperties");
            Class<?> props = Class.forName("journeymap.client.properties.MiniMapProperties");
            fEnabled = props.getField("enabled");
            fPosition = props.getField("position");
            fSize = props.getField("sizePercent");
            fPosX = props.getField("positionX");
            fPosY = props.getField("positionY");
            for (int i = 0; i < 4; i++) {
                try {
                    F_LABELS[i] = props.getField("info" + (i + 1) + "Label");
                    F_LABEL_POS[i] = props.getField("info" + (i + 1) + "LabelPosition");
                } catch (NoSuchFieldException ignored) {
                    // Versão sem os rótulos: conta só o mapa.
                }
            }
            return mapUi != null;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            AurorionFicha.LOGGER.warn("Ficha: JourneyMap sem a API esperada ({}); a pilha segue só a config", e.toString());
            return false;
        }
    }

    /** {@code campo.get()} de um {@code BooleanField}/{@code IntegerField}/{@code EnumField} do JourneyMap. */
    @Nullable
    private static Object value(@Nullable Field field, Object props) throws ReflectiveOperationException {
        if (field == null) return null;
        Object holder = field.get(props);
        if (holder == null) return null;
        return holder.getClass().getMethod("get").invoke(holder);
    }

    /** Altura do minimapa na GUI: o JourneyMap usa {@code max(128, sizePercent% da altura)} em pixels reais. */
    private static int mapHeight(Minecraft minecraft, int guiHeight) {
        double guiScale = Math.max(1.0, minecraft.getWindow().getGuiScale());
        double real = Math.max(128.0, Math.floor(mapSizePercent / 100.0 * minecraft.getWindow().getScreenHeight()));
        return (int) Math.ceil(real / guiScale);
    }

    /** Altura de uma faixa de rótulo (local, bioma…) do JourneyMap na GUI, com folga. */
    private static final int LABEL_H = 11;

    /**
     * Onde termina (embaixo) o minimapa que ocupa o lado direito do topo, ou 0 se não há. Inclui os
     * rótulos de cima e de baixo e uma folga para a moldura/bússola.
     */
    static int minimapTopRightBottom(Minecraft minecraft, int guiWidth, int guiHeight) {
        if (!readMinimap()) return 0;
        int size = mapHeight(minecraft, guiHeight);
        int labels = (mapLabelsTop + mapLabelsBottom) * LABEL_H;
        return switch (mapPosition) {
            case "TopRight" -> size + labels + 12;
            // Custom: positionX/positionY são o centro do mapa (só conta se ele estiver na metade direita).
            case "Custom" -> mapX >= 0.5f ? Math.round(mapY * guiHeight) + size / 2 + mapLabelsBottom * LABEL_H + 12 : 0;
            default -> 0;
        };
    }

    /** Onde começa (em cima) o minimapa no canto inferior direito, ou {@code guiHeight} se não há. */
    static int minimapBottomRightTop(Minecraft minecraft, int guiWidth, int guiHeight) {
        if (!readMinimap() || !"BottomRight".equals(mapPosition)) return guiHeight;
        int size = mapHeight(minecraft, guiHeight);
        return guiHeight - size - (mapLabelsTop + mapLabelsBottom) * LABEL_H - 12;
    }

    // ── Selo na mesa do mestre ──────────────────────────────────────────────────

    // A geometria do cabeçalho da MestreScreen (MAX_W, MAX_H, PAD e os dois botões da direita): o selo
    // fica alinhado à direita na linha do "MESA DO MESTRE", antes de "Atualizar" e "×".
    private static final int MESA_MAX_W = 476, MESA_MAX_H = 292, MESA_PAD = 7;
    private static final String BADGE_TIP = "Os cartões da ficha no HUD estão mostrando só o que um vizinho veria:"
            + " rolagem secreta sem números, sem CD oculta e com o bônus como \"?\". A mesa continua mostrando tudo."
            + "\n/streamer off desliga o modo streamer; ocultarSegredos fica em ficha-client.toml.";
    @Nullable
    private static FichaUi.Tooltip badgeTip;
    private static String badgeText = "";
    private static int badgeW;

    /** O selo discreto "MODO STREAMER" na mesa do mestre, enquanto os cartões escondem segredos. */
    @SubscribeEvent
    public static void onScreenRender(ScreenEvent.Render.Post event) {
        if (!(event.getScreen() instanceof MestreScreen screen)) return;
        String reason = hideReason();
        if (reason.isEmpty()) return;
        GuiGraphics g = event.getGuiGraphics();
        Font font = Minecraft.getInstance().font;
        String text = reason.toUpperCase(java.util.Locale.ROOT);
        if (!text.equals(badgeText)) {
            badgeText = text;
            badgeW = Math.round(font.width(text) * 0.55f) + 8;
            badgeTip = FichaUi.tooltip(font, BADGE_TIP, Math.min(220, screen.width / 2));
        }
        int w = Math.min(screen.width - 12, MESA_MAX_W);
        int h = Math.min(screen.height - 12, MESA_MAX_H);
        int x = (screen.width - w) / 2, y = (screen.height - h) / 2;
        int ix = x + MESA_PAD, iw = w - 2 * MESA_PAD;
        int refreshW = Math.max(FichaUi.buttonWidth(font, "Atualizar", 6, 7, true, false), FichaUi.buttonWidth(font, "Buscando…", 6, 7, true, false));
        int closeW = FichaUi.buttonWidth(font, "×", 8, 5, false, false);
        int bx = ix + iw - refreshW - 3 - closeW - 8 - badgeW, by = y + MESA_PAD;
        // Mesa estreita: sem espaço depois do título, o selo não entra (não cobre o cabeçalho).
        if (bx < ix + 80) return;
        g.pose().pushPose();
        g.pose().translate(0, 0, 300);
        FichaUi.card(g, bx, by, badgeW, 8, 0xE0221338, FichaUi.alpha(FichaUi.WARN, 0.75f));
        FichaUi.text(g, font, text, bx + 4, by + 2f, FichaUi.WARN, 0.55f, false);
        g.pose().popPose();
        int mx = event.getMouseX(), my = event.getMouseY();
        FichaUi.Tooltip tip = badgeTip;
        if (tip != null && mx >= bx && mx < bx + badgeW && my >= by && my < by + 8) {
            FichaUi.drawTooltip(g, font, tip, mx, my, screen.width, screen.height);
        }
    }
}
