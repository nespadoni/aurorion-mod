package com.aurorion.magia.client;

import com.aurorion.magia.AurorionMagia;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.sounds.SoundSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;

/**
 * O que a {@link FlashLayer} sozinha nao fecha, para o Clarao cegar de verdade:
 *
 * <ul>
 *   <li><b>Telas abertas.</b> O HUD fica embaixo do inventario e do ESC; sem isto, abrir uma tela
 *       deixaria ver o mundo atras dela. O branco e desenhado por cima de qualquer tela.</li>
 *   <li><b>Som abafado</b>, como na flashbang do CS: o volume geral cai para 15% no auge e volta junto
 *       com a visao. Nunca chega a zero — o vanilla para todos os sons quando o volume geral e zero.</li>
 * </ul>
 *
 * <p>O volume e o do proprio jogador, lido das opcoes a cada tick; ao acabar o clarao (ou sair do
 * servidor) volta exatamente para ele.
 */
@EventBusSubscriber(modid = AurorionMagia.MOD_ID, value = Dist.CLIENT)
public final class FlashBlindness {
    private static final float MUFFLED = 0.15F;
    private static boolean muffled;

    private FlashBlindness() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onScreenRender(ScreenEvent.Render.Post event) {
        float alpha = FlashLayer.alpha(event.getPartialTick());
        if (alpha <= 0.003F) return;
        // Itens (z 150), contagens (z 350) e tooltips (z 400) passam no teste de profundidade de um fill
        // em z 0 e apareceriam atraves do branco. Bem na frente de tudo, mas dentro do plano distante.
        GuiGraphics graphics = event.getGuiGraphics();
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 2000);
        FlashLayer.draw(graphics, alpha);
        graphics.pose().popPose();
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        float blind = FlashLayer.alpha(0);
        if (blind <= 0.003F) {
            if (muffled) restore(minecraft);
            return;
        }
        float master = minecraft.options.getSoundSourceVolume(SoundSource.MASTER);
        if (master <= 0) return;
        muffled = true;
        minecraft.getSoundManager().updateSourceVolume(SoundSource.MASTER,
                master * (MUFFLED + (1 - MUFFLED) * (1 - blind)));
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        if (muffled) restore(Minecraft.getInstance());
    }

    private static void restore(Minecraft minecraft) {
        muffled = false;
        float master = minecraft.options.getSoundSourceVolume(SoundSource.MASTER);
        if (master > 0) minecraft.getSoundManager().updateSourceVolume(SoundSource.MASTER, master);
    }
}
