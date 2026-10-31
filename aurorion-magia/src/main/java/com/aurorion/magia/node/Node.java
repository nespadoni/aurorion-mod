package com.aurorion.magia.node;

import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Um no liberado: qual dos tres, onde, e desde quando.
 *
 * @param index 1, 2 ou 3 — cada um tem a sua cena ({@code NodeRenderer})
 * @param since tempo de jogo do overworld na liberacao; o cliente usa para tocar a abertura so a quem
 *              estava la, e nao a cada login
 */
public record Node(int index, ResourceKey<Level> dimension, Vec3 pos, long since) {
    public static final int COUNT = 3;

    CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("No", index);
        tag.putString("Dimensao", dimension.location().toString());
        tag.putDouble("X", pos.x);
        tag.putDouble("Y", pos.y);
        tag.putDouble("Z", pos.z);
        tag.putLong("Desde", since);
        return tag;
    }

    @Nullable
    static Node load(CompoundTag tag) {
        ResourceLocation dimension = ResourceLocation.tryParse(tag.getString("Dimensao"));
        int index = tag.getInt("No");
        if (dimension == null || index < 1 || index > COUNT) return null;
        return new Node(index, ResourceKey.create(Registries.DIMENSION, dimension),
                new Vec3(tag.getDouble("X"), tag.getDouble("Y"), tag.getDouble("Z")), tag.getLong("Desde"));
    }
}
