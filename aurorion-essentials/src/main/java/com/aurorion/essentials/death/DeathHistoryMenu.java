package com.aurorion.essentials.death;

import com.aurorion.core.death.DeathClaims;
import com.aurorion.essentials.AurorionEssentials;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * A tela do {@code /deathhistory view}: um bau de seis linhas montado so no servidor (o cliente ve um
 * bau comum, entao funciona sem mod no cliente).
 *
 * <pre>
 * linhas 1-5   o conteudo da aba
 * linha 6      [Inventario] [Curios e acessorios] [Ender] -- [i] -- [<] [>] --
 * </pre>
 *
 * <p>Na aba do inventario cada item fica onde estava: armadura e mao secundaria na primeira linha (o
 * cursor no fim dela), o inventario no meio e a hotbar embaixo. Curios, Accessories e slots extras de
 * outros mods ficam juntos na segunda aba, so os ocupados, paginados.
 *
 * <p><b>Somente consulta</b>, a menos que a staff esteja no <b>criativo</b>: ai clicar num item pega
 * o item de verdade (sem o texto de indice da tela), como num bau. Cada item sai uma unica vez —
 * o mesmo recibo de {@code give}/{@code devolver}/{@code pegar} trava os outros caminhos, e a retirada
 * vai para o log. Item mantido na morte, ja recuperado ou de uma morte cujo espolio ja voltou pelo
 * Relicario nao sai por aqui.
 */
final class DeathHistoryMenu extends AbstractContainerMenu {
    enum Section { INVENTORY, ACCESSORIES, ENDER }

    private static final int ROWS = 6;
    private static final int CONTENT = 45;
    private static final int TAB_INVENTORY = 45, TAB_ACCESSORIES = 46, TAB_ENDER = 47, INFO = 49, PREVIOUS = 51, NEXT = 52;
    private static final int CURSOR_POSITION = 7;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm 'UTC'").withZone(ZoneOffset.UTC);

    private final SimpleContainer view = new SimpleContainer(ROWS * 9);
    private final ServerPlayer admin;
    private final UUID id;
    private final CompoundTag snapshot;
    private final ListTag entries;
    /** O item de verdade de cada entrada; {@code null} quando nao decodifica mais. */
    private final ItemStack[] originals;
    private final boolean restored;
    private final Set<Integer> recovered = new HashSet<>();
    /** Entradas da aba de acessorios: tudo o que nao e inventario comum, ender ou cursor. */
    private final List<Integer> others = new ArrayList<>();
    /** Qual entrada esta em cada slot de conteudo; -1 vazio. */
    private final int[] shown = new int[CONTENT];
    private Section section;
    private int page;

