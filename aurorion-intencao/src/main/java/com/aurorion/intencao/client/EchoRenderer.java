package com.aurorion.intencao.client;

import com.aurorion.intencao.entity.EchoEntity;
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
 * O Eco: o modelo de jogador com a pele do dono, sentado de pernas cruzadas e cabeca baixa,
 * translucido e clareado na cor da Casa. Mesma tecnica da Sombra Viva do magia, com outra pose.
 */
public class EchoRenderer extends EntityRenderer<EchoEntity> {
    private final PlayerModel<LivingEntity> wide;
    private final PlayerModel<LivingEntity> slim;

    public EchoRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.wide = new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false);
        this.slim = new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER_SLIM), true);
        this.shadowRadius = 0.35F;
    }

    @Override
    public ResourceLocation getTextureLocation(EchoEntity echo) {
        return skin(echo).texture();
    }

    @Override
    public void render(EchoEntity echo, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
        float life = echo.tickCount + partial;
        float fade = Mth.clamp(life / 20F, 0, 1);
        PlayerSkin skin = skin(echo);
        PlayerModel<LivingEntity> model = skin.model() == PlayerSkin.Model.SLIM ? slim : wide;
        seated(model, life);

        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(180 - echo.getYRot()));
        pose.scale(-1, -1, 1);
        // Sentado: o quadril desce ate o chao.
        pose.translate(0, -1.501 + .62, 0);
        int c = echo.color();
        int tint = FastColor.ARGB32.color((int) (150 * fade),
                (FastColor.ARGB32.red(c) + 255 * 2) / 3, (FastColor.ARGB32.green(c) + 255 * 2) / 3,
                (FastColor.ARGB32.blue(c) + 255 * 2) / 3);
        model.renderToBuffer(pose, buffers.getBuffer(RenderType.entityTranslucent(skin.texture())), light,
                OverlayTexture.NO_OVERLAY, tint);
        pose.popPose();
        super.render(echo, yaw, partial, pose, buffers, light);
    }

    /** Pernas cruzadas, maos nos joelhos, cabeca baixa respirando devagar. Reposta a cada quadro. */
    private static void seated(PlayerModel<LivingEntity> model, float life) {
        model.young = false;
        model.crouching = false;
        model.riding = false;
        model.setAllVisible(true);
        float breath = Mth.sin(life * .05F) * .03F;
        model.head.setRotation(.38F + breath, 0, 0);
        model.body.setRotation(.06F, 0, 0);
        model.rightArm.setRotation(-.7F + breath, -.12F, .08F);
        model.leftArm.setRotation(-.7F + breath, .12F, -.08F);
        model.rightLeg.setRotation(-1.4137F, .3142F, .07F);
        model.leftLeg.setRotation(-1.4137F, -.3142F, -.07F);
        model.hat.copyFrom(model.head);
        model.jacket.copyFrom(model.body);
        model.rightSleeve.copyFrom(model.rightArm);
        model.leftSleeve.copyFrom(model.leftArm);
        model.rightPants.copyFrom(model.rightLeg);
        model.leftPants.copyFrom(model.leftLeg);
    }

    private static PlayerSkin skin(EchoEntity echo) {
        UUID owner = echo.ownerId();
        if (owner == null) return DefaultPlayerSkin.get(echo.getUUID());
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        PlayerInfo info = connection == null ? null : connection.getPlayerInfo(owner);
        return info != null ? info.getSkin() : DefaultPlayerSkin.get(owner);
    }
}
