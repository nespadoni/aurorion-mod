package com.aurorion.mundos.mixin;

import com.aurorion.mundos.perfil.StructureDensity;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Densidade de estrutura por dimensao. O {@code ChunkMap} e quem cria o estado de estruturas da
 * dimensao, uma vez, e o {@code ServerLevel} so calcula as posicoes depois dele
 * ({@code ensureStructuresGenerated}, no fim do construtor do nivel) — o fim deste construtor e o
 * ultimo ponto em que a lista ainda pode mudar sem nada ter sido calculado com a antiga.
 */
@Mixin(ChunkMap.class)
public abstract class ChunkMapStructureMixin {
    @Shadow
    @Final
    ServerLevel level;

    @Shadow
    @Final
    private ChunkGeneratorStructureState chunkGeneratorState;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void aurorion_mundos$structureDensity(CallbackInfo ci) {
        StructureDensity.apply(level.dimension(), chunkGeneratorState);
    }
}
