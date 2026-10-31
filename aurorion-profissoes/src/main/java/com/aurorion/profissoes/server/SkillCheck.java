package com.aurorion.profissoes.server;

import com.aurorion.core.character.CharacterSheet;
import com.aurorion.core.character.SheetCheck;
import com.aurorion.profissoes.config.ProfessionsConfig;
import net.minecraft.server.level.ServerPlayer;

/**
 * Teste de uma perícia da ficha fora da fabricação: cura do médico (Medicina), preço do NPC (Diplomacia,
 * Intimidação), mesa de encantamentos (Astros &amp; Mistos). A mesma regra do {@link SheetCheck}; o bônus é o
 * bônus de teste da perícia, como o site rola, ou o padrão de quem não tem ficha lida.
 */
public final class SkillCheck {
    private SkillCheck() {
    }

    public static SheetCheck.Outcome roll(ServerPlayer player, String skill, int dc) {
        return roll(player, skill, dc, false);
    }

    public static SheetCheck.Outcome roll(ServerPlayer player, String skill, int dc, boolean advantage) {
        return SheetCheck.roll(player.getRandom(), bonus(player, skill), dc, advantage, ProfessionsConfig.CRAFT_NATURAL_MIN_DC.get());
    }

    public static int bonus(ServerPlayer player, String skill) {
        CharacterSheet.Stats stats = CharacterSheet.of(player.server, player.getUUID());
        return stats.known() ? stats.skillBonus(skill, 0) : ProfessionsConfig.CRAFT_DEFAULT_BONUS.get();
    }

    public static double failChance(ServerPlayer player, String skill, int dc) {
        return SheetCheck.failChance(bonus(player, skill), dc, false, ProfessionsConfig.CRAFT_NATURAL_MIN_DC.get());
    }
}
