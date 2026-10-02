package com.aurorion.trama.config;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.ModConfigSpec;
import java.util.List;

public final class TramaConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue ENABLED;
    public static final ModConfigSpec.IntValue XP_FIRST, XP_INCREMENT, MINUTES_FIRST, MINUTES_INCREMENT,
            PRACTICE_XP, PRACTICE_DAILY_CAP, BIOME_XP, BIOME_LIMIT, ADVANCEMENT_XP, RESPEC_HOURS;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> ADVANCEMENTS;
    static {
        var b = new ModConfigSpec.Builder();
        ENABLED = b.define("enabled", true);
        b.push("progression");
        XP_FIRST = b.comment("XP acumulativa por ponto apos os cinco iniciais; nunca usa XP vanilla.")
                .defineInRange("firstPointXp", 80, 1, 100000);
        XP_INCREMENT = b.defineInRange("xpCostIncrement", 20, 0, 100000);
        MINUTES_FIRST = b.comment("Tempo ativo acumulativo: primeira compra exige 15 min, a seguinte mais 19, etc.")
                .defineInRange("firstPointMinutes", 15, 1, 10000);
        MINUTES_INCREMENT = b.defineInRange("minuteCostIncrement", 4, 0, 10000);
        PRACTICE_XP = b.defineInRange("xpPerActiveMinute", 10, 1, 1000);
        PRACTICE_DAILY_CAP = b.comment("Teto apenas do XP de pratica por dia UTC. Descobertas ficam guardadas, sem expirar.",
                "Tempo ativo continua contando apos o teto. Nenhuma recompensa por matar mobs.")
                .defineInRange("dailyPracticeXpCap", 900, 1, 100000);
        BIOME_XP = b.defineInRange("firstBiomeXp", 120, 0, 10000);
        BIOME_LIMIT = b.defineInRange("rewardedBiomeLimit", 40, 0, 128);
        ADVANCEMENT_XP = b.defineInRange("firstAdvancementXp", 150, 0, 10000);
        ADVANCEMENTS = b.comment("IDs exatos de conquistas que dao XP uma vez por personagem.",
                "Aceita conquistas de mods/missoes. Receitas e conquistas fora desta lista dao zero XP.")
                .defineListAllowEmpty("advancements", List.of(
                        "minecraft:story/mine_stone", "minecraft:story/upgrade_tools",
                        "minecraft:story/smelt_iron", "minecraft:story/obtain_armor",
                        "minecraft:story/lava_bucket", "minecraft:story/iron_tools",
                        "minecraft:story/deflect_arrow", "minecraft:story/form_obsidian",
                        "minecraft:story/mine_diamond", "minecraft:story/enter_the_nether",
                        "minecraft:story/shiny_gear", "minecraft:story/enchant_item",
                        "minecraft:story/follow_ender_eye", "minecraft:story/enter_the_end",
                        "minecraft:adventure/sleep_in_bed", "minecraft:adventure/trade",
                        "minecraft:adventure/shoot_arrow", "minecraft:adventure/spyglass_at_parrot",
                        "minecraft:adventure/ol_betsy", "minecraft:adventure/salvage_sherd",
                        "minecraft:adventure/trim_with_any_armor_pattern", "minecraft:adventure/craft_decorated_pot",
                        "minecraft:adventure/adventuring_time", "minecraft:husbandry/plant_seed",
                        "minecraft:husbandry/breed_an_animal", "minecraft:husbandry/tame_an_animal",
                        "minecraft:husbandry/fishy_business", "minecraft:husbandry/safely_harvest_honey",
                        "minecraft:husbandry/balanced_diet", "minecraft:husbandry/wax_on",
                        "minecraft:husbandry/ride_a_boat_with_a_goat", "minecraft:husbandry/tadpole_in_a_bucket",
                        "minecraft:husbandry/make_a_sign_glow", "minecraft:nether/find_fortress",
                        "minecraft:nether/find_bastion", "minecraft:nether/explore_nether",
                        "minecraft:end/find_end_city", "minecraft:end/elytra"),
                        () -> "minecraft:story/mine_stone", v -> v instanceof String s && ResourceLocation.tryParse(s) != null);
        b.pop();
        RESPEC_HOURS = b.comment("Primeiro respec gratis e imediato fora de combate; seguintes respeitam este intervalo.")
                .defineInRange("respecCooldownHours", 24, 0, 720);
        SPEC = b.build();
    }
    private TramaConfig() {}
}
