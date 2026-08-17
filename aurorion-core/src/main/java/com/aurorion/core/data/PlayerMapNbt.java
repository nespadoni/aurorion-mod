package com.aurorion.core.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Leitura e escrita do formato "lista de entradas por jogador" em NBT.
 *
 * <p>Quatro mods do ecossistema guardam um {@code Map<UUID, ?>} em disco — nome falso, casa, passes
 * de viagem, vidas — e os quatro escreviam a mesma lista de {@link CompoundTag} com um UUID e um
 * valor. Muda so o valor; o esqueleto e sempre este.
 *
 * <p>A escolha de gravar como <b>lista</b>, e nao como mapa com o UUID de chave, e o que o vanilla
 * faz e o que sobrevive a um UUID invalido: uma entrada corrompida e pulada em vez de derrubar a
 * leitura do arquivo inteiro.
 */
public final class PlayerMapNbt {
    /** Nome do campo de UUID dentro de cada entrada. */
    public static final String KEY_PLAYER = "Player";

    private PlayerMapNbt() {
    }

    /**
     * @param writer preenche o resto da entrada; o UUID ja foi escrito quando ele e chamado.
     */
    public static <V> ListTag write(Map<UUID, V> entries, BiConsumer<CompoundTag, V> writer) {
        ListTag list = new ListTag();

        entries.forEach((player, value) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID(KEY_PLAYER, player);
            writer.accept(entry, value);
            list.add(entry);
        });
        return list;
    }

    /**
     * Le as entradas para dentro de {@code out}.
     *
     * @param reader devolve o valor da entrada, ou {@code null} para descartar essa linha — e assim
     *               que se ignora um dado que nao faz mais sentido (id de dimensao que nao parseia,
     *               por exemplo) sem perder o resto do arquivo.
     */
    public static <V> void read(CompoundTag tag, String listKey, Map<UUID, V> out,
                                Function<CompoundTag, @Nullable V> reader) {
        ListTag list = tag.getList(listKey, Tag.TAG_COMPOUND);

        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            if (!entry.hasUUID(KEY_PLAYER)) continue;

            V value = reader.apply(entry);
            if (value != null) {
                out.put(entry.getUUID(KEY_PLAYER), value);
            }
        }
    }
}
