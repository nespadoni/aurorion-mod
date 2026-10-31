package com.aurorion.ficha.server;

import com.aurorion.core.character.CharacterSheet;
import com.aurorion.core.data.PlayerMapNbt;
import com.aurorion.core.data.SavedDataAccess;
import com.aurorion.ficha.AurorionFicha;
import com.aurorion.ficha.config.FichaConfig;
import com.aurorion.ficha.sheet.FichaSheet;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * O retrato da ficha que fica: o que o site disse da última vez, em memória e no {@code data/} do
 * overworld, para os testes do jogo (magia, ofício) lerem na hora pelo {@link CharacterSheet}.
 *
 * <p>Por que gravar em disco: a fornalha de quem saiu continua cozinhando com a mão de quem a colocou,
 * e um restart não pode deixar todo mundo "sem ficha" até a próxima leitura. O arquivo cresce com quem
 * tem ficha com construção, não com todo visitante.
 *
 * <p>Ficha sem construção aprovada ({@code has_build=false}) chega sem atributos: não entra, e quem
 * pergunta usa o bônus padrão dele.
 */
public final class SheetStatsStore extends SavedData {
    private static final String FILE_ID = AurorionFicha.MOD_ID + "_stats";
    private static final String KEY_ENTRIES = "Entries";

    private static final SavedDataAccess<SheetStatsStore> ACCESS =
            new SavedDataAccess<>(FILE_ID, SheetStatsStore::new, SheetStatsStore::load);

    private final Map<UUID, CharacterSheet.Stats> stats = new HashMap<>();

    private SheetStatsStore() {
    }

    public static SheetStatsStore get(MinecraftServer server) {
        return ACCESS.get(server);
    }

    /** O provedor do {@link CharacterSheet}: só memória. */
    public static CharacterSheet.Stats of(MinecraftServer server, UUID profile) {
        return get(server).stats.getOrDefault(profile, CharacterSheet.Stats.UNKNOWN);
    }

    /**
     * Uma leitura do site com código {@code ok}: guarda o retrato novo (ou apaga, se a ficha não tem
     * construção). {@code no_sheet}/{@code needs_binding} também apagam: o perfil ficou sem ficha.
     */
    static void record(MinecraftServer server, UUID profile, String code, @Nullable FichaSheet sheet) {
        SheetStatsStore store = get(server);
        if (!"ok".equals(code) && !"no_sheet".equals(code) && !"needs_binding".equals(code)) return;
        CharacterSheet.Stats next = "ok".equals(code) && sheet != null && sheet.hasBuild() ? from(sheet) : null;
        CharacterSheet.Stats previous = next == null ? store.stats.remove(profile) : store.stats.put(profile, next);
        if (!java.util.Objects.equals(previous, next)) store.setDirty();
    }

    /** Personagem novo: o retrato do antigo não vale mais. */
    static void forget(MinecraftServer server, UUID profile) {
        SheetStatsStore store = get(server);
        if (store.stats.remove(profile) != null) store.setDirty();
    }

    int size() {
        return stats.size();
    }

