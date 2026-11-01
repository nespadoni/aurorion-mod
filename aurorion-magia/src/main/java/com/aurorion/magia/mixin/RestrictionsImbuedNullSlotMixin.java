package com.aurorion.magia.mixin;

import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.SpellSlot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Irons Restrictions 5.2.0, com {@code ImbuedItemsRequireLearning} desligado: a cada conjuracao ele varre
 * os slots de magia da mao, armadura e curios por {@code getAllSpells()} — que devolve {@code null} no
 * slot vazio — e chama {@code getSpell()} sem checar. Um item imbuido com slot sobrando dava NPE no
 * {@code canBeCastedBy} e a magia simplesmente nao saia. Slot vazio passa a ser "nao e esta magia".
 *
 * <p>O alvo e um metodo que o proprio Restrictions injeta no {@code AbstractSpell}; por isso a prioridade
 * acima da dele (1000) e {@code require = 0}: sem o Restrictions instalado o metodo nao existe.
 */
@Mixin(value = AbstractSpell.class, priority = 1500, remap = false)
public abstract class RestrictionsImbuedNullSlotMixin {
    @Redirect(method = "irons_Restrictions$isSpellImbued", require = 0,
            at = @At(value = "INVOKE",
                    target = "Lio/redspace/ironsspellbooks/api/spells/SpellSlot;getSpell()Lio/redspace/ironsspellbooks/api/spells/AbstractSpell;"))
    private AbstractSpell aurorion_magia$emptySlot(SpellSlot slot) {
        return slot == null ? null : slot.getSpell();
    }
}
