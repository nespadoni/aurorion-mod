package com.aurorion.essentials.death;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.items.IItemHandlerModifiable;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** Optional Curios 1.21.1 API. Resolve public interfaces once; never reflect in a tick loop. */
final class DeathCurios {
    private static Method inventory, curios, stacks, cosmetics, saveInventory;
    private static boolean resolved;
    private static ReflectiveOperationException failure;

    private DeathCurios() { }

    static ListTag archive(ServerPlayer player) throws ReflectiveOperationException {
        if (!ModList.get().isLoaded("curios")) return new ListTag();
        resolve();
        if (failure != null) throw new ReflectiveOperationException("Curios API unavailable", failure);
        Optional<?> handler = (Optional<?>) inventory.invoke(null, player);
        if (handler.isEmpty()) throw new ReflectiveOperationException("Curios inventory unavailable");
        return ((ListTag) saveInventory.invoke(handler.get(), false)).copy();
    }

    static Map<String, IItemHandlerModifiable> slots(ServerPlayer player) throws ReflectiveOperationException {
        Map<String, IItemHandlerModifiable> result = new LinkedHashMap<>();
        if (!ModList.get().isLoaded("curios")) return result;
        resolve();
        if (failure != null) throw new ReflectiveOperationException("Curios API unavailable", failure);
        Optional<?> handler = (Optional<?>) inventory.invoke(null, player);
        if (handler.isEmpty()) throw new ReflectiveOperationException("Curios inventory unavailable");
        Map<?, ?> groups = (Map<?, ?>) curios.invoke(handler.get());
        for (var entry : groups.entrySet()) {
            result.put("curios/" + entry.getKey(), (IItemHandlerModifiable) stacks.invoke(entry.getValue()));
            result.put("cosmetic/" + entry.getKey(), (IItemHandlerModifiable) cosmetics.invoke(entry.getValue()));
        }
        return result;
    }

    private static void resolve() {
        if (resolved) return;
        resolved = true;
        try {
            inventory = Class.forName("top.theillusivec4.curios.api.CuriosApi")
                    .getMethod("getCuriosInventory", LivingEntity.class);
            curios = Class.forName("top.theillusivec4.curios.api.type.capability.ICuriosItemHandler")
                    .getMethod("getCurios");
            saveInventory = Class.forName("top.theillusivec4.curios.api.type.capability.ICuriosItemHandler")
                    .getMethod("saveInventory", boolean.class);
            Class<?> group = Class.forName("top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler");
            stacks = group.getMethod("getStacks");
            cosmetics = group.getMethod("getCosmeticStacks");
        } catch (ReflectiveOperationException e) { failure = e; }
    }
}
