package com.aurorion.mundos.border;

import com.aurorion.core.data.SavedDataAccess;
import com.aurorion.mundos.AurorionMundos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * A barreira de cada mundo, em disco.
 *
 * <p>Existe porque o vanilla so persiste <b>uma</b> barreira: a do overworld, dentro do
 * {@code level.dat}. Toda outra dimensao recebe a dela por espelhamento em tempo de boot
 * ({@code MinecraftServer#createLevels}), entao nao ha onde guardar um raio diferente por mundo —
 * sem isto, toda barreira que a staff ajustasse voltaria ao valor do overworld no proximo restart.
 *
 * <p>Fica no {@code data/} do overworld, como todo dado de servidor do ecossistema: e informacao
 * sobre as dimensoes, nao de dentro de uma delas.
 */
public class BorderData extends SavedData {
    private static final String FILE_ID = AurorionMundos.MOD_ID + "_bordas";
    private static final String KEY_BORDERS = "Borders";
    private static final String KEY_DIMENSION = "Dimension";

    private static final SavedDataAccess<BorderData> ACCESS =
            new SavedDataAccess<>(FILE_ID, BorderData::new, BorderData::load);

    private final Map<ResourceKey<Level>, Settings> byDimension = new HashMap<>();

    public static BorderData get(MinecraftServer server) {
        return ACCESS.get(server);
    }

    // Visivel para teste: e o par de save(), e o que garante que a barreira sobreviva ao restart.
    static BorderData load(CompoundTag tag, HolderLookup.Provider registries) {
        BorderData data = new BorderData();
        ListTag saved = tag.getList(KEY_BORDERS, Tag.TAG_COMPOUND);

        for (int i = 0; i < saved.size(); i++) {
            CompoundTag entry = saved.getCompound(i);
            ResourceLocation id = ResourceLocation.tryParse(entry.getString(KEY_DIMENSION));
            if (id == null) {
                AurorionMundos.LOGGER.error("Barreira salva com id de dimensao invalido, ignorada: {}",
                        entry.getString(KEY_DIMENSION));
                continue;
            }
            data.byDimension.put(ResourceKey.create(Registries.DIMENSION, id), Settings.load(entry));
        }

        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag saved = new ListTag();

        byDimension.forEach((dimension, settings) -> {
            CompoundTag entry = new CompoundTag();
            entry.putString(KEY_DIMENSION, dimension.location().toString());
            settings.save(entry);
            saved.add(entry);
        });

        tag.put(KEY_BORDERS, saved);
        return tag;
    }

    /** @return o que foi salvo para esta dimensao, ou {@code null} se ela nunca foi ajustada. */
    @Nullable
    public Settings get(ResourceKey<Level> dimension) {
        return byDimension.get(dimension);
    }

    /** Grava o estado atual de uma barreira. Chamado depois de toda mudanca, nunca por tick. */
    public void remember(ResourceKey<Level> dimension, WorldBorder border) {
        byDimension.put(dimension, Settings.of(border));
        setDirty();
    }

    /**
     * Tudo que define uma barreira, menos a animacao em curso.
     *
     * <p>Um {@code lerp} em andamento nao e salvo de proposito: o vanilla tambem nao salva o dele, e
     * uma barreira que continuasse encolhendo por cima de um restart surpreenderia todo mundo. O
     * tamanho gravado e o de destino, entao reiniciar no meio de uma animacao termina ela na hora.
     */
    public record Settings(
            double centerX,
            double centerZ,
            double size,
            double damagePerBlock,
            double damageSafeZone,
            int warningBlocks,
            int warningTime
    ) {
        public static Settings of(WorldBorder border) {
            return new Settings(
                    border.getCenterX(),
                    border.getCenterZ(),
                    border.getSize(),
                    border.getDamagePerBlock(),
                    border.getDamageSafeZone(),
                    border.getWarningBlocks(),
                    border.getWarningTime());
        }

        /** O estado de um mundo que sobe pela primeira vez: centrado na origem, do tamanho da config. */
        public static Settings initial(double size) {
            WorldBorder defaults = new WorldBorder();
            return new Settings(0.0D, 0.0D, size,
                    defaults.getDamagePerBlock(), defaults.getDamageSafeZone(),
                    defaults.getWarningBlocks(), defaults.getWarningTime());
        }

        public void applyTo(WorldBorder border) {
            border.setCenter(centerX, centerZ);
            border.setSize(size);
            border.setDamagePerBlock(damagePerBlock);
            border.setDamageSafeZone(damageSafeZone);
            border.setWarningBlocks(warningBlocks);
            border.setWarningTime(warningTime);
        }

        private static Settings load(CompoundTag tag) {
            WorldBorder defaults = new WorldBorder();
            return new Settings(
                    tag.getDouble("CenterX"),
                    tag.getDouble("CenterZ"),
                    tag.contains("Size") ? tag.getDouble("Size") : defaults.getSize(),
                    tag.contains("DamagePerBlock") ? tag.getDouble("DamagePerBlock") : defaults.getDamagePerBlock(),
                    tag.contains("SafeZone") ? tag.getDouble("SafeZone") : defaults.getDamageSafeZone(),
                    tag.contains("WarningBlocks") ? tag.getInt("WarningBlocks") : defaults.getWarningBlocks(),
                    tag.contains("WarningTime") ? tag.getInt("WarningTime") : defaults.getWarningTime());
        }

        private void save(CompoundTag tag) {
            tag.putDouble("CenterX", centerX);
            tag.putDouble("CenterZ", centerZ);
            tag.putDouble("Size", size);
            tag.putDouble("DamagePerBlock", damagePerBlock);
            tag.putDouble("SafeZone", damageSafeZone);
            tag.putInt("WarningBlocks", warningBlocks);
            tag.putInt("WarningTime", warningTime);
        }
    }
}
