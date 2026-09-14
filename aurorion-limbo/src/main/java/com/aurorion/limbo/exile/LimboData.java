package com.aurorion.limbo.exile;

import com.aurorion.core.data.PlayerMapNbt;
import com.aurorion.core.data.SavedDataAccess;
import com.aurorion.limbo.AurorionLimbo;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Quem esta no Limbo agora, e quantas vezes cada um ja saiu pela Porta do Esquecido.
 *
 * <p>Duas coisas com tempos de vida bem diferentes no mesmo arquivo, de proposito:
 *
 * <ul>
 *   <li><b>{@code active}</b> e efemero e minusculo — some quando a pessoa sai. Quase sempre vazio,
 *       e no pior dia do servidor tem dezenas de entradas.</li>
 *   <li><b>{@code forgottenExits}</b> e permanente. E a memoria que da sentido a Porta: sair por ela
 *       uma vez e azar, sair tres vezes e um personagem que o servidor inteiro decidiu ignorar — e
 *       e exatamente dai que sai o RP de vilao.</li>
 * </ul>
 *
 * <p>Como no {@code aurorion-vidas}, quem nunca esteve no Limbo nao ocupa linha: o arquivo cresce
 * com quem caiu, nao com quem visitou o servidor.
 */
public class LimboData extends SavedData {
    private static final String FILE_ID = AurorionLimbo.MOD_ID + "_exiles";
    private static final String KEY_ACTIVE = "Active";
    private static final String KEY_FORGOTTEN = "Forgotten";
    private static final String KEY_COUNT = "Count";

    private static final SavedDataAccess<LimboData> ACCESS =
            new SavedDataAccess<>(FILE_ID, LimboData::new, LimboData::load);

    private final Map<UUID, ExileRecord> active = new HashMap<>();
    private final Map<UUID, Integer> forgottenExits = new HashMap<>();
    private final Map<UUID, String> forgottenNames = new HashMap<>();
    private final Map<UUID, DoorFrame> pendingDoors = new HashMap<>();

    /**
     * Instante monotônico da ultima varredura, apenas nesta sessao (nao vai para NBT).
     *
     * <p>E daqui que sai o "so conta tempo de servidor de pe": o prazo anda pela diferenca entre
     * duas varreduras. O primeiro passo depois do boot desconta zero; pausas descontam no maximo 5s.
     */
    private long lastTickAt;
    private boolean clockStarted;

    public static LimboData get(MinecraftServer server) {
        return ACCESS.get(server);
    }

    static LimboData load(CompoundTag tag, HolderLookup.Provider registries) {
        LimboData data = new LimboData();

        PlayerMapNbt.read(tag, KEY_ACTIVE, data.active, ExileRecord::read);
        PlayerMapNbt.read(tag, KEY_FORGOTTEN, data.forgottenExits, entry -> entry.getInt(KEY_COUNT));
        PlayerMapNbt.read(tag, "ForgottenNames", data.forgottenNames, entry -> entry.getString("Name"));
        PlayerMapNbt.read(tag, "PendingDoors", data.pendingDoors, DoorFrame::read);
        data.pendingDoors.values().removeIf(java.util.Objects::isNull);

        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.put(KEY_ACTIVE, PlayerMapNbt.write(active, (entry, record) -> record.write(entry)));
        tag.put(KEY_FORGOTTEN, PlayerMapNbt.write(forgottenExits, (entry, count) -> entry.putInt(KEY_COUNT, count)));
        tag.put("ForgottenNames", PlayerMapNbt.write(forgottenNames, (entry, name) -> entry.putString("Name", name)));
        tag.put("PendingDoors", PlayerMapNbt.write(pendingDoors, (entry, frame) -> frame.write(entry)));
        return tag;
    }

    // --- Exilados ativos -----------------------------------------------------------------------

    @Nullable
    public ExileRecord record(UUID player) {
        return active.get(player);
    }

    public boolean isTracked(UUID player) {
        return active.containsKey(player);
    }

    public void open(UUID player, ExileRecord record) {
        active.put(player, record);
        setDirty();
    }

    @Nullable
    public ExileRecord close(UUID player) {
        ExileRecord removed = active.remove(player);
        if (removed != null) {
            setDirty();
        }
        return removed;
    }

    /**
     * A visao do relatorio e da varredura.
     *
     * <p>Devolve o mapa vivo em vez de uma copia: a varredura roda uma vez por segundo, e alocar uma
     * copia por segundo para um mapa quase sempre vazio e o tipo de lixo que a SDD §2 cobra. Quem
     * itera aqui nao pode mexer no mapa durante a iteracao — o {@code LimboManager} recolhe o que
     * vai sair numa lista propria antes de remover.
     */
    public Map<UUID, ExileRecord> active() {
        return active;
    }

    // --- Memoria permanente --------------------------------------------------------------------

    public int forgottenExits(UUID player) {
        return forgottenExits.getOrDefault(player, 0);
    }

    /** Todo mundo que ja saiu pela Porta, para o {@code /limbo esquecidos}. */
    public Map<UUID, Integer> forgotten() {
        return forgottenExits;
    }

    public int addForgottenExit(UUID player) {
        int total = forgottenExits(player) + 1;
        forgottenExits.put(player, total);
        setDirty();
        return total;
    }

    public int addForgottenExit(UUID player, String name) {
        forgottenNames.put(player, name);
        return addForgottenExit(player);
    }

    @Nullable public String forgottenName(UUID player) { return forgottenNames.get(player); }

    public void deferDoor(DoorFrame frame) {
        pendingDoors.put(UUID.randomUUID(), frame);
        setDirty();
    }

    public Map<UUID, DoorFrame> pendingDoors() { return pendingDoors; }

    // --- Relogio -------------------------------------------------------------------------------

    public long elapsed(long monotonicMillis) {
        long elapsed = clockStarted ? Math.clamp(monotonicMillis - lastTickAt, 0L, 5_000L) : 0L;
        lastTickAt = monotonicMillis;
        clockStarted = true;
        return elapsed;
    }

    /** setDirty so agenda o autosave; nao escreve em disco a cada chamada. */
    public void drain(ExileRecord record, long millis) {
        if (millis <= 0 || record.remainingMillis() <= 0) return;
        record.drain(millis);
        setDirty();
    }
}
