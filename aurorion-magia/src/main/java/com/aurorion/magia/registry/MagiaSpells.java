package com.aurorion.magia.registry;

import com.aurorion.magia.AurorionMagia;
import com.aurorion.magia.spell.DolorCruciatusSpell;
import com.aurorion.magia.spell.ImperiumMentisSpell;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * As magias autorais, no registro de magias do Iron's.
 *
 * <p>Os ids sao {@code aurorion_magia:<nome>}: e esse o id usado em {@code /aurorion spells}, no
 * config do Iron's e nas traducoes {@code spell.aurorion_magia.<nome>}. Uma magia nova entra aqui com
 * uma linha, mais o icone em {@code textures/gui/spell_icons/<nome>.png}.
 */
public final class MagiaSpells {
    public static final DeferredRegister<AbstractSpell> SPELLS =
            DeferredRegister.create(SpellRegistry.SPELL_REGISTRY_KEY, AurorionMagia.MOD_ID);

    public static final DeferredHolder<AbstractSpell, DolorCruciatusSpell> DOLOR_CRUCIATUS =
            SPELLS.register("dolor_cruciatus", DolorCruciatusSpell::new);

    public static final DeferredHolder<AbstractSpell, ImperiumMentisSpell> IMPERIUM_MENTIS =
            SPELLS.register("imperium_mentis", ImperiumMentisSpell::new);

    private MagiaSpells() {
    }
}
