package com.aurorion.vidas.client;

import com.aurorion.vidas.AurorionVidas;
import com.aurorion.vidas.config.LivesClientConfig;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.resources.ResourceLocation;

/**
 * A fileira de vidas, logo acima da barra de fome.
 *
 * <p>A posicao nao e chutada em pixels: usa o mesmo par de referencias que o vanilla usa para
 * empilhar as linhas do canto direito — {@code guiWidth()/2 + 91} como borda direita e
 * {@link Gui#rightHeight} como altura corrente — e incrementa {@code rightHeight} ao terminar. E
 * isso que faz a fileira acompanhar a barra de fome quando algo entra ou sai da pilha (montar num
 * cavalo, bolhas de ar), em vez de sobrepor.
 *
 * <p>A camada e registrada <em>acima</em> de {@code FOOD_LEVEL}, mas uma camada de mod nao herda a
 * condicao interna que esconde o HUD vanilla. O estado de F1 e conferido no inicio do render.
 *
 * <p>Custo por frame: nenhuma alocacao e no maximo {@code maxLives} blits de 9x9 — e nada quando o
 * servidor ainda nao informou as vidas (SDD §2, zero alocacao no caminho quente do cliente).
 */
public final class LivesHudLayer implements LayeredDraw.Layer {
    public static final ResourceLocation ID =
            ResourceLocation.fromNamespaceAndPath(AurorionVidas.MOD_ID, "lives");

    /**
     * Os sprites. Para trocar a arte, e so substituir os PNG em
     * {@code assets/aurorion_vidas/textures/gui/sprites/hud/} — nao ha nada a recompilar, e da para
     * fazer por resource pack sem tocar no jar.
     */
    private static final ResourceLocation LIFE_FULL =
            ResourceLocation.fromNamespaceAndPath(AurorionVidas.MOD_ID, "hud/life_full");
    private static final ResourceLocation LIFE_EMPTY =
            ResourceLocation.fromNamespaceAndPath(AurorionVidas.MOD_ID, "hud/life_empty");

    /** Medidas do vanilla: icone 9x9, passo 8 (sobrepoem 1px), linha ocupa 10 de altura. */
    private static final int ICON = 9;
    private static final int STEP = 8;
    private static final int ROW_HEIGHT = 10;

    @Override
    public void render(GuiGraphics graphics, DeltaTracker delta) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.options.hideGui || minecraft.player == null) return;
        if (!LivesClientConfig.SHOW_HUD.get()) return;
        // max = 0 significa "servidor nao falou nada" — melhor nao desenhar que desenhar chutando.
        if (!ClientLives.known()) return;

        Gui gui = minecraft.gui;
        int right = graphics.guiWidth() / 2 + 91;
        int y = graphics.guiHeight() - gui.rightHeight;

        int lives = ClientLives.lives();
        int max = ClientLives.max();

        // Desenha da direita para a esquerda, igual a fome: as vidas que sobram ficam encostadas na
        // borda e o vazio cresce para o centro da tela.
        for (int i = 0; i < max; i++) {
            graphics.blitSprite(i < lives ? LIFE_FULL : LIFE_EMPTY, right - i * STEP - ICON, y, ICON, ICON);
        }

        gui.rightHeight += ROW_HEIGHT;
    }
}
