package com.aurorion.areas.mixin.world;

import com.aurorion.areas.server.AreaProtection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Agua e lava de fora nao escorrem para dentro de uma area protegida por {@code fluido}.
 *
 * <p>O NeoForge nao tem evento de escoamento: o {@code FluidPlaceBlockEvent} so cobre o fogo da lava
 * e a pedra do encontro de agua com lava. Sem isto, o balde despejado do lado de fora da parede de
 * uma casa continuaria sendo a forma mais simples de invadi-la. {@code canSpreadTo} e o ponto unico
 * por onde passa tanto a descida quanto o espalhamento para os lados.
 *
 * <p><b>Custo:</b> roda a cada passo de fluido do servidor. Numa dimensao sem protecao de bloco a
 * resposta sai de um {@code Set#contains} (ver {@link AreaProtection#guarded}).
 *
 * <p>{@code require = 0} e descritor completo: e uma rede de seguranca. Se o metodo mudar numa versao
 * do jogo, o alvo simplesmente nao e achado e o servidor sobe sem ela (o resto da protecao continua).
 */
@Mixin(FlowingFluid.class)
public abstract class FlowingFluidSpreadMixin {

    @Inject(method = "canSpreadTo(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/Direction;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/material/FluidState;Lnet/minecraft/world/level/material/Fluid;)Z",
            at = @At("HEAD"), cancellable = true, require = 0)
    private void aurorion$keepOutOfProtectedAreas(BlockGetter level, BlockPos fromPos, BlockState fromState,
                                                  Direction direction, BlockPos toPos, BlockState toState,
                                                  FluidState toFluid, Fluid fluid, CallbackInfoReturnable<Boolean> cir) {
        if (level instanceof ServerLevel server && AreaProtection.fluidCrosses(server, fromPos, toPos)) {
            cir.setReturnValue(false);
        }
    }
}
