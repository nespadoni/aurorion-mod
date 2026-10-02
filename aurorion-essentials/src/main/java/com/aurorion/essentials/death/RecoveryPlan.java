package com.aurorion.essentials.death;

import com.aurorion.essentials.AurorionEssentials;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntPredicate;

/**
 * Para onde vai cada item de uma morte no {@code devolver} (ao dono) e no {@code pegar} (para a staff).
 *
 * <p>Nunca sobrescreve nada: so ocupa slot vazio, entao desfazer e esvaziar de novo os slots usados.
 * Devolvendo ao dono, cada item tenta primeiro o lugar de onde caiu — armadura no corpo, anel no slot
 * do Curios, chapeu no Accessories — e so depois o primeiro slot livre do inventario. O que nao cabe
 * fica sem recibo e sai numa proxima chamada, depois de a pessoa liberar espaco.
 *
 * <p>Fica de fora: o ender chest (nao cai na morte), o que e mantido na morte, o que ja tem recibo e,
 * devolvendo ao dono, o item identico que ja esta no mesmo slot (keepInventory, item que nao cai, ou
 * a pessoa ja catou e guardou no mesmo lugar). Item recolhido do chao e guardado em outro lugar nao e
 * detectado: decidir se a devolucao e devida continua sendo da staff.
 */
final class RecoveryPlan {
    /** Um slot de destino, do inventario, do Curios ou do Accessories. */
    private interface Spot {
        ItemStack get();
        void set(ItemStack stack);
    }

    record Placement(int entry, ItemStack stack, Runnable apply, Runnable undo) { }

    final List<Placement> placements = new ArrayList<>();
    /** Identico ao que o jogador ja tem no mesmo slot. */
    int held;
    /** Mantido na morte (Fio da Volta, Relicario): o jogador nunca perdeu. */
    int kept;
    /** Ja saiu antes, por qualquer caminho (give, devolver, pegar, criativo). */
    int recovered;
    /** Nao decodifica mais (mod removido, componente invalido). */
    int broken;
    /** Sem espaco no destino. */
    final List<Component> leftover = new ArrayList<>();

    private RecoveryPlan() { }

    int[] entries() {
        return placements.stream().mapToInt(Placement::entry).toArray();
    }

    static RecoveryPlan build(CompoundTag snapshot, ServerPlayer target, boolean toOwner, IntPredicate alreadyRecovered) {
        RecoveryPlan plan = new RecoveryPlan();
        Inventory inventory = target.getInventory();
        boolean[] busy = new boolean[Inventory.INVENTORY_SIZE];
        for (int i = 0; i < busy.length; i++) busy[i] = !inventory.getItem(i).isEmpty();

        Map<String, IItemHandlerModifiable> curios = Map.of();
        Map<String, Container> accessories = Map.of();
        if (toOwner) {
            // Sem a API, o item vai para o inventario em vez do slot de origem; nao e motivo para recusar.
            try { curios = DeathCurios.slots(target); }
            catch (ReflectiveOperationException | RuntimeException | LinkageError e) { AurorionEssentials.LOGGER.warn("Death history: Curios indisponivel ao devolver", e); }
            try { accessories = DeathAccessories.slots(target); }
            catch (ReflectiveOperationException | RuntimeException | LinkageError e) { AurorionEssentials.LOGGER.warn("Death history: Accessories indisponivel ao devolver", e); }
        }

        Set<String> planned = new HashSet<>();
        ListTag entries = snapshot.getList("Items", Tag.TAG_COMPOUND);
        for (int index = 0; index < entries.size(); index++) {
            CompoundTag entry = entries.getCompound(index);
            String group = entry.getString("Group");
            if (group.equals("ender") || !entry.contains("Stack", Tag.TAG_COMPOUND)) continue;
            ItemStack stack;
            try { stack = DeathSnapshot.item(entry, target); }
            catch (RuntimeException e) { plan.broken++; continue; }
            if (stack.isEmpty()) continue;
            if (DeathSnapshot.keptOnDeath(entry, stack)) { plan.kept++; continue; }
            if (alreadyRecovered.test(index)) { plan.recovered++; continue; }

            int slot = entry.getInt("Slot");
            if (toOwner) {
                Spot origin = origin(group, slot, inventory, curios, accessories);
                if (origin != null) {
                    ItemStack current = origin.get();
                    if (ItemStack.matches(current, stack)) { plan.held++; continue; }
                    if (current.isEmpty() && planned.add(group + "/" + slot)) {
                        if (group.equals("inventory") && slot < busy.length) busy[slot] = true;
                        plan.add(index, stack, origin, target);
                        continue;
                    }
                }
            }
            int free = firstFree(busy);
            if (free < 0) { plan.leftover.add(stack.getHoverName()); continue; }
            busy[free] = true;
            plan.add(index, stack, inventorySpot(inventory, free), target);
        }
        return plan;
    }

    private void add(int entry, ItemStack stack, Spot spot, ServerPlayer target) {
        placements.add(new Placement(entry, stack,
                () -> { spot.set(stack.copy()); target.getInventory().setChanged(); },
                () -> { spot.set(ItemStack.EMPTY); target.getInventory().setChanged(); }));
    }

    private static int firstFree(boolean[] busy) {
        for (int i = 0; i < busy.length; i++) if (!busy[i]) return i;
        return -1;
    }

    @Nullable
    private static Spot origin(String group, int slot, Inventory inventory, Map<String, IItemHandlerModifiable> curios,
                               Map<String, Container> accessories) {
        if (group.equals("inventory")) {
            return slot >= 0 && slot < inventory.getContainerSize() ? inventorySpot(inventory, slot) : null;
        }
        if (DeathAccessories.owns(group)) {
            Container container = accessories.get(group);
            if (container == null || slot < 0 || slot >= container.getContainerSize()) return null;
            return new Spot() {
                @Override public ItemStack get() { return container.getItem(slot); }
                @Override public void set(ItemStack stack) { container.setItem(slot, stack); }
            };
        }
        IItemHandlerModifiable handler = curios.get(group);
        if (handler == null || slot < 0 || slot >= handler.getSlots()) return null;
        return new Spot() {
            @Override public ItemStack get() { return handler.getStackInSlot(slot); }
            @Override public void set(ItemStack stack) { handler.setStackInSlot(slot, stack); }
        };
    }

    private static Spot inventorySpot(Inventory inventory, int slot) {
        return new Spot() {
            @Override public ItemStack get() { return inventory.getItem(slot); }
            @Override public void set(ItemStack stack) { inventory.setItem(slot, stack); }
        };
    }
}
