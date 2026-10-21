package com.aurorion.servicos.data;

import com.aurorion.core.data.SavedDataAccess;
import com.aurorion.servicos.AurorionServicos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.UnaryOperator;

/**
 * Anuncios, pedidos e vagas do servidor inteiro, em {@code data/aurorion_servicos.dat}.
 *
 * <p>Tudo e da conta do personagem (o alt e outra conta). O reset por morte apaga o que era do morto
 * ({@link #forget(UUID)}), entao o personagem novo nao herda anuncio, pedido nem vaga.
 *
 * <p>Tamanho: poucos anuncios por pessoa, pedidos que somem em um dia, vagas em duas semanas — as
 * buscas lineares aqui passam por centenas de entradas, nao milhares. Isso deixou de ser so
 * expectativa: pedidos tem cota diaria por conta ({@link #ordersToday}) e teto global
 * ({@link #roomForOrder()}), e "quem oferece qual area" e um indice ({@link #offers}) em vez de uma
 * volta pelos anuncios para cada pedido de cada linha da tela.
 */
public final class ServicosData extends SavedData {
    private static final String FILE_ID = AurorionServicos.MOD_ID;
    private static final int VERSION = 1;

    private static final SavedDataAccess<ServicosData> ACCESS =
            new SavedDataAccess<>(FILE_ID, ServicosData::new, ServicosData::load);

    private final Map<Long, Anuncio> ads = new LinkedHashMap<>();
    private final Map<Long, Pedido> orders = new LinkedHashMap<>();
    private final Map<Long, Vaga> jobs = new LinkedHashMap<>();
    /** Pedidos criados hoje por conta. Gravado: reiniciar o servidor nao devolve a cota. */
    private final Map<UUID, Quota> quotas = new HashMap<>();
    /** Areas anunciadas por dono. Derivado de {@link #ads}; refeito sob demanda quando eles mudam. */
    @Nullable
    private Map<UUID, Set<String>> offerIndex;
    private long nextId = 1;

    private record Quota(long day, int count) { }

    public static ServicosData get(MinecraftServer server) {
        return ACCESS.get(server);
    }

    public long nextId() {
        setDirty();
        return nextId++;
    }

    public Collection<Anuncio> ads() {
        return Collections.unmodifiableCollection(ads.values());
    }

    public Collection<Pedido> orders() {
        return Collections.unmodifiableCollection(orders.values());
    }

    public Collection<Vaga> jobs() {
        return Collections.unmodifiableCollection(jobs.values());
    }

    @Nullable
    public Anuncio ad(long id) {
        return ads.get(id);
    }

    @Nullable
    public Pedido order(long id) {
        return orders.get(id);
    }

    @Nullable
    public Vaga job(long id) {
        return jobs.get(id);
    }

    public void put(Anuncio anuncio) {
        ads.put(anuncio.id(), anuncio);
        offerIndex = null;
        setDirty();
    }

    public void put(Pedido pedido) {
        orders.put(pedido.id(), pedido);
        setDirty();
    }

    public void put(Vaga vaga) {
        jobs.put(vaga.id(), vaga);
        setDirty();
    }

    public void removeAd(long id) {
        if (ads.remove(id) != null) {
            offerIndex = null;
            setDirty();
        }
    }

    public void removeOrder(long id) {
        if (orders.remove(id) != null) setDirty();
    }

    public void removeJob(long id) {
        if (jobs.remove(id) != null) setDirty();
    }

    public List<Anuncio> adsOf(UUID owner) {
        List<Anuncio> result = new ArrayList<>();
        for (Anuncio anuncio : ads.values()) if (anuncio.owner().equals(owner)) result.add(anuncio);
        return result;
    }

    public List<Vaga> jobsOf(UUID owner) {
        List<Vaga> result = new ArrayList<>();
        for (Vaga vaga : jobs.values()) if (vaga.owner().equals(owner)) result.add(vaga);
        return result;
    }

    /** O(1): chamado por pedido em cada linha da tela e em cada selo, nao pode varrer os anuncios. */
    public boolean offers(UUID owner, String category) {
        Map<UUID, Set<String>> index = offerIndex;
        if (index == null) {
            index = new HashMap<>();
            for (Anuncio anuncio : ads.values()) index.computeIfAbsent(anuncio.owner(), key -> new HashSet<>()).add(anuncio.category());
            offerIndex = index;
        }
        Set<String> categories = index.get(owner);
        return categories != null && categories.contains(category);
    }

