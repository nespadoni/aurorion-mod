package com.aurorion.areas.server;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

public final class AreaTags {
    public static final TagKey<Item> FLIGHT_ITEMS = TagKey.create(Registries.ITEM, id("flight_items"));
    public static final TagKey<Item> MAGIC_ITEMS = TagKey.create(Registries.ITEM, id("magic_items"));
    public static final TagKey<EntityType<?>> FLIGHT_MOUNTS = TagKey.create(Registries.ENTITY_TYPE, id("flight_mounts"));
    public static final TagKey<EntityType<?>> HOSTILES = TagKey.create(Registries.ENTITY_TYPE, id("hostiles"));
    public static final TagKey<EntityType<?>> EXEMPT_ENTITIES = TagKey.create(Registries.ENTITY_TYPE, id("exempt_entities"));
    public static final TagKey<MobEffect> FLIGHT_EFFECTS = TagKey.create(Registries.MOB_EFFECT, id("flight_effects"));
    /**
     * Blocos que contam como encanamento para a regra {@code agua_pura_pia}.
     *
     * <p>Vem com as pias de cozinha do Refurbished Furniture, que sao as unicas que o Legendary
     * Survival Overhaul sabe encher cantil (a integracao dele so conhece
     * {@code KitchenSinkBlockEntity} e {@code BasinBlockEntity}). Bacia fica <b>de fora</b> de
     * proposito: agua parada numa bacia nao e agua tratada. Acrescentar uma e uma linha no datapack.
     */
    public static final TagKey<Block> WATER_TAPS = TagKey.create(Registries.BLOCK, id("water_taps"));
    /**
     * Blocos sem inventario que a regra {@code interagir} tambem protege: portas, alcapoes, porteiras,
     * botoes, alavancas, repetidores... Todo bloco com block entity (bau, fornalha, placa, cama,
     * estante de livro, mesa de poções) ja entra sem estar aqui.
     */
    public static final TagKey<Block> PROTECTED_INTERACTIONS = TagKey.create(Registries.BLOCK, id("protected_interactions"));
    /**
     * Veiculos roteirizados pela staff (hoje, o feixe de abducao do {@code aurorion-utils}). Quem esta
     * montado neles nao e barrado nem tem posicao memorizada pela barreira de casa: o empurrao tiraria
     * a pessoa do feixe no meio da subida, e a "ultima posicao permitida" seria um ponto no ar.
     */
    public static final TagKey<EntityType<?>> SCRIPTED_VEHICLES = TagKey.create(Registries.ENTITY_TYPE, id("scripted_vehicles"));
    /** Includes seats nested inside a tagged flying vehicle, without moving or disabling the NPC/vehicle. */
    public static boolean isFlightMount(net.minecraft.world.entity.Entity entity) {
        for (var current = entity; current != null; current = current.getVehicle())
            if (current.getType().is(FLIGHT_MOUNTS)) return true;
        return false;
    }
    /** Veiculo roteirizado em qualquer nivel da pilha de montaria (ver {@link #SCRIPTED_VEHICLES}). */
    public static boolean isScriptedVehicle(net.minecraft.world.entity.Entity entity) {
        for (var current = entity; current != null; current = current.getVehicle())
            if (current.getType().is(SCRIPTED_VEHICLES)) return true;
        return false;
    }
    private AreaTags() {}
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("aurorion_areas", path); }
}
