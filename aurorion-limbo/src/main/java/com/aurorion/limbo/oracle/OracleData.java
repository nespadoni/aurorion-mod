package com.aurorion.limbo.oracle;

import com.aurorion.core.data.SavedDataAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.Objects;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Os lugares onde o Oraculo pode aparecer, o lugar de hoje e o dia do ultimo sorteio.
 *
 * <p>Fica no SavedData do mundo, e nao na config, porque ponto de rotacao e <b>dado de mundo</b>: a
 * staff cadastra andando ate o lugar com {@code /oraculo local ponto}, e uma coordenada digitada a
 * mao num TOML seria um jeito pior de fazer a mesma coisa. Mesma escolha de {@code /vidas exilio aqui}.
 */
public final class OracleData extends SavedData {
    public static final int MAX_SPOTS = 64;
    private static final SavedDataAccess<OracleData> ACCESS =
            new SavedDataAccess<>("aurorion_limbo_oraculo", OracleData::new, OracleData::load);

    /** Um lugar cadastrado. {@code yaw} guarda para que lado a staff estava olhando ao cadastrar. */
    public record Spot(String name, ResourceLocation dimension, BlockPos pos, float yaw) {}

    /** Ordem de insercao preservada: a lista da staff sai sempre igual, o que ajuda a conferir. */
    private final Map<String, Spot> spots = new LinkedHashMap<>();
    @Nullable private String current;
    /** Referencia persistente do NPC para reencontra-lo quando o chunk onde ele estava descarregou. */
    @Nullable private java.util.UUID oracleUuid;
    @Nullable private ResourceLocation oracleDimension;
    @Nullable private BlockPos oraclePos;
    /** Ponto aguardando o carregamento assincrono do chunk antigo do NPC. */
    @Nullable private String pending;
    /** Dia ja sorteado, em "dias desde a epoca" deslocados pela hora da rotacao. -1 = nunca sorteou. */
    private long rotatedDay = -1;

    public static OracleData get(MinecraftServer server) { return ACCESS.get(server); }

    static OracleData load(CompoundTag tag, HolderLookup.Provider registries) { // visivel para teste
        OracleData data = new OracleData();
        ListTag list = tag.getList("Spots", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            String name = entry.getString("Name");
            data.spots.put(name, new Spot(name,
                    ResourceLocation.parse(entry.getString("Dimension")),
                    new BlockPos(entry.getInt("X"), entry.getInt("Y"), entry.getInt("Z")),
                    entry.getFloat("Yaw")));
        }
        if (tag.contains("Current")) data.current = tag.getString("Current");
        data.rotatedDay = tag.contains("RotatedDay") ? tag.getLong("RotatedDay") : -1;
        if (tag.hasUUID("OracleUUID") && tag.contains("OracleDimension") && tag.contains("OracleX")
                && tag.contains("OracleY") && tag.contains("OracleZ")) {
            data.oracleUuid = tag.getUUID("OracleUUID");
            data.oracleDimension = ResourceLocation.parse(tag.getString("OracleDimension"));
            data.oraclePos = new BlockPos(tag.getInt("OracleX"), tag.getInt("OracleY"), tag.getInt("OracleZ"));
        }
        if (tag.contains("Pending")) data.pending = tag.getString("Pending");
        return data;
    }

    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Spot spot : spots.values()) {
            CompoundTag entry = new CompoundTag();
            entry.putString("Name", spot.name());
            entry.putString("Dimension", spot.dimension().toString());
            entry.putInt("X", spot.pos().getX());
            entry.putInt("Y", spot.pos().getY());
            entry.putInt("Z", spot.pos().getZ());
            entry.putFloat("Yaw", spot.yaw());
            list.add(entry);
        }
        tag.put("Spots", list);
        if (current != null) tag.putString("Current", current);
        tag.putLong("RotatedDay", rotatedDay);
        if (oracleUuid != null && oracleDimension != null && oraclePos != null) {
            tag.putUUID("OracleUUID", oracleUuid);
            tag.putString("OracleDimension", oracleDimension.toString());
            tag.putInt("OracleX", oraclePos.getX());
            tag.putInt("OracleY", oraclePos.getY());
            tag.putInt("OracleZ", oraclePos.getZ());
        }
        if (pending != null) tag.putString("Pending", pending);
        return tag;
    }

    /** @return false quando o nome ja existe ou a lista esta cheia. */
    public boolean add(Spot spot) {
        if (spots.size() >= MAX_SPOTS || spots.containsKey(spot.name())) return false;
        spots.put(spot.name(), spot);
        setDirty();
        return true;
    }
    public boolean remove(String name) {
        if (spots.remove(name) == null) return false;
        if (name.equals(current)) current = null;
        if (name.equals(pending)) pending = null;
        setDirty();
        return true;
    }
    public List<Spot> all() { return new ArrayList<>(spots.values()); }
    @Nullable public Spot spot(String name) { return spots.get(name); }
    @Nullable public Spot currentSpot() { return current == null ? null : spots.get(current); }
    public void setCurrent(String name) { current = name; setDirty(); }
    public long rotatedDay() { return rotatedDay; }
    public void setRotatedDay(long day) { rotatedDay = day; setDirty(); }

    /** Atualiza a referencia somente quando o NPC entra ou muda de ponto, nunca por tick. */
    public void remember(OracleEntity oracle) {
        ResourceLocation dimension = oracle.level().dimension().location();
        BlockPos pos = oracle.blockPosition();
        if (Objects.equals(oracleUuid, oracle.getUUID()) && Objects.equals(oracleDimension, dimension)
                && Objects.equals(oraclePos, pos)) return;
        oracleUuid = oracle.getUUID(); oracleDimension = dimension; oraclePos = pos.immutable(); setDirty();
    }

    @Nullable public java.util.UUID oracleUuid() { return oracleUuid; }
    @Nullable public ResourceLocation oracleDimension() { return oracleDimension; }
    @Nullable public BlockPos oraclePos() { return oraclePos; }
    @Nullable public Spot pendingSpot() { return pending == null ? null : spots.get(pending); }
    public void setPending(@Nullable String name) {
        if (Objects.equals(pending, name)) return;
        pending = name;
        setDirty();
    }
}
