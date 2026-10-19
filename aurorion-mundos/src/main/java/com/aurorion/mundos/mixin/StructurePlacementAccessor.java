package com.aurorion.mundos.mixin;

import net.minecraft.core.Vec3i;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.Optional;

/** Os campos de {@code StructurePlacement} sao protegidos; refazer um posicionamento precisa deles. */
@Mixin(StructurePlacement.class)
public interface StructurePlacementAccessor {
    @Invoker("locateOffset")
    Vec3i aurorion_mundos$locateOffset();

    @Invoker("frequencyReductionMethod")
    StructurePlacement.FrequencyReductionMethod aurorion_mundos$frequencyReductionMethod();

    @Invoker("frequency")
    float aurorion_mundos$frequency();

    @Invoker("salt")
    int aurorion_mundos$salt();

    @Invoker("exclusionZone")
    Optional<StructurePlacement.ExclusionZone> aurorion_mundos$exclusionZone();
}
