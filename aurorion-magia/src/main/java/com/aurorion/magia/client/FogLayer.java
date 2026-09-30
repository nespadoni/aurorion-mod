package com.aurorion.magia.client;

import com.aurorion.core.client.ScreenFog;
import com.aurorion.core.client.ShaderPacks;
import com.aurorion.magia.AurorionMagia;
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
 * <p>A Presenca Aterradora <b>nao</b> passa por aqui: quem esta dentro dela tem que continuar vendo a
 * cena inteira. O medo e so a sombra nos cantos da tela ({@code PossessionLayer}).
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

    /** A cor do ar do Devorar Luz. */
    private static final int DARK = 0x02000A;

    /** Suavizacao: a nevoa fecha e abre em rampa, e nao de um quadro para o outro. */
    private static float darkness;

    @Override
    public void render(GuiGraphics graphics, DeltaTracker delta) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null) return;

        Vec3 camera = minecraft.gameRenderer.getMainCamera().getPosition();
        float toDarkness = ClientSpellVisuals.darkness(camera);
        if (!minecraft.isPaused()) darkness += (toDarkness - darkness) * .08F;
        if (!ShaderPacks.inUse()) return;

        // Sem shader este peso vira neblina de verdade em MagiaClientEvents; com shader, aqui.
        ScreenFog.draw(graphics, DARK, darkness * .95F);
    }

    /** Sair do mundo com a tela meio fechada nao pode deixar a proxima entrada escura. */
    static void clear() {
        darkness = 0;
    }
}
