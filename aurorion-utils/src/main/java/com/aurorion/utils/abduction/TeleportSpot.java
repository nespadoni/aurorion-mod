package com.aurorion.utils.abduction;

import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/**
 * Posicao exata (nao so o bloco) + para onde a pessoa estava olhando, numa dimensao especifica.
 * Usado tanto para o destino de uma abducao quanto para a origem salva (o "voltar").
 */
public record TeleportSpot(ResourceKey<Level> dimension, double x, double y, double z, float yaw, float pitch) {

    public static TeleportSpot of(ServerPlayer player) {
        return new TeleportSpot(player.level().dimension(), player.getX(), player.getY(), player.getZ(),
                player.getYRot(), player.getXRot());
    }

    public CompoundTag save(CompoundTag tag) {
        tag.putString("Dimension", dimension.location().toString());
        tag.putDouble("X", x);
        tag.putDouble("Y", y);
        tag.putDouble("Z", z);
        tag.putFloat("Yaw", yaw);
        tag.putFloat("Pitch", pitch);
        return tag;
    }

    public static TeleportSpot load(CompoundTag tag) {
        ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION,
                ResourceLocation.parse(tag.getString("Dimension")));
        return new TeleportSpot(dimension, tag.getDouble("X"), tag.getDouble("Y"), tag.getDouble("Z"),
                tag.getFloat("Yaw"), tag.getFloat("Pitch"));
    }
}
