package com.aurorion.talk.mixin;

import com.aurorion.talk.client.BalloonRenderer;
import com.aurorion.talk.util.BalloonHolder;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerRenderer.class)
public abstract class PlayerRendererMixin extends LivingEntityRenderer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {

    public PlayerRendererMixin(EntityRendererProvider.Context context, PlayerModel<AbstractClientPlayer> model, float shadowRadius) {
        super(context, model, shadowRadius);
    }

    @Inject(
            method = "render(Lnet/minecraft/client/player/AbstractClientPlayer;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("HEAD")
    )
    private void aurorion_talk$renderBalloons(AbstractClientPlayer player, float entityYaw, float partialTicks,
                                              PoseStack poseStack, MultiBufferSource buffer, int packedLight,
                                              CallbackInfo ci) {
        if (player.isInvisible() || !player.isAlive()) return;

        long gameTime = player.level().getGameTime();
        BalloonHolder holder = (BalloonHolder) player;
        holder.aurorion_talk$pruneBalloons(gameTime);

        if (holder.aurorion_talk$getBalloons().isEmpty()) return;

        BalloonRenderer.render(player, poseStack, buffer, this.entityRenderDispatcher, this.getFont(), gameTime);
    }
}
