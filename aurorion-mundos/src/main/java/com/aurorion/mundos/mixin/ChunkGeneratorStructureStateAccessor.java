package com.aurorion.mundos.mixin;

import net.minecraft.core.Holder;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

/** Troca a lista de conjuntos de estrutura de uma dimensao, antes de o vanilla calcular as posicoes. */
@Mixin(ChunkGeneratorStructureState.class)
public interface ChunkGeneratorStructureStateAccessor {
    @Mutable
    @Accessor("possibleStructureSets")
    void aurorion_mundos$setPossibleStructureSets(List<Holder<StructureSet>> sets);
}
