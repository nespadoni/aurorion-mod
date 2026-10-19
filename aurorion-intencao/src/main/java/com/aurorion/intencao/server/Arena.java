package com.aurorion.intencao.server;

import com.aurorion.intencao.AurorionIntencao;
import com.aurorion.intencao.entity.FragmentEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Monta o lugar da batalha numa celula vazia do plano. Roda uma vez por batalha, nunca por tick.
 *
 * <p>Com {@code structure} no tema, coloca o {@code .nbt} dos builders centralizado, com a camada
 * y=0 dele como chao. Sem, monta o <b>Tribunal de Vitrais</b>: um disco de vitral sobre lanternas,
 * cada fatia na cor (de corante mais proxima) da Casa de um participante, uma faixa clara entre os
 * lados, pilares de pedra-negra com lanternas de alma e uma parede invisivel na borda.
 *
 * <p>Custo: o volume limpo e de ~35x35x16 blocos, com flag 2 (sem atualizar vizinhos) — da ordem do
 * que o Reino das Almas ja faz por duelo, e num vazio que nao tem nada para recalcular.
 */
final class Arena {
    private static final int SET_FLAGS = Block.UPDATE_CLIENTS;
    private static final int CLEAR_HEIGHT = 16;

    private Arena() {
    }

    static void build(ServerLevel level, BlockPos center, Theme theme, List<Integer> colors, UUID session,
                      RandomSource random) {
        Optional<StructureTemplate> template = theme.structure().flatMap(id -> load(level, id));
        int half = theme.radius() + 3;
        if (template.isPresent()) {
            Vec3i size = template.get().getSize();
            half = Math.max(half, Math.max(size.getX(), size.getZ()) / 2 + 2);
        }
        clear(level, center, half, Math.max(CLEAR_HEIGHT, template.map(t -> t.getSize().getY() + 2).orElse(0)));

        if (template.isPresent()) {
            Vec3i size = template.get().getSize();
            BlockPos origin = center.offset(-size.getX() / 2, -1, -size.getZ() / 2);
            template.get().placeInWorld(level, origin, origin, new StructurePlaceSettings(), random, SET_FLAGS);
        } else {
            tribunal(level, center, theme.radius(), colors);
        }

        for (int i = 0; i < theme.fragments(); i++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double distance = 3 + random.nextDouble() * Math.max(1, theme.radius() - 5);
            Vec3 at = new Vec3(center.getX() + .5 + Math.cos(angle) * distance,
                    center.getY() + 1.1 + random.nextDouble() * 1.6,
                    center.getZ() + .5 + Math.sin(angle) * distance);
            FragmentEntity.spawn(level, session, at, random.nextFloat() * 360);
        }
    }

    private static Optional<StructureTemplate> load(ServerLevel level, ResourceLocation id) {
        Optional<StructureTemplate> found = level.getStructureManager().get(id);
        if (found.isEmpty()) {
            AurorionIntencao.LOGGER.warn("Estrutura '{}' do tema nao encontrada; usando o Tribunal de Vitrais.", id);
        }
        return found;
    }

