package com.aurorion.magia.registry;

import com.aurorion.magia.AurorionMagia;
import com.aurorion.magia.effect.BoundEffect;
import com.aurorion.magia.effect.CagedEffect;
import com.aurorion.magia.effect.CaptiveEffect;
import com.aurorion.magia.effect.CruciatusEffect;
import com.aurorion.magia.effect.DisorientedEffect;
import com.aurorion.magia.effect.DominatedEffect;
import com.aurorion.magia.effect.DreadAuraEffect;
import com.aurorion.magia.effect.DrowningEffect;
import com.aurorion.magia.effect.GenuflectedEffect;
import com.aurorion.magia.effect.KneelingEffect;
import com.aurorion.magia.effect.MagiaEffect;
import com.aurorion.magia.effect.ThrownEffect;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Os estados que as magias deixam no alvo.
 *
 * <p>Efeito de status, e nao flag propria, porque o vanilla ja resolve tres coisas de graca: salva no
 * jogador e no mob, expira sozinho sem ninguem varrer lista, e sincroniza com o cliente do proprio
 * afetado. O cliente desenha tremor, controles invertidos e escurecimento lendo o efeito — sem
 * pacote nosso.
 */
public final class MagiaEffects {
    public static final DeferredRegister<MobEffect> EFFECTS =
            DeferredRegister.create(Registries.MOB_EFFECT, AurorionMagia.MOD_ID);

    /** Dor que paralisa: velocidade e pulo a zero. O dano vem da canalizacao, nao do efeito. */
    public static final DeferredHolder<MobEffect, CruciatusEffect> CRUCIATUS =
            EFFECTS.register("cruciatus", CruciatusEffect::new);

    /** Mob sob Imperium Mentis: luta por quem o dominou. */
    public static final DeferredHolder<MobEffect, DominatedEffect> DOMINATED =
            EFFECTS.register("dominado", DominatedEffect::new);

    /** Jogador sob Imperium Mentis: controles invertidos, tela escura, sem magia e sem item. */
    public static final DeferredHolder<MobEffect, DisorientedEffect> DISORIENTED =
            EFFECTS.register("desorientado", DisorientedEffect::new);

    /** Vinculum Carnificis: preso a uma ancora. */
    public static final DeferredHolder<MobEffect, BoundEffect> BOUND =
            EFFECTS.register("vinculado", BoundEffect::new);

    /** Mao do Algoz: voando depois de solto. Bate na parede, machuca. */
    public static final DeferredHolder<MobEffect, ThrownEffect> THROWN =
            EFFECTS.register("arremessado", ThrownEffect::new);

    /** Genua Flecte: de joelhos. */
    public static final DeferredHolder<MobEffect, KneelingEffect> KNEELING =
            EFFECTS.register("ajoelhado", KneelingEffect::new);

    /** Vox Interdicta: sem voz, sem chat, sem magia. */
    public static final DeferredHolder<MobEffect, MobEffect> SILENCED = EFFECTS.register("silenciado",
            () -> new MagiaEffect(MobEffectCategory.HARMFUL, 0x1B1024));

    /** Deiectio Corporis em quem ja estava no chao: nao consegue pular. */
    public static final DeferredHolder<MobEffect, MobEffect> GROUNDED = EFFECTS.register("abatido",
            () -> new MagiaEffect(MobEffectCategory.HARMFUL, 0x6F5B8C)
                    .addAttributeModifier(Attributes.JUMP_STRENGTH, AurorionMagia.id("abatido_jump"),
                            -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));

    /** Ferrum Ligatum: armadura e mao secundaria presas no corpo. */
    public static final DeferredHolder<MobEffect, MobEffect> IRON_BOUND = EFFECTS.register("ferro_vinculado",
            () -> new MagiaEffect(MobEffectCategory.HARMFUL, 0x8A8F99));

    /** Aspectus Captus: o olhar preso em quem conjurou, andando devagar. */
    public static final DeferredHolder<MobEffect, CaptiveEffect> CAPTIVE =
            EFFECTS.register("cativo", CaptiveEffect::new);

    /**
     * Mundus Vacuus: o mundo esvazia. So o cliente do afetado reage — ele deixa de desenhar todo
     * vivo em volta. No servidor nada muda: quem esta ali continua ali.
     */
    public static final DeferredHolder<MobEffect, MobEffect> SOLITARY = EFFECTS.register("solitario",
            () -> new MagiaEffect(MobEffectCategory.HARMFUL, 0x0B0714));

    // --- Agua ---------------------------------------------------------------------------------

    /** Submersio: o pulmao afoga em terra firme, gastando o ar do vanilla. */
    public static final DeferredHolder<MobEffect, DrowningEffect> DROWNING =
            EFFECTS.register("afogando", DrowningEffect::new);

    /** Carcer Aquae: preso boiando dentro da esfera, ate alguem estoura-la. */
    public static final DeferredHolder<MobEffect, CagedEffect> CAGED =
            EFFECTS.register("encarcerado", CagedEffect::new);

    // --- Presenca Aterradora (passiva) ---------------------------------------------------------

    /**
     * Quem carrega a aura, enquanto ela estiver ligada. Infinito e invisivel: e o relogio da passiva,
     * e nao um estado que o jogador ganhou.
     */
    public static final DeferredHolder<MobEffect, DreadAuraEffect> DREAD_AURA =
            EFFECTS.register("presenca_terrivel", DreadAuraEffect::new);

    /**
     * Quem esta dentro do raio da aura. Nao muda atributo nenhum de proposito: o que ele faz e na
     * tela e no ouvido de quem o tem ({@code PossessionLayer}, {@code MagiaSoundscape}).
     */
    public static final DeferredHolder<MobEffect, MobEffect> TERRIFIED = EFFECTS.register("apavorado",
            () -> new MagiaEffect(MobEffectCategory.HARMFUL, 0x4A142E));

    /** Um joelho no chao diante da aura. Nao ata as maos, ao contrario da Prostracao. */
    public static final DeferredHolder<MobEffect, GenuflectedEffect> GENUFLECTED =
            EFFECTS.register("genuflexo", GenuflectedEffect::new);

    private MagiaEffects() {
    }
}
