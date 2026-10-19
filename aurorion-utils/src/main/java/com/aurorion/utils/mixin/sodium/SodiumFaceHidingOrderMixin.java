package com.aurorion.utils.mixin.sodium;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Sodium 0.8.13 ({@code NeoForgeBlockAccess.shouldSkipRender}): a pergunta cara primeiro.
 *
 * <p>O original e {@code outro.hidesNeighborFace(...) && este.supportsExternalFaceHiding()}. Para cada
 * face de um bloco solido encostado num copycat, o Copycats calcula o {@code hidesNeighborFace} inteiro —
 * consulta de BE, forma da face, alocacoes — e o resultado e jogado fora, porque bloco solido nunca aceita
 * ter a face escondida por fora.
 *
 * <p>Aqui a parte barata vai na frente: {@code este.supportsExternalFaceHiding() && outro.hidesNeighborFace(...)}.
 * E um "e" de duas consultas sem efeito colateral, entao o resultado e identico; so a ordem muda. O
 * {@code supportsExternalFaceHiding()} ficou barato com o cache de camada solida
 * ({@code ClientHooksSolidLayerMixin}).
 *
 * <p>{@code @Redirect}, sem alocar nada por chamada. {@code require = 0}: se uma versao futura do Sodium
 * mudar este metodo, o jogo segue sem a otimizacao em vez de nao abrir.
 */
@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.neoforge.block.NeoForgeBlockAccess", remap = false)
public abstract class SodiumFaceHidingOrderMixin {
    @Redirect(method = "shouldSkipRender(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/world/level/block/state/BlockState;"
            + "Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/BlockPos;"
            + "Lnet/minecraft/core/Direction;)Z",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/BlockState;hidesNeighborFace("
                    + "Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;"
                    + "Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/Direction;)Z"),
            require = 0)
    private boolean aurorion_utils$cheapCheckFirst(BlockState other, BlockGetter level, BlockPos otherPos, BlockState self,
                                                   Direction facing) {
        return self.supportsExternalFaceHiding() && other.hidesNeighborFace(level, otherPos, self, facing);
    }
}
