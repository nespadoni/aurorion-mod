package com.aurorion.utils.render;

import com.aurorion.utils.AurorionUtils;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ModelEvent;

/**
 * "Este bloco desenha na camada solida?" guardado no proprio {@link BlockState}.
 *
 * <p>O NeoForge responde isso em {@code ClientHooks.isBlockInSolidLayer} pegando o modelo e pedindo as
 * camadas com um {@code RandomSource.create()} novo — uma alocacao e um {@code AtomicLong} disputado por
 * todas as threads de malha, a cada face testada. A resposta so muda quando os modelos mudam: aqui ela e
 * lida de um campo, e zerada a cada recarga de modelos.
 *
 * <p>0 = ainda nao sabe, 1 = solida, 2 = nao. Escrita sem trava de proposito: a conta e a mesma em
 * qualquer thread, entao duas escrevendo ao mesmo tempo gravam o mesmo valor.
 */
public interface SolidLayerCache {
    byte aurorion_utils$solidLayer();

    void aurorion_utils$setSolidLayer(byte value);

    @EventBusSubscriber(modid = AurorionUtils.MOD_ID, value = Dist.CLIENT)
    final class Reset {
        private Reset() {
        }

        private static volatile boolean resetNextTick;

        /**
         * Pacote de recursos novo pode trocar a camada de um bloco: tudo volta a "nao sabe".
         *
         * <p>Duas vezes: agora e no proximo tick. O {@code ModelManager.apply} dispara este evento
         * <b>antes</b> de trocar os modelos em uso; uma thread de malha que perguntar nesse intervalo
         * guardaria a resposta dos modelos antigos. No tick seguinte a troca ja aconteceu.
         */
        @SubscribeEvent
        public static void onModelsBaked(ModelEvent.BakingCompleted event) {
            reset();
            resetNextTick = true;
        }

        @SubscribeEvent
        public static void onClientTick(ClientTickEvent.Pre event) {
            if (!resetNextTick) return;
            resetNextTick = false;
            reset();
        }

        private static void reset() {
            for (BlockState state : Block.BLOCK_STATE_REGISTRY) {
                ((SolidLayerCache) state).aurorion_utils$setSolidLayer((byte) 0);
            }
        }
    }
}
