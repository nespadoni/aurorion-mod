package com.aurorion.utils.abduction;

import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

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

    /**
     * @return o ponto, ou {@code null} se a entrada esta corrompida (id de dimensao invalido ou
     * coordenada nao finita). {@code ResourceLocation.parse} lancaria excecao, e uma entrada ruim
     * derrubaria a leitura do arquivo inteiro — todas as origens de todo mundo, por causa de uma.
     */
    @Nullable
    public static TeleportSpot load(CompoundTag tag) {
        ResourceLocation id = ResourceLocation.tryParse(tag.getString("Dimension"));
        double x = tag.getDouble("X"), y = tag.getDouble("Y"), z = tag.getDouble("Z");
        if (id == null || !Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) return null;
        ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, id);
        return new TeleportSpot(dimension, x, y, z, tag.getFloat("Yaw"), tag.getFloat("Pitch"));
    }
}
