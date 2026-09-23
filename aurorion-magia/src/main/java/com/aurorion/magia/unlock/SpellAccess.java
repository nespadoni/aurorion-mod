package com.aurorion.magia.unlock;

import com.aurorion.magia.config.MagiaConfig;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.SchoolType;
import io.redspace.ironsspellbooks.api.util.Utils;
import io.redspace.ironsspellbooks.capabilities.magic.SyncedSpellData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Quem pode conjurar o que, e a ponte com o Iron's Restrictions.
 *
 * <h2>Como o Restrictions decide</h2>
 *
 * <p>Ele nao tem lista propria de permissao. Ele faz {@code AbstractSpell.requiresLearning()} devolver
 * {@code true} para toda magia, e a partir dai o {@code canBeCastedBy} do Iron's so deixa conjurar o
 * que estiver em {@code SyncedSpellData.learnedSpells} — o mesmo conjunto que o Iron's usa para as
 * magias eldritch. O livro de pesquisa, o scroll e o cliente leem dali.
 *
 * <p>Por isso a ponte e so escrever nesse conjunto: {@link #reconcile} faz o "aprendido" do Iron's
 * ficar igual a lista oficial do Aurorion. Nao ha import de classe do Restrictions — o que torna a
 * dependencia opcional de verdade.
 *
 * <h2>Por que ainda existe um gate nosso</h2>
 *
 * <p>O Restrictions deixa passar magia de item "imbuido" (livro ou arma com a magia gravada) quando
 * {@code ImbuedItemsRequireLearning} esta desligado, e um manuscrito dele ensina magia a qualquer
 * momento. O {@link #canCast} roda no {@code SpellPreCastEvent} e vale mesmo nesses casos — e mesmo
 * sem o Restrictions instalado.
 */
public final class SpellAccess {
    private static final int STAFF_LEVEL = 2;

    private SpellAccess() {
    }

    /** Custo: duas buscas em hash. Chamado a cada tentativa de conjuracao. */
    public static boolean canCast(ServerPlayer player, @Nullable AbstractSpell spell) {
        if (spell == null || spell == SpellRegistry.none()) return true;
        if (bypasses(player)) return true;
        if (grantsOf(player).allows(spell.getSpellResource(), schoolOf(spell))) return true;
        return !MagiaConfig.AUTHORITATIVE.get()
                && MagicData.getPlayerMagicData(player).getSyncedData().isSpellLearned(spell);
    }

    public static boolean bypasses(ServerPlayer player) {
        return MagiaConfig.STAFF_BYPASS.get() && player.hasPermissions(STAFF_LEVEL);
    }

    /**
     * Faz o "aprendido" do Iron's refletir a lista oficial. Roda no login, depois de cada comando e no
     * reset de personagem — nunca em tick.
     *
     * <p>O custo e uma passada pelo registro de magias (~150 entradas no pack) e no maximo dois
     * pacotes de sincronizacao, e so quando algo mudou: o caso comum, login sem mudanca, nao envia
     * nada.
     */
    public static void reconcile(ServerPlayer player) {
        MagicData magic = MagicData.getPlayerMagicData(player);
        SyncedSpellData synced = magic.getSyncedData();
        SpellGrants grants = grantsOf(player);
        boolean everything = bypasses(player);

        List<AbstractSpell> official = new ArrayList<>();
        boolean missing = false;
        boolean stray = false;

        for (AbstractSpell spell : SpellRegistry.REGISTRY) {
            if (spell == SpellRegistry.none()) continue;
            boolean allowed = everything || grants.allows(spell.getSpellResource(), schoolOf(spell));
            boolean learned = synced.isSpellLearned(spell);
            if (allowed) {
                official.add(spell);
                missing |= !learned;
            } else {
                stray |= learned;
            }
        }

        // O Iron's nao tem "esquecer uma": so esquecer todas. E o mesmo caminho que o comando
        // forgetSpell do Restrictions evita usando mixin; aqui o custo e aceitavel porque nao e tick.
        if (stray && MagiaConfig.AUTHORITATIVE.get()) {
            synced.forgetAllSpells();
            missing = !official.isEmpty();
        }
        if (missing) {
            for (AbstractSpell spell : official) synced.learnSpell(spell, false);
            synced.doSync();
        }

        // Liberacao revogada no meio de uma canalizacao nao espera o fim dela.
        if (magic.isCasting() && !canCast(player, SpellRegistry.getSpell(magic.getCastingSpellId()))) {
            Utils.serverSideCancelCast(player);
        }
    }

    private static SpellGrants grantsOf(ServerPlayer player) {
        return SpellUnlockData.get(player.server).grantsOf(player.getUUID());
    }

    @Nullable
    private static ResourceLocation schoolOf(AbstractSpell spell) {
        SchoolType school = spell.getSchoolType();
        return school == null ? null : school.getId();
    }
}
