package com.aurorion.mundos.perfil;

import com.aurorion.mundos.AurorionMundos;
import com.aurorion.mundos.mixin.ChunkGeneratorStructureStateAccessor;
import com.aurorion.mundos.mixin.StructurePlacementAccessor;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;

import java.util.ArrayList;
import java.util.List;

/**
 * Mais (ou menos) estruturas numa dimensao, sem datapack de worldgen e sem tocar nas outras.
 *
 * <h2>Como</h2>
 *
 * <p>Estrutura de espalhamento aleatorio ({@code minecraft:random_spread}, o tipo de vila, posto,
 * templo, navio, e de boa parte das estruturas de mod) cai uma por celula de
 * {@code spacing x spacing} chunks. Quantidade por area e {@code 1 / spacing²}; entao, para multiplicar
 * a quantidade por {@code d}, o spacing e dividido por {@code √d} — e o {@code separation} acompanha,
 * sempre menor que o spacing, como o vanilla exige.
 *
 * <p>O conjunto refeito vai para a lista desta dimensao como {@code Holder.direct}. O registro global
 * de conjuntos nao muda: o overworld, o Nether e as outras dimensoes continuam com o espacamento do
 * datapack.
 *
 * <h2>O que fica de fora</h2>
 *
 * <ul>
 *   <li>Fortaleza do End e estruturas em aneis ({@code concentric_rings}): posicao fixa por design.</li>
 *   <li>Tipos de posicionamento de mod (YUNG's, alguns pacotes de estrutura): sao classes proprias, e
 *       recriar uma delas como {@code random_spread} mudaria o comportamento dela. Seguem como vieram.</li>
 *   <li>A zona de exclusao (posto longe de vila) continua medida com o espacamento original do outro
 *       conjunto: com densidade alta, a distancia minima entre os dois encolhe menos que o resto.</li>
 * </ul>
 *
 * <p>Custo: uma volta pelos conjuntos (algumas centenas) quando a dimensao e criada. Nada depois.
 */
public final class StructureDensity {
    /** O codec do vanilla aceita ate 4096 chunks entre estruturas. */
    private static final int MAX_SPACING = 4096;

    private StructureDensity() {
    }

    public static void apply(ResourceKey<Level> dimension, ChunkGeneratorStructureState state) {
        WorldProfile profile = WorldProfiles.forLevel(dimension);
        if (profile == null || profile.structures().isVanilla()) return;
        WorldProfile.Structures structures = profile.structures();

        List<Holder<StructureSet>> sets = state.possibleStructureSets();
        List<Holder<StructureSet>> scaled = new ArrayList<>(sets.size());
        int changed = 0;
        for (Holder<StructureSet> holder : sets) {
            ResourceLocation id = holder.unwrapKey().map(ResourceKey::location).orElse(null);
            double density = id == null ? structures.density() : structures.densityFor(id);
            StructureSet set = holder.value();
            // Classe exata: subclasse de mod tem regra propria, e recria-la como vanilla a perderia.
            if (density == 1 || set.placement().getClass() != RandomSpreadStructurePlacement.class) {
                scaled.add(holder);
                continue;
            }
            scaled.add(Holder.direct(new StructureSet(set.structures(),
                    scale((RandomSpreadStructurePlacement) set.placement(), density))));
            changed++;
        }
        if (changed == 0) return;
        ((ChunkGeneratorStructureStateAccessor) state).aurorion_mundos$setPossibleStructureSets(List.copyOf(scaled));
        AurorionMundos.LOGGER.info("{}: perfil {} — {} conjunto(s) de estrutura com densidade ajustada (x{}).",
                dimension.location(), profile.id(), changed, structures.density());
    }

    static RandomSpreadStructurePlacement scale(RandomSpreadStructurePlacement placement, double density) {
        double factor = 1 / Math.sqrt(density);
        int spacing = Mth.clamp((int) Math.round(placement.spacing() * factor), 2, MAX_SPACING);
        int separation = Mth.clamp((int) Math.round(placement.separation() * factor), 0, spacing - 1);
        StructurePlacementAccessor base = (StructurePlacementAccessor) placement;
        return new RandomSpreadStructurePlacement(base.aurorion_mundos$locateOffset(),
                base.aurorion_mundos$frequencyReductionMethod(), base.aurorion_mundos$frequency(),
                base.aurorion_mundos$salt(), base.aurorion_mundos$exclusionZone(),
                spacing, separation, placement.spreadType());
    }
}
