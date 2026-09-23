package com.aurorion.profissoes.npc;

import com.aurorion.profissoes.AurorionProfissoes;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class NpcEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(Registries.ENTITY_TYPE, AurorionProfissoes.MOD_ID);

    /** {@code MISC} e {@code updateInterval} alto pelos mesmos motivos do Oraculo: nao nasce sozinho e nao anda. */
    public static final DeferredHolder<EntityType<?>, EntityType<ProfessionNpcEntity>> NPC =
            ENTITIES.register("npc", () -> EntityType.Builder
                    .of(ProfessionNpcEntity::new, MobCategory.MISC)
                    .sized(0.6F, 1.8F)
                    .clientTrackingRange(10)
                    .updateInterval(20)
                    .fireImmune()
                    .build("npc"));

    private NpcEntities() {}
}
