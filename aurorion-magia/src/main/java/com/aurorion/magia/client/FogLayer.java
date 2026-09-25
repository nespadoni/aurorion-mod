package com.aurorion.magia.client;

import com.aurorion.core.client.ScreenFog;
import com.aurorion.core.client.ShaderPacks;
import com.aurorion.magia.AurorionMagia;
import com.aurorion.magia.registry.MagiaEffects;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/**
 * A neblina das magias, desenhada em espaco de tela — o caminho que sobrevive a shader pack.
 *
 * <h2>O problema</h2>
 *
 * <p>{@code ViewportEvent.RenderFog} e um evento do pipeline do vanilla. Com o Iris carregando um
 * pacote de shaders, quem calcula a neblina passa a ser o shader, e o plano distante que a gente pede
 * simplesmente nao e consultado. Resultado: a escuridao do Devorar Luz e a nevoa da Presenca
 * Aterradora <b>sumiam</b> para quem joga com BSL, Complementary ou Solas, que e quase todo mundo.
 *
 * <p>A <b>Escuridao</b> que a Presenca Aterradora poe em quem esta dentro dela nao depende disto: ela e
 * um efeito do vanilla, apaga a luz do mundo e o shader pack a respeita como respeita a noite. Esta
 * camada e a nevoa preta que fecha o ar por cima dessa escuridao.
 *
 * <p>Esta camada e a outra metade da solucao. Enquanto houver shader ligado, a neblina e pintada aqui,
 * depois de o quadro estar composto, onde nenhum pacote interfere; sem shader, ela continua sendo a
 * neblina de verdade do {@code MagiaClientEvents}, que fica melhor. O interruptor entre os dois e
 * {@link ShaderPacks}, e ele vira sozinho quando o jogador aperta K.
 *
 * <p>As particulas (fumaça negra, vultos, bolhas, vento) nao precisam de nada disso: particula passa
 * pelo pipeline do shader como qualquer outra do jogo, e por isso ela e o corpo do efeito e a neblina
 * e so o ar em volta.
 *
 * <p>Registrada <b>abaixo</b> de {@code CAMERA_OVERLAYS}: neblina e cenario, e nao pode cobrir a barra
 * de itens nem o chat. Ela some com F1 junto com o resto do HUD, e tudo bem — quem escondeu o HUD
 * esta tirando foto.
 */
public final class FogLayer implements LayeredDraw.Layer {
    public static final ResourceLocation ID = AurorionMagia.id("nevoa");

    /** A cor do ar de cada magia. O medo nao tem cor: e o preto de nao haver ar nenhum. */
    private static final int DARK = 0x02000A;
    private static final int TERROR = 0x000000;

    /** Suavizacao: a nevoa fecha e abre em rampa, e nao de um quadro para o outro. */
    private static float darkness;
    private static float terror;

    @Override
    public void render(GuiGraphics graphics, DeltaTracker delta) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null) return;

        Vec3 camera = minecraft.gameRenderer.getMainCamera().getPosition();
        float toDarkness = ClientSpellVisuals.darkness(camera);
        // O medo so fecha o ar de quem esta com medo: quem carrega a aura enxerga a praça normalmente.
        float toTerror = player.hasEffect(MagiaEffects.TERRIFIED)
                ? ClientSpellVisuals.terror(minecraft.level, camera)
                : 0;

        if (!minecraft.isPaused()) {
            darkness += (toDarkness - darkness) * .08F;
            terror += (toTerror - terror) * .06F;
        }
        if (!ShaderPacks.inUse()) return;

        // Sem shader estes dois pesos viram neblina de verdade em MagiaClientEvents; com shader, aqui.
        ScreenFog.draw(graphics, DARK, darkness * .95F);
        ScreenFog.draw(graphics, TERROR, terror);
    }

    /** Sair do mundo com a tela meio fechada nao pode deixar a proxima entrada escura. */
    static void clear() {
        darkness = 0;
        terror = 0;
    }
}
