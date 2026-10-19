package com.aurorion.mundos.mixin;

import com.aurorion.mundos.perfil.SpawnCaps;
import net.minecraft.server.level.ServerChunkCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Marca de qual dimensao e o spawn natural que esta para rodar. Ver {@link SpawnCaps}: o teto e lido
 * dentro do {@code NaturalSpawner}, que nao sabe a dimensao; o {@code tickChunks} sabe, e roda uma vez
 * por tick por dimensao.
 */
@Mixin(ServerChunkCache.class)
public abstract class ServerChunkCacheSpawnCapMixin {
    @Inject(method = "tickChunks", at = @At("HEAD"))
    private void aurorion_mundos$enterSpawnCaps(CallbackInfo ci) {
        SpawnCaps.enter(((ServerChunkCache) (Object) this).level);
    }

    @Inject(method = "tickChunks", at = @At("RETURN"))
    private void aurorion_mundos$exitSpawnCaps(CallbackInfo ci) {
        SpawnCaps.exit();
    }
}
