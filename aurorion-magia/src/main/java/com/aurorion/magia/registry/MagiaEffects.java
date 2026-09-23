package com.aurorion.magia.registry;

import com.aurorion.magia.AurorionMagia;
import com.aurorion.magia.effect.CruciatusEffect;
import com.aurorion.magia.effect.DisorientedEffect;
import com.aurorion.magia.effect.DominatedEffect;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
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

    private MagiaEffects() {
    }
}
