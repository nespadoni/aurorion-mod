package com.aurorion.trama.server;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.EntityType;

final class TramaTags {
    static final TagKey<DamageType> MAGIC = damage("magic"), PHYSICAL = damage("physical"), ENVIRONMENT = damage("environment");
    static final TagKey<EntityType<?>> PROJECTILES = TagKey.create(Registries.ENTITY_TYPE,id("speed_projectiles"));
    static final TagKey<MobEffect> DEBUFFS = TagKey.create(Registries.MOB_EFFECT,id("debuffs"));
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("aurorion_trama",path); }
    private static TagKey<DamageType> damage(String path) { return TagKey.create(Registries.DAMAGE_TYPE,id(path)); }
    private TramaTags() {}
}
