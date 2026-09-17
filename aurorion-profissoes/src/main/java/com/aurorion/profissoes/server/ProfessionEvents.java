package com.aurorion.profissoes.server;

import com.aurorion.core.character.CharacterResetEvent;
import com.aurorion.profissoes.AurorionProfissoes;
import com.aurorion.profissoes.compat.FoodCompat;
import com.aurorion.profissoes.config.ProfessionsConfig;
import com.aurorion.profissoes.data.*;
import com.aurorion.profissoes.network.ProfessionsNetwork;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.*;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.neoforged.bus.api.*;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.brewing.PotionBrewEvent;
import net.neoforged.neoforge.event.enchanting.GetEnchantmentLevelEvent;
import net.neoforged.neoforge.event.entity.player.*;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.village.VillagerTradesEvent;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

@EventBusSubscriber(modid = AurorionProfissoes.MOD_ID)
public final class ProfessionEvents {
    private ProfessionEvents() {}
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) ProfessionsNetwork.sync(player);
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) { ServiceManager.forget(event.getEntity().getUUID()); }
    @SubscribeEvent public static void respawn(PlayerEvent.PlayerRespawnEvent event) {
        ServiceManager.forget(event.getEntity().getUUID());
        if (event.getEntity() instanceof ServerPlayer player) ProfessionsNetwork.sync(player);
    }
    @SubscribeEvent public static void stop(ServerStoppedEvent event) { ServiceManager.clear(); }
    @SubscribeEvent public static void reset(CharacterResetEvent event) {
        ProfessionData.get(event.server()).assign(event.account(), Profession.NONE);
        ServiceManager.forget(event.account());
        if (event.player() != null) ProfessionsNetwork.sync(event.player());
    }
    @SubscribeEvent public static void anvil(PlayerInteractEvent.RightClickBlock event) {
        if (!ProfessionsConfig.enabled() || !event.getLevel().getBlockState(event.getPos()).is(BlockTags.ANVIL)
                || SpecialtyRules.smith(event.getEntity())) return;
        event.setCanceled(true); event.setCancellationResult(InteractionResult.FAIL);
        if (event.getEntity() instanceof ServerPlayer player)
            ServiceManager.status(player, "Conhecimento de ferraria", "O uso da bigorna exige um ferreiro. Segure o equipamento e interaja com ele enquanto estiver agachado.");
    }
    @SubscribeEvent(priority = EventPriority.LOWEST) public static void mending(GetEnchantmentLevelEvent event) {
        if (!ProfessionsConfig.enabled() || SpecialtyRules.adminMending(event.getStack()) || !event.isTargetting(Enchantments.MENDING)) return;
        event.getEnchantments().removeIf(enchantment -> enchantment.is(Enchantments.MENDING));
    }
    @SubscribeEvent public static void brewing(PotionBrewEvent.Pre event) {
        if (ProfessionsConfig.enabled() && (event.getItem(3).is(Items.GLOWSTONE_DUST) || event.getItem(3).is(Items.REDSTONE))) event.setCanceled(true);
    }
    @SubscribeEvent(priority = EventPriority.LOWEST) public static void crafted(PlayerEvent.ItemCraftedEvent event) {
        if (event.getEntity() instanceof ServerPlayer && !FoodCompat.qualityAvailable()) FoodCompat.produced(event.getCrafting(), event.getEntity());
    }
    @SubscribeEvent(priority = EventPriority.LOWEST) public static void smelted(PlayerEvent.ItemSmeltedEvent event) {
        if (event.getEntity() instanceof ServerPlayer && !FoodCompat.qualityAvailable()) FoodCompat.produced(event.getSmelting(), event.getEntity());
    }
    @SubscribeEvent(priority = EventPriority.LOWEST) public static void fishing(ItemFishedEvent event) {
        for (var stack : event.getDrops()) SpecialtyRules.removeCommonMending(stack);
    }
    @SubscribeEvent(priority = EventPriority.LOWEST) public static void trades(VillagerTradesEvent event) {
        for (var listings : event.getTrades().values()) listings.replaceAll(factory -> (trader, random) -> {
            var offer = factory.getOffer(trader, random);
            if (offer == null || !ProfessionsConfig.enabled()) return offer;
            var result = offer.getResult();
            if (!SpecialtyRules.adminMending(result) && EnchantmentHelper.getEnchantmentsForCrafting(result).keySet().stream().anyMatch(e -> e.is(Enchantments.MENDING))) return null;
            return offer;
        });
    }
}
