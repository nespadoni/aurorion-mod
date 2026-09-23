package com.aurorion.magia.spell;

import com.aurorion.magia.AurorionMagia;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

/**
 * Quem nao pode ser arrastado, trocado de lugar ou jogado no chao por magia: chefes, por padrao.
 * Uma luta de chefe em que o Dragao e puxado para o chao com um feitico deixa de ser luta.
 */
public final class Displacement {
    public static final TagKey<EntityType<?>> IMMUNE =
            TagKey.create(Registries.ENTITY_TYPE, AurorionMagia.id("imune_deslocamento"));

    private Displacement() {
    }

    public static boolean isImmune(Entity entity) {
        return entity.getType().is(IMMUNE);
    }
}
