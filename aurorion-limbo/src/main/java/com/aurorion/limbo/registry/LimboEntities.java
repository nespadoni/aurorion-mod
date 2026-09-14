package com.aurorion.limbo.registry;

import com.aurorion.limbo.AurorionLimbo;
import com.aurorion.limbo.rescue.RescuePortalEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class LimboEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(Registries.ENTITY_TYPE, AurorionLimbo.MOD_ID);

    /**
     * A passagem do resgate.
     *
     * <p>{@code MISC} porque nao e mob: nao nasce por spawn natural, nao conta para o limite de mobs e
     * nao entra na conta de dificuldade. {@code clientTrackingRange} generoso pelo mesmo motivo do
     * {@code shouldRenderAtSqrDistance}: uma passagem que voce pagou uma vida para abrir precisa ser
     * vista de longe.
     *
     * <p>{@code updateInterval} alto porque ela <b>nao se move</b> — mandar posicao tres vezes por
     * segundo para uma entidade parada e trafego jogado fora (SDD §7.3).
     */
    public static final DeferredHolder<EntityType<?>, EntityType<RescuePortalEntity>> RESCUE_PORTAL =
            ENTITIES.register("passagem", () -> EntityType.Builder
                    .<RescuePortalEntity>of(RescuePortalEntity::new, MobCategory.MISC)
                    .sized(1.6F, 2.6F)
                    .clientTrackingRange(8)
                    .updateInterval(20)
                    .fireImmune()
                    .noSummon()
                    .build("passagem"));

    private LimboEntities() {
    }
}
