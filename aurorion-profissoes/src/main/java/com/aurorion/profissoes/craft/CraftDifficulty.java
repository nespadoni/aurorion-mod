package com.aurorion.profissoes.craft;

import com.aurorion.core.datapack.DatapackRegistry;
import com.aurorion.profissoes.AurorionProfissoes;
import com.aurorion.profissoes.data.Profession;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PotionItem;
import net.minecraft.world.item.Rarity;

import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Quão difícil é fazer cada item: a CD do teste de ofício, o nó da Trama que ele exige (se algum) e a
 * profissão cujo domínio dá vantagem.
 *
 * <p>Vem de datapack ({@code data/<ns>/aurorion/oficio/*.json}): {@code items}, {@code tags} e
 * {@code mods} apontam para o que a regra cobre; {@code cd}, {@code no} e {@code profissao} dizem o
 * resto. Item exato ganha de tag, que ganha de mod; empate fica com a maior {@code prioridade}. O que
 * nenhuma regra cobre cai na heurística: equipamento 12, comida 8, o resto 2, mais a raridade.
 *
 * <p>Custo: a resposta por item fica num {@link IdentityHashMap}, refeito no {@code /reload} e na troca
 * de tags. O teste lê um mapa.
 */
public final class CraftDifficulty {
    /** O que o teste precisa saber de um item. {@code node} vazio: não exige nó. */
    public record Entry(int dc, String node, Profession domain) {
    }

    public record Rule(ResourceLocation id, List<ResourceLocation> items, List<ResourceLocation> tags, List<String> mods,
                       int dc, Optional<String> node, Optional<String> profession, int priority) {
        static Codec<Rule> codec(ResourceLocation id) {
            return RecordCodecBuilder.create(i -> i.group(
                    ResourceLocation.CODEC.listOf().optionalFieldOf("items", List.of()).forGetter(Rule::items),
                    ResourceLocation.CODEC.listOf().optionalFieldOf("tags", List.of()).forGetter(Rule::tags),
                    Codec.STRING.listOf().optionalFieldOf("mods", List.of()).forGetter(Rule::mods),
                    Codec.intRange(0, 60).fieldOf("cd").forGetter(Rule::dc),
                    Codec.STRING.optionalFieldOf("no").forGetter(Rule::node),
                    Codec.STRING.optionalFieldOf("profissao").forGetter(Rule::profession),
                    Codec.INT.optionalFieldOf("prioridade", 0).forGetter(Rule::priority)
            ).apply(i, (items, tags, mods, dc, node, profession, priority) ->
                    new Rule(id, items, tags, mods, dc, node, profession, priority)));
        }

        Entry entry() {
            Profession domain = Profession.NONE;
            if (profession.isPresent()) {
                try {
                    domain = Profession.parse(profession.get());
                } catch (IllegalArgumentException e) {
                    AurorionProfissoes.LOGGER.warn("Ofício: profissão desconhecida '{}' na regra {}", profession.get(), id);
                }
            }
            return new Entry(dc, node.orElse(""), domain);
        }
    }

    public static final DatapackRegistry<Rule> RULES = new DatapackRegistry<>("aurorion/oficio", AurorionProfissoes.LOGGER,
            Rule::codec, Comparator.comparingInt(Rule::priority).reversed().thenComparing(Rule::id))
            .onReload(rules -> clear());

    /** Escrito e lido só na thread do servidor. */
    private static final Map<Item, Entry> CACHE = new IdentityHashMap<>();

    public static final int TRIVIAL = 2;
    public static final int FOOD = 8;
    public static final int EQUIPMENT = 12;

    private CraftDifficulty() {
    }

    public static Entry of(ItemStack stack) {
        Item item = stack.getItem();
        Entry entry = CACHE.get(item);
        if (entry == null) {
            entry = resolve(stack);
            CACHE.put(item, entry);
        }
        return entry;
    }

    /** Tags novas ({@code /reload}) ou regras novas: as respostas guardadas não valem mais. */
    public static void clear() {
        CACHE.clear();
    }

    private static Entry resolve(ItemStack stack) {
        Item item = stack.getItem();
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
        List<Rule> rules = RULES.all();
        for (Rule rule : rules) if (rule.items().contains(id)) return rule.entry();
        for (Rule rule : rules) {
            for (ResourceLocation tag : rule.tags()) {
                if (stack.is(TagKey.create(Registries.ITEM, tag))) return rule.entry();
            }
        }
        for (Rule rule : rules) if (rule.mods().contains(id.getNamespace())) return rule.entry();
        return heuristic(stack);
    }

    /** Sem regra: o que o próprio item diz de si. Material barato nunca falha na prática (CD 2). */
    static Entry heuristic(ItemStack stack) {
        int dc;
        Profession domain;
        if (stack.isDamageableItem()) {
            dc = EQUIPMENT;
            domain = Profession.SMITH;
        } else if (stack.has(DataComponents.FOOD)) {
            dc = FOOD;
            domain = Profession.CHEF;
        } else if (stack.getItem() instanceof PotionItem) {
            dc = FOOD;
            domain = Profession.DOCTOR;
        } else {
            dc = TRIVIAL;
            domain = Profession.NONE;
        }
        // Rarity é enum extensível no NeoForge: raridade de mod cai no padrão, sem MatchException.
        Rarity rarity = stack.getRarity();
        dc += rarity == Rarity.UNCOMMON ? 2 : rarity == Rarity.RARE ? 4 : rarity == Rarity.EPIC ? 7 : 0;
        return new Entry(dc, "", domain);
    }
}
