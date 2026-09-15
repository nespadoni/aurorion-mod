package com.aurorion.ethereal.house;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

/** Paleta e trilha da casa, substituiveis por datapack/resource pack. */
public record RiteStyle(int secondary, int accent, ResourceLocation music) {
    public static final RiteStyle DEFAULT = new RiteStyle(0xFFFFFF, 0xFFF3D6,
            ResourceLocation.fromNamespaceAndPath("aurorion_ethereal", "ceremony.going_north"));

    public static final Codec<RiteStyle> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            House.COLOR_CODEC.optionalFieldOf("secondary", DEFAULT.secondary).forGetter(RiteStyle::secondary),
            House.COLOR_CODEC.optionalFieldOf("accent", DEFAULT.accent).forGetter(RiteStyle::accent),
            ResourceLocation.CODEC.optionalFieldOf("music", DEFAULT.music).forGetter(RiteStyle::music)
    ).apply(instance, RiteStyle::new));
}
