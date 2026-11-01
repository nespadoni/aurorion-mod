package com.aurorion.magia.node;

import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Um no liberado: qual dos tres, onde, e desde quando. O mesmo no pode estar ligado em varios lugares
 * ao mesmo tempo — varios professores dando aula — e cada um tem o seu {@code id}.
 *
 * @param id    numero desta liberacao, para a staff desligar uma so ({@code /aurorion no listar})
 * @param index 1, 2 ou 3 — cada um tem a sua cena ({@code NodeRenderer})
 * @param since tempo de jogo do overworld na liberacao; o cliente usa para tocar a abertura so a quem
 *              estava la, e nao a cada login
 */
public record Node(int id, int index, ResourceKey<Level> dimension, Vec3 pos, long since) {
    public static final int COUNT = 3;
    /**
     * Teto de nos ligados no servidor inteiro. Cada cena e geometria e particula para todo cliente no
     * alcance (o No 3 e visto a 256 blocos); isto segura o pior caso, nao o uso normal.
     */
    public static final int MAX_ACTIVE = 16;

    CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Id", id);
        tag.putInt("No", index);
        tag.putString("Dimensao", dimension.location().toString());
        tag.putDouble("X", pos.x);
        tag.putDouble("Y", pos.y);
        tag.putDouble("Z", pos.z);
        tag.putLong("Desde", since);
        return tag;
    }

    /** @param fallbackId para o save de antes de existir {@code Id} (um no por numero) */
    @Nullable
    static Node load(CompoundTag tag, int fallbackId) {
        ResourceLocation dimension = ResourceLocation.tryParse(tag.getString("Dimensao"));
        int index = tag.getInt("No");
        if (dimension == null || index < 1 || index > COUNT) return null;
        int id = tag.contains("Id") ? tag.getInt("Id") : fallbackId;
        return new Node(id, index, ResourceKey.create(Registries.DIMENSION, dimension),
                new Vec3(tag.getDouble("X"), tag.getDouble("Y"), tag.getDouble("Z")), tag.getLong("Desde"));
    }

    boolean sameSpot(int index, ResourceKey<Level> dimension, Vec3 pos) {
        return this.index == index && this.dimension.equals(dimension) && this.pos.equals(pos);
    }
}
