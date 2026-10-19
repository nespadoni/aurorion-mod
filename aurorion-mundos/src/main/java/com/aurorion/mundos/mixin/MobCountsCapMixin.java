package com.aurorion.mundos.mixin;

import com.aurorion.mundos.perfil.SpawnCaps;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.entity.MobCategory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * O teto local, em volta de cada jogador — o que impede que um so jogador encha o teto da dimensao
 * inteira. Sem ele, dobrar so o global nao dobraria nada para quem esta sozinho num canto do mapa.
 */
@Mixin(targets = "net.minecraft.world.level.LocalMobCapCalculator$MobCounts")
public abstract class MobCountsCapMixin {
    @ModifyExpressionValue(method = "canSpawn",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/MobCategory;getMaxInstancesPerChunk()I"))
    private int aurorion_mundos$localCap(int original, @Local(argsOnly = true) MobCategory category) {
        return SpawnCaps.cap(category, original);
    }
}
