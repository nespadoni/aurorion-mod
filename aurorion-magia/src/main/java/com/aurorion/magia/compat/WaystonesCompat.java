package com.aurorion.magia.compat;

import com.aurorion.magia.AurorionMagia;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Ponte com o Waystones para o Robs: as pedras que ele ja ativou, e o ponto de chegada de cada uma.
 *
 * <p>Reflexiva, como a do Emotecraft: o Waystones fica opcional de verdade (sem ele, a passiva so nao
 * oferece pedras), e o build nao precisa do jar nem do Balm. Os nomes sao os da API publica
 * ({@code net.blay09.mods.waystones.api}), estavel entre as versoes 21.1.x. Se algum deixar de existir,
 * a ponte se desliga com uma linha de log em vez de derrubar quem apertou a tecla.
 *
 * <p>O teleporte em si e nosso, e nao o do Waystones: a energia quantica e o custo, e nao XP nem item —
 * e assim a corrente do Vinculum, o Reino das Almas e o Limbo continuam valendo como para qualquer
 * teleporte.
 */
public final class WaystonesCompat {
    private static final boolean LOADED = ModList.get().isLoaded("waystones");

    private static boolean resolved;
    private static boolean broken;
    private static Method activated, uid, name, dimension, valid, resolve, location, direction;

    private WaystonesCompat() {
    }

    /** Uma pedra que o jogador conhece. {@code handle} e o objeto do Waystones, opaco para nos. */
    public record Stone(UUID id, Component name, ResourceKey<Level> dimension, Object handle) {
    }

    public record Destination(ServerLevel level, Vec3 position, float yaw) {
    }

    public static boolean available() {
        return LOADED && resolve();
    }

    public static List<Stone> activated(ServerPlayer player) {
        if (!available()) return List.of();
        try {
            Collection<?> stones = (Collection<?>) activated.invoke(null, player);
            List<Stone> out = new ArrayList<>(stones.size());
            for (Object stone : stones) {
                if (!(Boolean) valid.invoke(stone)) continue;
                @SuppressWarnings("unchecked")
                ResourceKey<Level> dim = (ResourceKey<Level>) dimension.invoke(stone);
                out.add(new Stone((UUID) uid.invoke(stone), (Component) name.invoke(stone), dim, stone));
            }
            return out;
        } catch (ReflectiveOperationException | ClassCastException failure) {
            disable(failure);
            return List.of();
        }
    }

    @Nullable
    public static Destination destination(MinecraftServer server, Stone stone) {
        if (!available()) return null;
        ServerLevel level = server.getLevel(stone.dimension());
        if (level == null) return null;
        try {
            Optional<?> found = (Optional<?>) resolve.invoke(stone.handle(), level);
            if (found.isEmpty()) return null;
            Object target = found.get();
            Vec3 at = (Vec3) location.invoke(target);
            Direction facing = (Direction) direction.invoke(target);
            return new Destination(level, at, facing == null ? 0 : facing.toYRot());
        } catch (ReflectiveOperationException | ClassCastException failure) {
            disable(failure);
            return null;
        }
    }

    private static boolean resolve() {
        if (resolved) return !broken;
        resolved = true;
        try {
            Class<?> api = Class.forName("net.blay09.mods.waystones.api.WaystonesAPI");
            Class<?> waystone = Class.forName("net.blay09.mods.waystones.api.Waystone");
            Class<?> destination = Class.forName("net.blay09.mods.waystones.api.TeleportDestination");
            activated = api.getMethod("getActivatedWaystones", Player.class);
            uid = waystone.getMethod("getWaystoneUid");
            name = waystone.getMethod("getEffectiveName");
            dimension = waystone.getMethod("getDimension");
            valid = waystone.getMethod("isValid");
            resolve = waystone.getMethod("resolveDestination", ServerLevel.class);
            location = destination.getMethod("location");
            direction = destination.getMethod("direction");
        } catch (ReflectiveOperationException failure) {
            disable(failure);
        }
        return !broken;
    }

    private static void disable(Throwable failure) {
        if (!broken) AurorionMagia.LOGGER.warn("Ponte com o Waystones desligada: {}", failure.toString());
        broken = true;
    }
}
