package com.aurorion.magia.compat;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Os slots de acessorio do jogador, do Curios e do Accessories, vistos como um tipo so de grupo.
 *
 * <p>O pack tem os dois, sem se falarem: quem precisa de "tudo o que a pessoa carrega" (o Bolso do
 * Vazio, o Spoliatio) tem que olhar nos dois, senao um anel num deles passa batido.
 *
 * <p>Por reflexao, pela API publica (a mesma ponte do {@code aurorion-essentials}), resolvida uma vez:
 * nenhum dos dois e dependencia obrigatoria.
 */
public final class AccessorySlots {
    private AccessorySlots() {
    }

    /** Um grupo de slots, venha de onde vier. */
    public interface Slots {
        int size();

        ItemStack get(int slot);

        void set(int slot, ItemStack stack);
    }

    /**
     * Todos os grupos, por chave estavel ({@code curios/<tipo>}, {@code accessories_cosmetic/<tipo>}...).
     * Lanca quando a API de um dos mods instalados nao pode ser lida — quem chama decide se recusa.
     */
    public static Map<String, Slots> of(ServerPlayer player) throws ReflectiveOperationException {
        Map<String, Slots> out = new LinkedHashMap<>();
        if (ModList.get().isLoaded("curios")) Curios.collect(player, out);
        if (ModList.get().isLoaded("accessories")) Accessories.collect(player, out);
        return out;
    }

    private static Slots of(IItemHandlerModifiable handler) {
        return new Slots() {
            @Override public int size() { return handler.getSlots(); }
            @Override public ItemStack get(int slot) { return handler.getStackInSlot(slot); }
            @Override public void set(int slot, ItemStack stack) { handler.setStackInSlot(slot, stack); }
        };
    }

    private static Slots of(Container container) {
        return new Slots() {
            @Override public int size() { return container.getContainerSize(); }
            @Override public ItemStack get(int slot) { return container.getItem(slot); }
            @Override public void set(int slot, ItemStack stack) { container.setItem(slot, stack); }
        };
    }

    /** Curios 9.x: {@code CuriosApi.getCuriosInventory(LivingEntity)} → {@code getCurios()} → stacks e cosmeticos. */
    private static final class Curios {
        private static Method inventory, curios, stacks, cosmetics;
        private static boolean resolved;
        @Nullable private static ReflectiveOperationException failure;

        static void collect(ServerPlayer player, Map<String, Slots> out) throws ReflectiveOperationException {
            resolve();
            if (failure != null) throw failure;
            Optional<?> handler = (Optional<?>) inventory.invoke(null, player);
            if (handler.isEmpty()) return;
            for (var entry : ((Map<?, ?>) curios.invoke(handler.get())).entrySet()) {
                out.put("curios/" + entry.getKey(), of((IItemHandlerModifiable) stacks.invoke(entry.getValue())));
                out.put("curios_cosmetic/" + entry.getKey(), of((IItemHandlerModifiable) cosmetics.invoke(entry.getValue())));
            }
        }

        private static synchronized void resolve() {
            if (resolved) return;
            resolved = true;
            try {
                inventory = Class.forName("top.theillusivec4.curios.api.CuriosApi").getMethod("getCuriosInventory", LivingEntity.class);
                curios = Class.forName("top.theillusivec4.curios.api.type.capability.ICuriosItemHandler").getMethod("getCurios");
                Class<?> group = Class.forName("top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler");
                stacks = group.getMethod("getStacks");
                cosmetics = group.getMethod("getCosmeticStacks");
            } catch (ReflectiveOperationException e) {
                failure = e;
            }
        }
    }

    /** Accessories 1.1: {@code AccessoriesCapability.get(LivingEntity)} → {@code getContainers()} → containers. */
    private static final class Accessories {
        private static Method capability, containers, accessories, cosmetics;
        private static boolean resolved;
        @Nullable private static ReflectiveOperationException failure;

        static void collect(ServerPlayer player, Map<String, Slots> out) throws ReflectiveOperationException {
            resolve();
            if (failure != null) throw failure;
            Object handler = capability.invoke(null, player);
            if (handler == null) return;
            for (var entry : ((Map<?, ?>) containers.invoke(handler)).entrySet()) {
                out.put("accessories/" + entry.getKey(), of((Container) accessories.invoke(entry.getValue())));
                out.put("accessories_cosmetic/" + entry.getKey(), of((Container) cosmetics.invoke(entry.getValue())));
            }
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
            } catch (ReflectiveOperationException e) {
                failure = e;
            }
        }
    }
}