    static CharacterSheet.Stats from(FichaSheet sheet) {
        Map<String, Integer> attributes = new HashMap<>();
        for (FichaSheet.Attribute a : sheet.attributes()) if (!a.id().isEmpty()) attributes.put(a.id(), a.effective());
        Map<String, Integer> skills = new HashMap<>();
        Map<String, Integer> bonuses = new HashMap<>();
        for (FichaSheet.Skill s : sheet.skills()) {
            if (s.id().isEmpty()) continue;
            skills.put(s.id(), s.value());
            bonuses.put(s.id(), s.bonus());
        }
        Map<String, Integer> derived = new HashMap<>();
        FichaSheet.Derived d = sheet.derived();
        if (d != null) {
            derived.put("vida_max", d.vidaMax());
            derived.put("stamina_max", d.staminaMax());
            derived.put("pe_max", d.peMax());
            derived.put("defesa", d.defesa());
            derived.put("rd", d.rd());
            derived.put("iniciativa", d.iniciativaBonus());
            derived.put("fortitude", d.fortitude());
        }
        // A Alma é a escala dos números da ficha: o duelo escolhe a faixa de dano por ela.
        derived.put("alma", sheet.soul());
        // Magia: atributo efetivo + valor treinado da perícia (o bônus completo da perícia já somaria o
        // atributo dela, que não é o mesmo). Ofício: o bônus de teste da perícia, como o site rola.
        String magicAttribute = FichaConfig.get(FichaConfig.MAGIC_ATTRIBUTE);
        String magicSkill = FichaConfig.get(FichaConfig.MAGIC_SKILL);
        String craftSkill = FichaConfig.get(FichaConfig.CRAFT_SKILL);
        int magic = attributes.getOrDefault(magicAttribute, 0) + skills.getOrDefault(magicSkill, 0);
        FichaSheet.Skill craft = sheet.skill(craftSkill);
        int craftBonus = craft != null ? craft.bonus() : attributes.getOrDefault("sab", 0);
        List<CharacterSheet.Technique> techniques = new ArrayList<>();
        for (FichaSheet.Ability ability : sheet.abilities()) {
            if (!ability.equipped() || ability.name().isEmpty()) continue;
            techniques.add(new CharacterSheet.Technique(ability.name(), ability.element(), tier(ability.rarity()), ability.abilityLevel()));
        }
        return new CharacterSheet.Stats(true, sheet.rpgCharacterId(), attributes, skills, bonuses, derived, magic, craftBonus, techniques);
    }

    /** {@code rarity} da técnica é o tier 1–5 do site, em texto; o que não for número vira 1. */
    static int tier(String rarity) {
        try {
            return Math.max(1, Math.min(5, Integer.parseInt(rarity.trim())));
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    private static SheetStatsStore load(CompoundTag tag, HolderLookup.Provider registries) {
        SheetStatsStore store = new SheetStatsStore();
        PlayerMapNbt.read(tag, KEY_ENTRIES, store.stats, SheetStatsStore::read);
        return store;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.put(KEY_ENTRIES, PlayerMapNbt.write(stats, SheetStatsStore::write));
        return tag;
    }

    private static void write(CompoundTag entry, CharacterSheet.Stats value) {
        entry.putLong("Character", value.characterId());
        entry.put("Attributes", map(value.attributes()));
        entry.put("Skills", map(value.skills()));
        entry.put("Bonuses", map(value.skillBonuses()));
        entry.put("Derived", map(value.derived()));
        entry.putInt("Magic", value.magicBonus());
        entry.putInt("Craft", value.craftBonus());
        ListTag techniques = new ListTag();
        for (CharacterSheet.Technique technique : value.techniques()) {
            CompoundTag t = new CompoundTag();
            t.putString("Name", technique.name());
            t.putString("Element", technique.element());
            t.putInt("Tier", technique.tier());
            t.putInt("Level", technique.level());
            techniques.add(t);
        }
        entry.put("Techniques", techniques);
    }

    @Nullable
    private static CharacterSheet.Stats read(CompoundTag entry) {
        if (!entry.contains("Attributes", Tag.TAG_COMPOUND)) return null;
        return new CharacterSheet.Stats(true, entry.getLong("Character"), map(entry.getCompound("Attributes")),
                map(entry.getCompound("Skills")), map(entry.getCompound("Bonuses")), map(entry.getCompound("Derived")),
                entry.getInt("Magic"), entry.getInt("Craft"), techniques(entry.getList("Techniques", Tag.TAG_COMPOUND)));
    }

    private static List<CharacterSheet.Technique> techniques(ListTag list) {
        List<CharacterSheet.Technique> out = new ArrayList<>(list.size());
        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = list.getCompound(i);
            out.add(new CharacterSheet.Technique(t.getString("Name"), t.getString("Element"), t.getInt("Tier"), t.getInt("Level")));
        }
        return out;
    }

    private static CompoundTag map(Map<String, Integer> values) {
        CompoundTag tag = new CompoundTag();
        values.forEach(tag::putInt);
        return tag;
    }

    private static Map<String, Integer> map(CompoundTag tag) {
        Map<String, Integer> out = new HashMap<>();
        for (String key : tag.getAllKeys()) out.put(key, tag.getInt(key));
        return out;
    }
}
