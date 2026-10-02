package com.aurorion.essentials.death;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.fml.ModList;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.TreeMap;

/**
 * Ponte opcional com o Accessories (wispforest), por reflexao, como o {@link DeathCurios}.
 *
 * <p>O pack tem os dois sistemas de acessorio ao mesmo tempo, e eles nao se falam: Artifacts e Simple
 * Hats equipam no Accessories; Relics, Malum, Iron's Spellbooks e Cataclysm no Curios. Sem esta ponte,
 * tudo o que estava num slot do Accessories sumia do historico de mortes.
 *
 * <p>API publica conferida com {@code javap} no accessories-neoforge 1.1.0-beta.53:
 * {@code AccessoriesCapability.get(LivingEntity)} (nulo quando a entidade nao tem slots),
 * {@code getContainers()}, e por container {@code getAccessories()}/{@code getCosmeticAccessories()},
 * que sao {@code SimpleContainer}. Gravar com {@code setItem} basta: o container do Accessories escuta o
 * proprio inventario e marca a sincronizacao sozinho.
 */
final class DeathAccessories {
    static final String GROUP = "accessories/";
    static final String COSMETIC = "accessories_cosmetic/";

    private static Method capability, containers, accessories, cosmetics;
    private static boolean resolved;
    private static ReflectiveOperationException failure;

    private DeathAccessories() { }

    static boolean installed() {
        return ModList.get().isLoaded("accessories");
    }

    /** Slots por grupo, em ordem fixa (a mesma ordem em duas capturas seguidas, para o comparador). */
    static Map<String, Container> slots(ServerPlayer player) throws ReflectiveOperationException {
        Map<String, Container> result = new TreeMap<>();
        if (!installed()) return result;
        resolve();
        if (failure != null) throw new ReflectiveOperationException("Accessories API unavailable", failure);
        Object handler = capability.invoke(null, player);
        if (handler == null) return result;
        Map<?, ?> groups = (Map<?, ?>) containers.invoke(handler);
        for (var entry : groups.entrySet()) {
            result.put(GROUP + entry.getKey(), (Container) accessories.invoke(entry.getValue()));
            result.put(COSMETIC + entry.getKey(), (Container) cosmetics.invoke(entry.getValue()));
        }
        return result;
    }

    static boolean owns(String group) {
        return group.startsWith(GROUP) || group.startsWith(COSMETIC);
    }

    private static synchronized void resolve() {
        if (resolved) return;
        resolved = true;
        try {
            Class<?> api = Class.forName("io.wispforest.accessories.api.AccessoriesCapability");
            capability = api.getMethod("get", LivingEntity.class);
            containers = api.getMethod("getContainers");
            Class<?> container = Class.forName("io.wispforest.accessories.api.AccessoriesContainer");
            accessories = container.getMethod("getAccessories");
            cosmetics = container.getMethod("getCosmeticAccessories");
        } catch (ReflectiveOperationException e) { failure = e; }
    }
}
