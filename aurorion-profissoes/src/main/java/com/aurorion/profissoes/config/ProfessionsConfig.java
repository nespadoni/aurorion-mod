package com.aurorion.profissoes.config;

import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

public final class ProfessionsConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue ENABLED, FIX_FOODSPOIL_THAW;
    public static final ModConfigSpec.IntValue COMMON_ENCHANT_MAX, SPECIALIST_ENCHANT_MAX, CHEF_QUALITY;
    public static final ModConfigSpec.DoubleValue SEVERE_THRESHOLD, FIRST_AID_CEILING, CHEF_DECAY, AMATEUR_DECAY;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> EXCLUSIVE_RECIPES;
    public static final ModConfigSpec.BooleanValue CRAFT_ENABLED, FURNACE_OWNER;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> OWNER_MODS;
    public static final ModConfigSpec.ConfigValue<String> TECH_SKILL;
    public static final ModConfigSpec.IntValue HEAL_DC, TREAT_DC, COOK_DC, HAGGLE_PER_POINT, HAGGLE_CAP, INTIMIDATION_DC,
            OFFENDED_SECONDS, ENCHANT_DC, LOCKPICK_DC, FISHING_DC;
    public static final ModConfigSpec.ConfigValue<String> ENCHANT_SKILL, LOCKPICK_SKILL, FISHING_SKILL;
    public static final ModConfigSpec.IntValue CRAFT_NATURAL_MIN_DC, MASTERPIECE_DC, CRAFT_DEFAULT_BONUS, MACHINE_BONUS,
            FURNACE_EXTRA_DC;
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
        b.comment("Teste de oficio: fabricar pode falhar. d20 + Criacao da ficha contra a CD do item",
                "(datapack aurorion/oficio, ou a heuristica: equipamento 12, comida 8, o resto 2, + raridade).",
                "Falha: os ingredientes se perdem e sai uma Peca Arruinada. Critico em obra de CD alta: obra-prima.").push("oficio");
        CRAFT_ENABLED = b.comment("Desligado, nada falha.").define("habilitado", true);
        CRAFT_NATURAL_MIN_DC = b.comment("O 1 natural so derruba com CD a partir desta (tabua e graveto nunca falham).")
                .defineInRange("cdMinimaNatural", 8, 0, 60);
        MASTERPIECE_DC = b.comment("Obra-prima so em item com CD a partir desta: nao empilhavel ganha a assinatura do",
                "personagem; empilhavel sai com um a mais.").defineInRange("obraPrimaCd", 10, 0, 60);
        CRAFT_DEFAULT_BONUS = b.comment("Bonus de quem nao tem ficha lida.").defineInRange("bonusSemFicha", 3, -10, 30);
        FURNACE_EXTRA_DC = b.comment("Somado a CD do que sai de uma fornalha (fundir pede mao firme).")
                .defineInRange("cdFornalha", 4, 0, 30);
        FURNACE_OWNER = b.comment("Fornalha, suporte de pocoes e as maquinas dos mods em 'modsComDono' usam a ficha de",
                "quem as colocou (mesmo offline). Desligado, ou sem dono registrado, trabalham como maquina.")
                .define("donoDaFornalha", true);
        OWNER_MODS = b.comment("Mods cujos blocos com inventario guardam quem os colocou, para as maquinas deles",
                "testarem com a ficha dessa pessoa (adaptadores de oficio).")
                .defineListAllowEmpty("modsComDono", List.of("create", "createaddition", "farmersdelight", "oritech", "farm_and_charm", "candlelight", "malum", "mysticalagriculture"),
                        () -> "farmersdelight", entry -> entry instanceof String text && text.matches("[a-z0-9_.-]+"));
        MACHINE_BONUS = b.comment("Bonus fixo de maquina sem dono (Create e adaptadores de outros mods): material barato",
                "nunca falha, obra de CD alta falha muito — quem e bom faz a mao.").defineInRange("bonusMaquina", 3, -10, 30);
        TECH_SKILL = b.comment("Pericia da ficha somada (o valor treinado, 0-5) nas maquinas de tecnologia: Create e Oritech.")
                .define("periciaTecnologia", "tecnologia", value -> value instanceof String text && text.matches("[a-z_]{2,32}"));
        b.pop();
        b.comment("Testes de pericia fora da fabricacao (mesma regra: d20 + bonus da pericia na ficha contra a CD).").push("pericias");
        HEAL_DC = b.comment("Medico curando outra pessoa (Shift+G): teste de Medicina. Critico cura e da Regeneracao;",
                "falha cura so metade do que falta e nao trata os ferimentos; falha critica, um quarto.")
                .defineInRange("curaCd", 10, 0, 40);
        TREAT_DC = b.comment("Medico tratando um membro pela tela de atendimento: teste de Medicina. Na falha o medkit",
                "ou tonico se gasta e o membro nao sara.").defineInRange("tratarMembroCd", 12, 0, 40);
        COOK_DC = b.comment("Cozinheiro: a qualidade do prato (Quality Food) varia com a Criacao. Critico: chefQualityLevel + 2;",
                "falha: - 1; falha critica: - 2 (minimo 1).").defineInRange("cozinhaCd", 10, 0, 40);
        HAGGLE_PER_POINT = b.comment("NPCs: % de desconto no preco em dinheiro por ponto de bonus de Diplomacia (ou de Intimidacao,",
                "se render mais). Itens pedidos em troca nao mudam.").defineInRange("descontoPorPonto", 2, 0, 10);
        HAGGLE_CAP = b.comment("Teto do desconto, em %.").defineInRange("descontoMaximo", 20, 0, 90);
        INTIMIDATION_DC = b.comment("Desconto na ameaca: cada compra testa Intimidacao contra esta CD; so a falha critica",
                "ofende o NPC.").defineInRange("intimidacaoCd", 12, 0, 40);
        OFFENDED_SECONDS = b.comment("Quanto tempo o NPC ofendido recusa atender essa pessoa.").defineInRange("segundosOfendido", 120, 0, 3600);
        ENCHANT_SKILL = b.comment("Pericia do teste da mesa de encantamentos.")
                .define("encantamentoPericia", "astros_e_mistos", value -> value instanceof String text && text.matches("[a-z_]{2,32}"));
        ENCHANT_DC = b.comment("CD da primeira opcao da mesa; +3 na segunda e +6 na terceira. O arcanista rola com vantagem.",
                "Critico: a lapis volta. Falha critica: lapis e niveis se gastam e o item sai sem encanto.")
                .defineInRange("encantamentoCd", 8, 0, 40);
        LOCKPICK_SKILL = b.comment("Locks Reforged: pericia do teste a cada pino errado ao arrombar.")
                .define("arrombarPericia", "furtividade", value -> value instanceof String text && text.matches("[a-z_]{2,32}"));
        LOCKPICK_DC = b.comment("CD do arrombamento. Sucesso: a gazua gasta metade e o progresso nao zera; critico: nao",
                "gasta; falha critica: gasta o dobro.").defineInRange("arrombarCd", 12, 0, 40);
        FISHING_SKILL = b.comment("Pesca (vanilla, Aquaculture, Starcatcher): pericia do teste a cada peixe.")
                .define("pescaPericia", "natureza", value -> value instanceof String text && text.matches("[a-z_]{2,32}"));
        FISHING_DC = b.comment("CD da pesca. Critico: um peixe a mais; falha critica: o peixe escapa.")
                .defineInRange("pescaCd", 10, 0, 40);
        b.pop();
        SPEC = b.build();
    }
    public static List<? extends String> exclusiveRecipes() { return SPEC.isLoaded() ? EXCLUSIVE_RECIPES.get() : DEFAULT_EXCLUSIVE_RECIPES; }
    public static boolean enabled() { return !SPEC.isLoaded() || ENABLED.get(); }
    /** Nao passa por {@link #enabled()}: desligar profissoes nao pode devolver o bug do congelador. */
    public static boolean fixFoodSpoilThaw() { return !SPEC.isLoaded() || FIX_FOODSPOIL_THAW.get(); }
    public static double severeThreshold() { return SPEC.isLoaded() ? SEVERE_THRESHOLD.get() : .35; }
    public static double firstAidCeiling() { return SPEC.isLoaded() ? FIRST_AID_CEILING.get() : .5; }
    public static boolean craftEnabled() { return SPEC.isLoaded() && enabled() && CRAFT_ENABLED.get(); }
    private ProfessionsConfig() {}
}
