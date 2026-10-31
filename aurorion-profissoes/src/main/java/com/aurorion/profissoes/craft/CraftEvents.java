package com.aurorion.profissoes.craft;

import com.aurorion.profissoes.AurorionProfissoes;
import com.aurorion.profissoes.config.ProfessionsConfig;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.TagsUpdatedEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

/** O que o teste de ofício precisa fora dos mixins: as regras de CD do datapack e o dono das fornalhas. */
@EventBusSubscriber(modid = AurorionProfissoes.MOD_ID)
public final class CraftEvents {
    private CraftEvents() {
    }

    @SubscribeEvent
    public static void onReload(AddReloadListenerEvent event) {
        event.addListener(CraftDifficulty.RULES.listener());
    }

    /** As tags de item podem ter mudado: a CD guardada por item é refeita na próxima fabricação. */
    @SubscribeEvent
    public static void onTags(TagsUpdatedEvent event) {
        CraftDifficulty.clear();
    }

    /**
     * Quem coloca uma fornalha, um suporte de poções ou uma máquina de um mod de {@code modsComDono} fica
     * gravado nela: o que ela produzir passa pelo teste com a ficha dessa pessoa. Um baú vanilla não ganha
     * dado nenhum.
     */
    @SubscribeEvent
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        BlockEntity block = event.getLevel().getBlockEntity(event.getPos());
        if (block == null || !ProfessionsConfig.craftEnabled()) return;
        if (block instanceof AbstractFurnaceBlockEntity || block instanceof BrewingStandBlockEntity
                || ProfessionsConfig.OWNER_MODS.get().contains(BuiltInRegistries.BLOCK.getKey(event.getPlacedBlock().getBlock()).getNamespace())) {
            block.getPersistentData().putUUID(CraftRoll.OWNER_KEY, player.getUUID());
            block.setChanged();
        }
    }
}
