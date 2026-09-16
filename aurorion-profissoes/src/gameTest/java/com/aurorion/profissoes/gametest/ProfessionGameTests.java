package com.aurorion.profissoes.gametest;

import com.aurorion.profissoes.compat.*;
import com.aurorion.profissoes.data.*;
import com.aurorion.profissoes.network.*;
import com.aurorion.profissoes.server.*;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.alchemy.*;
import net.minecraft.world.item.enchantment.*;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.brewing.PotionBrewEvent;
import net.neoforged.neoforge.gametest.*;
import net.neoforged.neoforge.network.registration.ChannelAttributes;
import java.lang.reflect.*;
import java.util.*;

@Mod("aurorion_profissoes_tests")
@GameTestHolder("aurorion_profissoes_tests")
@PrefixGameTestTemplate(false)
public final class ProfessionGameTests {
    public ProfessionGameTests() {}
    private record TestPlayer(ServerPlayer player, EmbeddedChannel channel) implements AutoCloseable {
        PanelPayload panel() {
            PanelPayload last = null; Object packet;
            while ((packet = channel.readOutbound()) != null) if (packet instanceof ClientboundCustomPayloadPacket custom && custom.payload() instanceof PanelPayload panel) last = panel;
            if (last == null) throw new AssertionError("Panel payload was not sent");
            return last;
        }
        public void close() {
            ServiceManager.forget(player.getUUID());
            ProfessionData.get(player.server).assign(player.getUUID(), Profession.NONE);
            player.server.getPlayerList().remove(player);
            channel.finishAndReleaseAll();
        }
    }
    private static TestPlayer player(GameTestHelper helper, String name, Profession profession) {
        var cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), name), false);
        var player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), cookie.gameProfile(), cookie.clientInformation());
        var connection = new Connection(PacketFlow.SERVERBOUND);
        var channel = new EmbeddedChannel(connection);
        var channels = ChannelAttributes.getOrCreateAdHocChannels(connection);
        channels.add(PanelPayload.TYPE.id()); channels.add(ProfessionPayload.TYPE.id());
        // Simula os canais negociados pelo cliente real do LSO antes do login.
        for (String nameId : List.of("body_part_healing_time", "drink_block_fluid", "sync_body_damage_healing_consumables", "sync_body_part_resistance_items", "sync_body_parts_damage_sources", "sync_temperature_biomes", "sync_temperature_blocks", "sync_temperature_consumable_blocks", "sync_temperature_consumables", "sync_temperature_dimensions", "sync_temperature_fuel_items", "sync_temperature_items", "sync_temperature_mounts", "sync_temperature_origins", "sync_thirst_blocks", "sync_thirst_consumables", "update_body_damage", "update_hearts", "update_temperatures", "update_thirst", "update_wetness"))
            channels.add(ResourceLocation.fromNamespaceAndPath("legendarysurvivaloverhaul", nameId));
        player.server.getPlayerList().placeNewPlayer(connection, player, cookie);
        player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        var origin = helper.absolutePos(new BlockPos(2, 2, 2));
        player.teleportTo(origin.getX() + .5, origin.getY(), origin.getZ() + .5);
        ProfessionData.get(player.server).assign(player.getUUID(), profession);
        player.setExperienceLevels(30);
        return new TestPlayer(player, channel);
    }
    private static void equipment(ServerPlayer customer, ServerPlayer smith) {
        var sword = new ItemStack(Items.DIAMOND_SWORD); sword.setDamageValue(1000);
        customer.setItemInHand(InteractionHand.MAIN_HAND, sword);
        smith.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.DIAMOND, 4));
        smith.level().setBlockAndUpdate(smith.blockPosition().offset(1, 0, 0), Blocks.ANVIL.defaultBlockState());
    }
    @GameTest(template="empty") public static void twoPlayersConsentAndReplay(GameTestHelper helper) {
        try (var customer = player(helper, "Cliente", Profession.NONE); var smith = player(helper, "Ferreiro", Profession.SMITH)) {
            equipment(customer.player, smith.player);
            ServiceManager.open(customer.player, smith.player);
            var catalog = customer.panel();
            ServiceManager.action(customer.player, new ActionPayload(catalog.token(), "repair"));
            var approval = smith.panel();
            helper.assertTrue(customer.player.getMainHandItem().getDamageValue() == 1000, "Nao pode reparar antes do aceite");
            ServiceManager.action(smith.player, new ActionPayload(approval.token(), "accept"));
            helper.assertTrue(customer.player.getMainHandItem().getDamageValue() == 0, "Reparo deve atingir o equipamento real do cliente");
            helper.assertTrue(smith.player.getOffhandItem().getCount() == 1, "Reparo deve consumir exatamente tres diamantes");
            helper.assertTrue(smith.player.experienceLevel == 27, "Reparo deve consumir tres niveis");
            ServiceManager.action(smith.player, new ActionPayload(approval.token(), "accept"));
            helper.assertTrue(smith.player.getOffhandItem().getCount() == 1, "Repetir pacote nao pode consumir nem executar de novo");
        }
        helper.succeed();
    }
    @GameTest(template="empty") public static void swappingItemsInvalidatesConsent(GameTestHelper helper) {
        try (var customer = player(helper, "Troca", Profession.NONE); var smith = player(helper, "Forja", Profession.SMITH)) {
            equipment(customer.player, smith.player);
            ServiceManager.open(customer.player, smith.player);
            ServiceManager.action(customer.player, new ActionPayload(customer.panel().token(), "repair"));
            var approval = smith.panel();
            customer.player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK));
            ServiceManager.action(smith.player, new ActionPayload(approval.token(), "accept"));
            helper.assertTrue(customer.player.getMainHandItem().is(Items.STICK), "Item trocado nao pode ser substituido");
            helper.assertTrue(smith.player.getOffhandItem().getCount() == 4, "Falha nao pode gastar material");
        }
        helper.succeed();
    }
    @GameTest(template="empty") public static void anvilAndMendingRespectSpecialties(GameTestHelper helper) {
        try (var user = player(helper, "Bigorna", Profession.NONE)) {
            equipment(user.player, user.player);
            var menu = new AnvilMenu(1, user.player.getInventory(), ContainerLevelAccess.create(helper.getLevel(), user.player.blockPosition().offset(1, 0, 0)));
            menu.getSlot(0).set(user.player.getMainHandItem().copy()); menu.getSlot(1).set(new ItemStack(Items.DIAMOND)); menu.createResult();
            helper.assertTrue(menu.getSlot(2).getItem().isEmpty(), "Nao ferreiro nao pode fabricar resultado na bigorna");
            ProfessionData.get(user.player.server).assign(user.player.getUUID(), Profession.SMITH); menu.createResult();
            helper.assertTrue(!menu.getSlot(2).getItem().isEmpty(), "Ferreiro pode reparar pela bigorna vanilla");
            var mending = helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.MENDING);
            var sword = user.player.getMainHandItem(); sword.enchant(mending, 1);
            helper.assertTrue(sword.getEnchantmentLevel(mending) == 0, "Mending comum deve ser inerte");
            helper.assertTrue(EnchantmentHelper.getRandomItemWith(EnchantmentEffectComponents.REPAIR_WITH_XP, user.player, ItemStack::isDamaged).isEmpty(),
                    "Coleta de XP nao pode selecionar Mending comum");
            SpecialtyRules.authorizeMending(sword, true);
            helper.assertTrue(sword.getEnchantmentLevel(mending) == 1, "Mending autorizado deve funcionar");
            helper.assertTrue(EnchantmentHelper.getRandomItemWith(EnchantmentEffectComponents.REPAIR_WITH_XP, user.player, ItemStack::isDamaged).isPresent(),
                    "Coleta de XP deve selecionar o equipamento autorizado");
            var book = new ItemStack(Items.ENCHANTED_BOOK); book.enchant(mending, 1); SpecialtyRules.removeCommonMending(book);
            helper.assertTrue(EnchantmentHelper.getEnchantmentsForCrafting(book).isEmpty(), "Mending deve ser removido do loot comum");
        }
        helper.succeed();
    }
    @GameTest(template="empty") public static void arcanistUsesBooksAndVanillaPotionRecipes(GameTestHelper helper) {
        try (var mage = player(helper, "Arcanista", Profession.ARCANIST); var customer = player(helper, "Encantado", Profession.NONE)) {
            helper.getLevel().setBlockAndUpdate(mage.player.blockPosition().offset(1, 0, 0), Blocks.ENCHANTING_TABLE.defaultBlockState());
            var protection = helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.PROTECTION);
            customer.player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_CHESTPLATE));
            var book = new ItemStack(Items.ENCHANTED_BOOK); book.enchant(protection, 5);
            mage.player.setItemInHand(InteractionHand.OFF_HAND, book);
            var plan = ServiceActions.plan(mage.player, customer.player, "enchant");
            ServiceActions.execute(plan, mage.player, customer.player);
            helper.assertTrue(customer.player.getMainHandItem().getEnchantmentLevel(protection) == 5, "Arcanista aplica nivel real do livro acima do vanilla");
            helper.assertTrue(mage.player.getOffhandItem().isEmpty(), "O livro deve ser consumido");
            helper.getLevel().setBlockAndUpdate(mage.player.blockPosition().offset(1, 0, 0), Blocks.BREWING_STAND.defaultBlockState());
            customer.player.setItemInHand(InteractionHand.MAIN_HAND, PotionContents.createItemStack(Items.POTION, Potions.SWIFTNESS));
            mage.player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.REDSTONE));
            ServiceActions.execute(ServiceActions.plan(mage.player, customer.player, "potion_duration"), mage.player, customer.player);
            helper.assertTrue(customer.player.getMainHandItem().get(net.minecraft.core.component.DataComponents.POTION_CONTENTS).is(Potions.LONG_SWIFTNESS), "Aprimoramento deve usar a receita de pocao do jogo");
            var items = net.minecraft.core.NonNullList.withSize(5, ItemStack.EMPTY); items.set(3, new ItemStack(Items.REDSTONE));
            var event = new PotionBrewEvent.Pre(items); NeoForge.EVENT_BUS.post(event);
            helper.assertTrue(event.isCanceled(), "Automacao nao deve produzir aprimoramentos exclusivos");
        }
        helper.succeed();
    }
    @GameTest(template="empty") public static void realLsoTraumaPersistsAndDoctorTreats(GameTestHelper helper) throws Exception {
        if (!ModList.get().isLoaded("legendarysurvivaloverhaul")) { helper.succeed(); return; }
        try (var doctor = player(helper, "Medico", Profession.DOCTOR); var patient = player(helper, "Paciente", Profession.NONE)) {
            helper.assertTrue(LsoCompat.available(), "LSO instalado deve expor o sistema de ferimentos");
            WoundPart part = LsoCompat.part(patient.player, "LEFT_ARM");
            part.getClass().getMethod("setMaxHealth", float.class).invoke(part, 10f);
            part.getClass().getMethod("hurt", float.class).invoke(part, 9f);
            helper.assertTrue(part.aurorionCritical(), "Dano grave precisa marcar o membro");
            part.getClass().getMethod("heal", float.class).invoke(part, 100f);
            helper.assertTrue(part.aurorionHealth() == 5f, "Primeiros socorros estabilizam sem remover lesao");
            var nbt = (CompoundTag)part.getClass().getMethod("writeNbt", CompoundTag.class).invoke(part, new CompoundTag());
            part.aurorionTreat(); part.getClass().getMethod("readNBT", CompoundTag.class).invoke(part, nbt);
            helper.assertTrue(part.aurorionCritical(), "Recarregar NBT nao pode remover lesao");
            doctor.player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("legendarysurvivaloverhaul:medkit"))));
            ServiceActions.execute(ServiceActions.plan(doctor.player, patient.player, "treat:LEFT_ARM"), doctor.player, patient.player);
            helper.assertTrue(!part.aurorionCritical() && part.aurorionHealth() == 10f, "Medico deve tratar o membro real do LSO");
            helper.assertTrue(doctor.player.getOffhandItem().isEmpty(), "Kit medico deve ser consumido");
        }
        helper.succeed();
    }
    @GameTest(template="empty") public static void realFoodQualityAndFreshnessSurviveHandover(GameTestHelper helper) throws Exception {
        if (!ModList.get().isLoaded("quality_food")) { helper.succeed(); return; }
        try (var chef = player(helper, "Cozinheiro", Profession.CHEF); var customer = player(helper, "Comensal", Profession.NONE)) {
            var bread = new ItemStack(Items.BREAD, 2);
            FoodCompat.produced(bread, customer.player);
            helper.assertTrue(FoodCompat.grade(bread) == 0, "Nao cozinheiro produz alimento comum");
            if (ModList.get().isLoaded("foodspoil")) {
                Class<?> food = Class.forName("com.elcuruxa.foodspoil.data.FoodData");
                food.getMethod("saveSnapshot", ItemStack.class, float.class, long.class).invoke(null, bread, 70f, helper.getLevel().getGameTime());
            }
            FoodCompat.finish(bread, true, helper.getLevel());
            var quality = Class.forName("de.cadentem.quality_food.util.QualityUtils").getMethod("getQuality", ItemStack.class).invoke(null, bread);
            helper.assertTrue(((Number)quality.getClass().getMethod("level").invoke(quality)).intValue() == 3, "Cozinheiro deve produzir qualidade diamante real");
            customer.player.setItemInHand(InteractionHand.MAIN_HAND, bread);
            helper.assertTrue(FoodCompat.grade(customer.player.getMainHandItem()) == 1, "Entrega nao pode rebaixar qualidade");
            helper.assertTrue(!FoodCompat.samePreparation(bread, new ItemStack(Items.BREAD)), "Comida comum nao pode receber qualidade por empilhamento");
            if (ModList.get().isLoaded("foodspoil")) {
                Class<?> food = Class.forName("com.elcuruxa.foodspoil.data.FoodData");
                float freshness = ((Number)food.getMethod("getSnapshotFreshness", ItemStack.class).invoke(null, bread)).floatValue();
                helper.assertTrue(freshness == 70f, "Trocar conservacao nao pode rejuvenescer alimento");
                float multiplier = ((Number)Class.forName("com.elcuruxa.foodspoil.util.FoodDetector").getMethod("getDecayRateMultiplier", ItemStack.class).invoke(null, bread)).floatValue();
                helper.assertTrue(multiplier == .5f, "FoodSpoil deve usar a taxa do cozinheiro");
            }
        }
        helper.succeed();
    }

    @GameTest(template="empty") public static void distanceAndMissingMaterialsRejectService(GameTestHelper helper) {
        try (var customer = player(helper, "Distante", Profession.NONE); var smith = player(helper, "Forjador", Profession.SMITH)) {
            equipment(customer.player, smith.player);
            smith.player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
            ServiceManager.open(customer.player, smith.player);
            var unavailable = customer.panel();
            helper.assertTrue(!unavailable.rows().getFirst().enabled(), "Falta de material deve desabilitar o pedido");
            ServiceManager.action(customer.player, new ActionPayload(unavailable.token(), "repair"));
            helper.assertTrue(customer.player.getMainHandItem().getDamageValue() == 1000, "Pacote forjado sem material nao pode reparar");
            ServiceManager.forget(customer.player.getUUID());
            smith.player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.DIAMOND, 4));
            ServiceManager.open(customer.player, smith.player);
            ServiceManager.action(customer.player, new ActionPayload(customer.panel().token(), "repair"));
            var approval = smith.panel();
            ServiceManager.open(smith.player, smith.player);
            helper.assertTrue(smith.panel().token().equals(approval.token()), "Abrir painel durante pedido deve recuperar o aceite pendente");
            customer.player.teleportTo(customer.player.getX() + 10, customer.player.getY(), customer.player.getZ());
            ServiceManager.action(smith.player, new ActionPayload(approval.token(), "accept"));
            helper.assertTrue(customer.player.getMainHandItem().getDamageValue() == 1000 && smith.player.getOffhandItem().getCount() == 4,
                    "Afastamento deve cancelar sem modificar itens ou materiais");
        }
        helper.succeed();
    }
    @GameTest(template="empty") public static void actualProductionHooksRespectProfession(GameTestHelper helper) throws Exception {
        if (!ModList.get().isLoaded("quality_food")) { helper.succeed(); return; }
        try (var chef = player(helper, "Culinaria", Profession.CHEF); var novice = player(helper, "Aprendiz", Profession.NONE)) {
            var apply = Class.forName("de.cadentem.quality_food.util.QualityUtils").getMethod("applyQuality", ItemStack.class, Collection.class,
                    net.minecraft.world.entity.player.Player.class, net.minecraft.core.RegistryAccess.class);
            var bread = new ItemStack(Items.BREAD);
            apply.invoke(null, bread, List.of(new ItemStack(Items.WHEAT)), chef.player, helper.getLevel().registryAccess());
            helper.assertTrue(FoodCompat.grade(bread) == 1, "Hook real de crafting deve reconhecer cozinheiro");
            var basic = new ItemStack(Items.BREAD);
            apply.invoke(null, basic, List.of(new ItemStack(Items.WHEAT)), novice.player, helper.getLevel().registryAccess());
            helper.assertTrue(FoodCompat.grade(basic) == 0, "Hook real de crafting deve limitar aprendiz");
            var furnace = new net.minecraft.world.level.block.entity.FurnaceBlockEntity(chef.player.blockPosition(), Blocks.FURNACE.defaultBlockState());
            furnace.setLevel(helper.getLevel());
            var use = Class.forName("de.cadentem.quality_food.util.Utils").getMethod("useQuality", net.minecraft.world.level.block.entity.BlockEntity.class,
                    ItemStack.class, net.minecraft.world.entity.player.Player.class);
            var steak = new ItemStack(Items.COOKED_BEEF);
            use.invoke(null, furnace, steak, chef.player);
            helper.assertTrue(FoodCompat.grade(steak) == 1, "Retirada pelo cozinheiro deve produzir preparo profissional");
            var automated = new ItemStack(Items.COOKED_BEEF);
            use.invoke(null, furnace, automated, null);
            helper.assertTrue(FoodCompat.grade(automated) == 0, "Automacao deve produzir preparo comum");
            var pos = chef.player.blockPosition().offset(0, 0, 2);
            helper.getLevel().setBlockAndUpdate(pos, Blocks.CAMPFIRE.defaultBlockState());
            var camp = (net.minecraft.world.level.block.entity.CampfireBlockEntity)helper.getLevel().getBlockEntity(pos);
            camp.placeFood(novice.player, new ItemStack(Items.BEEF), 1);
            net.minecraft.world.level.block.entity.CampfireBlockEntity.cookTick(helper.getLevel(), pos, Blocks.CAMPFIRE.defaultBlockState(), camp);
            var drops = helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new net.minecraft.world.phys.AABB(pos).inflate(1));
            helper.assertTrue(drops.stream().anyMatch(drop -> drop.getItem().is(Items.COOKED_BEEF) && FoodCompat.grade(drop.getItem()) == 0),
                    "Fogueira deve entregar comida comum marcada para conservacao");
        }
        helper.succeed();
    }

    @GameTest(template="empty") public static void vanillaMenusCannotBypassSpecialties(GameTestHelper helper) throws Exception {
        try (var user = player(helper, "Bancadas", Profession.NONE)) {
            var sword = new ItemStack(Items.DIAMOND_SWORD); sword.setDamageValue(1000);
            var grind = new GrindstoneMenu(2, user.player.getInventory());
            grind.getSlot(0).set(sword.copy()); grind.getSlot(1).set(sword.copy());
            helper.assertTrue(grind.getSlot(2).getItem().isEmpty(), "Pedra de amolar nao pode reparar dois itens");
            var registry = helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
            sword.enchant(registry.getOrThrow(Enchantments.SHARPNESS), 2);
            grind.getSlot(1).set(ItemStack.EMPTY); grind.getSlot(0).set(sword.copy());
            helper.assertTrue(!grind.getSlot(2).getItem().isEmpty() && grind.getSlot(2).getItem().getDamageValue() == 1000,
                    "Desencantar um item continua permitido e nao repara");
            var repair = new net.minecraft.world.item.crafting.RepairItemRecipe(net.minecraft.world.item.crafting.CraftingBookCategory.MISC);
            var input = net.minecraft.world.item.crafting.CraftingInput.of(2, 1, List.of(sword.copy(), sword.copy()));
            helper.assertTrue(!repair.matches(input, helper.getLevel()), "Grade de criacao nao pode contornar a bigorna");
            var menu = new EnchantmentMenu(3, user.player.getInventory());
            var method = EnchantmentMenu.class.getDeclaredMethod("getEnchantmentList", net.minecraft.core.RegistryAccess.class, ItemStack.class, int.class, int.class);
            method.setAccessible(true);
            @SuppressWarnings("unchecked") var options = (List<EnchantmentInstance>)method.invoke(menu, helper.getLevel().registryAccess(), new ItemStack(Items.DIAMOND_PICKAXE), 2, 30);
            helper.assertTrue(!options.isEmpty() && options.stream().allMatch(enchantment -> enchantment.level <= 3 && !enchantment.enchantment.is(Enchantments.MENDING)),
                    "Menu real da mesa limita opcoes comuns sem impedir encantamento");
            var brew = net.minecraft.world.level.block.entity.BrewingStandBlockEntity.class.getDeclaredMethod("isBrewable", PotionBrewing.class, net.minecraft.core.NonNullList.class);
            brew.setAccessible(true);
            var items = net.minecraft.core.NonNullList.withSize(5, ItemStack.EMPTY);
            items.set(0, PotionContents.createItemStack(Items.POTION, Potions.SWIFTNESS)); items.set(3, new ItemStack(Items.REDSTONE));
            helper.assertTrue(!(boolean)brew.invoke(null, helper.getLevel().potionBrewing(), items), "Suporte vanilla nao inicia aprimoramento reservado");
            items.set(0, PotionContents.createItemStack(Items.POTION, Potions.AWKWARD)); items.set(3, new ItemStack(Items.SUGAR));
            helper.assertTrue((boolean)brew.invoke(null, helper.getLevel().potionBrewing(), items), "Suporte continua produzindo pocoes basicas");
        }
        helper.succeed();
    }
}
