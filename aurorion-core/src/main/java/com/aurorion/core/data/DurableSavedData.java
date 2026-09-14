package com.aurorion.core.data;

import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.LevelResource;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.*;

/** Rare transaction boundary: normal ticks still use autosave. IO failures must reach the caller. */
final class DurableSavedData {
    private DurableSavedData() { }
    static void flush(MinecraftServer server, String fileId, SavedData data) throws IOException {
        if (!data.isDirty()) return;
        Path directory = server.getWorldPath(LevelResource.ROOT).resolve("data");
        Files.createDirectories(directory);
        Path target = directory.resolve(fileId + ".dat");
        Path temporary = directory.resolve(fileId + ".character-tmp");
        CompoundTag file = new CompoundTag();
        file.put("data", data.save(new CompoundTag(), server.registryAccess()));
        file.putInt("DataVersion", SharedConstants.getCurrentVersion().getDataVersion().getVersion());
        NbtIo.writeCompressed(file, temporary);
        try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) { channel.force(true); }
        Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        data.setDirty(false);
    }
}
