package com.aurorion.intencao.client;

import com.aurorion.intencao.entity.FragmentEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** O fragmento: um caco de eco girando devagar, subindo e descendo, aceso no escuro. */
public class FragmentRenderer extends EntityRenderer<FragmentEntity> {
    private static final ItemStack SHARD = new ItemStack(Items.ECHO_SHARD);
    private final ItemRenderer items;

    public FragmentRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.items = context.getItemRenderer();
        this.shadowRadius = 0;
    }

    @Override
    public ResourceLocation getTextureLocation(FragmentEntity fragment) {
        return TextureAtlas.LOCATION_BLOCKS;
    }

    @Override
    public void render(FragmentEntity fragment, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
        float life = fragment.tickCount + partial;
        // Fase pelo id: fragmentos vizinhos nao sobem e descem juntos.
        float phase = fragment.getId() * 1.7F;
        pose.pushPose();
        pose.translate(0, .2 + Mth.sin(life * .07F + phase) * .12, 0);
        pose.mulPose(Axis.YP.rotationDegrees(fragment.getYRot() + life * 2.2F));
        pose.scale(1.1F, 1.1F, 1.1F);
        items.renderStatic(SHARD, ItemDisplayContext.GROUND, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, pose,
                buffers, fragment.level(), fragment.getId());
        pose.popPose();
        super.render(fragment, yaw, partial, pose, buffers, light);
    }
}
