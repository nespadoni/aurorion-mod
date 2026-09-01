package com.aurorion.vidas.lives;

import com.aurorion.core.data.PlayerMapNbt;
import com.aurorion.core.data.SavedDataAccess;
import com.aurorion.vidas.AurorionVidas;
import com.aurorion.vidas.config.LivesConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Vidas por UUID e o ponto de chegada do exilio, persistidos no {@code data/} do overworld.
 *
 * <p>Quem nunca morreu <b>nao tem entrada</b> no mapa: a leitura devolve o maximo da config. Isso
 * evita gravar uma linha por jogador que ja passou pelo servidor — num mundo com anos de historico,
 * o arquivo cresceria com o total de visitantes em vez de com o de pessoas que perderam vida.
 *
 * <p>O ponto de exilio mora aqui, e nao na config, porque e uma coordenada de <em>mundo</em>: tem
 * que acompanhar o que foi construido e sumir junto quando o mundo for trocado.
 */
public class LivesData extends SavedData {
    private static final String FILE_ID = AurorionVidas.MOD_ID + "_lives";
    private static final String KEY_ENTRIES = "Entries";
    private static final String KEY_LIVES = "Lives";
    private static final String KEY_EXILE_DIM = "ExileDimension";
    private static final String KEY_EXILE_POS = "ExilePos";

    private static final SavedDataAccess<LivesData> ACCESS =
            new SavedDataAccess<>(FILE_ID, LivesData::new, LivesData::load);

    private final Map<UUID, Integer> lives = new HashMap<>();

    @Nullable
    private ResourceKey<Level> exileDimension;
    @Nullable
    private BlockPos exilePos;

    public static LivesData get(MinecraftServer server) {
        return ACCESS.get(server);
    }


    private static LivesData load(CompoundTag tag, HolderLookup.Provider registries) {
        LivesData data = new LivesData();

        PlayerMapNbt.read(tag, KEY_ENTRIES, data.lives, entry -> entry.getInt(KEY_LIVES));

        if (tag.contains(KEY_EXILE_DIM) && tag.contains(KEY_EXILE_POS)) {
            ResourceLocation id = ResourceLocation.tryParse(tag.getString(KEY_EXILE_DIM));
            if (id != null) {
                data.exileDimension = ResourceKey.create(Registries.DIMENSION, id);
                data.exilePos = NbtUtils.readBlockPos(tag, KEY_EXILE_POS).orElse(null);
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.put(KEY_ENTRIES, PlayerMapNbt.write(lives, (entry, count) -> entry.putInt(KEY_LIVES, count)));

        if (exileDimension != null && exilePos != null) {
            tag.putString(KEY_EXILE_DIM, exileDimension.location().toString());
            tag.put(KEY_EXILE_POS, NbtUtils.writeBlockPos(exilePos));
        }
        return tag;
    }

    // --- Vidas ---------------------------------------------------------------------------------

    /** Nunca acima do maximo atual: baixar {@code maxLives} na config vale para quem ja jogava. */
    public int livesOf(UUID player) {
        int max = LivesConfig.MAX_LIVES.get();
        Integer stored = lives.get(player);
        return stored == null ? max : Math.clamp(stored.intValue(), 0, max);
    }

    /** @return o valor efetivamente gravado, ja limitado ao intervalo valido. */
    public int setLives(UUID player, int value) {
        int max = LivesConfig.MAX_LIVES.get();
        int clamped = Math.clamp(value, 0, max);

        // Vida cheia e representada pela ausencia da entrada. Alem de manter o save proporcional a
        // quem perdeu vida, isso faz um aumento futuro de maxLives valer automaticamente para quem
        // ja tinha sido restaurado ao maximo anterior.
        Integer previous = clamped == max ? lives.remove(player) : lives.put(player, clamped);
        Integer current = clamped == max ? null : clamped;
        if (!Objects.equals(previous, current)) {
            setDirty();
        }
        return clamped;
    }

    public boolean isExiled(UUID player) {
        return livesOf(player) <= 0;
    }

    // --- Ponto de exilio -----------------------------------------------------------------------

    /** @return o ponto marcado, ou {@code null} se ainda nao ha um para a dimensao pedida. */
    @Nullable
    public BlockPos exilePosIn(ResourceKey<Level> dimension) {
        return exileDimension == dimension ? exilePos : null;
    }

    public void setExile(ResourceKey<Level> dimension, BlockPos pos) {
        exileDimension = dimension;
        exilePos = pos;
        setDirty();
    }

    @Nullable
    public ResourceKey<Level> exileDimension() {
        return exileDimension;
    }
}
