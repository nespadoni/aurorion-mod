package com.aurorion.areas.server;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;

public final class AreaTags {
    public static final TagKey<Item> FLIGHT_ITEMS = TagKey.create(Registries.ITEM, id("flight_items"));
    public static final TagKey<Item> MAGIC_ITEMS = TagKey.create(Registries.ITEM, id("magic_items"));
    public static final TagKey<EntityType<?>> FLIGHT_MOUNTS = TagKey.create(Registries.ENTITY_TYPE, id("flight_mounts"));
    public static final TagKey<EntityType<?>> HOSTILES = TagKey.create(Registries.ENTITY_TYPE, id("hostiles"));
    public static final TagKey<EntityType<?>> EXEMPT_ENTITIES = TagKey.create(Registries.ENTITY_TYPE, id("exempt_entities"));
    public static final TagKey<MobEffect> FLIGHT_EFFECTS = TagKey.create(Registries.MOB_EFFECT, id("flight_effects"));
    /** Includes seats nested inside a tagged flying vehicle, without moving or disabling the NPC/vehicle. */
    public static boolean isFlightMount(net.minecraft.world.entity.Entity entity) {
        for (var current = entity; current != null; current = current.getVehicle())
            if (current.getType().is(FLIGHT_MOUNTS)) return true;
        return false;
    }
    private AreaTags() {}
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("aurorion_areas", path); }
}
