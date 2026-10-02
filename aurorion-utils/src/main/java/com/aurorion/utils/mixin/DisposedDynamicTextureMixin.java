package com.aurorion.utils.mixin;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.renderer.texture.DynamicTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A texture can be closed between off-thread construction and the queued GPU upload. */
@Mixin(DynamicTexture.class)
public abstract class DisposedDynamicTextureMixin {
    @Shadow private NativeImage pixels;

    @Inject(method = "close", at = @At("HEAD"), cancellable = true)
    private void aurorion_utils$closeOnRenderThread(CallbackInfo callback) {
        if (!RenderSystem.isOnRenderThread()) {
            RenderSystem.recordRenderCall(() -> ((DynamicTexture) (Object) this).close());
            callback.cancel();
        }
    }

    @Inject(method = "lambda$new$0", at = @At("HEAD"), cancellable = true)
    private void aurorion_utils$skipDisposedUpload(CallbackInfo callback) {
        // Do not allocate a GPU texture for an image already disposed by its owner.
        if (pixels == null) callback.cancel();
    }
}
