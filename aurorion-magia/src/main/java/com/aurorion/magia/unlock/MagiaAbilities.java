package com.aurorion.magia.unlock;

import com.aurorion.core.character.CharacterAbilities;
import com.aurorion.core.character.CharacterAbilities.Group;
import com.aurorion.core.character.CharacterAbilities.Item;
import com.aurorion.core.character.CharacterAbilities.State;
import com.aurorion.magia.passive.AnachronicSight;
import com.aurorion.magia.passive.Passive;
import com.aurorion.magia.passive.PassiveActions;
import com.aurorion.magia.passive.PassiveData;
import com.aurorion.magia.passive.VoidPocket;
import com.aurorion.magia.unlock.SpellGrants.GrantKind;
import io.redspace.ironsspellbooks.api.registry.SchoolRegistry;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import com.aurorion.magia.config.MagiaConfig;
import io.redspace.ironsspellbooks.api.spells.SchoolType;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * Magias e passivas do personagem para a aba Habilidades da ficha, pelo
 * {@link CharacterAbilities} do core — este mod não sabe que a ficha existe.
 *
 * <p>Custo: só quando alguém abre a ficha. Lê as duas tabelas em memória ({@link SpellUnlockData},
 * {@link PassiveData}) e, por escola liberada, uma passada pelo registro de magias para contar quantas
 * ela entrega (~150 entradas no pack). Nada por tick, nada de disco.
 *
 * <p>Nomes e descrições vão com a chave de tradução: o servidor dedicado só tem o {@code en_us}, e o
 * cliente mostra no idioma dele. O texto em inglês fica de reserva.
 */
public final class MagiaAbilities {
    private static final String SPELL_ICON = "irons_spellbooks:scroll";
    private static final String SCHOOL_ICON = "irons_spellbooks:blank_rune";
    private static final String PASSIVE_ICON = "aurorion_magia:pergaminho_passiva";

    private MagiaAbilities() {
    }

    public static List<Group> collect(ServerPlayer player) {
        List<Group> out = new ArrayList<>(3);
        Group arcane = arcane(player);
        if (arcane != null) out.add(arcane);
        out.add(spells(player));
        Group passives = passives(player);
        if (passives != null) out.add(passives);
        return out;
    }

    // --- Despertar Arcano e teste ------------------------------------------------------------------

    private static final String[] TIER_TEXT = {
            "Sem o Despertar, nenhuma magia responde. Só um professor libera (/trama no conceder).",
            "Magias comuns e incomuns, pergaminhos e a oficina arcana.",
            "Todas as magias das escolas: raras, épicas e lendárias.",
            "Também as proibidas."};

    @Nullable
    private static Group arcane(ServerPlayer player) {
        List<Item> items = new ArrayList<>(4);
        ArcaneProfile.Profile profile = ArcaneProfile.of(player);
        if (ArcaneProfile.nodeMode()) {
            int tier = profile.tier();
            items.add(Item.of(tier == 0 ? "Despertar Arcano" : "Despertar Arcano " + SpellAccess.roman(tier), TIER_TEXT[tier],
                    tier == 0 ? "" : "Nível " + tier, tier == 0 ? State.LOCKED : State.ACTIVE, "minecraft:lapis_lazuli"));
        }
        SchoolType specialty = profile.specialty();
        if (specialty != null) {
            Component name = specialty.getDisplayName();
            items.add(Item.of("Especialista: " + name.getString(), "Teste com vantagem e +" + MagiaConfig.SPECIALIST_BONUS.get()
                            + "; " + MagiaConfig.SPECIALIST_MANA.get() + "% menos mana, " + MagiaConfig.SPECIALIST_COOLDOWN.get()
                            + "% menos recarga e +" + Math.round(MagiaConfig.SPECIALIST_POWER.get() * 100) + "% de poder da escola.",
                    "Especialidade", State.ACTIVE, runeOf(specialty.getId())));
        }
        if (MagiaConfig.TEST_ENABLED.get()) {
            boolean known = com.aurorion.core.character.CharacterSheet.of(player.server, player.getUUID()).known();
            StringBuilder detail = new StringBuilder("Chance de a magia falhar: ");
            SpellRarity[] rarities = SpellRarity.values();
            for (int i = 0; i < rarities.length; i++) {
                if (i > 0) detail.append(", ");
                detail.append(RARITY_NAMES[i]).append(' ').append(percent(SpellCheck.failChance(player, rarities[i], false, false)));
            }
            detail.append("; proibida lendária ").append(percent(SpellCheck.failChance(player, SpellRarity.LEGENDARY, true, false))).append('.');
            if (specialty != null) {
                detail.append(" Na sua escola, rara ").append(percent(SpellCheck.failChance(player, SpellRarity.RARE, false, true))).append('.');
            }
            detail.append(known ? " Pela sua ficha." : " Sem ficha lida: valor de iniciante.");
            items.add(Item.of("Teste da magia", detail.toString(), "Bônus " + SpellCheck.bonus(player, false),
                    State.UNLOCKED, "irons_spellbooks:arcane_essence"));
        }
        return items.isEmpty() ? null : new Group("despertar", "Despertar Arcano", "#9FC8FF", 9, items);
    }

