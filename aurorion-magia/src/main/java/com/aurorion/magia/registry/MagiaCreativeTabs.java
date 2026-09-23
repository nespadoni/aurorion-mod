package com.aurorion.magia.registry;

import com.aurorion.magia.AurorionMagia;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import io.redspace.ironsspellbooks.registries.ItemRegistry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Aba do criativo "Aurorion — Magias": o pergaminho de cada magia do Aurorion, em todos os niveis,
 * na ordem de {@link MagiaSpells} (as proibidas por ultimo).
 *
 * <p>O Iron's ja poe toda magia registrada na aba de pergaminhos dele, mas misturadas com as mais de
 * 150 do pack. Aqui ficam so as nossas, para a staff achar o que vai entregar em aula. O pergaminho e
 * montado exatamente como o Iron's monta os dele ({@code ISpellContainer.createScrollContainer}).
 *
 * <p>Ter o pergaminho nao libera nada: conjurar continua exigindo a liberacao em
 * {@code /aurorion spells} (a staff com permissao 2 passa pelo {@code staffBypass}).
 */
public final class MagiaCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, AurorionMagia.MOD_ID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> SPELLS = TABS.register("magias",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.aurorion_magia.magias"))
                    .icon(() -> scroll(MagiaSpells.DOLOR_CRUCIATUS.get(), 1))
                    .displayItems((parameters, output) -> {
                        for (DeferredHolder<AbstractSpell, ? extends AbstractSpell> holder : MagiaSpells.SPELLS.getEntries()) {
                            AbstractSpell spell = holder.get();
                            // Magia desligada no config do Iron's nao aparece, como na aba dele.
                            if (!spell.isEnabled()) continue;
                            for (int level = spell.getMinLevel(); level <= spell.getMaxLevel(); level++) {
                                output.accept(scroll(spell, level));
                            }
                        }
                    })
                    .build());

    private MagiaCreativeTabs() {
    }

    private static ItemStack scroll(AbstractSpell spell, int level) {
        ItemStack stack = new ItemStack(ItemRegistry.SCROLL.get());
        ISpellContainer.createScrollContainer(spell, level, stack);
        return stack;
    }
}
