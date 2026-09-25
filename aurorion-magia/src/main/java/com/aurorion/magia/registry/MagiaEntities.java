package com.aurorion.magia.registry;

import com.aurorion.magia.AurorionMagia;
import com.aurorion.magia.entity.SpellZoneEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * A unica entidade do mod: a zona de vento ({@link SpellZoneEntity}), nas tres formas da barreira, da
 * coluna e do furacao.
 *
 * <p>{@code noSummon}: ninguem invoca isto com {@code /summon} nem com ovo. Ela nasce de uma magia e
 * morre no tempo dela. {@code updateInterval(1)} porque o furacao anda: a uma atualizacao a cada tres
 * ticks, como e o padrao, a tempestade andaria aos solavancos na tela.
 */
public final class MagiaEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(Registries.ENTITY_TYPE, AurorionMagia.MOD_ID);

    public static final DeferredHolder<EntityType<?>, EntityType<SpellZoneEntity>> SPELL_ZONE =
            ENTITIES.register("zona_de_magia", () -> EntityType.Builder
                    .<SpellZoneEntity>of(SpellZoneEntity::new, MobCategory.MISC)
                    .sized(0.5F, 0.5F)
                    .fireImmune()
                    .noSummon()
                    .noSave()
                    .clientTrackingRange(6)
                    .updateInterval(1)
                    .build("zona_de_magia"));

    private MagiaEntities() {
    }
}
