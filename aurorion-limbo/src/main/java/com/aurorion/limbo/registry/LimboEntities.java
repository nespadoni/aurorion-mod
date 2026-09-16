package com.aurorion.limbo.registry;

import com.aurorion.limbo.AurorionLimbo;
import com.aurorion.limbo.oracle.OracleEntity;
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

    /**
     * O Oraculo. {@code MISC} pelo mesmo motivo da passagem: ele nao nasce por spawn natural, nao
     * conta para o limite de mobs do servidor e nao entra na conta de dificuldade — a staff invoca um
     * e a rotacao diaria o leva de um ponto a outro.
     *
     * <p>{@code updateInterval} alto porque ele <b>nao se move</b> sozinho: so muda de lugar por
     * teleporte, que e sincronizado a parte. Mandar posicao tres vezes por segundo para um NPC parado
     * e trafego jogado fora (SDD 7.3).
     */
    public static final DeferredHolder<EntityType<?>, EntityType<OracleEntity>> ORACLE =
            ENTITIES.register("oraculo", () -> EntityType.Builder
                    .of(OracleEntity::new, MobCategory.MISC)
                    .sized(0.6F, 1.8F)
                    .clientTrackingRange(10)
                    .updateInterval(20)
                    .fireImmune()
                    .build("oraculo"));

    private LimboEntities() {
    }
}
