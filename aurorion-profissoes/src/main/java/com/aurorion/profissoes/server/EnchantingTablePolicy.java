package com.aurorion.profissoes.server;

import com.aurorion.profissoes.data.Profession;

public final class EnchantingTablePolicy {
    private EnchantingTablePolicy() {}

    public static boolean canUseOption(Profession profession, int option) {
        return option >= 0 && option < 3 && (option < 2 || profession == Profession.ARCANIST);
    }
}