    /** Pedidos que {@code account} ja criou no dia UTC de {@code now}. */
    public int ordersToday(UUID account, long now) {
        Quota quota = quotas.get(account);
        return quota == null || quota.day() != ServicosRules.dayOf(now) ? 0 : quota.count();
    }

    public void countOrder(UUID account, long now) {
        long day = ServicosRules.dayOf(now);
        quotas.put(account, new Quota(day, ordersToday(account, now) + 1));
        setDirty();
    }

    /**
     * Garante espaco para mais um pedido sob {@link ServicosRules#MAX_ORDERS_TOTAL}, tirando primeiro
     * os encerrados mais antigos. Falso se todos os que sobraram estao em andamento.
     */
    public boolean roomForOrder() {
        if (orders.size() < ServicosRules.MAX_ORDERS_TOTAL) return true;
        List<Pedido> finished = new ArrayList<>();
        for (Pedido pedido : orders.values()) if (pedido.status().finished()) finished.add(pedido);
        finished.sort(java.util.Comparator.comparingLong(Pedido::updatedAt));
        for (int i = 0; i < finished.size() && orders.size() >= ServicosRules.MAX_ORDERS_TOTAL; i++) {
            orders.remove(finished.get(i).id());
            setDirty();
        }
        return orders.size() < ServicosRules.MAX_ORDERS_TOTAL;
    }

    public int openOrdersOf(UUID requester) {
        int count = 0;
        for (Pedido pedido : orders.values()) {
            if (pedido.requester().equals(requester) && pedido.status() == PedidoStatus.ABERTO) count++;
        }
        return count;
    }

    /** Passa o tempo em pedidos, vagas e anuncios. Devolve os pedidos que mudaram de estado. */
    public List<Pedido> age(long now, java.util.function.Predicate<UUID> online) {
        List<Pedido> changed = new ArrayList<>();
        boolean dirty = false;
        for (var it = orders.entrySet().iterator(); it.hasNext(); ) {
            var entry = it.next();
            Pedido pedido = entry.getValue();
            if (ServicosRules.purge(pedido, now)) {
                it.remove();
                dirty = true;
                continue;
            }
            Pedido aged = ServicosRules.age(pedido, now);
            if (aged != pedido) {
                entry.setValue(aged);
                changed.add(aged);
                dirty = true;
            }
        }
        dirty |= jobs.values().removeIf(vaga -> ServicosRules.jobExpired(vaga, now));
        if (ads.values().removeIf(anuncio -> !online.test(anuncio.owner()) && ServicosRules.adExpired(anuncio, now))) {
            offerIndex = null;
            dirty = true;
        }
        long today = ServicosRules.dayOf(now);
        dirty |= quotas.values().removeIf(quota -> quota.day() != today);
        if (dirty) setDirty();
        return changed;
    }

    /** O dono entrou: os anuncios dele continuam valendo. */
    public void touch(UUID owner, long now) {
        replaceAll(ads, anuncio -> anuncio.owner().equals(owner) ? anuncio.seen(now) : anuncio);
    }

    /** O personagem morreu de vez: nada dele fica no app, nem a candidatura em vaga dos outros. */
    public void forget(UUID account) {
        boolean dirty = ads.values().removeIf(anuncio -> anuncio.owner().equals(account));
        offerIndex = null;
        dirty |= orders.values().removeIf(pedido -> pedido.involves(account));
        dirty |= jobs.values().removeIf(vaga -> vaga.owner().equals(account));
        for (var entry : jobs.entrySet()) {
            Vaga without = entry.getValue().withoutCandidate(account);
            if (without != entry.getValue()) {
                entry.setValue(without);
                dirty = true;
            }
        }
        if (dirty) setDirty();
    }

    private <T> void replaceAll(Map<Long, T> map, UnaryOperator<T> change) {
        if (map == ads) offerIndex = null;
        boolean dirty = false;
        for (var entry : map.entrySet()) {
            T next = change.apply(entry.getValue());
            if (next != entry.getValue()) {
                entry.setValue(next);
                dirty = true;
            }
        }
        if (dirty) setDirty();
    }

    // ---- NBT -------------------------------------------------------------------------------------

