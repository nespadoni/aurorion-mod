package com.aurorion.magia.registry;

import com.aurorion.magia.AurorionMagia;
import com.aurorion.magia.effect.BoundEffect;
import com.aurorion.magia.effect.CruciatusEffect;
import com.aurorion.magia.effect.DisorientedEffect;
import com.aurorion.magia.effect.DominatedEffect;
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

    /** Furtum Impetus, no alvo: o impulso foi roubado e o corpo fica parado um instante. */
    public static final DeferredHolder<MobEffect, MobEffect> STASIS = EFFECTS.register("estase",
            () -> new MagiaEffect(MobEffectCategory.HARMFUL, 0xA8E6FF)
                    .addAttributeModifier(Attributes.MOVEMENT_SPEED, AurorionMagia.id("estase_speed"),
                            -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)
                    .addAttributeModifier(Attributes.JUMP_STRENGTH, AurorionMagia.id("estase_jump"),
                            -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));

    /** Furtum Impetus, no conjurador: carrega um impulso para devolver. So icone. */
    public static final DeferredHolder<MobEffect, MobEffect> MOMENTUM = EFFECTS.register("impeto",
            () -> new MagiaEffect(MobEffectCategory.BENEFICIAL, 0xDDF6FF));

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

    private MagiaEffects() {
    }
}
