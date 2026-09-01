package com.aurorion.core.level;

import com.aurorion.core.AurorionCore;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

/** Tags de blocos usadas pelas regras compartilhadas de posicionamento seguro. */
public final class CoreLevelTags {
    /**
     * Blocos onde um teleporte/respawn nao pode colocar o corpo nem usar como piso. Datapacks do
     * modpack podem acrescentar perigos de outros mods sem criar dependencia de codigo.
     */
    public static final TagKey<Block> UNSAFE_TELEPORT = TagKey.create(
            Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath(AurorionCore.MOD_ID, "unsafe_teleport"));

    private CoreLevelTags() {
    }
}