    private static void clear(ServerLevel level, BlockPos center, int half, int height) {
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = -half; x <= half; x++) {
            for (int z = -half; z <= half; z++) {
                for (int y = -3; y <= height; y++) {
                    cursor.set(center.getX() + x, center.getY() + y, center.getZ() + z);
                    if (!level.getBlockState(cursor).isAir()) level.setBlock(cursor, air, SET_FLAGS);
                }
            }
        }
    }

    private static void tribunal(ServerLevel level, BlockPos center, int radius, List<Integer> colors) {
        BlockState[] sides = new BlockState[colors.size()];
        for (int i = 0; i < sides.length; i++) sides[i] = stainedGlass(colors.get(i));
        BlockState dark = Blocks.BLACK_STAINED_GLASS.defaultBlockState();
        BlockState seam = Blocks.WHITE_STAINED_GLASS.defaultBlockState();
        BlockState light = Blocks.SEA_LANTERN.defaultBlockState();
        BlockState rim = Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
        BlockState barrier = Blocks.BARRIER.defaultBlockState();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int n = Math.max(1, sides.length);

        for (int x = -radius - 1; x <= radius + 1; x++) {
            for (int z = -radius - 1; z <= radius + 1; z++) {
                double distance = Math.sqrt(x * x + z * z);
                if (distance > radius + 1.5) continue;
                int wx = center.getX() + x, wz = center.getZ() + z;
                if (distance > radius + .5) {
                    level.setBlock(cursor.set(wx, center.getY() - 1, wz), rim, SET_FLAGS);
                    for (int y = 0; y < 5; y++) level.setBlock(cursor.set(wx, center.getY() + y, wz), barrier, SET_FLAGS);
                    continue;
                }
                level.setBlock(cursor.set(wx, center.getY() - 2, wz), light, SET_FLAGS);
                BlockState floor;
                if (distance < 1.6) {
                    floor = Blocks.CHISELED_QUARTZ_BLOCK.defaultBlockState();
                } else if (n > 1 && nearSeam(x, z, n)) {
                    floor = seam;
                } else {
                    int ring = (int) (distance / 2.5);
                    floor = ring % 2 == 0 ? sides[Plane.side(x, z, n)] : dark;
                }
                level.setBlock(cursor.set(wx, center.getY() - 1, wz), floor, SET_FLAGS);
            }
        }

        // Oito pilares na borda, com lanterna de alma no topo: a unica luz "quente" do lugar.
        for (int k = 0; k < 8; k++) {
            double angle = k * Math.PI / 4 + Math.PI / 8;
            int px = center.getX() + Mth.floor(Math.cos(angle) * (radius - 1));
            int pz = center.getZ() + Mth.floor(Math.sin(angle) * (radius - 1));
            for (int y = 0; y < 5; y++) level.setBlock(cursor.set(px, center.getY() + y, pz), rim, SET_FLAGS);
            level.setBlock(cursor.set(px, center.getY() + 5, pz), Blocks.CRYING_OBSIDIAN.defaultBlockState(), SET_FLAGS);
            level.setBlock(cursor.set(px, center.getY() + 6, pz), Blocks.SOUL_LANTERN.defaultBlockState(), SET_FLAGS);
        }
    }

    /** A menos de ~0,6 bloco (medido no arco) da divisa entre duas fatias. */
    private static boolean nearSeam(int x, int z, int n) {
        double slice = Math.PI * 2 / n;
        double rel = Math.atan2(z, x) - Plane.spawnAngle(0, n) + slice / 2;
        double into = rel - Math.floor(rel / slice) * slice;
        return Math.min(into, slice - into) * Math.sqrt(x * x + z * z) < .6;
    }

    /** O vitral de corante mais parecido com a cor da Casa. */
    private static BlockState stainedGlass(int color) {
        DyeColor best = DyeColor.WHITE;
        long bestDistance = Long.MAX_VALUE;
        for (DyeColor dye : DyeColor.values()) {
            int c = dye.getTextureDiffuseColor();
            long dr = FastColor.ARGB32.red(c) - FastColor.ARGB32.red(color);
            long dg = FastColor.ARGB32.green(c) - FastColor.ARGB32.green(color);
            long db = FastColor.ARGB32.blue(c) - FastColor.ARGB32.blue(color);
            long distance = dr * dr * 3 + dg * dg * 4 + db * db * 2;
            if (distance < bestDistance && dye != DyeColor.BLACK) {
                bestDistance = distance;
                best = dye;
            }
        }
        Block block = BuiltInRegistries.BLOCK.get(ResourceLocation.withDefaultNamespace(best.getName() + "_stained_glass"));
        return block == Blocks.AIR ? Blocks.WHITE_STAINED_GLASS.defaultBlockState() : block.defaultBlockState();
    }
}
