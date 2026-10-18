package com.aurorion.essentials.compat;

import com.aurorion.core.data.SavedDataAccess;
import com.aurorion.essentials.AurorionEssentials;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * O {@link PhoneGroupBook} em disco ({@code data/aurorion_essentials_phone_groups.dat} do overworld).
 *
 * <p>Gravado pelo autosave do mundo, como todo {@code SavedData}: uma mensagem para quem esta online
 * nao marca nada; so grupo mudado e fila de offline mudada sujam o arquivo. O arquivo e pequeno —
 * no pior caso, 2048 grupos de 16 UUIDs e 50 linhas por conta.
 */
public final class PhoneGroupData extends SavedData {
    private static final String FILE_ID = AurorionEssentials.MOD_ID + "_phone_groups";

    private static final SavedDataAccess<PhoneGroupData> ACCESS =
            new SavedDataAccess<>(FILE_ID, PhoneGroupData::new, PhoneGroupData::load);

    private final PhoneGroupBook book = new PhoneGroupBook(this::setDirty);

    public static PhoneGroupData get(MinecraftServer server) {
        return ACCESS.get(server);
    }

    public PhoneGroupBook book() {
        return book;
    }

    private static PhoneGroupData load(CompoundTag tag, HolderLookup.Provider registries) {
        PhoneGroupData data = new PhoneGroupData();
        ListTag groups = tag.getList("Groups", Tag.TAG_COMPOUND);
        for (int i = 0; i < groups.size(); i++) {
            CompoundTag row = groups.getCompound(i);
            if (!row.hasUUID("Id")) continue;
            List<UUID> members = new ArrayList<>();
            for (Tag member : row.getList("Members", Tag.TAG_INT_ARRAY)) {
                try {
                    members.add(NbtUtils.loadUUID(member));
                } catch (IllegalArgumentException ignored) {
                    // Entrada corrompida: o resto do grupo vale.
                }
            }
            data.book.restore(row.getUUID("Id"), row.getString("Name"), row.getLong("Created"), members);
        }

        ListTag queues = tag.getList("Queues", Tag.TAG_COMPOUND);
        for (int i = 0; i < queues.size(); i++) {
            CompoundTag row = queues.getCompound(i);
            if (!row.hasUUID("Member")) continue;
            UUID member = row.getUUID("Member");
            ListTag lines = row.getList("Lines", Tag.TAG_COMPOUND);
            for (int j = 0; j < lines.size(); j++) {
                CompoundTag line = lines.getCompound(j);
                if (!line.hasUUID("Group")) continue;
                data.book.restoreLine(member, new PhoneGroupBook.Line(line.getUUID("Group"), line.getByte("Kind"),
                        line.getString("Nick"), line.getString("Name"), line.getString("Text"), line.getLong("At")));
            }
        }
        data.book.purgeExpired(System.currentTimeMillis());
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag groups = new ListTag();
        for (PhoneGroupBook.Group group : book.all()) {
            CompoundTag row = new CompoundTag();
            row.putUUID("Id", group.id());
            row.putString("Name", group.name());
            row.putLong("Created", group.createdAt());
            ListTag members = new ListTag();
            for (UUID member : group.members()) members.add(NbtUtils.createUUID(member));
            row.put("Members", members);
            groups.add(row);
        }
        tag.put("Groups", groups);

        ListTag queues = new ListTag();
        book.queues().forEach((member, lines) -> {
            if (lines.isEmpty()) return;
            CompoundTag row = new CompoundTag();
            row.putUUID("Member", member);
            ListTag list = new ListTag();
            for (PhoneGroupBook.Line line : lines) {
                CompoundTag entry = new CompoundTag();
                entry.putUUID("Group", line.group());
                entry.putByte("Kind", line.kind());
                entry.putString("Nick", line.nick());
                entry.putString("Name", line.name());
                entry.putString("Text", line.text());
                entry.putLong("At", line.at());
                list.add(entry);
            }
            row.put("Lines", list);
            queues.add(row);
        });
        tag.put("Queues", queues);
        return tag;
    }
}
