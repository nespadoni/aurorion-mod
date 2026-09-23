package com.aurorion.utils.freeze;

import com.aurorion.utils.AurorionUtils;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class FreezeEffects {
    public static final DeferredRegister<MobEffect> EFFECTS =
            DeferredRegister.create(Registries.MOB_EFFECT, AurorionUtils.MOD_ID);

    public static final DeferredHolder<MobEffect, FrozenEffect> FROZEN = EFFECTS.register("congelado", FrozenEffect::new);

    private FreezeEffects() {
    }
}
