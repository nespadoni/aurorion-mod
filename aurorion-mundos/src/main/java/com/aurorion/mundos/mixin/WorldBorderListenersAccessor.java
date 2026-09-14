package com.aurorion.mundos.mixin;

import net.minecraft.world.level.border.BorderChangeListener;
import net.minecraft.world.level.border.WorldBorder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

/**
 * Le a lista de ouvintes de uma {@link WorldBorder}.
 *
 * <p>O vanilla tem {@code removeListener}, mas nao tem como <b>descobrir</b> qual instancia remover:
 * o ouvinte que amarra as dimensoes a barreira do overworld e criado inline em
 * {@code MinecraftServer#createLevels}, e ninguem guarda a referencia. Ver
 * {@code BorderUnbinder} para o que e feito com a lista.
 */
@Mixin(WorldBorder.class)
public interface WorldBorderListenersAccessor {

    @Accessor("listeners")
    List<BorderChangeListener> aurorion_mundos$listeners();
}
