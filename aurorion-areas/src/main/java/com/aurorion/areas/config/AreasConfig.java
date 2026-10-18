package com.aurorion.areas.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class AreasConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue ENABLED, CREATIVE_BYPASS, HOUSE_BARRIER;
    public static final ModConfigSpec.IntValue ENTRY_ALERT_COOLDOWN;
    public static final ModConfigSpec.ConfigValue<java.util.List<? extends String>> FLIGHT_SPELLS;
    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        ENABLED = b.comment("Chave geral. Desligar suspende regras e ambientes, sem apagar areas.")
                .define("enabled", true);
        CREATIVE_BYPASS = b.comment("Criativo de staff (permissao 2) ignora restricoes e efeitos pessoais.",
                "NPCs e espectadores nao recebem restricoes de voo/magia. Excecoes de personagens sao por area.")
                .define("creativeStaffBypass", true);
        HOUSE_BARRIER = b.comment("Barreira das areas de casa: quem nao e da casa nao atravessa o limite.",
                "Criativo sempre passa, com ou sem OP; sobrevivencia nunca passa, com ou sem OP.")
                .define("houseBarrier", true);
        ENTRY_ALERT_COOLDOWN = b.comment("Areas com /area aviso: silencio minimo, em segundos, entre dois avisos de entrada",
                "do mesmo jogador na mesma area. Evita uma mensagem por passo de quem anda na borda.")
                .defineInRange("entryAlertCooldownSeconds", 60, 0, 86_400);
        FLIGHT_SPELLS = b.comment("Iron spells classified as flight; IDs include namespace.")
                .defineListAllowEmpty("flightSpells", java.util.List.of("irons_spellbooks:angel_wing"),
                        () -> "irons_spellbooks:angel_wing", value -> value instanceof String id
                                && net.minecraft.resources.ResourceLocation.tryParse(id) != null);
        SPEC = b.build();
    }
    private AreasConfig() {}
}
