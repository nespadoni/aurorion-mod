package com.aurorion.ethereal.client;

import com.aurorion.ethereal.ceremony.BindingRite;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;

/**
 * O simbolo da casa, girando acima de quem esta sendo vinculado.
 *
 * <h2>Por que dentro do render do jogador</h2>
 *
 * <p>Desenhar num estagio solto do mundo obrigaria a achar a entidade, interpolar a posicao dela e
 * desfazer a camera na mao. O {@code RenderPlayerEvent.Post} entrega tudo isso pronto: a pilha de
 * matrizes ja esta no pe do jogador, com o eixo Y para cima, e o {@code partialTick} ja veio junto.
 * Menos codigo e, principalmente, menos chance de o simbolo ficar atrasado um quadro em relacao ao
 * corpo — que e o tipo de defeito que so aparece quando a pessoa esta correndo.
 *
 * <h2>Nada de billboard</h2>
 *
 * <p>O simbolo gira em torno do proprio eixo em vez de encarar a camera. Encarar a camera e o certo
 * para texto, que precisa ser lido; para um objeto, girar e o que da volume e deixa claro que ele
 * esta <b>no mundo</b>, e nao grudado na tela de quem olha.
 */
public final class RiteRenderer {
    /** Altura do simbolo acima dos pes. Acima da cabeca e da placa de nome, sem sumir do enquadramento. */
    private static final float HEIGHT = 2.75F;
    private static final float SIZE = 1.35F;

    /** Quanto o simbolo passa do tamanho final antes de assentar. E o que da o "estalo" da chegada. */
    private static final float OVERSHOOT = 1.9F;
    private static final int GROW_TICKS = 14;

    private RiteRenderer() {
    }

    public static void render(RenderPlayerEvent.Post event) {
        RiteClient.Rite rite = RiteClient.of(event.getEntity().getId());
        if (rite == null || !rite.revealed()) return;

        float age = rite.sinceReveal() + event.getPartialTick();
        float scale = SIZE * grow(age);
        if (scale <= 0.001F) return;

        float life = rite.tick() + event.getPartialTick();
        float bob = Mth.sin(life * 0.09F) * 0.09F;
        float spin = life * 2.6F;

        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        pose.translate(0.0F, HEIGHT + bob, 0.0F);
        pose.mulPose(Axis.YP.rotationDegrees(spin));
        pose.scale(scale, scale, scale);

        Minecraft minecraft = Minecraft.getInstance();
        minecraft.getItemRenderer().renderStatic(rite.symbol(), ItemDisplayContext.GROUND,
                LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, pose,
                event.getMultiBufferSource(), minecraft.level, 0);

        pose.popPose();
    }

    /**
     * Cresce do nada, passa do ponto e volta — e some junto com o fim do rito.
     *
     * <p>Chegar direto ao tamanho final parece um objeto que <em>ja estava la</em> e ninguem tinha
     * visto. O exagero de meio segundo e o que faz parecer que ele <em>chegou</em>.
     */
    private static float grow(float age) {
        int fadeStart = BindingRite.CROWN_TICKS - 20;
        float since = age - BindingRite.BURST_TICKS;

        if (since > fadeStart) {
            return Math.max(0.0F, 1.0F - (since - fadeStart) / 20.0F);
        }
        if (age >= GROW_TICKS) return 1.0F;

        float t = Math.max(0.0F, age) / GROW_TICKS;
        return Mth.sin(t * Mth.PI * 0.5F) * (1.0F + (OVERSHOOT - 1.0F) * (1.0F - t) * t * 4.0F);
    }
}
