package com.aurorion.magia.passive;

import com.aurorion.magia.AurorionMagia;
import com.aurorion.magia.compat.AccessorySlots;
import com.aurorion.magia.compat.AccessorySlots.Slots;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

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
 * criativo num slot do Curios passaria pela porta sem isto. A leitura deles e do {@link AccessorySlots}.
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
            extra = AccessorySlots.of(player);
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
            extra = AccessorySlots.of(player);
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
}
