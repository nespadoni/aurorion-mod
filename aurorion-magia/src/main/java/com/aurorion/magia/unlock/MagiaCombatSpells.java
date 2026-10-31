package com.aurorion.magia.unlock;

import com.aurorion.core.character.CharacterSpells;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import io.redspace.ironsspellbooks.api.spells.SpellSlot;
import io.redspace.ironsspellbooks.api.util.Utils;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * As magias do livro equipado que a pessoa pode lançar agora (Despertar Arcano, liberações), com a CD e o
 * bônus do mesmo teste da conjuração — para o combate com ficha ({@link CharacterSpells}). Só leitura; o
 * combate não conjura no mundo, ele usa os números.
 */
public final class MagiaCombatSpells {
    private static final int MAX = 8;

    private MagiaCombatSpells() {
    }

    public static List<CharacterSpells.Option> of(ServerPlayer player) {
        ItemStack book = Utils.getPlayerSpellbookStack(player);
        if (book == null || book.isEmpty() || !ISpellContainer.isSpellContainer(book)) return List.of();
        List<CharacterSpells.Option> out = new ArrayList<>(MAX);
        for (SpellSlot slot : ISpellContainer.get(book).getActiveSpells()) {
            if (out.size() >= MAX) break;
            AbstractSpell spell = slot.getSpell();
            int level = slot.getLevel();
            if (spell == null || spell == SpellRegistry.none() || !spell.isEnabled() || !SpellAccess.canCast(player, spell, level)) continue;
            boolean specialist = ArcaneProfile.isSpecialty(player, spell.getSchoolType());
            out.add(new CharacterSpells.Option(spell.getSpellId(), spell.getComponentId(), spell.getRarity(level).ordinal(), level,
                    SpellCheck.difficulty(spell, level), SpellCheck.bonus(player, specialist), specialist));
        }
        return out;
    }
}