    private static final String[] RARITY_NAMES = {"comum", "incomum", "rara", "épica", "lendária"};

    private static String percent(double chance) {
        return Math.round(chance * 100) + "%";
    }

    // --- Magias -----------------------------------------------------------------------------------

    private static Group spells(ServerPlayer player) {
        SpellGrants grants = SpellUnlockData.get(player.server).grantsOf(player.getUUID());
        List<Item> items = new ArrayList<>();
        if (SpellAccess.bypasses(player)) {
            items.add(Item.of("Todas as magias", "Staff: conjura qualquer magia, inclusive as proibidas, enquanto"
                    + " staffBypass estiver ligado.", "Staff", State.ACTIVE, "irons_spellbooks:gold_spell_book"));
        }

        Set<ResourceLocation> schools = grants.view(GrantKind.SCHOOL);
        List<Item> schoolItems = new ArrayList<>(schools.size());
        for (ResourceLocation id : schools) schoolItems.add(school(id));
        schoolItems.sort(Comparator.comparing(Item::name, String.CASE_INSENSITIVE_ORDER));
        items.addAll(schoolItems);

        List<Item> spellItems = new ArrayList<>();
        for (ResourceLocation id : grants.view(GrantKind.SPELL)) {
            Item item = spell(id, schools);
            if (item != null) spellItems.add(item);
        }
        spellItems.sort(Comparator.comparing(Item::name, String.CASE_INSENSITIVE_ORDER));
        items.addAll(spellItems);

        if (items.isEmpty() && !ArcaneProfile.nodeMode()) {
            items.add(Item.of("Nenhuma magia liberada", "Magia se aprende em aula: a staff libera magias soltas ou"
                    + " escolas inteiras para o personagem.", "", State.LOCKED, SPELL_ICON));
        }
        if (items.isEmpty()) {
            items.add(Item.of("Nenhuma liberação avulsa", "Quem decide as magias é o Despertar Arcano (grupo acima)."
                    + " A staff ainda pode liberar uma magia ou escola como exceção.", "", State.LOCKED, SPELL_ICON));
        }
        return new Group("magias", "Magias", "#9B7BFF", 10, fit(items));
    }

    private static Item school(ResourceLocation id) {
        SchoolType school = SchoolRegistry.REGISTRY.get(id);
        if (school == null) {
            return Item.of(id.toString(), "Escola de um addon que não está no pack agora. A liberação continua"
                    + " guardada e volta a valer se ele voltar.", "Escola", State.LOCKED, SCHOOL_ICON);
        }
        int count = 0;
        for (AbstractSpell spell : SpellRegistry.getSpellsForSchool(school)) {
            if (spell.isEnabled() && !SpellAccess.isForbidden(spell)) count++;
        }
        Component name = school.getDisplayName();
        String detail = count == 1 ? "A escola inteira: 1 magia." : "A escola inteira: " + count + " magias.";
        return Item.of(name.getString(), detail + " As proibidas só com liberação própria.", "Escola",
                State.UNLOCKED, runeOf(id)).withKeys(keyOf(name), "");
    }

