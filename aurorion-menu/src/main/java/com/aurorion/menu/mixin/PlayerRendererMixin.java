package com.aurorion.menu.mixin;

import com.aurorion.core.client.IrisShadowPass;
import com.aurorion.menu.client.MenuPose;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * O holograma do menu na frente de quem o abriu (no fim do render, depois do corpo), e a descida do corpo
 * na pose de meditação desenhada por nós (sem o Emotecraft). O holograma pula o boneco do inventário e o
 * passe de sombra do Iris.
 */
@Mixin(PlayerRenderer.class)
public abstract class PlayerRendererMixin extends LivingEntityRenderer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    /** Este render empurrou a pose para baixo (sentado) e o RETURN tem que devolver. Render não é reentrante. */
    @Unique private boolean aurorion_menu$lowered;

    public PlayerRendererMixin(EntityRendererProvider.Context context, PlayerModel<AbstractClientPlayer> model, float shadowRadius) {
        super(context, model, shadowRadius);
    }

    @Inject(method = "render(Lnet/minecraft/client/player/AbstractClientPlayer;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("HEAD"))
    private void aurorion_menu$presence(AbstractClientPlayer player, float yaw, float partial, PoseStack pose,
                                        MultiBufferSource buffers, int light, CallbackInfo ci) {
        aurorion_menu$lowered = false;
        if (player.isAlive() && MenuPose.sitting(player)) {
            pose.pushPose();
            pose.translate(0, MenuPose.SIT_DROP, 0);
            aurorion_menu$lowered = true;
        }
    }

    @Inject(method = "render(Lnet/minecraft/client/player/AbstractClientPlayer;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("RETURN"))
    private void aurorion_menu$restore(AbstractClientPlayer player, float yaw, float partial, PoseStack pose,
                                       MultiBufferSource buffers, int light, CallbackInfo ci) {
        if (aurorion_menu$lowered) {
            aurorion_menu$lowered = false;
            pose.popPose();
        }
        // O painel vem depois do corpo: pedir outro buffer fecha o lote do boneco, que é desenhado primeiro.
        if (!player.isAlive() || player.isInvisible() || !MenuPose.hasExtras(player) || IrisShadowPass.active()) return;
        boolean doll = !((EntityRenderDispatcherAccessor) this.entityRenderDispatcher).aurorion_menu$shouldRenderShadow();
        if (!doll) MenuPose.renderExtras(player, partial, pose, buffers, this.entityRenderDispatcher.distanceToSqr(player));
    }
}
