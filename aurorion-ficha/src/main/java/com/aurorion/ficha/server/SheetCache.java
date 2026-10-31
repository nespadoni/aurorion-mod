package com.aurorion.ficha.server;

import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.LongSupplier;

/**
 * A última ficha lida do site, por perfil, por alguns segundos: abrir e fechar a tela várias vezes
 * não vira uma chamada por abertura.
 *
 * <p>A entrada também guarda o personagem do mod que a pediu — renascer com outro personagem no
 * mesmo perfil não reaproveita a ficha velha. Quem muda a ficha (rolar, vincular, sair) chama
 * {@link #invalidate}. {@link #ageMs} diz há quanto tempo a cópia veio do site, para quem quiser
 * mostrá-la na hora e conferir a versão em segundo plano.
 *
 * <p>Memória: o servidor tira a entrada de quem sai, e a vencida sai ao ser lida; o teto
 * ({@code maxEntries}, o mais antigo sai primeiro) é a rede de segurança se alguma saída escapar.
 * Só a thread do servidor mexe aqui; sem dependência do Minecraft (testável).
 */
public final class SheetCache<V> {
    /** Mais que o dobro de um servidor cheio; cada entrada é uma ficha já cortada nos tetos do modelo. */
    public static final int DEFAULT_MAX_ENTRIES = 512;

    private record Entry<V>(V value, @Nullable String characterId, long storedAt) {
    }

    private final Map<UUID, Entry<V>> entries;
    private final LongSupplier clock;

    public SheetCache(LongSupplier clock) {
        this(clock, DEFAULT_MAX_ENTRIES);
    }

    public SheetCache(LongSupplier clock, int maxEntries) {
        this.clock = clock;
        int max = Math.max(1, maxEntries);
        this.entries = new LinkedHashMap<>(32, 0.75f, false) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<UUID, Entry<V>> eldest) {
                return size() > max;
            }
        };
    }

    /** A cópia guardada se ainda vale ({@code ttlMs} ≤ 0 desliga o cache). */
    @Nullable
    public V get(UUID profile, @Nullable String characterId, long ttlMs) {
        Entry<V> entry = entries.get(profile);
        if (entry == null || ttlMs <= 0) return null;
        if (clock.getAsLong() - entry.storedAt() > ttlMs || !Objects.equals(entry.characterId(), characterId)) {
            entries.remove(profile);
            return null;
        }
        return entry.value();
    }

    /** Há quanto tempo a cópia do perfil veio do site; {@code -1} sem cópia. Não confere validade. */
    public long ageMs(UUID profile) {
        Entry<V> entry = entries.get(profile);
        return entry == null ? -1L : Math.max(0L, clock.getAsLong() - entry.storedAt());
    }

    public void put(UUID profile, @Nullable String characterId, V value) {
        // remove antes: a entrada renovada vai para o fim da fila do teto, não fica na posição velha.
        entries.remove(profile);
        entries.put(profile, new Entry<>(value, characterId, clock.getAsLong()));
    }

    public void invalidate(UUID profile) {
        entries.remove(profile);
    }

    public void clear() {
        entries.clear();
    }

    public int size() {
        return entries.size();
    }
}
