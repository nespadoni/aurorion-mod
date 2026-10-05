package com.aurorion.areas.mixin.lso;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Impede que a Sun Fern e a Ice Fern do LSO derrubem o servidor quando outro mod troca a planta
 * durante o crescimento.
 *
 * <p>O {@code randomTick} das duas chama {@code super.randomTick} (o crescimento do {@code CropBlock})
 * e depois rele o bloco da posicao e pede {@code getAge} dele, sem conferir se ainda e a samambaia. O
 * Crop Critters injeta no {@code CropBlock.randomTick} e pode trocar a plantacao por erva daninha
 * (ex.: {@code cropcritters:liverwort}) ou degradar o solo ate ela quebrar; ai o {@code getAge} cai num
 * bloco sem a propriedade {@code age} e o tick do mundo morre com {@code IllegalArgumentException}
 * (crash de 04/10/2026). Logo depois do {@code super}, se o bloco ja nao e a samambaia, o resto do
 * metodo — so a chance de virar samambaia dourada — nao faz sentido e e pulado.
 *
 * <p>{@code require = 0}: e uma rede de seguranca. Se uma versao nova do LSO mudar o metodo, o servidor
 * sobe sem ela em vez de nao subir; o {@code LsoFernTickContractTest} acusa a mudanca no build.
 */
@Pseudo
@Mixin(targets = {
        "sfiomn.legendarysurvivaloverhaul.common.blocks.SunFernBlock",
        "sfiomn.legendarysurvivaloverhaul.common.blocks.IceFernBlock"
}, remap = false)
public abstract class FernTickGuardMixin {

    @Inject(method = "randomTick(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/util/RandomSource;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/block/CropBlock;randomTick(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/util/RandomSource;)V",
                    shift = At.Shift.AFTER),
            cancellable = true, require = 0)
    private void aurorion$stopIfReplacedDuringGrowth(BlockState state, ServerLevel level, BlockPos pos,
                                                     RandomSource random, CallbackInfo ci) {
        if (!level.getBlockState(pos).is((Block) (Object) this)) {
            ci.cancel();
        }
    }
}
