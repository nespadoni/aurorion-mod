package com.aurorion.essentials.perf;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.internal.Streams;
import com.google.gson.stream.JsonReader;
import com.mojang.serialization.JsonOps;
import net.minecraft.SharedConstants;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Le o arquivo de conquistas de um jogador em tempo linear.
 *
 * <h2>Por que existe</h2>
 *
 * <p>O vanilla decodifica o arquivo com {@code Codec.unboundedMap}, e o DataFixerUpper monta esse mapa
 * num {@code Object2ObjectArrayMap} — uma lista: cada entrada nova e comparada com todas as anteriores.
 * O custo e quadratico no numero de conquistas. Com ~400 mods cada receita e uma conquista, o arquivo
 * de quem joga ha semanas passa de milhares de entradas, e cada login congelava o servidor por um a
 * tres segundos (spark de 25/10/2026: {@code PlayerAdvancements.load} = 6,8% do tempo da thread
 * principal, MSPT maximo de 2,9 s). Como o arquivo so cresce, o servidor ia piorando sozinho.
 *
 * <p>Aqui o mapa e um {@link HashMap}. Cada conquista continua decodificada pelo proprio
 * {@link AdvancementProgress#CODEC}, entao o resultado e o mesmo do vanilla.
 *
 * <h2>Quando devolve {@code null}</h2>
 *
 * <p>Sempre que o caminho vanilla tem algo a fazer que este nao faz: arquivo de versao antiga (o
 * DataFixer precisa converter), formato inesperado, id invalido, conquista que nao decodifica, erro de
 * leitura. Nesses casos o vanilla le o arquivo como sempre leu, inclusive registrando o erro no log.
 */
public final class AdvancementFile {
    private static final String DATA_VERSION = "DataVersion";

    private AdvancementFile() {
    }

    @Nullable
    public static Map<ResourceLocation, AdvancementProgress> read(Path file) {
        if (!Files.isRegularFile(file)) return null;
        JsonElement root;
        try (JsonReader reader = new JsonReader(Files.newBufferedReader(file, StandardCharsets.UTF_8))) {
            reader.setLenient(false);
            root = Streams.parse(reader);
        } catch (IOException | RuntimeException failure) {
            return null;
        }
        if (!root.isJsonObject()) return null;
        JsonObject object = root.getAsJsonObject();

        // Mesma versao do jogo: o DataFixer nao faria nada. Versao diferente ou ausente: vanilla converte.
        JsonElement version = object.get(DATA_VERSION);
        if (version == null || !version.isJsonPrimitive() || !version.getAsJsonPrimitive().isNumber()
                || version.getAsInt() != SharedConstants.getCurrentVersion().getDataVersion().getVersion()) {
            return null;
        }

        Map<ResourceLocation, AdvancementProgress> progress = new HashMap<>(object.size() * 2);
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            if (entry.getKey().equals(DATA_VERSION)) continue;
            ResourceLocation id = ResourceLocation.tryParse(entry.getKey());
            if (id == null) return null;
            Optional<AdvancementProgress> decoded = AdvancementProgress.CODEC.parse(JsonOps.INSTANCE, entry.getValue()).result();
            if (decoded.isEmpty()) return null;
            progress.put(id, decoded.get());
        }
        return progress;
    }
}
