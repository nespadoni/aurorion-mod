package com.aurorion.profissoes.config;

import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

public final class ProfessionsConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue ENABLED, FIX_FOODSPOIL_THAW;
    public static final ModConfigSpec.IntValue COMMON_ENCHANT_MAX, SPECIALIST_ENCHANT_MAX, CHEF_QUALITY;
    public static final ModConfigSpec.DoubleValue SEVERE_THRESHOLD, FIRST_AID_CEILING, CHEF_DECAY, AMATEUR_DECAY;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> EXCLUSIVE_RECIPES;
    /**
     * As receitas de gema, soquete e sigilo do Apotheosis 8.9, uma por uma. Nao por mod do serializer:
     * os 27 upgrades de equipamento dele (cota de malha → ferro, ouro → diamante) tambem usam serializer
     * proprio ({@code apotheosis:sized_upgrade_recipe}) e iriam junto para o arcanista.
     * {@code widthdrawal} e o nome real do arquivo no Apotheosis, com o erro de digitacao.
     */
    private static final List<String> DEFAULT_EXCLUSIVE_RECIPES = List.of(
            "apotheosis:socketing=arcanista", "apotheosis:widthdrawal=arcanista",
            "apotheosis:sigil_add_sockets=arcanista", "apotheosis:unnaming=arcanista",
            "apotheosis:malice=arcanista", "apotheosis:supremacy=arcanista");
    static {
        var b = new ModConfigSpec.Builder();
        ENABLED = b.define("enabled", true);
        COMMON_ENCHANT_MAX = b.defineInRange("commonEnchantmentMaxLevel", 3, 1, 10);
        SPECIALIST_ENCHANT_MAX = b.defineInRange("specialistEnchantmentMaxLevel", 10, 3, 255);
        CHEF_QUALITY = b.defineInRange("chefQualityLevel", 3, 1, 10);
        SEVERE_THRESHOLD = b.comment("Fracao de vida do membro que marca uma lesao grave.")
                .defineInRange("severeInjuryThreshold", .35, .05, .49);
        FIRST_AID_CEILING = b.comment("Primeiros socorros estabilizam uma lesao grave ate esta fracao.")
                .defineInRange("firstAidCeiling", .5, .5, .95);
        CHEF_DECAY = b.defineInRange("chefFoodDecayMultiplier", .5, .1, 1.0);
        AMATEUR_DECAY = b.defineInRange("amateurFoodDecayMultiplier", 2.0, 1.0, 10.0);
        FIX_FOODSPOIL_THAW = b.comment(
                "Conserta o descongelamento do FoodSpoil 1.1.7: sem isto, o tempo que a comida passou",
                "congelada e cobrado de uma vez ao descongelar, e ela vira carne podre na hora.",
                "Independente de 'enabled': e correcao de bug de terceiro, nao regra de profissao.",
                "Desligue se uma versao nova do FoodSpoil corrigir isso por conta propria.")
                .define("fixFoodSpoilThaw", true);
        EXCLUSIVE_RECIPES = b.comment(
                "Receitas da mesa de ferraria que pertencem a outro oficio, e nao ao ferreiro.",
                "Formato <chave>=<profissao> (medico, ferreiro, cozinheiro, arcanista, corretor). A chave pode ser:",
                "  <mod>          receitas cujo SERIALIZER vem desse mod (no Apotheosis pega tambem os upgrades de equipamento)",
                "  id:<mod>       receitas cujo ID vem desse mod",
                "  <mod>:<id>     uma receita exata (o padrao: so gemas, soquetes e sigilos do Apotheosis)",
                "Blocos e itens exclusivos sao por tag: aurorion_profissoes:exclusivo/<profissao>.")
                .defineListAllowEmpty("receitasExclusivas", DEFAULT_EXCLUSIVE_RECIPES,
                        () -> "apotheosis:socketing=arcanista", entry -> entry instanceof String text && text.indexOf('=') > 0);
        SPEC = b.build();
    }
    public static List<? extends String> exclusiveRecipes() { return SPEC.isLoaded() ? EXCLUSIVE_RECIPES.get() : DEFAULT_EXCLUSIVE_RECIPES; }
    public static boolean enabled() { return !SPEC.isLoaded() || ENABLED.get(); }
    /** Nao passa por {@link #enabled()}: desligar profissoes nao pode devolver o bug do congelador. */
    public static boolean fixFoodSpoilThaw() { return !SPEC.isLoaded() || FIX_FOODSPOIL_THAW.get(); }
    public static double severeThreshold() { return SPEC.isLoaded() ? SEVERE_THRESHOLD.get() : .35; }
    public static double firstAidCeiling() { return SPEC.isLoaded() ? FIRST_AID_CEILING.get() : .5; }
    private ProfessionsConfig() {}
}
