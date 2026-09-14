package com.aurorion.mundos.mixin;

import net.minecraft.world.level.border.BorderChangeListener;
import net.minecraft.world.level.border.WorldBorder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Le para qual barreira um {@code DelegateBorderChangeListener} repassa as mudancas.
 *
 * <p>E o que permite remover <b>so</b> os delegados dos nossos mundos, deixando intactos os do
 * Nether, do End e de qualquer dimensao de mod — que devem continuar seguindo o overworld como
 * sempre seguiram.
 */
@Mixin(BorderChangeListener.DelegateBorderChangeListener.class)
public interface DelegateBorderTargetAccessor {

    @Accessor("worldBorder")
    WorldBorder aurorion_mundos$target();
}
