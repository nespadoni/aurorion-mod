package com.aurorion.menu.mixin;

import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** O vanilla só desliga a sombra no boneco do inventário: é assim que o holograma sabe que não deve aparecer ali. */
@Mixin(EntityRenderDispatcher.class)
public interface EntityRenderDispatcherAccessor {
    @Accessor("shouldRenderShadow")
    boolean aurorion_menu$shouldRenderShadow();
}
