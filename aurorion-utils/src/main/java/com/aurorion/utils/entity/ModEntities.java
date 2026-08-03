package com.aurorion.utils.entity;

import com.aurorion.utils.AurorionUtils;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(BuiltInRegistries.ENTITY_TYPE, AurorionUtils.MOD_ID);

    /**
     * A hitbox em si fica modesta (3x3 de base, pedido original) — o feixe visual sobe bem alem
     * dela, ver {@link AbductionBeamEntity#getBoundingBoxForCulling()}, entao nao precisa de uma
     * entidade gigante so pra nao ser cortada da tela.
     */
    public static final DeferredHolder<EntityType<?>, EntityType<AbductionBeamEntity>> ABDUCTION_BEAM =
            ENTITY_TYPES.register("abduction_beam", () -> EntityType.Builder
                    .<AbductionBeamEntity>of(AbductionBeamEntity::new, MobCategory.MISC)
                    .sized(3.0F, 3.0F)
                    .noSummon()
                    .noSave()
                    .clientTrackingRange(10)
                    .updateInterval(20)
                    .build("abduction_beam"));

    /**
     * Invisivel, usada tanto pelo {@code /freeze} quanto pela abducao (montaria = imobilidade real,
     * nao um {@code MobEffect} — ver {@link FreezeAnchorEntity}). Hitbox minima de proposito: nunca
     * e vista, so precisa existir pra alguem montar nela.
     */
    public static final DeferredHolder<EntityType<?>, EntityType<FreezeAnchorEntity>> FREEZE_ANCHOR =
            ENTITY_TYPES.register("freeze_anchor", () -> EntityType.Builder
                    .<FreezeAnchorEntity>of(FreezeAnchorEntity::new, MobCategory.MISC)
                    .sized(0.5F, 0.5F)
                    .noSummon()
                    .noSave()
                    .clientTrackingRange(10)
                    .updateInterval(20)
                    .build("freeze_anchor"));

    private ModEntities() {
    }
}
