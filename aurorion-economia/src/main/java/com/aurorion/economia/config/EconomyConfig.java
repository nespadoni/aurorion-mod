package com.aurorion.economia.config;

import com.aurorion.economia.money.Money;
import net.neoforged.neoforge.common.ModConfigSpec;

/** Server-side prices, read at quotation and confirmation, never cached in a GUI. */
public final class EconomyConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.LongValue[] UPGRADE_PRICES = new ModConfigSpec.LongValue[5];
    public static final ModConfigSpec.LongValue[] ZONE_RATES = new ModConfigSpec.LongValue[5];
    public static final String[] UPGRADE_IDS = {"cofre1", "cofre2", "cofre3", "protetor1", "protetor2"};
    public static final String[] ZONE_NAMES = {"Periferia de Greymor", "Residencial", "Academia", "Centro comercial", "Premium"};
    public static final ModConfigSpec.LongValue MIN_PLOT_PRICE;
    public static final ModConfigSpec.IntValue MAX_PLOT_SIDE;

    static {
        var builder = new ModConfigSpec.Builder();
        builder.push("houseUpgrades");
        for (int i = 0; i < UPGRADE_PRICES.length; i++) {
            UPGRADE_PRICES[i] = builder.comment("Price in Fragments; -1 disables purchase. 10 Fragments = 1 Obolo.")
                    .defineInRange(UPGRADE_IDS[i], -1L, -1L, Money.MAX);
        }
        builder.pop().push("land");
        for (int i = 0; i < ZONE_RATES.length; i++) {
            ZONE_RATES[i] = builder.comment(ZONE_NAMES[i] + ": Fragments per square block.")
                    .defineInRange("zone" + i, (long) i + 1, 1L, Money.MAX / 1_048_576L);
        }
        MIN_PLOT_PRICE = builder.defineInRange("minimumPriceFragments", 1L, 1L, Money.MAX);
        MAX_PLOT_SIDE = builder.defineInRange("maximumSideBlocks", 256, 1, 1024);
        builder.pop();
        SPEC = builder.build();
    }

    private EconomyConfig() { }

    public static long price(boolean protector, int nextLevel) {
        if (nextLevel < 1 || nextLevel > (protector ? 2 : 3)) return -1L;
        return UPGRADE_PRICES[(protector ? 3 : 0) + nextLevel - 1].get();
    }
}
