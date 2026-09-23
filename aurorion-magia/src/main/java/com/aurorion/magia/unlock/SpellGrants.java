package com.aurorion.magia.unlock;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * O que um personagem pode conjurar: magias soltas e escolas inteiras.
 *
 * <p>Sem nenhum tipo do Iron's de proposito. A regra ("magia liberada OU escola liberada") e a parte
 * que quebraria em silencio, entao ela fica testavel sem o Iron's no classpath de teste.
 *
 * <p>Ids desconhecidos sao guardados como vieram. Tirar um addon de magias do pack e colocar de
 * volta nao pode apagar a liberacao de ninguem.
 */
public final class SpellGrants {
    public static final SpellGrants EMPTY = new SpellGrants();

    private static final String KEY_SPELLS = "Spells";
    private static final String KEY_SCHOOLS = "Schools";

    private final Set<ResourceLocation> spells = new HashSet<>();
    private final Set<ResourceLocation> schools = new HashSet<>();

    /** Duas buscas em {@link HashSet}: e o que roda a cada tentativa de conjuracao. */
    public boolean allows(ResourceLocation spell, @Nullable ResourceLocation school) {
        return spells.contains(spell) || school != null && schools.contains(school);
    }

    /** @return se mudou alguma coisa. */
    public boolean grant(GrantKind kind, ResourceLocation id) {
        return mutable(kind).add(id);
    }

    /** @return se mudou alguma coisa. */
    public boolean revoke(GrantKind kind, ResourceLocation id) {
        return mutable(kind).remove(id);
    }

    public Set<ResourceLocation> view(GrantKind kind) {
        return Collections.unmodifiableSet(set(kind));
    }

    public boolean isEmpty() {
        return spells.isEmpty() && schools.isEmpty();
    }

    private Set<ResourceLocation> set(GrantKind kind) {
        return kind == GrantKind.SPELL ? spells : schools;
    }

    private Set<ResourceLocation> mutable(GrantKind kind) {
        if (this == EMPTY) throw new UnsupportedOperationException("SpellGrants.EMPTY e compartilhado");
        return set(kind);
    }

    void write(CompoundTag entry) {
        entry.put(KEY_SPELLS, writeIds(spells));
        entry.put(KEY_SCHOOLS, writeIds(schools));
    }

    static SpellGrants read(CompoundTag entry) {
        SpellGrants grants = new SpellGrants();
        readIds(entry, KEY_SPELLS, grants.spells);
        readIds(entry, KEY_SCHOOLS, grants.schools);
        return grants;
    }

    private static ListTag writeIds(Set<ResourceLocation> ids) {
        ListTag list = new ListTag();
        for (ResourceLocation id : ids) list.add(StringTag.valueOf(id.toString()));
        return list;
    }

    private static void readIds(CompoundTag entry, String key, Set<ResourceLocation> out) {
        ListTag list = entry.getList(key, Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) {
            ResourceLocation id = ResourceLocation.tryParse(list.getString(i));
            if (id != null) out.add(id);
        }
    }

    public enum GrantKind {
        SPELL, SCHOOL
    }
}
