package com.aurorion.essentials.death;

import com.aurorion.core.character.CharacterData;
import com.aurorion.core.death.KeptOnDeath;
import com.aurorion.essentials.AurorionEssentials;
import com.aurorion.essentials.fakename.FakeName;
import com.aurorion.essentials.fakename.FakeNameRegistry;
import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Capture live objects only on the server thread. The detached NBT can then go to the IO worker. */
public final class DeathSnapshot {
    private DeathSnapshot() { }

    static CompoundTag metadata(ServerPlayer player, String cause) {
        return metadata(player, cause, UUID.randomUUID());
    }

    /**
     * @param id numa morte, o {@code DeathId} do core — o mesmo id que o espolio do Limbo usa para
     *           a mesma morte. Fora de uma morte (backup antes de restaurar), um id novo.
     */
    static CompoundTag metadata(ServerPlayer player, String cause, UUID id) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Format", 1);
        tag.putBoolean("CuriosComplete", false);
        tag.putInt("DataVersion", SharedConstants.getCurrentVersion().getDataVersion().getVersion());
        tag.putUUID("Id", id);
        tag.putUUID("Owner", player.getUUID());
        tag.putString("Name", player.getGameProfile().getName());
        // O nome pelo qual as pessoas chamavam quem morreu, gravado na hora. Quem procura no
        // historico procura por este nome, nao pelo nick da Mojang nem pela UUID — e trocar de nome
        // amanha nao pode reescrever o que aconteceu ontem, por isso fica no snapshot e nao e lido do
        // registro atual na hora da consulta.
        FakeName fakeName = FakeNameRegistry.get(player.getUUID());
        if (fakeName != null) {
            tag.putString("FakeName", fakeName.raw());
            tag.putString("FakeNamePlain", fakeName.plain());
        }
        // A conta nao diz qual personagem morreu quando uma conta tem varios (staff, NPCs). find, e nao
        // current: capturar uma morte nao pode criar identidade como efeito colateral.
        CharacterData.Character character = CharacterData.get(player.server).find(player.getUUID());
        if (character != null) {
            tag.putUUID("Character", character.id());
            if (character.named()) tag.putString("CharacterName", character.fullName());
        }
        tag.putLong("Time", System.currentTimeMillis());
        tag.putString("Cause", cause);
        tag.putString("Dimension", player.level().dimension().location().toString());
        tag.putDouble("X", player.getX()); tag.putDouble("Y", player.getY()); tag.putDouble("Z", player.getZ());
        tag.putFloat("Yaw", player.getYRot()); tag.putFloat("Pitch", player.getXRot());
        return tag;
    }

    public static CompoundTag capture(ServerPlayer player, String cause) {
        return capture(player, cause, UUID.randomUUID());
    }

    public static CompoundTag capture(ServerPlayer player, String cause, UUID id) {
        CompoundTag tag = metadata(player, cause, id);
        // Archive only. Never Player#load: that would rewind identity, lives, quests and capabilities.
        CompoundTag playerData = player.saveWithoutId(new CompoundTag());
        tag.put("PlayerData", playerData);
        // O PlayerData acima ja serializou inventario e ender chest. Os itens da lista saem dele (copia
        // sem o "Slot"), e nao de um segundo encode de cada ItemStack. Slot cujo item gravado nao bate
        // com o vivo (mod que mexe no layout ou no save do inventario) cai no encode de sempre.
        Inventory inventory = player.getInventory();
        boolean vanillaLayout = inventory.items.size() == 36 && inventory.armor.size() == 4
                && inventory.offhand.size() == 1 && inventory.getContainerSize() == 41;
        Map<Integer, CompoundTag> savedInventory = vanillaLayout
                ? bySlot(playerData.getList("Inventory", Tag.TAG_COMPOUND)) : Map.of();
        Map<Integer, CompoundTag> savedEnder = bySlot(playerData.getList("EnderItems", Tag.TAG_COMPOUND));
        ListTag items = new ListTag();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            add(items, "inventory", i, inventory.getItem(i), player, savedInventory.get(vanillaSlot(i)));
        }
        for (int i = 0; i < player.getEnderChestInventory().getContainerSize(); i++) {
            add(items, "ender", i, player.getEnderChestInventory().getItem(i), player, savedEnder.get(i));
        }
        add(items, "cursor", 0, player.containerMenu.getCarried(), player);
        try {
            tag.put("CuriosInventory", DeathCurios.archive(player));
            for (var group : DeathCurios.slots(player).entrySet()) {
                for (int i = 0; i < group.getValue().getSlots(); i++) {
                    add(items, group.getKey(), i, group.getValue().getStackInSlot(i), player);
                }
            }
            tag.putBoolean("CuriosComplete", true);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            tag.putBoolean("CuriosComplete", false);
            AurorionEssentials.LOGGER.error("Death history: Curios capture failed for {}", player.getUUID(), e);
        }
        // Accessories e outro sistema de slots, separado do Curios (Artifacts, Simple Hats). Falhar aqui
        // nao invalida o resto: so marca o registro, e a lista mostra "[Accessories incompleto]".
        tag.putBoolean("AccessoriesComplete", false);
        try {
            for (var group : DeathAccessories.slots(player).entrySet()) {
                for (int i = 0; i < group.getValue().getContainerSize(); i++) {
                    add(items, group.getKey(), i, group.getValue().getItem(i), player);
                }
            }
            tag.putBoolean("AccessoriesComplete", true);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            AurorionEssentials.LOGGER.error("Death history: Accessories capture failed for {}", player.getUUID(), e);
        }
        tag.put("Items", items);
        ListTag effects = new ListTag();
        var ops = player.registryAccess().createSerializationContext(NbtOps.INSTANCE);
        for (MobEffectInstance effect : player.getActiveEffects()) {
            effects.add(MobEffectInstance.CODEC.encodeStart(ops, effect).getOrThrow());
        }
        tag.put("Effects", effects);
        tag.putInt("XpLevel", player.experienceLevel);
        tag.putInt("XpTotal", player.totalExperience);
        tag.putFloat("XpProgress", player.experienceProgress);
        tag.putInt("Selected", player.getInventory().selected);
        tag.putInt("Food", player.getFoodData().getFoodLevel());
        tag.putFloat("Saturation", player.getFoodData().getSaturationLevel());
        return tag;
    }

    private static void add(ListTag items, String group, int slot, ItemStack stack, ServerPlayer player) {
        add(items, group, slot, stack, player, null);
    }

    /** @param saved o mesmo item ja serializado no PlayerData (com "Slot"), ou {@code null} */
    private static void add(ListTag items, String group, int slot, ItemStack stack, ServerPlayer player,
                            @Nullable CompoundTag saved) {
        CompoundTag entry = new CompoundTag();
        entry.putString("Group", group); entry.putInt("Slot", slot);
        if (!stack.isEmpty()) {
            Tag encoded;
            if (sameItem(saved, stack)) {
                CompoundTag copy = saved.copy();
                copy.remove("Slot");
                encoded = copy;
            } else {
                encoded = stack.save(player.registryAccess());
            }
            entry.put("Stack", encoded);
        }
        items.add(entry);
    }

    /** Lista do save vanilla ({@code Inventory#save}, {@code EnderItems}) indexada pelo byte "Slot". */
    private static Map<Integer, CompoundTag> bySlot(ListTag saved) {
        Map<Integer, CompoundTag> slots = new HashMap<>();
        for (int i = 0; i < saved.size(); i++) {
            CompoundTag item = saved.getCompound(i);
            slots.put(item.getByte("Slot") & 255, item);
        }
        return slots;
    }

    /** Indice do container ({@code Inventory#getItem}) para o "Slot" do save: 0-35, armadura 100+, mao secundaria 150. */
    private static int vanillaSlot(int index) {
        if (index < 36) return index;
        if (index < 40) return 100 + index - 36;
        return 150 + index - 40;
    }

    /** O item gravado e o mesmo do slot vivo (id e quantidade). Barato, e pega layout fora do vanilla. */
    private static boolean sameItem(@Nullable CompoundTag saved, ItemStack stack) {
        return saved != null
                && (saved.contains("count") ? saved.getInt("count") : 1) == stack.getCount()
                && saved.getString("id").equals(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
    }

    /**
     * O item estava no corpo e ficou com o jogador na morte ({@code aurorion_core:kept_on_death}).
     * Restaura-lo criaria uma segunda copia. O ender chest nao cai na morte, entao o que esta nele e
     * so guardado, nunca "mantido".
     */
    public static boolean keptOnDeath(CompoundTag entry, ItemStack stack) {
        return !entry.getString("Group").equals("ender") && KeptOnDeath.is(stack);
    }

    public static ItemStack item(CompoundTag entry, ServerPlayer player) {
        if (!entry.contains("Stack", Tag.TAG_COMPOUND)) return ItemStack.EMPTY;
        return ItemStack.parse(player.registryAccess(), entry.getCompound("Stack"))
                .orElseThrow(() -> new IllegalStateException("Item ausente ou incompativel; nada foi restaurado."));
    }

    /**
     * Prepare all decoding and destination checks before touching any player state.
     *
     * <p>Itens mantidos na morte nao voltam do snapshot, e os que o destinatario tem <b>agora</b> sao
     * devolvidos depois da troca: o jogador termina com o inventario da morte e os mesmos itens
     * mantidos que ja tinha, sem copia. O mesmo vale para o rollback, que passa por aqui.
     */
    public static Runnable prepareFull(CompoundTag tag, ServerPlayer target) throws ReflectiveOperationException {
        if (!tag.getBoolean("CuriosComplete")) throw new IllegalStateException("Snapshot incompleto de Curios.");
        Map<String, IItemHandlerModifiable> curios = DeathCurios.slots(target);
        ListTag entries = tag.getList("Items", Tag.TAG_COMPOUND);
        // Registro anterior a ponte com o Accessories nao tem esses grupos: os slots ficam como estao, e
        // a API nem e consultada (um Accessories quebrado nao impede restaurar um registro sem ele).
        boolean hasAccessories = entries.stream().anyMatch(raw -> DeathAccessories.owns(((CompoundTag) raw).getString("Group")));
        Map<String, Container> accessories = hasAccessories ? DeathAccessories.slots(target) : Map.of();
        List<ItemStack> keep = new ArrayList<>();
        for (int i = 0; i < target.getInventory().getContainerSize(); i++) {
            ItemStack current = target.getInventory().getItem(i);
            if (KeptOnDeath.is(current)) keep.add(current.copy());
        }
        List<Runnable> changes = new ArrayList<>();
        ItemStack cursor = ItemStack.EMPTY;
        int cursorSlot = -1;
        for (Tag raw : entries) {
            CompoundTag entry = (CompoundTag) raw;
            String group = entry.getString("Group"); int slot = entry.getInt("Slot");
            ItemStack decoded = item(entry, target);
            ItemStack stack = keptOnDeath(entry, decoded) ? ItemStack.EMPTY : decoded;
            if (group.equals("inventory")) {
                if (slot < 0 || slot >= target.getInventory().getContainerSize()) throw new IllegalStateException("Slot de inventario invalido.");
                changes.add(() -> target.getInventory().setItem(slot, stack.copy()));
                if (slot < 36 && stack.isEmpty() && cursorSlot < 0) cursorSlot = slot;
            } else if (group.equals("ender")) {
                if (slot < 0 || slot >= target.getEnderChestInventory().getContainerSize()) throw new IllegalStateException("Slot de ender chest invalido.");
                changes.add(() -> target.getEnderChestInventory().setItem(slot, stack.copy()));
            } else if (group.equals("cursor")) {
                cursor = stack;
            } else if (DeathAccessories.owns(group)) {
                Container container = accessories.get(group);
                if (container == null || slot < 0 || slot >= container.getContainerSize()) throw new IllegalStateException("Slot Accessories indisponivel: " + group + "/" + slot);
                changes.add(() -> container.setItem(slot, stack.copy()));
            } else {
                IItemHandlerModifiable handler = curios.get(group);
                if (handler == null || slot < 0 || slot >= handler.getSlots()) throw new IllegalStateException("Slot Curios indisponivel: " + group + "/" + slot);
                changes.add(() -> handler.setStackInSlot(slot, stack.copy()));
            }
        }
        if (!cursor.isEmpty()) {
            if (cursorSlot < 0) throw new IllegalStateException("Snapshot tem inventario cheio e item no cursor. Use recuperacao individual.");
            int destination = cursorSlot;
            ItemStack carried = cursor;
            changes.add(() -> target.getInventory().setItem(destination, carried.copy()));
        }
        List<MobEffectInstance> effects = new ArrayList<>();
        var ops = target.registryAccess().createSerializationContext(NbtOps.INSTANCE);
        for (Tag raw : tag.getList("Effects", Tag.TAG_COMPOUND)) {
            effects.add(MobEffectInstance.CODEC.parse(ops, raw).getOrThrow());
        }
        return () -> {
            changes.forEach(Runnable::run);
            keep.forEach(stack -> target.getInventory().placeItemBackInInventory(stack.copy()));
            target.removeAllEffects();
            effects.forEach(effect -> target.addEffect(new MobEffectInstance(effect)));
            target.experienceLevel = tag.getInt("XpLevel");
            target.totalExperience = tag.getInt("XpTotal");
            target.experienceProgress = tag.getFloat("XpProgress");
            target.setExperienceLevels(target.experienceLevel);
            target.getInventory().selected = Math.clamp(tag.getInt("Selected"), 0, 8);
            target.getFoodData().setFoodLevel(tag.getInt("Food"));
            target.getFoodData().setSaturation(tag.getFloat("Saturation"));
            target.getInventory().setChanged();
            target.inventoryMenu.broadcastChanges();
        };
    }
}
