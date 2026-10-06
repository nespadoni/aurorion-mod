package com.aurorion.magia.client;

import com.aurorion.magia.entity.ShadowEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

import java.util.UUID;

/**
 * A Sombra Viva: o modelo de jogador com a pele de quem a conjurou, tingido de quase-preto e meio
 * translucido. Pose fixa de quem segura laminas — braços um pouco abertos —, sem animacao: a sombra
 * esta parada, a espera.
 */
public class ShadowRenderer extends EntityRenderer<ShadowEntity> {
    private final PlayerModel<LivingEntity> wide;
    private final PlayerModel<LivingEntity> slim;

    public ShadowRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.wide = new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false);
        this.slim = new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER_SLIM), true);
        this.shadowRadius = 0.4F;
    }

    @Override
    public ResourceLocation getTextureLocation(ShadowEntity shadow) {
        return skin(shadow).texture();
    }

    @Override
    public void render(ShadowEntity shadow, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
        float fade = shadow.fade(partial);
        if (fade <= .01F) return;
        PlayerSkin skin = skin(shadow);
        PlayerModel<LivingEntity> model = skin.model() == PlayerSkin.Model.SLIM ? slim : wide;
        pose(model, shadow.tickCount + partial);

        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(180 - Mth.rotLerp(partial, shadow.yRotO, shadow.getYRot())));
        pose.scale(-1, -1, 1);
        pose.translate(0, -1.501, 0);
        int tint = FastColor.ARGB32.color((int) (210 * fade), 40, 12, 22);
        model.renderToBuffer(pose, buffers.getBuffer(RenderType.entityTranslucent(skin.texture())), light,
                OverlayTexture.NO_OVERLAY, tint);
        pose.popPose();
    }

    /** Pose fixa, reposta a cada quadro: o modelo e compartilhado entre todas as sombras. */
    private static void pose(PlayerModel<LivingEntity> model, float life) {
        model.young = false;
        model.crouching = false;
        model.riding = false;
        model.setAllVisible(true);
        float sway = Mth.sin(life * .08F) * .04F;
        model.head.setRotation(0, 0, 0);
        model.body.setRotation(0, 0, 0);
        model.rightArm.setRotation(-.35F + sway, 0, .35F);
        model.leftArm.setRotation(-.35F - sway, 0, -.35F);
        model.rightLeg.setRotation(.05F, 0, .06F);
        model.leftLeg.setRotation(-.05F, 0, -.06F);
        model.hat.copyFrom(model.head);
        model.jacket.copyFrom(model.body);
        model.rightSleeve.copyFrom(model.rightArm);
        model.leftSleeve.copyFrom(model.leftArm);
        model.rightPants.copyFrom(model.rightLeg);
        model.leftPants.copyFrom(model.leftLeg);
    }

    private static PlayerSkin skin(ShadowEntity shadow) {
        UUID owner = shadow.ownerId();
        if (owner == null) return DefaultPlayerSkin.get(shadow.getUUID());
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        PlayerInfo info = connection == null ? null : connection.getPlayerInfo(owner);
        return info != null ? info.getSkin() : DefaultPlayerSkin.get(owner);
    }
}
