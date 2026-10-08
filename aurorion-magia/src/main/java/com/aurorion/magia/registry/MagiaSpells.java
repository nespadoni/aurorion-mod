package com.aurorion.magia.registry;

import com.aurorion.magia.AurorionMagia;
import com.aurorion.magia.spell.AestusSanguinisSpell;
import com.aurorion.magia.spell.AspectusCaptusSpell;
import com.aurorion.magia.spell.CampusStaticusSpell;
import com.aurorion.magia.spell.CeifarVidaSpell;
import com.aurorion.magia.spell.EntregarVidaSpell;
import com.aurorion.magia.spell.EsconjurarSpell;
import com.aurorion.magia.spell.CarcerAquaeSpell;
import com.aurorion.magia.spell.ColumnaVentiSpell;
import com.aurorion.magia.spell.DeiectioCorporisSpell;
import com.aurorion.magia.spell.DoliumArdensSpell;
import com.aurorion.magia.spell.DolorCruciatusSpell;
import com.aurorion.magia.spell.DolorUniversusSpell;
import com.aurorion.magia.spell.FerrumLigatumSpell;
import com.aurorion.magia.spell.FurorVenatorisSpell;
import com.aurorion.magia.spell.GenuaFlecteSpell;
import com.aurorion.magia.spell.ImperiumMentisSpell;
import com.aurorion.magia.spell.ImpetusVentiSpell;
import com.aurorion.magia.spell.IncusCaelestisSpell;
import com.aurorion.magia.spell.IustitiaDemaciaeSpell;
import com.aurorion.magia.spell.LacusSanguinisSpell;
import com.aurorion.magia.spell.LuxFinalisSpell;
import com.aurorion.magia.spell.LuxVorataSpell;
import com.aurorion.magia.spell.ManusCarnificisSpell;
import com.aurorion.magia.spell.ManusRapaxSpell;
import com.aurorion.magia.spell.ManusVacuaSpell;
import com.aurorion.magia.spell.MessisUberrimaSpell;
import com.aurorion.magia.spell.MorsExProfundisSpell;
import com.aurorion.magia.spell.MortemDicoSpell;
import com.aurorion.magia.spell.MundusVacuusSpell;
import com.aurorion.magia.spell.MutatioFeraeSpell;
import com.aurorion.magia.spell.PestisSanguineaSpell;
import com.aurorion.magia.spell.PossessioCorporisSpell;
import com.aurorion.magia.spell.ProcellaCorvorumSpell;
import com.aurorion.magia.spell.PyrobolusInfernalisSpell;
import com.aurorion.magia.spell.SagittaExploratrixSpell;
import com.aurorion.magia.spell.SagittaFulminisSpell;
import com.aurorion.magia.spell.SectioUmbraeSpell;
import com.aurorion.magia.spell.SigillumClausumSpell;
import com.aurorion.magia.spell.SignumMortisSpell;
import com.aurorion.magia.spell.SphaeraSpiritusSpell;
import com.aurorion.magia.spell.StellaeLaminataeSpell;
import com.aurorion.magia.spell.SubmersioSpell;
import com.aurorion.magia.spell.TemperiesFatiSpell;
import com.aurorion.magia.spell.TempusSistereSpell;
import com.aurorion.magia.spell.TransfusioSanguinisSpell;
import com.aurorion.magia.spell.TranspositioSpell;
import com.aurorion.magia.spell.TurboVentorumSpell;
import com.aurorion.magia.spell.UmbraVivaSpell;
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
    public static final DeferredHolder<AbstractSpell, CeifarVidaSpell> CEIFAR_VIDA =
            SPELLS.register("ceifar_vida", CeifarVidaSpell::new);
    public static final DeferredHolder<AbstractSpell, EntregarVidaSpell> ENTREGAR_VIDA =
            SPELLS.register("entregar_vida", EntregarVidaSpell::new);
    public static final DeferredHolder<AbstractSpell, EsconjurarSpell> ESCONJURAR =
            SPELLS.register("esconjurar", EsconjurarSpell::new);
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
    public static final DeferredHolder<AbstractSpell, ImpetusVentiSpell> IMPETUS_VENTI =
            SPELLS.register("impetus_venti", ImpetusVentiSpell::new);

    // --- Proibidas: sem craft, sem loot, sem vir pela escola. So a staff concede, uma a uma. -----
    public static final DeferredHolder<AbstractSpell, TempusSistereSpell> TEMPUS_SISTERE =
            SPELLS.register("tempus_sistere", TempusSistereSpell::new);
    public static final DeferredHolder<AbstractSpell, MortemDicoSpell> MORTEM_DICO =
            SPELLS.register("mortem_dico", MortemDicoSpell::new);
    public static final DeferredHolder<AbstractSpell, DolorUniversusSpell> DOLOR_UNIVERSUS =
            SPELLS.register("dolor_universus", DolorUniversusSpell::new);
    public static final DeferredHolder<AbstractSpell, SphaeraSpiritusSpell> SPHAERA_SPIRITUS =
            SPELLS.register("sphaera_spiritus", SphaeraSpiritusSpell::new);
    public static final DeferredHolder<AbstractSpell, PossessioCorporisSpell> POSSESSIO_CORPORIS =
            SPELLS.register("possessio_corporis", PossessioCorporisSpell::new);
    public static final DeferredHolder<AbstractSpell, IustitiaDemaciaeSpell> IUSTITIA_DEMACIAE =
            SPELLS.register("iustitia_demaciae", IustitiaDemaciaeSpell::new);
    public static final DeferredHolder<AbstractSpell, MorsExProfundisSpell> MORS_EX_PROFUNDIS =
            SPELLS.register("mors_ex_profundis", MorsExProfundisSpell::new);

    // --- Ultimates: luz, destino, eletricidade, fogo e ferro -------------------------------------
    public static final DeferredHolder<AbstractSpell, LuxFinalisSpell> LUX_FINALIS =
            SPELLS.register("lux_finalis", LuxFinalisSpell::new);
    public static final DeferredHolder<AbstractSpell, TemperiesFatiSpell> TEMPERIES_FATI =
            SPELLS.register("temperies_fati", TemperiesFatiSpell::new);
    public static final DeferredHolder<AbstractSpell, ManusRapaxSpell> MANUS_RAPAX =
            SPELLS.register("manus_rapax", ManusRapaxSpell::new);
    public static final DeferredHolder<AbstractSpell, CampusStaticusSpell> CAMPUS_STATICUS =
            SPELLS.register("campus_staticus", CampusStaticusSpell::new);
    public static final DeferredHolder<AbstractSpell, DoliumArdensSpell> DOLIUM_ARDENS =
            SPELLS.register("dolium_ardens", DoliumArdensSpell::new);
    public static final DeferredHolder<AbstractSpell, PyrobolusInfernalisSpell> PYROBOLUS_INFERNALIS =
            SPELLS.register("pyrobolus_infernalis", PyrobolusInfernalisSpell::new);
    public static final DeferredHolder<AbstractSpell, IncusCaelestisSpell> INCUS_CAELESTIS =
            SPELLS.register("incus_caelestis", IncusCaelestisSpell::new);
    public static final DeferredHolder<AbstractSpell, MutatioFeraeSpell> MUTATIO_FERAE =
            SPELLS.register("mutatio_ferae", MutatioFeraeSpell::new);

    // --- Corvos ----------------------------------------------------------------------------------
    public static final DeferredHolder<AbstractSpell, MessisUberrimaSpell> MESSIS_UBERRIMA =
            SPELLS.register("messis_uberrima", MessisUberrimaSpell::new);
    public static final DeferredHolder<AbstractSpell, ProcellaCorvorumSpell> PROCELLA_CORVORUM =
            SPELLS.register("procella_corvorum", ProcellaCorvorumSpell::new);

    // --- Sangue: o kit inteiro -------------------------------------------------------------------
    public static final DeferredHolder<AbstractSpell, TransfusioSanguinisSpell> TRANSFUSIO_SANGUINIS =
            SPELLS.register("transfusio_sanguinis", TransfusioSanguinisSpell::new);
    public static final DeferredHolder<AbstractSpell, LacusSanguinisSpell> LACUS_SANGUINIS =
            SPELLS.register("lacus_sanguinis", LacusSanguinisSpell::new);
    public static final DeferredHolder<AbstractSpell, AestusSanguinisSpell> AESTUS_SANGUINIS =
            SPELLS.register("aestus_sanguinis", AestusSanguinisSpell::new);
    public static final DeferredHolder<AbstractSpell, PestisSanguineaSpell> PESTIS_SANGUINEA =
            SPELLS.register("pestis_sanguinea", PestisSanguineaSpell::new);

    // --- Sombra: o kit inteiro -------------------------------------------------------------------
    public static final DeferredHolder<AbstractSpell, StellaeLaminataeSpell> STELLAE_LAMINATAE =
            SPELLS.register("stellae_laminatae", StellaeLaminataeSpell::new);
    public static final DeferredHolder<AbstractSpell, UmbraVivaSpell> UMBRA_VIVA =
            SPELLS.register("umbra_viva", UmbraVivaSpell::new);
    public static final DeferredHolder<AbstractSpell, SectioUmbraeSpell> SECTIO_UMBRAE =
            SPELLS.register("sectio_umbrae", SectioUmbraeSpell::new);
    public static final DeferredHolder<AbstractSpell, SignumMortisSpell> SIGNUM_MORTIS =
            SPELLS.register("signum_mortis", SignumMortisSpell::new);

    // --- Arco: so com arco ou besta na mao -------------------------------------------------------
    public static final DeferredHolder<AbstractSpell, SagittaExploratrixSpell> SAGITTA_EXPLORATRIX =
            SPELLS.register("sagitta_exploratrix", SagittaExploratrixSpell::new);
    public static final DeferredHolder<AbstractSpell, SagittaFulminisSpell> SAGITTA_FULMINIS =
            SPELLS.register("sagitta_fulminis", SagittaFulminisSpell::new);
    public static final DeferredHolder<AbstractSpell, FurorVenatorisSpell> FUROR_VENATORIS =
            SPELLS.register("furor_venatoris", FurorVenatorisSpell::new);

    private MagiaSpells() {
    }
}
