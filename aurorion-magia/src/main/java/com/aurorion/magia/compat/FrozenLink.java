package com.aurorion.magia.compat;

import com.aurorion.magia.AurorionMagia;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

/**
 * O freeze de verdade mora no {@code aurorion-utils} (o do {@code /freeze}): efeito
 * {@code aurorion_utils:congelado} mais a montaria que prende no lugar, teclado zerado no cliente e
 * toda acao recusada no servidor. O Tempus Sistere usa exatamente esse freeze — um freeze so no
 * servidor, com as mesmas regras para comando e magia.
 *
 * <p>A ligacao e pelo <b>id do efeito no registro</b>, nao por import: aplicar o efeito basta, porque
 * o utils reage a ele venha de onde vier. Os dois mods continuam independentes.
 *
 * <p>Sem o utils no pack, o Tempus Sistere cai na Lentidao do vanilla no nivel em que a velocidade
 * chega a zero, e avisa no log uma vez. O fallback e de proposito um efeito <b>do vanilla</b>, e nao
 * um nosso: efeito com modificador de atributo que o servidor cai no meio de aplicar deixa o
 * modificador preso na entidade, e foi assim que a antiga "estase" travava gente para sempre. O que e
 * do vanilla o vanilla limpa.
 */
public final class FrozenLink {
    private static final ResourceKey<MobEffect> KEY =
            ResourceKey.create(Registries.MOB_EFFECT, ResourceLocation.fromNamespaceAndPath("aurorion_utils", "congelado"));
    /** Lentidao VII: -105% de velocidade, ou seja, zero. */
    private static final int SLOWNESS_LEVEL = 6;

    private static boolean resolved;
    @Nullable
    private static Holder<MobEffect> frozen;

    private FrozenLink() {
    }

    public static void freeze(LivingEntity target, int ticks, @Nullable Entity source) {
        Holder<MobEffect> effect = effect();
        if (effect != null) {
            target.addEffect(new MobEffectInstance(effect, ticks, 0, false, false, true), source);
        } else {
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks, SLOWNESS_LEVEL,
                    false, false, true), source);
        }
    }

    public static boolean isFrozen(LivingEntity entity) {
        Holder<MobEffect> effect = effect();
        return effect != null && entity.hasEffect(effect);
    }

    /** Registro fechado depois da carga: resolve uma vez e guarda. */
    @Nullable
    private static Holder<MobEffect> effect() {
        if (!resolved) {
            resolved = true;
            frozen = BuiltInRegistries.MOB_EFFECT.getHolder(KEY).orElse(null);
            if (frozen == null) {
                AurorionMagia.LOGGER.warn("Magia: aurorion_utils ausente; Tempus Sistere usa so Lentidao (sem trava de teclado).");
            }
        }
        return frozen;
    }
}
