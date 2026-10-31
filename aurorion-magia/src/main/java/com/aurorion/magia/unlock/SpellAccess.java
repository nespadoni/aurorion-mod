package com.aurorion.magia.unlock;

import com.aurorion.magia.config.MagiaConfig;
import com.aurorion.magia.mixin.SyncedSpellDataAccessor;
import com.aurorion.magia.spell.AurorionSpell;
import io.redspace.ironsspellbooks.api.magic.LearnedSpellData;
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

    /** No menor nivel da magia: o espelho do Iron's e a canalizacao que perdeu a liberacao. */
    public static boolean canCast(ServerPlayer player, @Nullable AbstractSpell spell) {
        return spell == null || canCast(player, spell, spell.getMinLevel());
    }

    /**
     * Custo: tres buscas em hash. Chamado a cada tentativa de conjuracao.
     *
     * <p>No modo {@code no} (padrao, com a Trama no pack) decide o Despertar Arcano que o professor
     * concedeu: o nivel exigido sai da raridade do nivel conjurado ({@link ArcaneProfile#requiredTier}).
     * A liberacao da staff por {@code /aurorion spells unlock} continua valendo como excecao, nos dois modos.
     */
    public static boolean canCast(ServerPlayer player, @Nullable AbstractSpell spell, int level) {
        if (spell == null || spell == SpellRegistry.none()) return true;
        if (bypasses(player)) return true;
        if (granted(grantsOf(player), spell)) return true;
        if (ArcaneProfile.nodeMode()) return ArcaneProfile.of(player).tier() >= ArcaneProfile.requiredTier(spell, level);
        // Aprendido por fora (manuscrito, eldritch) nunca vale para magia proibida.
        return !MagiaConfig.AUTHORITATIVE.get() && !isForbidden(spell)
                && MagicData.getPlayerMagicData(player).getSyncedData().isSpellLearned(spell);
    }

    /** O Despertar que falta para esta conjuracao, ou 0 quando o motivo nao e o no (modo aula). */
    public static int missingTier(ServerPlayer player, AbstractSpell spell, int level) {
        if (!ArcaneProfile.nodeMode()) return 0;
        int required = ArcaneProfile.requiredTier(spell, level);
        return ArcaneProfile.of(player).tier() >= required ? 0 : required;
    }

    public static String roman(int tier) {
        return switch (tier) {
            case 1 -> "I";
            case 2 -> "II";
            default -> "III";
        };
    }

    /**
     * Magia proibida: as do Aurorion marcadas como tal (Tempus Sistere, Mortem Dico, Dolor Universus)
     * e as que a staff listar em {@code forbiddenSpells}. So a liberacao da propria magia as concede —
     * nunca a da escola.
     */
    public static boolean isForbidden(AbstractSpell spell) {
        return spell instanceof AurorionSpell ours && ours.isForbidden()
                || MagiaConfig.FORBIDDEN_SPELLS.get().contains(spell.getSpellId());
    }

    private static boolean granted(SpellGrants grants, AbstractSpell spell) {
        return isForbidden(spell)
                ? grants.allowsForbidden(spell.getSpellResource())
                : grants.allows(spell.getSpellResource(), schoolOf(spell));
    }

    public static boolean bypasses(ServerPlayer player) {
        return MagiaConfig.STAFF_BYPASS.get() && player.hasPermissions(STAFF_LEVEL);
    }

    /**
     * Faz o "aprendido" do Iron's refletir a lista oficial. Roda no login, depois de cada comando e no
     * reset de personagem — nunca em tick.
     *
     * <p>O custo e uma passada pelo registro de magias (~150 entradas no pack) e no maximo um pacote
     * de sincronizacao, e so quando algo mudou: o caso comum, login sem mudanca, nao envia nada.
     */
    public static void reconcile(ServerPlayer player) {
        MagicData magic = MagicData.getPlayerMagicData(player);
        SyncedSpellData synced = magic.getSyncedData();
        SpellGrants grants = grantsOf(player);
        boolean everything = bypasses(player);
        int tier = ArcaneProfile.nodeMode() ? ArcaneProfile.of(player).tier() : -1;

        List<AbstractSpell> official = new ArrayList<>();
        boolean missing = false;
        boolean stray = false;

        for (AbstractSpell spell : SpellRegistry.REGISTRY) {
            if (spell == SpellRegistry.none()) continue;
            // No modo do Despertar, "aprendida" e a magia que o nivel alcanca ao menos no nivel minimo dela.
            boolean allowed = everything || granted(grants, spell)
                    || tier >= 0 && tier >= ArcaneProfile.requiredTier(spell, spell.getMinLevel());
            boolean learned = synced.isSpellLearned(spell);
            if (allowed) {
                official.add(spell);
                missing |= !learned;
            } else {
                stray |= learned;
            }
        }

        // Nunca forgetAllSpells/learnSpell aqui: o forgetAllSpells ja sincroniza, e o pacote dele ainda
        // esta na fila do Netty quando o learnSpell mexe no mesmo set — o CME derrubava quem estava
        // perto no login. Monta o conjunto novo a parte e troca de uma vez (ver SyncedSpellDataAccessor).
        boolean purge = stray && (MagiaConfig.AUTHORITATIVE.get() || tier >= 0);
        if (purge || missing) {
            SyncedSpellDataAccessor access = (SyncedSpellDataAccessor) synced;
            LearnedSpellData next = new LearnedSpellData();
            if (!purge) next.learnedSpells.addAll(access.aurorion_magia$learned().learnedSpells);
            for (AbstractSpell spell : official) next.learnedSpells.add(spell.getSpellResource());
            access.aurorion_magia$setLearned(next);
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
