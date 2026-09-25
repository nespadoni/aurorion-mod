package com.aurorion.magia.registry;

import com.aurorion.magia.AurorionMagia;
import com.aurorion.magia.spell.AspectusCaptusSpell;
import com.aurorion.magia.spell.CarcerAquaeSpell;
import com.aurorion.magia.spell.ColumnaVentiSpell;
import com.aurorion.magia.spell.DeiectioCorporisSpell;
import com.aurorion.magia.spell.DolorCruciatusSpell;
import com.aurorion.magia.spell.DolorUniversusSpell;
import com.aurorion.magia.spell.FerrumLigatumSpell;
import com.aurorion.magia.spell.GenuaFlecteSpell;
import com.aurorion.magia.spell.ImperiumMentisSpell;
import com.aurorion.magia.spell.LuxVorataSpell;
import com.aurorion.magia.spell.MundusVacuusSpell;
import com.aurorion.magia.spell.ManusCarnificisSpell;
import com.aurorion.magia.spell.ManusVacuaSpell;
import com.aurorion.magia.spell.MortemDicoSpell;
import com.aurorion.magia.spell.SigillumClausumSpell;
import com.aurorion.magia.spell.SubmersioSpell;
import com.aurorion.magia.spell.TempusSistereSpell;
import com.aurorion.magia.spell.TranspositioSpell;
import com.aurorion.magia.spell.TurboVentorumSpell;
import com.aurorion.magia.spell.UndaMagnaSpell;
import com.aurorion.magia.spell.VentusCustosSpell;
import com.aurorion.magia.spell.VinculumCarnificisSpell;
import com.aurorion.magia.spell.VoxInterdictaSpell;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * As magias autorais, no registro de magias do Iron's.
 *
 * <p>O id e a invocacao ({@code aurorion_magia:vinculum_carnificis}); o nome pelo qual a magia e
 * conhecida ("Vinculo do Carrasco") vem da traducao. E esse id que vai em {@code /aurorion spells},
 * no config do Iron's e nas chaves {@code spell.aurorion_magia.<id>}. Uma magia nova entra aqui com
 * uma linha, mais o icone em {@code textures/gui/spell_icons/<id>.png}.
 */
public final class MagiaSpells {
    public static final DeferredRegister<AbstractSpell> SPELLS =
            DeferredRegister.create(SpellRegistry.SPELL_REGISTRY_KEY, AurorionMagia.MOD_ID);

    public static final DeferredHolder<AbstractSpell, DolorCruciatusSpell> DOLOR_CRUCIATUS =
            SPELLS.register("dolor_cruciatus", DolorCruciatusSpell::new);
    public static final DeferredHolder<AbstractSpell, ImperiumMentisSpell> IMPERIUM_MENTIS =
            SPELLS.register("imperium_mentis", ImperiumMentisSpell::new);

    // --- Captura e controle -------------------------------------------------------------------
    public static final DeferredHolder<AbstractSpell, VinculumCarnificisSpell> VINCULUM_CARNIFICIS =
            SPELLS.register("vinculum_carnificis", VinculumCarnificisSpell::new);
    public static final DeferredHolder<AbstractSpell, GenuaFlecteSpell> GENUA_FLECTE =
            SPELLS.register("genua_flecte", GenuaFlecteSpell::new);
    public static final DeferredHolder<AbstractSpell, AspectusCaptusSpell> ASPECTUS_CAPTUS =
            SPELLS.register("aspectus_captus", AspectusCaptusSpell::new);
    public static final DeferredHolder<AbstractSpell, VoxInterdictaSpell> VOX_INTERDICTA =
            SPELLS.register("vox_interdicta", VoxInterdictaSpell::new);
    public static final DeferredHolder<AbstractSpell, FerrumLigatumSpell> FERRUM_LIGATUM =
            SPELLS.register("ferrum_ligatum", FerrumLigatumSpell::new);
    public static final DeferredHolder<AbstractSpell, MundusVacuusSpell> MUNDUS_VACUUS =
            SPELLS.register("mundus_vacuus", MundusVacuusSpell::new);

    // --- Movimento e forca --------------------------------------------------------------------
    public static final DeferredHolder<AbstractSpell, TranspositioSpell> TRANSPOSITIO =
            SPELLS.register("transpositio", TranspositioSpell::new);
    public static final DeferredHolder<AbstractSpell, ManusCarnificisSpell> MANUS_CARNIFICIS =
            SPELLS.register("manus_carnificis", ManusCarnificisSpell::new);
    public static final DeferredHolder<AbstractSpell, ManusVacuaSpell> MANUS_VACUA =
            SPELLS.register("manus_vacua", ManusVacuaSpell::new);
    public static final DeferredHolder<AbstractSpell, DeiectioCorporisSpell> DEIECTIO_CORPORIS =
            SPELLS.register("deiectio_corporis", DeiectioCorporisSpell::new);

    // --- Mundo --------------------------------------------------------------------------------
    public static final DeferredHolder<AbstractSpell, SigillumClausumSpell> SIGILLUM_CLAUSUM =
            SPELLS.register("sigillum_clausum", SigillumClausumSpell::new);
    public static final DeferredHolder<AbstractSpell, LuxVorataSpell> LUX_VORATA =
            SPELLS.register("lux_vorata", LuxVorataSpell::new);

    // --- Agua: afogar, empurrar, prender -------------------------------------------------------
    public static final DeferredHolder<AbstractSpell, SubmersioSpell> SUBMERSIO =
            SPELLS.register("submersio", SubmersioSpell::new);
    public static final DeferredHolder<AbstractSpell, UndaMagnaSpell> UNDA_MAGNA =
            SPELLS.register("unda_magna", UndaMagnaSpell::new);
    public static final DeferredHolder<AbstractSpell, CarcerAquaeSpell> CARCER_AQUAE =
            SPELLS.register("carcer_aquae", CarcerAquaeSpell::new);

    // --- Vento: defender, subir, arrastar ------------------------------------------------------
    public static final DeferredHolder<AbstractSpell, VentusCustosSpell> VENTUS_CUSTOS =
            SPELLS.register("ventus_custos", VentusCustosSpell::new);
    public static final DeferredHolder<AbstractSpell, ColumnaVentiSpell> COLUMNA_VENTI =
            SPELLS.register("columna_venti", ColumnaVentiSpell::new);
    public static final DeferredHolder<AbstractSpell, TurboVentorumSpell> TURBO_VENTORUM =
            SPELLS.register("turbo_ventorum", TurboVentorumSpell::new);

    // --- Proibidas: sem craft, sem loot, sem vir pela escola. So a staff concede, uma a uma. -----
    public static final DeferredHolder<AbstractSpell, TempusSistereSpell> TEMPUS_SISTERE =
            SPELLS.register("tempus_sistere", TempusSistereSpell::new);
    public static final DeferredHolder<AbstractSpell, MortemDicoSpell> MORTEM_DICO =
            SPELLS.register("mortem_dico", MortemDicoSpell::new);
    public static final DeferredHolder<AbstractSpell, DolorUniversusSpell> DOLOR_UNIVERSUS =
            SPELLS.register("dolor_universus", DolorUniversusSpell::new);

    private MagiaSpells() {
    }
}
