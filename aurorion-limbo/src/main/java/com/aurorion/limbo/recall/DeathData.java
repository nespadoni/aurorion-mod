package com.aurorion.limbo.recall;

import com.aurorion.core.data.PlayerMapNbt;
import com.aurorion.core.data.SavedDataAccess;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * A ultima morte de cada personagem: onde foi e qual marca os drops dela carregam.
 *
 * <p>So a <b>ultima</b>. O Fio da Volta e o Relicario falam "daquela morte", e guardar um historico
 * abriria a pergunta "qual delas?" — que ninguem fez e que so serviria para chamar de volta espolio
 * de semanas atras.
 *
 * <p>Nao usamos o {@code lastDeathLocation} do vanilla: ele guarda um bloco, nao a posicao exata, e
 * nao carrega a marca que liga a morte aos itens que ela deixou no chao.
 *
 * <p>A chave e o id do <b>personagem</b> ({@code CharacterData}), nao a conta: uma conta com varios
 * personagens tem uma ultima morte para cada um. O formato em disco usa o {@code PlayerMapNbt}, que
 * so conhece "um UUID por entrada" — o UUID ali e o do personagem.
 */
public final class DeathData extends SavedData {
    private static final SavedDataAccess<DeathData> ACCESS =
            new SavedDataAccess<>("aurorion_limbo_mortes", DeathData::new, DeathData::load);

    /**
     * @param id        o {@code DeathId} do core: marca gravada em cada drop desta morte, e o mesmo
     *                  id do registro no {@code /deathhistory}.
     * @param dimension onde a pessoa morreu.
     * @param position  a posicao exata, com fracao — o Fio leva ao lugar, nao ao bloco.
     * @param diedAt    relogio real, so para a staff ler.
     */
    public record Death(UUID id, ResourceLocation dimension, Vec3 position, long diedAt) {
    }

    private final Map<UUID, Death> deaths = new HashMap<>();

    public static DeathData get(MinecraftServer server) {
        return ACCESS.get(server);
    }

    static DeathData load(CompoundTag tag, HolderLookup.Provider registries) { // visivel para teste
        DeathData data = new DeathData();
        PlayerMapNbt.read(tag, "Deaths", data.deaths, entry -> {
            if (!entry.hasUUID("Id")) return null;
            ResourceLocation dimension = ResourceLocation.tryParse(entry.getString("Dimension"));
            if (dimension == null) return null;
            return new Death(entry.getUUID("Id"), dimension,
                    new Vec3(entry.getDouble("X"), entry.getDouble("Y"), entry.getDouble("Z")),
                    entry.getLong("DiedAt"));
        });
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.put("Deaths", PlayerMapNbt.write(deaths, (entry, death) -> {
            entry.putUUID("Id", death.id());
            entry.putString("Dimension", death.dimension().toString());
            entry.putDouble("X", death.position().x);
            entry.putDouble("Y", death.position().y);
            entry.putDouble("Z", death.position().z);
            entry.putLong("DiedAt", death.diedAt());
        }));
        return tag;
    }

    @Nullable
    public Death last(UUID character) {
        return deaths.get(character);
    }

    /** A morte nova substitui a anterior; os drops antigos ficam sem ninguem que os chame. */
    public void record(UUID character, Death death) {
        deaths.put(character, death);
        setDirty();
    }

    public void clear(UUID character) {
        if (deaths.remove(character) != null) setDirty();
    }
}
