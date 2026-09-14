package com.aurorion.ethereal.ceremony;

import com.aurorion.core.data.PlayerMapNbt;
import com.aurorion.core.data.SavedDataAccess;
import com.aurorion.ethereal.AurorionEthereal;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * O que sobrevive a um restart entre o fim de uma cerimonia e a decisao da staff.
 *
 * <p>Sem isto, o fluxo mediado por admin so funcionaria com staff online no exato momento em que o
 * jogador termina de responder. Num servidor de 80 pessoas isso e o caso raro, nao o comum: alguem
 * termina a cerimonia as tres da manha e a decisao sai no dia seguinte. Duas coisas ficam gravadas:
 *
 * <ul>
 *   <li><b>Vereditos</b>: cerimonia terminada, esperando {@code /casa cerimonia confirmar}.</li>
 *   <li><b>Revelacoes pendentes</b>: casa ja confirmada com o jogador offline. A revelacao toca no
 *       proximo login dele — a cerimonia dele nao pode terminar num anuncio no chat que ele nem viu.
 *       </li>
 * </ul>
 */
public final class CeremonyData extends SavedData {
    private static final String FILE_ID = AurorionEthereal.MOD_ID + "_ceremonies";
    private static final String KEY_VERDICTS = "Verdicts";
    private static final String KEY_REVEALS = "Reveals";
    private static final String KEY_NAME = "Name";
    private static final String KEY_SUGGESTED = "Suggested";
    private static final String KEY_AT = "At";
    private static final String KEY_TALLY = "Tally";
    private static final String KEY_SUMMARY = "Summary";
    private static final String KEY_HOUSE = "House";
    private static final String KEY_POINTS = "Points";

    private static final SavedDataAccess<CeremonyData> ACCESS =
            new SavedDataAccess<>(FILE_ID, CeremonyData::new, CeremonyData::load);

    /**
     * Cerimonia terminada, esperando decisao.
     *
     * @param tally   pontos por casa; a ordem de insercao ja e a de exibicao (maior primeiro).
     * @param summary uma linha por resposta, como a staff vai ler.
     */
    public record Verdict(String playerName,
                          Map<ResourceLocation, Integer> tally,
                          List<String> summary,
                          @Nullable ResourceLocation suggested,
                          long finishedAt) {
    }

    private final Map<UUID, Verdict> verdicts = new LinkedHashMap<>();
    private final Map<UUID, ResourceLocation> reveals = new HashMap<>();

    public static CeremonyData get(MinecraftServer server) {
        return ACCESS.get(server);
    }

    private static CeremonyData load(CompoundTag tag, HolderLookup.Provider registries) {
        CeremonyData data = new CeremonyData();

        PlayerMapNbt.read(tag, KEY_VERDICTS, data.verdicts, entry -> {
            Map<ResourceLocation, Integer> tally = new LinkedHashMap<>();
            ListTag tallyTag = entry.getList(KEY_TALLY, Tag.TAG_COMPOUND);
            for (int i = 0; i < tallyTag.size(); i++) {
                CompoundTag row = tallyTag.getCompound(i);
                ResourceLocation house = ResourceLocation.tryParse(row.getString(KEY_HOUSE));
                if (house != null) {
                    tally.put(house, row.getInt(KEY_POINTS));
                }
            }

            List<String> summary = new ArrayList<>();
            ListTag summaryTag = entry.getList(KEY_SUMMARY, Tag.TAG_STRING);
            for (int i = 0; i < summaryTag.size(); i++) {
                summary.add(summaryTag.getString(i));
            }

            return new Verdict(entry.getString(KEY_NAME), tally, List.copyOf(summary),
                    ResourceLocation.tryParse(entry.getString(KEY_SUGGESTED)), entry.getLong(KEY_AT));
        });

        PlayerMapNbt.read(tag, KEY_REVEALS, data.reveals,
                entry -> ResourceLocation.tryParse(entry.getString(KEY_HOUSE)));
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.put(KEY_VERDICTS, PlayerMapNbt.write(verdicts, (entry, verdict) -> {
            entry.putString(KEY_NAME, verdict.playerName());
            entry.putLong(KEY_AT, verdict.finishedAt());
            if (verdict.suggested() != null) {
                entry.putString(KEY_SUGGESTED, verdict.suggested().toString());
            }

            ListTag tallyTag = new ListTag();
            verdict.tally().forEach((house, points) -> {
                CompoundTag row = new CompoundTag();
                row.putString(KEY_HOUSE, house.toString());
                row.putInt(KEY_POINTS, points);
                tallyTag.add(row);
            });
            entry.put(KEY_TALLY, tallyTag);

            ListTag summaryTag = new ListTag();
            verdict.summary().forEach(line -> summaryTag.add(StringTag.valueOf(line)));
            entry.put(KEY_SUMMARY, summaryTag);
        }));

        tag.put(KEY_REVEALS, PlayerMapNbt.write(reveals,
                (entry, house) -> entry.putString(KEY_HOUSE, house.toString())));
        return tag;
    }

    // --- Vereditos ---

    public void putVerdict(UUID player, Verdict verdict) {
        verdicts.put(player, verdict);
        setDirty();
    }

    @Nullable
    public Verdict verdict(UUID player) {
        return verdicts.get(player);
    }

    /** So leitura: quem muda o mapa e {@link #putVerdict} e {@link #removeVerdict}. */
    public Map<UUID, Verdict> verdicts() {
        return Collections.unmodifiableMap(verdicts);
    }

    public boolean removeVerdict(UUID player) {
        if (verdicts.remove(player) == null) {
            return false;
        }
        setDirty();
        return true;
    }

    // --- Revelacoes pendentes ---

    public void queueReveal(UUID player, ResourceLocation house) {
        reveals.put(player, house);
        setDirty();
    }

    /** Tira a revelacao da fila e devolve. Chamada uma vez, no login. */
    @Nullable
    public ResourceLocation takeReveal(UUID player) {
        ResourceLocation house = reveals.remove(player);
        if (house != null) {
            setDirty();
        }
        return house;
    }
}
