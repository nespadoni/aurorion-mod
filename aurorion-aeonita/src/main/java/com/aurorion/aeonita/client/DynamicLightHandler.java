package com.aurorion.aeonita.client;

import com.aurorion.aeonita.AurorionAeonita;
import com.aurorion.aeonita.config.AeonitaClientConfig;
import com.aurorion.aeonita.registry.AeonitaTags;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Luz dinamica: qualquer item da tag {@link AeonitaTags#EMITS_LIGHT} acende onde esta — na mao, no
 * chao ou dentro de um quadro. A luz e uma mentira local: troca o ar da posicao pelo bloco invisivel
 * {@code minecraft:light} na copia do mundo <em>deste</em> cliente. O servidor nunca fica sabendo,
 * nada disso vai para o save.
 *
 * <p>Roda por tick do cliente, entao vale a meta de "zero alocacao por frame" do SDD §2:
 * <ul>
 *   <li>as duas colecoes sao campos reaproveitados ({@code fastutil} com chave {@code int}, sem
 *       boxing de {@code Integer} por entidade por tick);</li>
 *   <li>a posicao da entidade viva e calculada num {@link BlockPos.MutableBlockPos} reaproveitado —
 *       so vira {@code BlockPos} imutavel quando a luz realmente muda de lugar;</li>
 *   <li>a varredura de entidades que sumiram (a unica parte que aloca um iterador) so roda quando
 *       {@code ACTIVE} tem mais entradas do que as vistas neste tick.</li>
 * </ul>
 *
 * <p>O custo que sobra e inerente ao truque: cada mudanca de posicao e um {@code setBlock}, que
 * refaz iluminacao do chunk. Por isso a feature tem chave de desligar em
 * {@link AeonitaClientConfig} — num modpack pesado, esse e o primeiro item a sacrificar por FPS.
 */
@EventBusSubscriber(modid = AurorionAeonita.MOD_ID, value = Dist.CLIENT)
public final class DynamicLightHandler {
    /** Id da entidade -> posicao onde a luz falsa dela esta agora. */
    private static final Int2ObjectOpenHashMap<BlockPos> ACTIVE = new Int2ObjectOpenHashMap<>();

    /** Entidades que terminaram este tick com luz acesa. Invariante: sempre subconjunto de ACTIVE. */
    private static final IntOpenHashSet SEEN = new IntOpenHashSet();

    private static final BlockPos.MutableBlockPos SCRATCH = new BlockPos.MutableBlockPos();

    /**
     * O minimo que faz a luz falsa aparecer: marca a secao para redesenho e nada mais.
     *
     * <p>Era {@code Block.UPDATE_ALL}, que e {@code UPDATE_NEIGHBORS | UPDATE_CLIENTS} — e o bit de
     * vizinhos nao tem o que fazer aqui. A luz aparece e some varias vezes por segundo debaixo de
     * quem anda com um item aceso; cada troca mandava os blocos em volta reagirem a um bloco que so
     * existe na copia local do mundo, e a cascata de {@code updateShape} podia ate deixar vizinho
     * desenhado errado ate o proximo carregamento do chunk.
     *
     * <p>A iluminacao continua sendo recalculada: quem faz isso e o motor de luz, chamado pelo
     * proprio {@code setBlockState} quando a emissao muda, sem depender destes bits.
     */
    private static final int LIGHT_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    @Nullable
    private static ClientLevel lastLevel;

    private DynamicLightHandler() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;

        if (level != lastLevel) {
            // Trocou de mundo: as luzes antigas foram embora junto com a copia local anterior.
            ACTIVE.clear();
            lastLevel = level;
        }

        if (level == null || minecraft.isPaused()) {
            return;
        }

        if (!AeonitaClientConfig.DYNAMIC_LIGHT_ENABLED.get()) {
            if (!ACTIVE.isEmpty()) {
                clearAll(level);
            }
            return;
        }

        int lightLevel = AeonitaClientConfig.DYNAMIC_LIGHT_LEVEL.get();
        SEEN.clear();

        for (Entity entity : level.entitiesForRendering()) {
            BlockPos target = glowPos(entity);
            if (target == null) {
                continue;
            }

            int id = entity.getId();
            BlockPos previous = ACTIVE.get(id);

            if (previous != null && previous.equals(target)) {
                SEEN.add(id);
                continue;
            }

            if (previous != null) {
                clearLight(level, previous);
            }

            if (level.getBlockState(target).isAir()) {
                BlockPos placed = target.immutable();
                level.setBlock(placed, Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, lightLevel), LIGHT_FLAGS);
                ACTIVE.put(id, placed);
                SEEN.add(id);
            } else {
                // Nao ha ar aqui (o item esta dentro de um bloco): fica sem luz ate sair.
                ACTIVE.remove(id);
            }
        }

        // ACTIVE contem SEEN por construcao, entao tamanhos diferentes == sobrou entidade que sumiu.
        if (ACTIVE.size() != SEEN.size()) {
            pruneGone(level);
        }
    }

    @Nullable
    private static BlockPos glowPos(Entity entity) {
        if (entity instanceof ItemEntity item) {
            return emitsLight(item.getItem()) ? item.blockPosition() : null;
        }
        if (entity instanceof ItemFrame frame) {
            return emitsLight(frame.getItem()) ? frame.blockPosition() : null;
        }
        if (entity instanceof LivingEntity living
                && (emitsLight(living.getMainHandItem()) || emitsLight(living.getOffhandItem()))) {
            return SCRATCH.set(
                    Mth.floor(living.getX()),
                    Mth.floor(living.getEyeY() - 0.5),
                    Mth.floor(living.getZ()));
        }
        return null;
    }

    private static boolean emitsLight(ItemStack stack) {
        return !stack.isEmpty() && stack.is(AeonitaTags.EMITS_LIGHT);
    }

    private static void pruneGone(ClientLevel level) {
        ObjectIterator<Int2ObjectMap.Entry<BlockPos>> iterator = ACTIVE.int2ObjectEntrySet().fastIterator();
        while (iterator.hasNext()) {
            Int2ObjectMap.Entry<BlockPos> entry = iterator.next();
            if (!SEEN.contains(entry.getIntKey())) {
                clearLight(level, entry.getValue());
                iterator.remove();
            }
        }
    }

    private static void clearAll(ClientLevel level) {
        for (BlockPos pos : ACTIVE.values()) {
            clearLight(level, pos);
        }
        ACTIVE.clear();
    }

    private static void clearLight(ClientLevel level, BlockPos pos) {
        if (level.getBlockState(pos).is(Blocks.LIGHT)) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), LIGHT_FLAGS);
        }
    }
}
