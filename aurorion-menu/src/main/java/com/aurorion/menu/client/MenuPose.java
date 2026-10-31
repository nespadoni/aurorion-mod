package com.aurorion.menu.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.fml.ModList;

/**
 * O corpo de quem usa o menu, como os outros veem:
 * <ul>
 *   <li><b>menu aberto:</b> o braço direito sobe acima da cabeça e desce num risco (o gesto de abrir), e
 *   fica estendido à frente, "tocando" o painel holográfico;</li>
 *   <li><b>meditando, sem o Emotecraft:</b> sentado de pernas cruzadas, mãos nos joelhos. Com o Emotecraft
 *   no pack o emote "Meditar" já cuida disso e esta pose não entra.</li>
 * </ul>
 * Aplicado no fim do {@code setupAnim} do modelo (mixin), só para quem tem estado; o resto não paga nada.
 */
public final class MenuPose {
    public static final float SIT_DROP = -0.62f;
    private static final float SWIPE_SECONDS = 0.35f;
    private static Boolean emotecraft;

    private MenuPose() {
    }

    public static void renderExtras(AbstractClientPlayer player, float partial, PoseStack pose, MultiBufferSource buffers, double distanceSqr) {
        // A própria pessoa vê o painel de verdade (MenuSpace), não o borrado.
        if (player == Minecraft.getInstance().player) return;
        if (ClientPresence.menu(player.getUUID()) && !ClientPresence.meditating(player.getUUID())) HoloRenderer.render(player, partial, pose, buffers, distanceSqr);
    }

    /** Tem algo a desenhar em volta (painel borrado)? Barato: só o mapa de presença. */
    public static boolean hasExtras(AbstractClientPlayer player) {
        return player != Minecraft.getInstance().player && ClientPresence.menu(player.getUUID());
    }

    public static boolean sitting(AbstractClientPlayer player) {
        return ClientPresence.meditating(player.getUUID()) && !emotecraft();
    }

    public static void apply(PlayerModel<?> model, LivingEntity entity) {
        if (!(entity instanceof AbstractClientPlayer player)) return;
        if (ClientPresence.menu(player.getUUID())) {
            float age = (Util.getMillis() - ClientPresence.of(player.getUUID()).since()) / 1000f;
            float t = Mth.clamp(age / SWIPE_SECONDS, 0, 1);
            float eased = 1 - (1 - t) * (1 - t);
            // Do alto (acima da cabeça) até à frente do peito.
            model.rightArm.xRot = Mth.lerp(eased, -2.9f, -1.45f);
            model.rightArm.yRot = Mth.lerp(eased, 0.1f, -0.35f);
            model.rightArm.zRot = 0.05f;
            model.rightSleeve.copyFrom(model.rightArm);
        }
        if (sitting(player)) {
            model.rightLeg.xRot = -1.45f;
            model.rightLeg.yRot = 0.45f;
            model.rightLeg.zRot = 0.1f;
            model.leftLeg.xRot = -1.45f;
            model.leftLeg.yRot = -0.45f;
            model.leftLeg.zRot = -0.1f;
            if (!ClientPresence.menu(player.getUUID())) {
                model.rightArm.xRot = -0.85f;
                model.rightArm.yRot = -0.25f;
                model.rightArm.zRot = 0;
            }
            model.leftArm.xRot = -0.85f;
            model.leftArm.yRot = 0.25f;
            model.leftArm.zRot = 0;
            model.rightPants.copyFrom(model.rightLeg);
            model.leftPants.copyFrom(model.leftLeg);
            model.rightSleeve.copyFrom(model.rightArm);
            model.leftSleeve.copyFrom(model.leftArm);
        }
    }

    private static boolean emotecraft() {
        if (emotecraft == null) emotecraft = ModList.get().isLoaded("emotecraft");
        return emotecraft;
    }
}
