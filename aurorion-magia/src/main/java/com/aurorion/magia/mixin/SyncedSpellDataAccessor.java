package com.aurorion.magia.mixin;

import io.redspace.ironsspellbooks.api.magic.LearnedSpellData;
import io.redspace.ironsspellbooks.capabilities.magic.SyncedSpellData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * O {@code SyncPlayerDataPacket} guarda o {@link SyncedSpellData} vivo e so o serializa depois, na
 * thread do Netty. Mexer no set de aprendidas nesse meio-tempo derruba com
 * {@code ConcurrentModificationException} todo mundo que rastreia o jogador. Trocar o objeto inteiro
 * (copy-on-write) e o jeito seguro: o pacote pendente le o antigo ou o novo, nunca um pela metade.
 */
@Mixin(value = SyncedSpellData.class, remap = false)
public interface SyncedSpellDataAccessor {
    @Accessor("learnedSpellData")
    LearnedSpellData aurorion_magia$learned();

    @Accessor("learnedSpellData")
    void aurorion_magia$setLearned(LearnedSpellData data);
}
