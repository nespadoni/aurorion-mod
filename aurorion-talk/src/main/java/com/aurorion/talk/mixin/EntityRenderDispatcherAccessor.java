package com.aurorion.talk.mixin;

import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Le o {@code shouldRenderShadow} do dispatcher.
 *
 * <p>O vanilla so o desliga enquanto desenha uma entidade dentro de uma tela — o boneco do
 * inventario ({@code InventoryScreen#renderEntityInInventory}). E o sinal mais barato de "isto nao e
 * o mundo": o balao acima da cabeca nao tem o que fazer ali, e nem caberia no quadro.
 */
@Mixin(EntityRenderDispatcher.class)
public interface EntityRenderDispatcherAccessor {

    @Accessor("shouldRenderShadow")
    boolean aurorion_talk$shouldRenderShadow();
}
