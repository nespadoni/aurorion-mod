package com.aurorion.menu.mixin;

import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.client.gui.components.toasts.AdvancementToast;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** A conquista dentro do aviso do vanilla, para o cartão do menu mostrar no lugar dele. */
@Mixin(AdvancementToast.class)
public interface AdvancementToastAccessor {
    @Accessor("advancement")
    AdvancementHolder aurorion_menu$advancement();
}
