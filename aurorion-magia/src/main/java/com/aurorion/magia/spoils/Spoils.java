package com.aurorion.magia.spoils;

import com.aurorion.magia.AurorionMagia;
import com.aurorion.magia.compat.AccessorySlots;
import com.aurorion.magia.compat.AccessorySlots.Slots;
import com.aurorion.magia.registry.MagiaComponents;
import com.aurorion.magia.registry.MagiaItems;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * O confisco do Spoliatio: tudo o que a pessoa carrega sai dela e vai, em mochilas, para quem conjurou.
 *
 * <p>"Tudo" e literal: inventario, hotbar, armadura, mao secundaria, o item preso no cursor, a grade de
 * craft 2x2 e todos os slots do Curios e do Accessories, cosmeticos inclusive. O telefone vai junto, e e
 * esse o ponto — quem foi levado fica sem ter como chamar ninguem. O bau do End nao: ele nao esta no corpo.
 *
 * <p>Nada e destruido nem copiado: cada stack sai de um slot e entra numa mochila. Mochila que nao cabe
 * no inventario de quem conjurou cai aos pes dele, so ele pode pegar e ela nao some com o tempo.
 * O log do servidor guarda o que foi tomado, para a staff resolver disputa.
 */
public final class Spoils {
    private Spoils() {
    }

    /** @return quantos stacks foram tomados */
    public static int confiscate(ServerPlayer caster, ServerPlayer victim) {
        // Fecha o que estiver aberto: o item no cursor e a grade 2x2 voltam ao inventario antes da coleta.
        victim.closeContainer();

        Map<String, Slots> extra;
        try {
            extra = AccessorySlots.of(victim);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            // Os acessorios ficam, mas o resto vai: sequestro interrompido por mod de acessorio seria pior.
            AurorionMagia.LOGGER.warn("Spoliatio: slots de acessorio de {} ilegiveis; so o inventario foi tomado.",
                    victim.getGameProfile().getName(), failure);
            extra = Map.of();
        }

        List<ItemStack> taken = new ArrayList<>();
        Inventory inventory = victim.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.removeItemNoUpdate(i);
            if (!stack.isEmpty()) taken.add(stack);
        }
        for (Slots slots : extra.values()) {
            for (int i = 0; i < slots.size(); i++) {
                ItemStack stack = slots.get(i);
                if (stack.isEmpty()) continue;
                slots.set(i, ItemStack.EMPTY);
                taken.add(stack);
            }
        }
        victim.inventoryMenu.broadcastChanges();
        if (taken.isEmpty()) return 0;

        String owner = victim.getDisplayName().getString();
        int parts = (taken.size() + SpoilsBagItem.SIZE - 1) / SpoilsBagItem.SIZE;
        for (int part = 0; part < parts; part++) {
            List<ItemStack> chunk = taken.subList(part * SpoilsBagItem.SIZE,
                    Math.min(taken.size(), (part + 1) * SpoilsBagItem.SIZE));
            give(caster, bag(owner, chunk, part + 1, parts));
        }
        caster.inventoryMenu.broadcastChanges();

        AurorionMagia.LOGGER.info("Spoliatio: {} tomou {} stack(s) de {} ({}) em {} {}: {}",
                caster.getGameProfile().getName(), taken.size(), victim.getGameProfile().getName(), owner,
                victim.level().dimension().location(), victim.blockPosition().toShortString(),
                taken.stream().map(s -> s.getCount() + "x " + BuiltInRegistries.ITEM.getKey(s.getItem()))
                        .collect(Collectors.joining(", ")));
        return taken.size();
    }

    private static ItemStack bag(String owner, List<ItemStack> items, int part, int parts) {
        ItemStack bag = new ItemStack(MagiaItems.SPOILS_BAG.get());
        bag.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(items));
        bag.set(MagiaComponents.SPOILS.get(), new SpoilsInfo(owner, part, parts));
        return bag;
    }

    private static void give(ServerPlayer caster, ItemStack bag) {
        if (caster.getInventory().add(bag)) return;
        ItemEntity drop = new ItemEntity(caster.level(), caster.getX(), caster.getY() + .5, caster.getZ(), bag);
        drop.setTarget(caster.getUUID());
        drop.setUnlimitedLifetime();
        drop.setPickUpDelay(10);
        caster.level().addFreshEntity(drop);
    }
}