    @Nullable
    private static Item spell(ResourceLocation id, Set<ResourceLocation> schools) {
        AbstractSpell spell = SpellRegistry.getSpell(id);
        if (spell == null || spell == SpellRegistry.none()) {
            return Item.of(id.toString(), "Magia de um addon que não está no pack agora. A liberação continua"
                    + " guardada.", "", State.LOCKED, SPELL_ICON);
        }
        boolean forbidden = SpellAccess.isForbidden(spell);
        SchoolType school = spell.getSchoolType();
        // Já entregue pela escola: a linha da escola diz isso, e a lista não repete.
        if (!forbidden && school != null && schools.contains(school.getId())) return null;

        String nameKey = spell.getComponentId();
        String detailKey = nameKey + ".guide";
        Language lang = Language.getInstance();
        String detail = lang.has(detailKey) ? lang.getOrDefault(detailKey, "") : "";
        State state = spell.isEnabled() ? State.UNLOCKED : State.LOCKED;
        if (!spell.isEnabled()) detail = "Desligada no servidor.";
        String level = forbidden ? "Proibida" : "";
        return new Item(lang.getOrDefault(nameKey, spell.getSpellName()), nameKey, detail,
                spell.isEnabled() && lang.has(detailKey) ? detailKey : "", level, state, CharacterAbilities.NO_PROGRESS, SPELL_ICON);
    }

    /** {@code irons_spellbooks:fire} → a runa {@code irons_spellbooks:fire_rune}, quando ela existe. */
    private static String runeOf(ResourceLocation school) {
        ResourceLocation rune = ResourceLocation.tryBuild(school.getNamespace(), school.getPath() + "_rune");
        return rune != null && BuiltInRegistries.ITEM.containsKey(rune) ? rune.toString() : SCHOOL_ICON;
    }

    private static String keyOf(Component name) {
        return name.getContents() instanceof TranslatableContents t ? t.getKey() : "";
    }

    // --- Passivas ---------------------------------------------------------------------------------

    @Nullable
    private static Group passives(ServerPlayer player) {
        PassiveData data = PassiveData.get(player.server);
        Set<Passive> owned = data.owned(player.getUUID());
        if (owned.isEmpty()) return null;
        List<Item> items = new ArrayList<>(owned.size());
        Language lang = Language.getInstance();
        for (Passive passive : Passive.values()) {
            if (!owned.contains(passive)) continue;
            String nameKey = "passive.aurorion_magia." + passive.key();
            String detailKey = nameKey + ".guide";
            State state;
            String level;
            if (passive.isToggleable()) {
                boolean on = data.isActive(player.getUUID(), passive);
                state = on ? State.ACTIVE : State.UNLOCKED;
                level = on ? "Ligada" : "Desligada";
            } else if (PassiveActions.usesKey(passive)) {
                state = inUse(player, passive) ? State.ACTIVE : State.UNLOCKED;
                level = state == State.ACTIVE ? "Em uso" : "Tecla";
            } else {
                // Sem interruptor nem tecla: ter já é usar.
                state = State.ACTIVE;
                level = "Sempre";
            }
            items.add(new Item(lang.getOrDefault(nameKey, passive.key()), nameKey,
                    lang.has(detailKey) ? lang.getOrDefault(detailKey, "") : "", detailKey, level, state,
                    CharacterAbilities.NO_PROGRESS, PASSIVE_ICON));
        }
        return new Group("passivas", "Passivas", "#E8C15A", 11, items);
    }

    private static boolean inUse(ServerPlayer player, Passive passive) {
        return switch (passive) {
            case ANACHRONIC -> AnachronicSight.isActive(player);
            case VOID_POCKET -> VoidPocket.isInside(player);
            default -> false;
        };
    }

    /** Mais que o teto: a última linha diz quantas ficaram de fora, em vez de cortar calado. */
    private static List<Item> fit(List<Item> items) {
        int max = CharacterAbilities.MAX_ITEMS;
        if (items.size() <= max) return items;
        List<Item> out = new ArrayList<>(items.subList(0, max - 1));
        int rest = items.size() - (max - 1);
        out.add(Item.of("… e mais " + rest, "A staff tem a lista completa (/aurorion spells list).", "", State.UNLOCKED, SPELL_ICON));
        return out;
    }
}
