package com.aurorion.limbo.mixin;

import com.aurorion.limbo.client.ClientFinale;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Local camera only: no server teleport, chunk generation or respawn animation to reverse. */
@Mixin(Camera.class)
public abstract class FinaleCameraMixin {
    @Shadow protected abstract void setPosition(double x, double y, double z);
    @Shadow protected abstract void setRotation(float yaw, float pitch);
    @Inject(method = "setup", at = @At("TAIL"))
    private void aurorion$finale(BlockGetter level, Entity entity, boolean detached, boolean reverse,
                                  float partialTick, CallbackInfo ci) {
        if (!ClientFinale.cinematic()) return;
        var origin = ClientFinale.origin();
        setPosition(origin.x, origin.y + ClientFinale.cameraRise(), origin.z);
        setRotation(ClientFinale.yaw(), (float) (30 + 60 * ClientFinale.riseProgress()));
    }
}
