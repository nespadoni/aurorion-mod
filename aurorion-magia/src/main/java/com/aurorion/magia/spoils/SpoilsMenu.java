package com.aurorion.magia.spoils;

import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;

/**
 * A mochila aberta: um bau de seis fileiras cujo conteudo e o componente da propria mochila.
 *
 * <p>Usa o {@code GENERIC_9x6} do vanilla, entao o cliente abre um bau comum — nada a registrar la. O
 * servidor e quem guarda as regras:
 *
 * <ul>
 *   <li><b>Escrita a cada clique</b> no item que esta na mao. Nao ha copia do conteudo esperando o
 *       menu fechar, que e onde mora o duplicador classico de mochila (abre, larga a mochila, tira os
 *       itens: dois donos para a mesma coisa).</li>
 *   <li><b>A mochila aberta nao se mexe</b>: clicar nela, troca-la com o numero da hotbar ou com o F
 *       e ignorado. E {@link #stillValid} fecha o menu se ela sair da mao por qualquer outro caminho
 *       (morte, outro mod) — e daquele ponto em diante nenhum clique e aceito.</li>
 *   <li><b>Mochila nao entra em mochila</b>.</li>
 * </ul>
 */
final class SpoilsMenu extends ChestMenu {
    private static final int ROWS = 6;
    private static final int OFFHAND_SWAP = 40;

    private final ItemStack bag;
    private final InteractionHand hand;
    private final SimpleContainer box;
    private final Player owner;

    SpoilsMenu(int id, Inventory inventory, ItemStack bag, InteractionHand hand) {
        this(id, inventory, bag, hand, load(bag));
    }

    private SpoilsMenu(int id, Inventory inventory, ItemStack bag, InteractionHand hand, SimpleContainer box) {
        super(MenuType.GENERIC_9x6, id, inventory, box, ROWS);
        this.bag = bag;
        this.hand = hand;
        this.box = box;
        this.owner = inventory.player;
        box.addListener(changed -> save());
    }

    private static SimpleContainer load(ItemStack bag) {
        NonNullList<ItemStack> items = NonNullList.withSize(SpoilsBagItem.SIZE, ItemStack.EMPTY);
        bag.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).copyInto(items);
        SimpleContainer box = new SimpleContainer(SpoilsBagItem.SIZE);
        for (int i = 0; i < items.size(); i++) box.setItem(i, items.get(i));
        return box;
    }

    private boolean holding() {
        return !bag.isEmpty() && owner.getItemInHand(hand) == bag;
    }

    private void save() {
        if (holding()) bag.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(box.getItems()));
    }

    @Override
    public boolean stillValid(Player player) {
        return player == owner && holding();
    }

    @Override
    public void clicked(int slotId, int button, ClickType type, Player player) {
        if (!stillValid(player) || refused(slotId, button, type, player)) return;
        super.clicked(slotId, button, type, player);
    }

    private boolean refused(int slotId, int button, ClickType type, Player player) {
        boolean inSlots = slotId >= 0 && slotId < slots.size();
        boolean inBox = inSlots && slotId < SpoilsBagItem.SIZE;
        if (inSlots && slots.get(slotId).getItem() == bag) return true;
        if (type == ClickType.SWAP) {
            ItemStack swapped = button == OFFHAND_SWAP || (button >= 0 && button < 9)
                    ? player.getInventory().getItem(button) : ItemStack.EMPTY;
            if (swapped == bag || inBox && SpoilsBagItem.isBag(swapped)) return true;
        }
        if (SpoilsBagItem.isBag(getCarried()) && (inBox || type == ClickType.QUICK_CRAFT)) return true;
        return type == ClickType.QUICK_MOVE && inSlots && !inBox && SpoilsBagItem.isBag(slots.get(slotId).getItem());
    }

    /** Ja salvo a cada clique; aqui so some a mochila que ficou vazia. */
    @Override
    public void removed(Player player) {
        super.removed(player);
        save();
        if (holding() && box.isEmpty()) bag.shrink(1);
    }
}