    DeathHistoryMenu(int window, Inventory inventory, ServerPlayer admin, UUID id, CompoundTag snapshot,
                     CompoundTag journal, Section section) {
        super(MenuType.GENERIC_9x6, window);
        this.admin = admin;
        this.id = id;
        this.snapshot = snapshot;
        this.entries = snapshot.getList("Items", Tag.TAG_COMPOUND);
        this.section = section;
        this.restored = journal.contains("full");
        for (String key : journal.getAllKeys()) {
            if (!key.startsWith("item_")) continue;
            try { recovered.add(Integer.parseInt(key.substring("item_".length()))); }
            catch (NumberFormatException ignored) { }
        }
        this.originals = new ItemStack[entries.size()];
        for (int i = 0; i < entries.size(); i++) {
            CompoundTag entry = entries.getCompound(i);
            try { originals[i] = DeathSnapshot.item(entry, admin); }
            catch (RuntimeException e) { originals[i] = null; }
            String group = entry.getString("Group");
            boolean common = group.equals("ender") || group.equals("cursor")
                    || group.equals("inventory") && inventoryPosition(entry.getInt("Slot")) >= 0;
            if (!common && (originals[i] == null || !originals[i].isEmpty())) others.add(i);
        }

        for (int row = 0; row < ROWS; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(view, column + row * 9, 8 + column * 18, 18 + row * 18) {
                    @Override public boolean mayPlace(ItemStack stack) { return false; }
                    @Override public boolean mayPickup(Player player) { return false; }
                });
            }
        }
        int offset = (ROWS - 4) * 18;
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(inventory, column + row * 9 + 9, 8 + column * 18, 103 + row * 18 + offset));
            }
        }
        for (int column = 0; column < 9; column++) addSlot(new Slot(inventory, column, 8 + column * 18, 161 + offset));
        render();
    }

    // --- Cliques ------------------------------------------------------------------------------

    @Override
    public void clicked(int slotId, int button, ClickType type, Player player) {
        if (slotId >= 0 && slotId < view.getContainerSize()) {
            // A tela nunca recebe item, e os botoes da ultima linha nao saem do lugar. O que o cliente
            // previu volta ao normal no broadcastChanges que o servidor faz depois deste clique.
            if (slotId < CONTENT) take(slotId, button, type);
            else navigate(slotId);
            return;
        }
        super.clicked(slotId, button, type, player);
    }

    private void take(int slotId, int button, ClickType type) {
        // Arrastar, duplo clique, Q: nunca tiram nada da tela, nem mostram aviso.
        if (type != ClickType.PICKUP && type != ClickType.CLONE && type != ClickType.QUICK_MOVE && type != ClickType.SWAP) return;
        int entry = shown[slotId];
        if (entry < 0) return;
        ItemStack original = originals[entry];
        if (original == null || original.isEmpty()) return;
        if (!admin.isCreative()) {
            hint("Somente consulta. No modo criativo da para pegar o item clicando nele.", ChatFormatting.GRAY);
            return;
        }
        if (restored || recovered.contains(entry) || RecoveryLedger.claimed(id, entry)) {
            hint("Este item ja foi recuperado deste registro.", ChatFormatting.RED);
            return;
        }
        if (DeathSnapshot.keptOnDeath(entries.getCompound(entry), original)) {
            hint("Mantido na morte: o jogador ja ficou com este item.", ChatFormatting.RED);
            return;
        }
        if (DeathClaims.get(admin.server).find(id) != null) {
            hint("O espolio desta morte ja voltou pelo Relicario. Para duplicar, use give ... confirm duplicar.", ChatFormatting.GOLD);
            return;
        }
        if (!RecoveryLedger.claim(id, entry)) {
            hint("Este item esta sendo recuperado por outro comando agora.", ChatFormatting.RED);
            return;
        }
        ItemStack stack = original.copy();
        Inventory inventory = admin.getInventory();
        boolean placed = switch (type) {
            case PICKUP, CLONE -> {
                if (!getCarried().isEmpty()) yield false;
                setCarried(stack);
                yield true;
            }
            case QUICK_MOVE -> {
                int free = inventory.getFreeSlot();
                if (free < 0) yield false;
                inventory.setItem(free, stack);
                yield true;
            }
            case SWAP -> {
                boolean target = button >= 0 && button < Inventory.getSelectionSize() || button == Inventory.SLOT_OFFHAND;
                if (!target || !inventory.getItem(button).isEmpty()) yield false;
                inventory.setItem(button, stack);
                yield true;
            }
            default -> false;
        };
        if (!placed) {
            RecoveryLedger.release(id, entry);
            return;
        }
        recovered.add(entry);
        render();

        String actor = admin.getGameProfile().getName();
        UUID adminId = admin.getUUID();
        AurorionEssentials.LOGGER.info("Death history: {} pegou no criativo o item #{} ({} x{}) do registro {}",
                actor, entry, stack.getItem(), stack.getCount(), id);
        DeathHistoryStore store = DeathHistoryEvents.store(admin.server);
        try {
            store.submit(() -> {
                try { store.recordTaken(id, entry, actor, adminId); }
                catch (Exception e) { AurorionEssentials.LOGGER.error("Death history: recibo da retirada #{} do registro {} nao foi gravado", entry, id, e); }
            });
        } catch (RuntimeException e) {
            // Sem recibo em disco o item continua travado nesta sessao pelo RecoveryLedger.
            AurorionEssentials.LOGGER.error("Death history: fila cheia; retirada #{} do registro {} sem recibo em disco", entry, id, e);
        }
    }

    private void navigate(int slotId) {
        switch (slotId) {
            case TAB_INVENTORY -> show(Section.INVENTORY);
            case TAB_ACCESSORIES -> show(Section.ACCESSORIES);
            case TAB_ENDER -> show(Section.ENDER);
            case PREVIOUS -> { if (page > 0) { page--; render(); } }
            case NEXT -> { if (page + 1 < pages()) { page++; render(); } }
            default -> { }
        }
    }

    private void show(Section next) {
        if (section == next) return;
        section = next;
        page = 0;
        render();
    }

    private int pages() {
        return section == Section.ACCESSORIES ? Math.max(1, (others.size() + CONTENT - 1) / CONTENT) : 1;
    }

    private void hint(String text, ChatFormatting color) {
        admin.displayClientMessage(Component.literal(text).withStyle(color), true);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        // Shift-clique no proprio inventario nao manda nada para a tela; o da tela e tratado no take.
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.hasPermissions(2);
    }

    // --- Desenho ------------------------------------------------------------------------------

    private void render() {
        view.clearContent();
        Arrays.fill(shown, -1);
        switch (section) {
            case INVENTORY -> {
                for (int i = 0; i < entries.size(); i++) {
                    CompoundTag entry = entries.getCompound(i);
                    String group = entry.getString("Group");
                    int position = group.equals("cursor") ? CURSOR_POSITION
                            : group.equals("inventory") ? inventoryPosition(entry.getInt("Slot")) : -1;
                    if (position >= 0) put(position, i);
                }
            }
            case ACCESSORIES -> {
                for (int slot = 0; slot < CONTENT; slot++) {
                    int index = page * CONTENT + slot;
                    if (index >= others.size()) break;
                    put(slot, others.get(index));
                }
            }
            case ENDER -> {
                for (int i = 0; i < entries.size(); i++) {
                    CompoundTag entry = entries.getCompound(i);
                    int slot = entry.getInt("Slot");
                    if (entry.getString("Group").equals("ender") && slot >= 0 && slot < CONTENT) put(slot, i);
                }
            }
        }

        ItemStack filler = button(Items.GRAY_STAINED_GLASS_PANE, Component.literal(" "), List.of(), false);
        for (int slot = CONTENT; slot < view.getContainerSize(); slot++) view.setItem(slot, filler.copy());
        view.setItem(TAB_INVENTORY, button(Items.CHEST, Component.literal("Inventario (" + count(Section.INVENTORY) + ")"),
                List.of(gray("Armadura, mao secundaria, inventario e hotbar.")), section == Section.INVENTORY));
        view.setItem(TAB_ACCESSORIES, button(Items.AMETHYST_SHARD, Component.literal("Curios e acessorios (" + others.size() + ")"),
                List.of(gray("Curios, Accessories e slots extras de outros mods.")), section == Section.ACCESSORIES));
        view.setItem(TAB_ENDER, button(Items.ENDER_CHEST, Component.literal("Ender chest (" + count(Section.ENDER) + ")"),
                List.of(gray("Nao cai na morte: devolver e pegar nao mexem aqui.")), section == Section.ENDER));
        view.setItem(INFO, info());
        if (page > 0) view.setItem(PREVIOUS, button(Items.ARROW, Component.literal("Pagina anterior"), List.of(), false));
        if (page + 1 < pages()) view.setItem(NEXT, button(Items.ARROW,
                Component.literal("Proxima pagina (" + (page + 2) + "/" + pages() + ")"), List.of(), false));
    }

    private void put(int position, int entry) {
        ItemStack display = display(entry);
        if (display.isEmpty()) return;
        view.setItem(position, display);
        shown[position] = entry;
    }

    private int count(Section of) {
        int total = 0;
        for (int i = 0; i < entries.size(); i++) {
            CompoundTag entry = entries.getCompound(i);
            String group = entry.getString("Group");
            boolean inSection = of == Section.ENDER ? group.equals("ender")
                    : group.equals("cursor") || group.equals("inventory") && inventoryPosition(entry.getInt("Slot")) >= 0;
            if (inSection && (originals[i] == null || !originals[i].isEmpty())) total++;
        }
        return total;
    }

    /** O item como a tela mostra: o original, com o indice do {@code give} e o estado no tooltip. */
    private ItemStack display(int entry) {
        CompoundTag raw = entries.getCompound(entry);
        String where = "#" + entry + " · " + raw.getString("Group") + "/" + raw.getInt("Slot");
        ItemStack original = originals[entry];
        if (original == null) {
            return button(Items.BARRIER, Component.literal("Item ausente ou incompativel"),
                    List.of(gray(where), gray("O mod do item saiu do pack ou o item mudou de formato.")), false);
        }
        if (original.isEmpty()) return ItemStack.EMPTY;
        boolean kept = DeathSnapshot.keptOnDeath(raw, original);
        boolean gone = restored || recovered.contains(entry) || RecoveryLedger.claimed(id, entry);
        // No criativo a tela e um bau: o que ja saiu some do slot. Na consulta fica, marcado.
        if (gone && !kept && admin.isCreative()) return ItemStack.EMPTY;
        ItemStack copy = original.copy();
        ItemLore lore = copy.getOrDefault(DataComponents.LORE, ItemLore.EMPTY).withLineAdded(gray(where));
        if (kept) lore = lore.withLineAdded(line("Mantido na morte: nao volta", ChatFormatting.DARK_GRAY));
        else if (gone) lore = lore.withLineAdded(line("Ja recuperado", ChatFormatting.RED));
        else if (admin.isCreative()) lore = lore.withLineAdded(line("Clique para pegar", ChatFormatting.GREEN));
        copy.set(DataComponents.LORE, lore);
        return copy;
    }

    private ItemStack info() {
        List<Component> lines = new ArrayList<>();
        lines.add(gray(snapshot.getString("Cause")));
        lines.add(gray(DATE.format(Instant.ofEpochMilli(snapshot.getLong("Time")))));
        lines.add(gray(snapshot.getString("Dimension") + " " + (int) Math.floor(snapshot.getDouble("X")) + " "
                + (int) Math.floor(snapshot.getDouble("Y")) + " " + (int) Math.floor(snapshot.getDouble("Z"))));
        lines.add(gray("Conta: " + snapshot.getString("Name") + " | XP nivel " + snapshot.getInt("XpLevel")
                + " | Fome " + snapshot.getInt("Food")));
        var ops = admin.registryAccess().createSerializationContext(NbtOps.INSTANCE);
        for (Tag raw : snapshot.getList("Effects", Tag.TAG_COMPOUND)) {
            MobEffectInstance.CODEC.parse(ops, raw).result().ifPresent(effect -> lines.add(Component.literal("Efeito: ")
                    .append(effect.getEffect().value().getDisplayName())
                    .append(" " + (effect.getAmplifier() + 1) + " (" + effect.getDuration() / 20 + "s)")
                    .withStyle(s -> s.withItalic(false).withColor(ChatFormatting.DARK_AQUA))));
        }
        if (!snapshot.getBoolean("CuriosComplete")) lines.add(line("Curios incompleto neste registro", ChatFormatting.RED));
        if (snapshot.contains("AccessoriesComplete") && !snapshot.getBoolean("AccessoriesComplete"))
            lines.add(line("Accessories incompleto neste registro", ChatFormatting.RED));
        if (DeathClaims.get(admin.server).find(id) != null) lines.add(line("O espolio ja voltou pelo Relicario", ChatFormatting.GOLD));
        if (restored) lines.add(line("Registro ja restaurado por completo", ChatFormatting.RED));
        else if (!recovered.isEmpty()) lines.add(line(recovered.size() + " item(ns) ja recuperado(s)", ChatFormatting.RED));
        lines.add(admin.isCreative() ? line("Criativo: clique num item para pega-lo", ChatFormatting.GREEN)
                : line("Somente consulta (no criativo da para pegar)", ChatFormatting.GRAY));
        return button(Items.PAPER, Component.literal("Morte de " + displayName(snapshot)), lines, false);
    }

    // --- Utilidades ---------------------------------------------------------------------------

    /**
     * Onde cada slot do inventario aparece na aba: armadura (39 cabeca .. 36 pes) e mao secundaria na
     * primeira linha, o inventario (9-35) no meio e a hotbar (0-8) na ultima. -1 para slot que nao e do
     * inventario vanilla (inventario expandido por mod), que vai para a aba de acessorios.
     */
    static int inventoryPosition(int slot) {
        if (slot >= 0 && slot < 9) return 36 + slot;
        if (slot >= 9 && slot < Inventory.INVENTORY_SIZE) return slot;
        return switch (slot) {
            case 39 -> 0;
            case 38 -> 1;
            case 37 -> 2;
            case 36 -> 3;
            case Inventory.SLOT_OFFHAND -> 5;
            default -> -1;
        };
    }

    static String displayName(CompoundTag snapshot) {
        if (snapshot.contains("FakeNamePlain")) return snapshot.getString("FakeNamePlain");
        if (snapshot.contains("CharacterName")) return snapshot.getString("CharacterName");
        return snapshot.getString("Name");
    }

    private static ItemStack button(Item item, Component name, List<Component> lore, boolean glint) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.CUSTOM_NAME, name.copy().withStyle(s -> s.withItalic(false)));
        if (!lore.isEmpty()) stack.set(DataComponents.LORE, new ItemLore(lore));
        if (glint) stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        return stack;
    }

    private static Component gray(String text) {
        return line(text, ChatFormatting.GRAY);
    }

    private static Component line(String text, ChatFormatting color) {
        return Component.literal(text).withStyle(s -> s.withItalic(false).withColor(color));
    }
}
