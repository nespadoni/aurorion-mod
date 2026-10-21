package com.aurorion.magia.passive;

import com.aurorion.magia.AurorionMagia;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
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
 * Os pertences de quem entra no Bolso do Vazio: nada entra, nada sai.
 *
 * <p>Na entrada, inventario (com armadura e mao secundaria) e os slots de acessorio do Curios e do
 * Accessories sao guardados na ancora — no {@code VoidPocketData}, fora do NBT do jogador, para
 * sobreviver a crash — e esvaziados. Na saida, o que foi pego la dentro some e o que era da pessoa volta.
 *
 * <p>E isso que fecha o criativo de Iris: sem esvaziar na saida, um item tirado do menu criativo sairia
 * no bolso dela (ou no de um marcado a quem ela entregou), e o bolso viraria uma fabrica de qualquer
 * coisa. Os dois sistemas de acessorio entram porque o pack tem os dois, sem se falarem — um anel do
 * criativo num slot do Curios passaria pela porta sem isto.
 *
 * <p>Curios e Accessories por reflexao, pela API publica (a mesma ponte do {@code aurorion-essentials}),
 * resolvida uma vez: nenhum dos dois e dependencia obrigatoria.
 */
final class PocketBelongings {
    private static final String INVENTORY = "Inventory";
    private static final String EXTRA = "Extra";

    private PocketBelongings() {
    }

    /**
     * Guarda e esvazia. {@code null} quando um slot extra nao pode ser lido: a pessoa nao entra, em vez de
     * entrar com um anel que o bolso nao viu.
     */
    @Nullable
    static CompoundTag stash(ServerPlayer player) {
        Map<String, Slots> extra;
        try {
            extra = extraSlots(player);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            AurorionMagia.LOGGER.warn("Bolso do Vazio: slots de acessorio de {} ilegiveis; entrada recusada.",
                    player.getGameProfile().getName(), failure);
            return null;
        }
        CompoundTag tag = new CompoundTag();
        tag.put(INVENTORY, player.getInventory().save(new ListTag()));
        CompoundTag extras = new CompoundTag();
        extra.forEach((key, slots) -> {
            ListTag list = new ListTag();
            for (int i = 0; i < slots.size(); i++) {
                ItemStack stack = slots.get(i);
                if (stack.isEmpty()) continue;
                CompoundTag entry = new CompoundTag();
                entry.putInt("Slot", i);
                entry.put("Item", stack.save(player.registryAccess()));
                list.add(entry);
                slots.set(i, ItemStack.EMPTY);
            }
            if (!list.isEmpty()) extras.put(key, list);
        });
        tag.put(EXTRA, extras);
        player.getInventory().clearContent();
        player.inventoryMenu.broadcastChanges();
        return tag;
    }

    /** Descarta o que veio de dentro e devolve o que era da pessoa. Item sem slot de volta vai ao inventario. */
    static void unstash(ServerPlayer player, CompoundTag tag) {
        Map<String, Slots> extra;
        try {
            extra = extraSlots(player);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            AurorionMagia.LOGGER.warn("Bolso do Vazio: slots de acessorio de {} ilegiveis na volta.",
                    player.getGameProfile().getName(), failure);
            extra = Map.of();
        }
        // O que foi pego la dentro (menu criativo inclusive) nao atravessa.
        player.getInventory().clearContent();
        for (Slots slots : extra.values()) {
            for (int i = 0; i < slots.size(); i++) slots.set(i, ItemStack.EMPTY);
        }
        player.getInventory().load(tag.getList(INVENTORY, Tag.TAG_COMPOUND));
        CompoundTag extras = tag.getCompound(EXTRA);
        for (String key : extras.getAllKeys()) {
            Slots slots = extra.get(key);
            ListTag list = extras.getList(key, Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag entry = list.getCompound(i);
                ItemStack stack = ItemStack.parseOptional(player.registryAccess(), entry.getCompound("Item"));
                if (stack.isEmpty()) continue;
                int slot = entry.getInt("Slot");
                // Slot que mudou de configuracao (ou mod de acessorio que saiu): o item nao se perde.
                if (slots != null && slot >= 0 && slot < slots.size() && slots.get(slot).isEmpty()) slots.set(slot, stack);
                else player.getInventory().placeItemBackInInventory(stack);
            }
        }
        player.inventoryMenu.broadcastChanges();
    }

    // --- Slots de acessorio ----------------------------------------------------------------------------

    /** Um grupo de slots, venha de onde vier. */
    private interface Slots {
        int size();

        ItemStack get(int slot);

        void set(int slot, ItemStack stack);
    }

    private static Map<String, Slots> extraSlots(ServerPlayer player) throws ReflectiveOperationException {
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
