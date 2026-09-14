package com.aurorion.mundos.portal;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Optional;

/**
 * Para onde leva um portal aceso em {@code from}.
 *
 * <p>Mora em {@code data/<namespace>/aurorion/ligacoes/<nome>.json}, igual receita ou loot table — o
 * {@code id} vem do caminho do arquivo. Isto e conteudo, nao regra: mudar o destino de um portal, ou
 * criar um par de mundos novo, e editar arquivo e dar {@code /reload} (SDD §7, diretriz 4).
 *
 * <p>Repare que <b>o seed nao esta aqui</b>, e de proposito — ver {@code MundosConfig}. Destino de
 * portal pode mudar com o servidor no ar; seed de mundo ja gerado, nao.
 *
 * <h2>Por que existe {@code area}</h2>
 *
 * <p>Um portal do Nether carrega um destino so por dimensao de origem: a pergunta que o vanilla faz e
 * "de onde voce veio", nunca "por qual porta". Com tres mundos isso daria uma corrente — para ir do
 * primeiro ao terceiro seria preciso passar pelo segundo.
 *
 * <p>{@code area} resolve sem inventar bloco novo: a ligacao vale so para portais construidos dentro
 * de um circulo. Assim um mundo pode ter varias saidas, cada uma num lugar declarado, e quem fica de
 * fora de todos os circulos cai na ligacao sem area — ou no vanilla, se nao houver nenhuma.
 *
 * @param searchRadius vazio usa o padrao da config. Ver {@code MundosConfig#DEFAULT_SEARCH_RADIUS}
 *                     para por que 16 e nao os 128 do vanilla.
 */
public record DimensionLink(
        ResourceLocation id,
        ResourceKey<Level> from,
        ResourceKey<Level> to,
        Optional<Integer> searchRadius,
        Optional<Area> area,
        int order
) {
    public static Codec<DimensionLink> codec(ResourceLocation id) {
        return RecordCodecBuilder.create(instance -> instance.group(
                ResourceKey.codec(Registries.DIMENSION).fieldOf("from").forGetter(DimensionLink::from),
                ResourceKey.codec(Registries.DIMENSION).fieldOf("to").forGetter(DimensionLink::to),
                Codec.intRange(1, 128).optionalFieldOf("searchRadius").forGetter(DimensionLink::searchRadius),
                Area.CODEC.optionalFieldOf("area").forGetter(DimensionLink::area),
                Codec.INT.optionalFieldOf("order", 0).forGetter(DimensionLink::order)
        ).apply(instance, (from, to, searchRadius, area, order) ->
                new DimensionLink(id, from, to, searchRadius, area, order)));
    }

    /**
     * Um circulo no plano horizontal. A altura nao entra: um portal continua sendo a mesma porta
     * esteja ele no fundo de uma caverna ou no topo da montanha acima dela.
     */
    public record Area(int centerX, int centerZ, int radius) {
        private static final Codec<List<Integer>> CENTER = Codec.INT.listOf().comapFlatMap(
                list -> list.size() == 2
                        ? DataResult.success(list)
                        : DataResult.error(() -> "center precisa ter exatamente dois numeros: [x, z]"),
                list -> list);

        public static final Codec<Area> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                CENTER.fieldOf("center").forGetter(area -> List.of(area.centerX(), area.centerZ())),
                Codec.intRange(1, 30000000).fieldOf("radius").forGetter(Area::radius)
        ).apply(instance, (center, radius) -> new Area(center.get(0), center.get(1), radius)));

        /** Distancia ao quadrado, para nao pagar uma raiz quadrada numa checagem por travessia. */
        public boolean contains(double x, double z) {
            double dx = x - centerX;
            double dz = z - centerZ;
            return dx * dx + dz * dz <= (double) radius * (double) radius;
        }
    }
}
