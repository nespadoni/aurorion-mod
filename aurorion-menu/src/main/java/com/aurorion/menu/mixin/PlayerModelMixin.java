package com.aurorion.menu.mixin;

import com.aurorion.menu.client.MenuPose;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A pose do braço (menu aberto) e a pose sentada (meditação sem o Emotecraft), no fim do {@code setupAnim}:
 * depois do vanilla, para não ser desfeita. Prioridade alta para vir depois de mods de animação que mexem no
 * mesmo ponto; só altera o modelo de quem tem estado de menu.
 */
@Mixin(value = PlayerModel.class, priority = 1500)
public abstract class PlayerModelMixin {
    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void aurorion_menu$pose(LivingEntity entity, float limbSwing, float limbSwingAmount, float age,
                                    float headYaw, float headPitch, CallbackInfo ci) {
        // O braço em primeira pessoa (PlayerRenderer.renderHand) chama setupAnim com idade 0: ali não.
        if (age == 0f && limbSwing == 0f && headYaw == 0f && headPitch == 0f) return;
        MenuPose.apply((PlayerModel<?>) (Object) this, entity);
    }
}
