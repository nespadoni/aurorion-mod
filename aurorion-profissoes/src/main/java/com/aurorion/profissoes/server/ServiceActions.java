package com.aurorion.profissoes.server;

import com.aurorion.profissoes.api.ProfessionApi;
import com.aurorion.profissoes.compat.*;
import com.aurorion.profissoes.config.ProfessionsConfig;
import com.aurorion.profissoes.data.Profession;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import java.util.*;
import java.util.function.Predicate;

public final class ServiceActions {
    private static final TagKey<Item> MEDICAL_SUPPLIES = TagKey.create(Registries.ITEM, ResourceLocation.parse("aurorion_profissoes:medical_supplies"));
    public record Plan(String action, String title, String detail, ItemStack result, int materials, int levels, WoundPart wound, boolean honey) {}
    public static final class Refusal extends RuntimeException { public Refusal(String message) { super(message); } }
    private ServiceActions() {}
    public static List<String> actions(Profession profession, ServerPlayer customer) {
        return switch (profession) {
            case DOCTOR -> LsoCompat.parts(customer).keySet().stream().sorted(Comparator.comparingInt(Enum::ordinal)).map(e -> "treat:" + e.name()).toList();
            case SMITH -> List.of("repair");
            case CHEF -> List.of("finish_food");
            case ARCANIST -> List.of("enchant", "potion_strength", "potion_duration");
            case NONE, BROKER -> List.of();
        };
    }
    public static String title(String action) {
        if (action.startsWith("treat:")) return "Tratar " + LsoCompat.label(action.substring(6)).toLowerCase(Locale.ROOT);
        return switch (action) {
            case "repair" -> "Restaurar equipamento"; case "finish_food" -> "Finalizar prato";
            case "enchant" -> "Inscrever encantamentos"; case "potion_strength" -> "Concentrar poção";
            case "potion_duration" -> "Prolongar poção"; default -> "Serviço desconhecido";
        };
    }
    private static void require(boolean condition, String reason) { if (!condition) throw new Refusal(reason); }
    public static Plan plan(ServerPlayer professional, ServerPlayer customer, String action) {
        var profession = ProfessionApi.of(professional);
        ItemStack subject = customer.getMainHandItem(), supply = professional.getOffhandItem();
        if (action.startsWith("treat:")) {
            require(profession == Profession.DOCTOR, "Este atendimento exige um médico.");
            require(professional != customer, "Procure outro médico para realizar o atendimento.");
            require(LsoCompat.available(), "O sistema de ferimentos do LSO está indisponível.");
            var part = LsoCompat.part(customer, action.substring(6));
            require(part != null && part.aurorionMaxHealth() > 0, "Parte do corpo indisponível.");
            require(part.aurorionCritical() || part.aurorionHealth() < part.aurorionMaxHealth(), "Este membro já está saudável.");
            require(supply.is(MEDICAL_SUPPLIES), "Médico: segure 1 kit médico do LSO na mão secundária.");
            return new Plan(action, title(action), (part.aurorionCritical() ? "Lesão grave" : "Ferimento leve")
                    + " • " + Math.round(100 * part.aurorionHealth() / part.aurorionMaxHealth()) + "% de saúde • 1 kit médico", ItemStack.EMPTY, 1, 0, part, false);
        }
        require(!subject.isEmpty(), "Cliente: segure o item a ser atendido na mão principal.");
        ItemStack result = subject.copy(); int materials = 1, levels = 0; boolean honey = false;
        String detail;
        switch (action) {
            case "repair" -> {
                require(profession == Profession.SMITH, "O reparo exige um ferreiro.");
                require(near(professional, state -> state.is(BlockTags.ANVIL)), "O ferreiro precisa estar a até 3 blocos de uma bigorna.");
                require(subject.getCount() == 1 && subject.isDamaged() && subject.isRepairable(), "Segure um equipamento danificado que aceite reparo.");
                require(subject.getItem().isValidRepairItem(subject, supply), "Ferreiro: segure o material de reparo adequado na mão secundária.");
                int perMaterial = Math.max(1, subject.getMaxDamage() / 4);
                materials = Math.min(supply.getCount(), (subject.getDamageValue() + perMaterial - 1) / perMaterial);
                levels = materials;
                result.setDamageValue(Math.max(0, subject.getDamageValue() - perMaterial * materials));
                detail = itemName(subject) + " • +" + (subject.getDamageValue() - result.getDamageValue()) + " durabilidade • " + materials + " materiais • " + levels + " níveis do ferreiro";
            }
            case "enchant" -> {
                require(profession == Profession.ARCANIST, "A inscrição exige um arcanista.");
                require(near(professional, state -> state.is(Blocks.ENCHANTING_TABLE)), "O arcanista precisa estar a até 3 blocos da mesa de encantamentos.");
                require(subject.getCount() == 1 && EnchantmentHelper.canStoreEnchantments(subject) && !subject.is(Items.ENCHANTED_BOOK), "Segure um equipamento ou livro comum.");
                require(supply.is(Items.ENCHANTED_BOOK), "Arcanista: segure o livro encantado na mão secundária.");
                var original = EnchantmentHelper.getEnchantmentsForCrafting(subject);
                var merged = new ItemEnchantments.Mutable(original);
                for (var entry : EnchantmentHelper.getEnchantmentsForCrafting(supply).entrySet()) {
                    var enchant = entry.getKey();
                    require(!enchant.is(Enchantments.MENDING), "Mending é reservado a equipamentos autorizados pela staff.");
                    int level = entry.getIntValue();
                    require(level <= ProfessionsConfig.SPECIALIST_ENCHANT_MAX.get(), "O nível do livro ultrapassa o limite deste ofício.");
                    if (level <= original.getLevel(enchant)) continue;
                    require(subject.is(Items.BOOK) || enchant.value().canEnchant(subject), "O livro não é compatível com este equipamento.");
                    for (var existing : merged.keySet()) require(existing.equals(enchant) || Enchantment.areCompatible(existing, enchant), "Há encantamentos incompatíveis no item.");
                    merged.set(enchant, level); levels += Math.max(1, level - original.getLevel(enchant)) * 2;
                }
                require(levels > 0, "O livro não acrescenta encantamentos ao item.");
                if (subject.is(Items.BOOK)) result = subject.transmuteCopy(Items.ENCHANTED_BOOK);
                EnchantmentHelper.setEnchantments(result, merged.toImmutable());
                detail = itemName(subject) + " • 1 livro consumido • " + levels + " níveis do arcanista";
            }
            case "potion_strength", "potion_duration" -> {
                require(profession == Profession.ARCANIST, "O aprimoramento exige um arcanista.");
                require(near(professional, state -> state.is(Blocks.BREWING_STAND)), "O arcanista precisa estar a até 3 blocos de um suporte de poções.");
                Item catalyst = action.equals("potion_strength") ? Items.GLOWSTONE_DUST : Items.REDSTONE;
                require(supply.is(catalyst), "Arcanista: segure " + catalyst.getDescription().getString() + " na mão secundária.");
                require(subject.getCount() == 1 && customer.level().potionBrewing().hasMix(subject, supply), "Esta poção não aceita esse aprimoramento.");
                result = customer.level().potionBrewing().mix(supply, subject);
                require(!ItemStack.isSameItemSameComponents(subject, result), "Esta poção já atingiu o limite.");
                detail = itemName(subject) + " → " + itemName(result) + " • 1 catalisador";
            }
            case "finish_food" -> {
                require(profession == Profession.CHEF, "A finalização exige um cozinheiro.");
                require(FoodCompat.qualityAvailable(), "A integração Quality Food está indisponível.");
                require(FoodCompat.isFood(subject) && subject.getCount() <= 16, "Segure um lote de até 16 alimentos.");
                require(FoodCompat.grade(subject) != 1, "Este lote já recebeu preparo profissional.");
                require(FoodCompat.freshEnough(subject, customer), "Este alimento perdeu o frescor; a finalização não recupera comida estragada.");
                require(supply.is(Items.HONEY_BOTTLE), "Cozinheiro: segure 1 frasco de mel na mão secundária.");
                honey = true;
                detail = subject.getCount() + " × " + itemName(subject) + " • 1 frasco de mel • a idade do alimento é mantida";
            }
            default -> throw new Refusal("Serviço desconhecido.");
        }
        require(professional.experienceLevel >= levels || professional.isCreative(), "O profissional não possui os níveis de experiência necessários.");
        return new Plan(action, title(action), detail, result, materials, levels, null, honey);
    }
    public static void execute(Plan plan, ServerPlayer professional, ServerPlayer customer) {
        if (plan.wound != null) LsoCompat.treat(customer, plan.wound);
        else {
            if (plan.honey) FoodCompat.finish(plan.result, true, customer.level());
            customer.setItemInHand(InteractionHand.MAIN_HAND, plan.result);
        }
        professional.getOffhandItem().shrink(plan.materials);
        if (plan.levels > 0 && !professional.isCreative()) professional.giveExperienceLevels(-plan.levels);
        if (plan.honey) {
            var bottle = new ItemStack(Items.GLASS_BOTTLE);
            if (!professional.getInventory().add(bottle)) professional.drop(bottle, false);
        }
        professional.inventoryMenu.broadcastFullState();
        customer.inventoryMenu.broadcastFullState();
    }
    private static String itemName(ItemStack stack) {
        String name = stack.getHoverName().getString(); return name.length() > 80 ? name.substring(0, 80) : name;
    }
    private static boolean near(ServerPlayer player, Predicate<BlockState> match) {
        BlockPos origin = player.blockPosition();
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-3, -3, -3), origin.offset(3, 3, 3)))
            if (pos.distSqr(origin) <= 9 && player.level().hasChunkAt(pos) && match.test(player.level().getBlockState(pos))) return true;
        return false;
    }
}
