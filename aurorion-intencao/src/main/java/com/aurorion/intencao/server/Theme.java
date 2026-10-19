package com.aurorion.intencao.server;

import com.aurorion.core.datapack.DatapackRegistry;
import com.aurorion.intencao.AurorionIntencao;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Um tema do Plano da Intencao, lido de {@code data/<ns>/aurorion/intencao_tema/<id>.json}.
 *
 * <p>Tudo e opcional menos o arquivo existir: um JSON vazio ({@code {}}) ja e um tema valido, com a
 * arena procedural de vitrais e o som da Vale das Almas. Os builders trocam o lugar apontando
 * {@code structure} para um {@code .nbt} salvo com o Structure Block — sem mexer em codigo.
 *
 * <p>Os textos aceitam chave de traducao ou texto direto: {@code Component.translatable} cai no
 * proprio texto quando a chave nao existe.
 *
 * @param structure    estrutura colocada com o chao (camada y=0 do .nbt) na altura dos pes; vazio
 *                     usa a arena procedural de vitrais, tingida com as cores das Casas
 * @param radius       raio jogavel; quem passa dele volta ao seu lugar
 * @param spawnRadius  distancia do centro onde cada participante aparece, de frente para o centro
 * @param fog          cor do ar no plano
 * @param ambience     som em laco enquanto se esta no plano
 * @param whisper      sussurro espacado, e o som do fragmento tocado
 * @param fragments    quantos fragmentos de memoria flutuam na arena
 * @param phrases      o que os fragmentos dizem; vazio usa as frases padrao do mod
 */
public record Theme(ResourceLocation id, String name, Optional<ResourceLocation> structure, int radius,
                    int spawnRadius, int fog, ResourceLocation ambience, ResourceLocation whisper,
                    int fragments, List<String> phrases, String call, String win, String lose, String draw) {

    public static final ResourceLocation DEFAULT = AurorionIntencao.id("tribunal");
    private static final int DEFAULT_FRAGMENT_PHRASES = 8;

    public static final DatapackRegistry<Theme> THEMES = new DatapackRegistry<>("aurorion/intencao_tema",
            AurorionIntencao.LOGGER, Theme::codec, Comparator.comparing(theme -> theme.id().toString()));

    /**
     * Texto que vai na rede: o pacote da cena aceita ate 1024 caracteres por campo, e estourar um teto de
     * codec derruba a conexao. Aqui o tema invalido falha na carga, com log, e nunca chega a um jogador.
     */
    private static final Codec<String> TEXT = Codec.sizeLimitedString(1024);
    /** Id de som: vai no mesmo pacote, com teto de 256. */
    private static final Codec<ResourceLocation> SOUND = ResourceLocation.CODEC.validate(id -> id.toString().length() <= 256
            ? DataResult.success(id) : DataResult.error(() -> "Id de som com mais de 256 caracteres: " + id));
    /** Tema so com os valores padrao, para quando nenhum datapack carregou o do mod. */
    private static volatile Theme fallback;

    /** Aceita "#RRGGBB" ou "RRGGBB", como as cores das Casas. */
    static final Codec<Integer> COLOR = Codec.STRING.comapFlatMap(raw -> {
        String hex = raw.startsWith("#") ? raw.substring(1) : raw;
        if (hex.length() != 6) return DataResult.error(() -> "Cor precisa ter 6 digitos (RRGGBB): " + raw);
        try {
            return DataResult.success(Integer.parseInt(hex, 16));
        } catch (NumberFormatException e) {
            return DataResult.error(() -> "Cor hexadecimal invalida: " + raw);
        }
    }, color -> String.format("#%06X", color & 0xFFFFFF));

    public static Codec<Theme> codec(ResourceLocation id) {
        return RecordCodecBuilder.create(instance -> instance.group(
                TEXT.optionalFieldOf("name", "aurorion_intencao.tema." + id.getPath()).forGetter(Theme::name),
                ResourceLocation.CODEC.optionalFieldOf("structure").forGetter(Theme::structure),
                Codec.intRange(6, 48).optionalFieldOf("radius", 14).forGetter(Theme::radius),
                Codec.intRange(2, 40).optionalFieldOf("spawn_radius", 6).forGetter(Theme::spawnRadius),
                COLOR.optionalFieldOf("fog", 0x23203A).forGetter(Theme::fog),
                SOUND.optionalFieldOf("ambience",
                        ResourceLocation.withDefaultNamespace("ambient.soul_sand_valley.loop")).forGetter(Theme::ambience),
                SOUND.optionalFieldOf("whisper",
                        ResourceLocation.withDefaultNamespace("ambient.soul_sand_valley.mood")).forGetter(Theme::whisper),
                Codec.intRange(0, 24).optionalFieldOf("fragments", 7).forGetter(Theme::fragments),
                TEXT.listOf().optionalFieldOf("phrases", List.of()).forGetter(Theme::phrases),
                TEXT.optionalFieldOf("call", "aurorion_intencao.chamado").forGetter(Theme::call),
                TEXT.optionalFieldOf("win", "aurorion_intencao.veredito.vitoria").forGetter(Theme::win),
                TEXT.optionalFieldOf("lose", "aurorion_intencao.veredito.derrota").forGetter(Theme::lose),
                TEXT.optionalFieldOf("draw", "aurorion_intencao.veredito.empate").forGetter(Theme::draw)
        ).apply(instance, (name, structure, radius, spawnRadius, fog, ambience, whisper, fragments, phrases,
                           call, win, lose, draw) -> new Theme(id, name, structure, radius,
                Math.min(spawnRadius, radius - 1), fog, ambience, whisper, fragments, phrases, call, win, lose, draw)));
    }

    /** O tema, ou o padrao se o id sumiu num /reload; sem nenhum, um tema so com os valores padrao. */
    public static Theme resolve(ResourceLocation id) {
        Theme theme = THEMES.get(id);
        if (theme == null) theme = THEMES.get(DEFAULT);
        if (theme != null) return theme;
        Theme defaults = fallback;
        if (defaults == null) {
            defaults = codec(DEFAULT).parse(com.mojang.serialization.JsonOps.INSTANCE,
                    new com.google.gson.JsonObject()).getOrThrow();
            fallback = defaults;
        }
        return defaults;
    }

    /** Uma frase de fragmento, sorteada pelo chamador. */
    public String phrase(int roll) {
        if (!phrases.isEmpty()) return phrases.get(Math.floorMod(roll, phrases.size()));
        return "aurorion_intencao.fragmento." + (Math.floorMod(roll, DEFAULT_FRAGMENT_PHRASES) + 1);
    }
}
