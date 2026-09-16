package com.aurorion.profissoes.compat;

import com.aurorion.profissoes.AurorionProfissoes;
import com.aurorion.profissoes.api.ProfessionApi;
import com.aurorion.profissoes.config.ProfessionsConfig;
import com.aurorion.profissoes.data.Profession;
import net.minecraft.core.component.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.fml.ModList;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import java.lang.reflect.*;

public final class FoodCompat {
    private static final String GRADE = "AurorionCookingGrade";
    private static boolean initialized;
    private static Method randomQuality, freshness, snapshot, decayRate;
    private static DataComponentType<Object> qualityComponent;
    private static Object noQuality;
    private FoodCompat() {}
    @SuppressWarnings("unchecked")
    private static synchronized void init() {
        if (initialized) return;
        initialized = true;
        try {
            if (ModList.get().isLoaded("quality_food")) {
                var quality = Class.forName("de.cadentem.quality_food.core.codecs.Quality");
                randomQuality = quality.getMethod("getRandom", ItemStack.class, int.class);
                noQuality = quality.getField("NONE").get(null);
                qualityComponent = (DataComponentType<Object>)net.minecraft.core.registries.BuiltInRegistries.DATA_COMPONENT_TYPE
                        .get(net.minecraft.resources.ResourceLocation.parse("quality_food:quality"));
            }
            if (ModList.get().isLoaded("foodspoil")) {
                freshness = Class.forName("com.elcuruxa.foodspoil.data.FoodData")
                        .getMethod("calculateFreshness", ItemStack.class, long.class, float.class, net.minecraft.world.level.Level.class);
                snapshot = Class.forName("com.elcuruxa.foodspoil.data.FoodData").getMethod("saveSnapshot", ItemStack.class, float.class, long.class);
                decayRate = Class.forName("com.elcuruxa.foodspoil.config.FoodSpoilConfig").getMethod("getBaseDecayRate");
            }
        } catch (ReflectiveOperationException | LinkageError error) {
            randomQuality = null; qualityComponent = null;
            AurorionProfissoes.LOGGER.error("Profissoes: ponte alimentar incompativel.", error);
        }
    }
    public static boolean qualityAvailable() { init(); return qualityComponent != null; }
    public static boolean isFood(ItemStack stack) { return !stack.isEmpty() && stack.has(DataComponents.FOOD); }
    public static void produced(ItemStack stack, Player player) {
        if (!ProfessionsConfig.enabled() || !isFood(stack) || player != null && player.level().isClientSide()) return;
        var server = ServerLifecycleHooks.getCurrentServer();
        if (player == null && (server == null || !server.isSameThread())) return;
        finish(stack, player != null && (ProfessionApi.has(player, Profession.CHEF) || ProfessionApi.staff(player)),
                player == null ? server.overworld() : player.level());
    }
    public static void finish(ItemStack stack, boolean chef, Level level) {
        init();
        // Fixar o frescor ANTES de trocar a taxa evita rejuvenescimento retroativo.
        if (freshness != null) {
            try {
                float current = currentFreshness(stack, level);
                snapshot.invoke(null, stack, current, level.getGameTime());
            } catch (ReflectiveOperationException error) { throw new IllegalStateException("Falha ao preservar validade", error); }
        }
        if (qualityComponent != null) {
            try {
                stack.set(qualityComponent, chef ? randomQuality.invoke(null, stack, ProfessionsConfig.CHEF_QUALITY.get()) : noQuality);
            } catch (ReflectiveOperationException error) { throw new IllegalStateException("Falha ao aplicar qualidade", error); }
        }
        // Nao apaga datas, snapshots, congelamento ou outros dados do FoodSpoil.
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putInt(GRADE, chef ? 1 : 0));
    }
    public static float decayMultiplier(ItemStack stack) {
        if (!ProfessionsConfig.enabled()) return 1;
        var custom = stack.get(DataComponents.CUSTOM_DATA);
        if (custom == null) return 1;
        var tag = custom.getUnsafe(); // Somente leitura; nao copiar NBT no calculo de deterioracao.
        if (!tag.contains(GRADE)) return 1;
        return tag.getInt(GRADE) == 1 ? ProfessionsConfig.CHEF_DECAY.get().floatValue() : ProfessionsConfig.AMATEUR_DECAY.get().floatValue();
    }
    public static boolean freshEnough(ItemStack stack, Player owner) {
        init();
        if (freshness == null) return true;
        try {
            return currentFreshness(stack, owner.level()) >= 60;
        } catch (ReflectiveOperationException error) { throw new IllegalStateException("Falha ao consultar validade", error); }
    }
    private static float currentFreshness(ItemStack stack, Level level) throws ReflectiveOperationException {
        float rate = ((Number)decayRate.invoke(null)).floatValue();
        return ((Number)freshness.invoke(null, stack, level.getGameTime(), rate, level)).floatValue();
    }
    public static int grade(ItemStack stack) {
        var data = stack.get(DataComponents.CUSTOM_DATA);
        return data == null || !data.contains(GRADE) ? -1 : data.getUnsafe().getInt(GRADE);
    }
    public static boolean samePreparation(ItemStack a, ItemStack b) {
        init();
        return grade(a) == grade(b) && (qualityComponent == null || java.util.Objects.equals(a.get(qualityComponent), b.get(qualityComponent)));
    }
}
