package com.aurorion.economia.land;

import com.aurorion.core.data.SavedDataAccess;
import com.aurorion.economia.AurorionEconomia;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.LongUnaryOperator;

/**
 * O mapa de zonas de preco dos terrenos, desenhado pela staff com {@code /economia zona regiao}.
 *
 * <h2>Por que o servidor decide a zona</h2>
 *
 * <p>A tela do corretor mandava o indice da zona e o servidor aceitava: bastava escolher
 * "Periferia" para vender um lote no centro pelo preco minimo da zona mais barata. Agora a zona de
 * um lote e calculada aqui, pela geometria, e o pedido do cliente so e aceito se bater.</p>
 *
 * <h2>A regra</h2>
 *
 * <ul>
 *   <li>Lote que toca uma ou mais regioes paga a <b>mais cara</b> entre elas (pela taxa atual de cada
 *       zona, nao pelo indice). Encostar um canto no centro comercial ja e centro comercial.</li>
 *   <li>Lote que nao toca regiao nenhuma paga a zona mais cara que existe. Terra sem mapa nunca e
 *       desconto: e a staff que marca onde e barato, e nao o corretor.</li>
 * </ul>
 *
 * <p>Coordenada mora no save do mundo, nunca em config (ECONOMIA.md §4.2). Regioes sao retangulos
 * X/Z inclusivos por dimensao, poucas (teto {@value #MAX_REGIONS}); a busca e linear.</p>
 */
public final class LandZoneData extends SavedData {
    public static final int MAX_REGIONS = 256;
    public static final int ZONES = 5;

    private static final SavedDataAccess<LandZoneData> ACCESS =
            new SavedDataAccess<>(AurorionEconomia.MOD_ID + "_zonas", LandZoneData::new, LandZoneData::load);

    public record Region(int id, ResourceLocation dimension, int minX, int minZ, int maxX, int maxZ, int zone) {
        public Region {
            if (dimension == null || zone < 0 || zone >= ZONES || minX > maxX || minZ > maxZ)
                throw new IllegalArgumentException("Regiao de zona invalida");
        }
    }

    private final List<Region> regions = new ArrayList<>();
    private int nextId = 1;

    public static LandZoneData get(MinecraftServer server) {
        return ACCESS.get(server);
    }

    public List<Region> regions() {
        return Collections.unmodifiableList(regions);
    }

    /** @return a regiao criada, ou {@code null} se o teto de regioes foi atingido */
    @Nullable
    public Region add(ResourceLocation dimension, int x1, int z1, int x2, int z2, int zone) {
        if (regions.size() >= MAX_REGIONS) return null;
        Region region = new Region(nextId++, dimension, Math.min(x1, x2), Math.min(z1, z2),
                Math.max(x1, x2), Math.max(z1, z2), zone);
        regions.add(region);
        setDirty();
        return region;
    }

    public boolean remove(int id) {
        boolean removed = regions.removeIf(region -> region.id() == id);
        if (removed) setDirty();
        return removed;
    }

    /**
     * A zona que o servidor cobra por este lote. Pura (sem config): {@code rate} da a taxa atual de
     * cada indice, para que "mais cara" acompanhe os precos que a staff ajustar.
     */
    public int zoneFor(LandDeed deed, LongUnaryOperator rate) {
        return zoneFor(regions, deed.dimension(), deed.x(), deed.z(), deed.endX(), deed.endZ(), rate);
    }

    /** Mesma regra, para um retangulo ainda sem escritura. */
    public int zoneFor(ResourceLocation dimension, int x, int z, int endX, int endZ, LongUnaryOperator rate) {
        return zoneFor(regions, dimension, x, z, endX, endZ, rate);
    }

    static int zoneFor(List<Region> regions, ResourceLocation dimension, int x, int z, int endX, int endZ,
                       LongUnaryOperator rate) {
        int best = -1;
        for (Region region : regions) {
            if (!region.dimension().equals(dimension) || region.minX() > endX || region.maxX() < x
                    || region.minZ() > endZ || region.maxZ() < z) continue;
            if (best < 0 || rate.applyAsLong(region.zone()) > rate.applyAsLong(best)) best = region.zone();
        }
        if (best >= 0) return best;
        int priciest = 0;
        for (int zone = 1; zone < ZONES; zone++)
            if (rate.applyAsLong(zone) > rate.applyAsLong(priciest)) priciest = zone;
        return priciest;
    }

    private static LandZoneData load(CompoundTag tag, HolderLookup.Provider registries) {
        LandZoneData data = new LandZoneData();
        data.nextId = Math.max(1, tag.getInt("NextId"));
        ListTag list = tag.getList("Regions", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size() && data.regions.size() < MAX_REGIONS; i++) {
            CompoundTag entry = list.getCompound(i);
            ResourceLocation dimension = ResourceLocation.tryParse(entry.getString("Dimension"));
            try {
                if (dimension == null) throw new IllegalArgumentException("dimensao");
                Region region = new Region(entry.getInt("Id"), dimension, entry.getInt("MinX"), entry.getInt("MinZ"),
                        entry.getInt("MaxX"), entry.getInt("MaxZ"), entry.getInt("Zone"));
                data.regions.add(region);
                data.nextId = Math.max(data.nextId, region.id() + 1);
            } catch (IllegalArgumentException error) {
                AurorionEconomia.LOGGER.warn("Regiao de zona {} ilegivel no save; ignorada.", i, error);
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("Version", 1);
        tag.putInt("NextId", nextId);
        ListTag list = new ListTag();
        for (Region region : regions) {
            CompoundTag entry = new CompoundTag();
            entry.putInt("Id", region.id());
            entry.putString("Dimension", region.dimension().toString());
            entry.putInt("MinX", region.minX());
            entry.putInt("MinZ", region.minZ());
            entry.putInt("MaxX", region.maxX());
            entry.putInt("MaxZ", region.maxZ());
            entry.putInt("Zone", region.zone());
            list.add(entry);
        }
        tag.put("Regions", list);
        return tag;
    }
}
