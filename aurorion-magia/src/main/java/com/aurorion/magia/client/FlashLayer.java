package com.aurorion.magia.client;

import com.aurorion.magia.AurorionMagia;
import com.aurorion.magia.registry.MagiaEffects;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;

/**
 * O Clarao, visto de dentro: a tela inteira branca, como a flashbang do CS. Registrada <b>acima de
 * tudo</b> — hotbar, chat, mira, F3 —, porque uma granada de atordoar que deixa a barra de vida desenhada
 * por cima nao cega ninguem. Telas abertas (inventario, ESC) sao cobertas tambem, em {@link FlashBlindness}.
 *
 * <p>Branco 100% opaco ate os ultimos {@value #FADE_TICKS} ticks; dai o olho volta devagar, numa curva
 * que fica clara por muito tempo e so solta no fim — nao um degrade linear, que pareceria um filtro.
 *
 * <p>Nao ha pacote: o cliente le o efeito {@code ofuscado} do proprio jogador. Fora dele, a camada sai
 * na primeira linha. Ignora a opcao de distorcao de proposito: e um branco liso, sem movimento nenhum, e
 * suprimi-lo tiraria a magia de quem a desligou.
 */
public final class FlashLayer implements LayeredDraw.Layer {
    public static final net.minecraft.resources.ResourceLocation ID = AurorionMagia.id("clarao");
    private static final float FADE_TICKS = 50F;

    @Override
    public void render(GuiGraphics graphics, DeltaTracker delta) {
        draw(graphics, alpha(delta.getGameTimeDeltaPartialTick(false)));
    }

    /** 1 = cego por completo, 0 = enxergando. */
    static float alpha(float partial) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return 0;
        MobEffectInstance flash = player.getEffect(MagiaEffects.FLASHED);
        if (flash == null) return 0;
        float left = Mth.clamp((flash.getDuration() - partial) / FADE_TICKS, 0, 1);
        // Raiz: no meio do fade ainda esta ~70% branco. A visao so volta de verdade no ultimo segundo.
        return (float) Math.sqrt(left);
    }

    static void draw(GuiGraphics graphics, float alpha) {
        if (alpha <= 0.003F) return;
        int a = Math.round(alpha * 255) << 24;
        // Fim do clarao com um resto quente na retina, em vez de branco de papel.
        int rgb = alpha >= 1 ? 0xFFFFFF : 0xFFFBF0;
        graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), a | rgb);
    }
}
