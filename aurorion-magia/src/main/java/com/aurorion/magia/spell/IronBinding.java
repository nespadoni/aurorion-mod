package com.aurorion.magia.spell;

import com.aurorion.magia.registry.MagiaEffects;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A regra do Ferrum Ligatum: a armadura e a mao secundaria que o alvo usava ficam presas no corpo.
 *
 * <p>O vanilla nao tem evento de "clicou no slot de armadura". O que ele tem e a checagem de
 * equipamento que ja roda todo tick em toda entidade viva e dispara
 * {@code LivingEquipmentChangeEvent} quando algo muda. Reagimos a ela: se a peca presa saiu do
 * slot, ela e achada (no cursor ou no inventario) e posta de volta, e o que ocupou o lugar volta para
 * onde ela estava. Para quem joga, a peca simplesmente nao sai.
 *
 * <p>Estrago em combate muda o item (durabilidade) e dispara o mesmo evento — por isso "mesmo item"
 * atualiza o retrato em vez de desfazer a troca.
 */
public final class IronBinding {
    static final EquipmentSlot[] SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.OFFHAND};

    /** Retrato do que estava equipado. Memoria so: o efeito salvo refaz o retrato no proximo evento. */
    private static final Map<UUID, EnumMap<EquipmentSlot, ItemStack>> BOUND = new ConcurrentHashMap<>();

    private IronBinding() {
    }

    public static void bind(ServerPlayer player) {
        EnumMap<EquipmentSlot, ItemStack> snapshot = new EnumMap<>(EquipmentSlot.class);
        for (EquipmentSlot slot : SLOTS) {
            ItemStack stack = player.getItemBySlot(slot);
            if (!stack.isEmpty()) snapshot.put(slot, stack.copy());
        }
        BOUND.put(player.getUUID(), snapshot);
    }

    public static void release(UUID player) {
        BOUND.remove(player);
    }

    public static void clear() {
        BOUND.clear();
    }

    public static void onEquipmentChange(ServerPlayer player, EquipmentSlot slot, ItemStack from, ItemStack to) {
        if (!player.hasEffect(MagiaEffects.IRON_BOUND)) return;
        EnumMap<EquipmentSlot, ItemStack> snapshot = BOUND.get(player.getUUID());
        if (snapshot == null) {
            // Voltou de um relog com o efeito ainda ativo: o retrato e o que esta vestido agora.
            bind(player);
            return;
        }
        ItemStack bound = snapshot.get(slot);
        if (bound == null) return;
        if (ItemStack.isSameItem(to, bound)) {
            snapshot.put(slot, to.copy());
            return;
        }
        if (restore(player, slot, from, to)) {
            player.displayClientMessage(Component.translatable("aurorion_magia.ferro_preso"), true);
        } else {
            // Nao achamos a peca (foi consumida, ou saiu por um caminho que nao conhecemos): solta.
            snapshot.remove(slot);
        }
    }

    /** Jogar a peca presa fora (Q no cursor): ela volta para o corpo em vez de ir ao chao. */
    public static boolean onToss(ServerPlayer player, ItemStack tossed) {
        if (!player.hasEffect(MagiaEffects.IRON_BOUND)) return false;
        EnumMap<EquipmentSlot, ItemStack> snapshot = BOUND.get(player.getUUID());
        if (snapshot == null) return false;
        for (Map.Entry<EquipmentSlot, ItemStack> entry : snapshot.entrySet()) {
            if (!ItemStack.isSameItemSameComponents(tossed, entry.getValue())) continue;
            ItemStack occupying = player.getItemBySlot(entry.getKey());
            if (ItemStack.isSameItem(occupying, tossed)) continue;
            player.setItemSlot(entry.getKey(), tossed.copy());
            if (!occupying.isEmpty() && !player.getInventory().add(occupying)) player.drop(occupying, false);
            player.displayClientMessage(Component.translatable("aurorion_magia.ferro_preso"), true);
            return true;
        }
        return false;
    }

    private static boolean restore(ServerPlayer player, EquipmentSlot slot, ItemStack from, ItemStack to) {
        ItemStack carried = player.containerMenu.getCarried();
        if (ItemStack.isSameItemSameComponents(carried, from)) {
            player.containerMenu.setCarried(to.copy());
            player.setItemSlot(slot, carried.copy());
            player.containerMenu.broadcastChanges();
            return true;
        }
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack candidate = inventory.getItem(i);
            if (!ItemStack.isSameItemSameComponents(candidate, from)) continue;
            // O slot equipado que acabou de mudar tambem esta no inventario; nao e ele que buscamos.
            if (candidate == player.getItemBySlot(slot)) continue;
            inventory.setItem(i, to.copy());
            player.setItemSlot(slot, candidate.copy());
            player.containerMenu.broadcastChanges();
            return true;
        }
        return false;
    }
}
