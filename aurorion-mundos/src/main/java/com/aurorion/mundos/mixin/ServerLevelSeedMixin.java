package com.aurorion.mundos.mixin;

import com.aurorion.mundos.world.WorldCatalog;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.BiomeManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Da a cada mundo declarado um seed proprio.
 *
 * <p>Sem isto, dois overworlds com o mesmo gerador geram <b>o mesmo mapa</b>: o vanilla nao tem seed
 * por dimensao. {@code ServerLevel#getSeed()} devolve {@code worldGenOptions().seed()} e e o unico
 * consumidor disso em {@code ChunkMap} (linha 182), que o usa para as tres coisas que definem o
 * terreno — {@code RandomState} (relevo e biomas), {@code ChunkGeneratorStructureState}
 * (posicionamento de estruturas) e, por chunk, a semente de decoracao em
 * {@code NoiseBasedChunkGenerator}.
 *
 * <h2>Por que sao dois pontos e nao um</h2>
 *
 * <p>Mexer so no {@code getSeed()} deixaria cliente e servidor discordando na mistura de biomas. O
 * {@code BiomeManager} — que decide o embaralhado das bordas de bioma — e construido de um seed
 * separado: o servidor recebe o dele por parametro de construtor, derivado do seed do <b>mundo</b>
 * ({@code MinecraftServer#createLevels:362}), enquanto o cliente monta o seu com
 * {@code BiomeManager.obfuscateSeed(level.getSeed())} ({@code ServerPlayer#createCommonSpawnInfo}).
 * Corrigido um, o outro tem que acompanhar, senao a cor de grama do cliente deixa de bater com o
 * bioma que o servidor calcula.
 *
 * <p>A troca acontece no {@code RETURN} do construtor, e nao no parametro, porque injetar em
 * parametro de construtor e uma forma bem mais fragil de mixin. No {@code RETURN} o objeto ja esta
 * inteiro e a substituicao e uma atribuicao de campo. Nada guarda o {@code BiomeManager} antigo
 * durante a construcao — {@code ChunkMap} e {@code ServerChunkCache} chamam
 * {@code level.getBiomeManager()} na hora do uso.
 *
 * <h2>Custo</h2>
 *
 * <p>Frio. Uma consulta por nivel criado, uma por chunk gerado, uma por troca de dimensao. Nada por
 * tick. Dimensao sem seed declarado sai pelo caminho de cima sem tocar em nada (SDD §2).
 */
@Mixin(ServerLevel.class)
public abstract class ServerLevelSeedMixin {

    @Inject(method = "getSeed", at = @At("HEAD"), cancellable = true)
    private void aurorion_mundos$dimensionSeed(CallbackInfoReturnable<Long> cir) {
        ServerLevel self = (ServerLevel) (Object) this;
        Long seed = WorldCatalog.seedFor(self.dimension());
        if (seed != null) {
            cir.setReturnValue(seed);
        }
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void aurorion_mundos$dimensionBiomeZoom(CallbackInfo ci) {
        ServerLevel self = (ServerLevel) (Object) this;
        Long seed = WorldCatalog.seedFor(self.dimension());
        if (seed == null) return;

        ((LevelBiomeManagerAccessor) self).aurorion_mundos$setBiomeManager(
                new BiomeManager(self, BiomeManager.obfuscateSeed(seed)));
    }
}
