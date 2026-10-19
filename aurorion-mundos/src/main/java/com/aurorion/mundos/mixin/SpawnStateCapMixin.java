package com.aurorion.mundos.mixin;

import com.aurorion.mundos.perfil.SpawnCaps;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.NaturalSpawner;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * O teto global de uma categoria na dimensao, multiplicado pelo perfil de mundo. {@code
 * ModifyExpressionValue} (MixinExtras, que vem com o NeoForge), e nao {@code Redirect}: assim outro mod
 * que mexa no mesmo teto (In Control!) compoe com este em vez de brigar por ele.
 */
@Mixin(NaturalSpawner.SpawnState.class)
public abstract class SpawnStateCapMixin {
    @ModifyExpressionValue(method = "canSpawnForCategory",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/MobCategory;getMaxInstancesPerChunk()I"))
    private int aurorion_mundos$globalCap(int original, @Local(argsOnly = true) MobCategory category) {
        return SpawnCaps.cap(category, original);
    }
}