    private static ServicosData load(CompoundTag tag, HolderLookup.Provider registries) {
        ServicosData data = new ServicosData();
        data.nextId = Math.max(1, tag.getLong("NextId"));
        for (Tag entry : tag.getList("Ads", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) entry;
            Anuncio anuncio = new Anuncio(c.getLong("Id"), c.getUUID("Owner"), c.getString("Category"),
                    c.getString("Title"), c.getString("Description"), c.getString("Price"),
                    c.getLong("Created"), c.getLong("Seen"));
            data.ads.put(anuncio.id(), anuncio);
        }
        for (Tag entry : tag.getList("Orders", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) entry;
            Pedido pedido = new Pedido(c.getLong("Id"), c.getUUID("Requester"), c.getString("Category"),
                    c.getString("Text"), c.hasUUID("Target") ? c.getUUID("Target") : null, c.getLong("Ad"),
                    PedidoStatus.parse(c.getString("Status")), c.hasUUID("Provider") ? c.getUUID("Provider") : null,
                    c.getLong("Created"), c.getLong("Updated"));
            data.orders.put(pedido.id(), pedido);
        }
        for (Tag entry : tag.getList("Jobs", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) entry;
            List<UUID> candidates = new ArrayList<>();
            for (Tag candidate : c.getList("Candidates", Tag.TAG_INT_ARRAY)) candidates.add(NbtUtils.loadUUID(candidate));
            Vaga vaga = new Vaga(c.getLong("Id"), c.getUUID("Owner"), c.getString("Category"), c.getString("Title"),
                    c.getString("Description"), c.getString("Salary"), c.getLong("Created"), candidates);
            data.jobs.put(vaga.id(), vaga);
        }
        long today = ServicosRules.dayOf(System.currentTimeMillis());
        for (Tag entry : tag.getList("Quotas", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) entry;
            if (c.hasUUID("Account") && c.getLong("Day") == today)
                data.quotas.put(c.getUUID("Account"), new Quota(today, Math.max(0, c.getInt("Count"))));
        }
        for (long id : data.ads.keySet()) data.nextId = Math.max(data.nextId, id + 1);
        for (long id : data.orders.keySet()) data.nextId = Math.max(data.nextId, id + 1);
        for (long id : data.jobs.keySet()) data.nextId = Math.max(data.nextId, id + 1);
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("Version", VERSION);
        tag.putLong("NextId", nextId);

        ListTag adList = new ListTag();
        for (Anuncio anuncio : ads.values()) {
            CompoundTag c = new CompoundTag();
            c.putLong("Id", anuncio.id());
            c.putUUID("Owner", anuncio.owner());
            c.putString("Category", anuncio.category());
            c.putString("Title", anuncio.title());
            c.putString("Description", anuncio.description());
            c.putString("Price", anuncio.price());
            c.putLong("Created", anuncio.createdAt());
            c.putLong("Seen", anuncio.seenAt());
            adList.add(c);
        }
        tag.put("Ads", adList);

        ListTag orderList = new ListTag();
        for (Pedido pedido : orders.values()) {
            CompoundTag c = new CompoundTag();
            c.putLong("Id", pedido.id());
            c.putUUID("Requester", pedido.requester());
            c.putString("Category", pedido.category());
            c.putString("Text", pedido.text());
            if (pedido.target() != null) c.putUUID("Target", pedido.target());
            c.putLong("Ad", pedido.adId());
            c.putString("Status", pedido.status().name());
            if (pedido.provider() != null) c.putUUID("Provider", pedido.provider());
            c.putLong("Created", pedido.createdAt());
            c.putLong("Updated", pedido.updatedAt());
            orderList.add(c);
        }
        tag.put("Orders", orderList);

        ListTag jobList = new ListTag();
        for (Vaga vaga : jobs.values()) {
            CompoundTag c = new CompoundTag();
            c.putLong("Id", vaga.id());
            c.putUUID("Owner", vaga.owner());
            c.putString("Category", vaga.category());
            c.putString("Title", vaga.title());
            c.putString("Description", vaga.description());
            c.putString("Salary", vaga.salary());
            c.putLong("Created", vaga.createdAt());
            ListTag candidates = new ListTag();
            for (UUID candidate : vaga.candidates()) candidates.add(NbtUtils.createUUID(candidate));
            c.put("Candidates", candidates);
            jobList.add(c);
        }
        tag.put("Jobs", jobList);

        ListTag quotaList = new ListTag();
        quotas.forEach((account, quota) -> {
            CompoundTag c = new CompoundTag();
            c.putUUID("Account", account);
            c.putLong("Day", quota.day());
            c.putInt("Count", quota.count());
            quotaList.add(c);
        });
        tag.put("Quotas", quotaList);
        return tag;
    }
}
