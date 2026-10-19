package com.aurorion.profissoes.server;

import com.aurorion.profissoes.AurorionProfissoes;
import com.aurorion.profissoes.api.ProfessionApi;
import com.aurorion.profissoes.config.ProfessionsConfig;
import com.aurorion.profissoes.data.Profession;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * "Isto aqui e de oficio": blocos, itens e receitas que so uma profissao usa.
 *
 * <p>E a peca reutilizavel por tras do "Apotheosis so para arcanista", e serve igual para qualquer mod
 * que entre no pack depois. Tres portas, nenhuma com uma linha de codigo por mod:
 *
 * <ul>
 *   <li><b>Bloco</b>: tag {@code aurorion_profissoes:exclusivo/<profissao>}. Clicar com o direito num
 *       bloco da tag (mesa, altar, maquina) e recusado para quem nao e daquele oficio.</li>
 *   <li><b>Item</b>: tag de item com o mesmo nome. Usar o item (botao direito) e recusado.</li>
 *   <li><b>Receita da mesa de ferraria</b>: config {@code receitasExclusivas}. A mesa ja e do
 *       ferreiro; uma regra aqui passa uma familia de receitas para outro oficio — o encaixe de gemas e
 *       os sigilos do Apotheosis vao para o arcanista, e o upgrade de equipamento continua do ferreiro.</li>
 * </ul>
 *
 * <p>Custo: as tags sao {@code TagKey} criadas uma vez; conferir um bloco e um teste de tag por
 * profissao (cinco), so quando alguem clica. As regras de receita sao relidas so quando a config muda.
 */
public final class ExclusiveUse {
    private static final Map<Profession, TagKey<Block>> BLOCKS = new EnumMap<>(Profession.class);
    private static final Map<Profession, TagKey<Item>> ITEMS = new EnumMap<>(Profession.class);

    /** Ultima lista lida da config; trocar a lista (reload de config) reconstroi os indices. */
    private static List<? extends String> parsedFrom;
    private static Map<String, Profession> byType = Map.of();
    private static Map<String, Profession> byIdNamespace = Map.of();
    private static Map<ResourceLocation, Profession> byId = Map.of();
    private static Set<Profession> smithingOwners = EnumSet.noneOf(Profession.class);

    static {
        for (Profession profession : Profession.values()) {
            if (profession == Profession.NONE) continue;
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath(AurorionProfissoes.MOD_ID, "exclusivo/" + profession.id());
            BLOCKS.put(profession, TagKey.create(Registries.BLOCK, id));
            ITEMS.put(profession, TagKey.create(Registries.ITEM, id));
        }
    }

    private ExclusiveUse() {}

    /** O oficio dono deste bloco, ou {@code null} se qualquer um usa. */
    @Nullable
    public static Profession owner(BlockState state) {
        for (Map.Entry<Profession, TagKey<Block>> entry : BLOCKS.entrySet()) {
            if (state.is(entry.getValue())) return entry.getKey();
        }
        return null;
    }

    @Nullable
    public static Profession owner(ItemStack stack) {
        if (stack.isEmpty()) return null;
        for (Map.Entry<Profession, TagKey<Item>> entry : ITEMS.entrySet()) {
            if (stack.is(entry.getValue())) return entry.getKey();
        }
        return null;
    }

    /**
     * O oficio dono da receita, pela ordem: id exato, mod do id da receita ({@code id:<mod>}), mod do
     * serializer da receita ({@code <mod>}; o {@code getType()} e {@code minecraft:smithing} em todas da mesa,
     * entao e o serializer que diz de qual mod ela e). {@code null} quando nenhuma regra fala dela.
     */
    @Nullable
    public static Profession owner(RecipeHolder<? extends Recipe<?>> holder) {
        refresh();
        Profession exact = byId.get(holder.id());
        if (exact != null) return exact;
        Profession fromId = byIdNamespace.get(holder.id().getNamespace());
        if (fromId != null) return fromId;
        ResourceLocation type = BuiltInRegistries.RECIPE_SERIALIZER.getKey(holder.value().getSerializer());
        return type == null ? null : byType.get(type.getNamespace());
    }

    /** Este oficio tem alguma familia de receita da mesa de ferraria? Entao ele pode abrir a mesa. */
    public static boolean ownsSmithingRecipes(Player player) {
        refresh();
        if (smithingOwners.isEmpty()) return false;
        Profession profession = ProfessionApi.of(player);
        return profession != Profession.NONE && smithingOwners.contains(profession);
    }

    /** Pode usar o que e de {@code owner}: o proprio oficio, a staff criativa, ou tudo com o mod desligado. */
    public static boolean allowed(Player player, Profession owner) {
        return !ProfessionsConfig.enabled() || ProfessionApi.staff(player) || ProfessionApi.has(player, owner);
    }

    /**
     * Receita da mesa de ferraria: com regra, vale o oficio da regra; sem regra, a mesa e do ferreiro.
     */
    public static boolean maySmith(Player player, @Nullable RecipeHolder<? extends Recipe<?>> recipe) {
        if (recipe == null) return SpecialtyRules.smith(player);
        Profession owner = owner(recipe);
        return owner == null ? SpecialtyRules.smith(player) : allowed(player, owner);
    }

    private static void refresh() {
        List<? extends String> raw = ProfessionsConfig.exclusiveRecipes();
        if (raw == parsedFrom) return;
        Map<String, Profession> type = new HashMap<>(), idNamespace = new HashMap<>();
        Map<ResourceLocation, Profession> exact = new HashMap<>();
        Set<Profession> owners = EnumSet.noneOf(Profession.class);
        for (String entry : raw) {
            int split = entry.lastIndexOf('=');
            if (split <= 0) continue;
            String key = entry.substring(0, split).trim();
            Profession profession;
            try {
                profession = Profession.parse(entry.substring(split + 1).trim());
            } catch (IllegalArgumentException invalid) {
                AurorionProfissoes.LOGGER.warn("receitasExclusivas: '{}' ignorada ({})", entry, invalid.getMessage());
                continue;
            }
            if (profession == Profession.NONE) continue;
            if (key.startsWith("id:")) {
                idNamespace.put(key.substring(3), profession);
            } else if (key.indexOf(':') > 0) {
                ResourceLocation id = ResourceLocation.tryParse(key);
                if (id == null) continue;
                exact.put(id, profession);
            } else {
                type.put(key, profession);
            }
            owners.add(profession);
        }
        byType = Map.copyOf(type);
        byIdNamespace = Map.copyOf(idNamespace);
        byId = Map.copyOf(exact);
        smithingOwners = owners;
        parsedFrom = raw;
    }
}
