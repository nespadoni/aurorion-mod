package com.aurorion.essentials.death;

import com.aurorion.core.character.CharacterData;
import com.aurorion.core.death.KeptOnDeath;
import com.aurorion.essentials.AurorionEssentials;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandlerModifiable;

import java.util.ArrayList;
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
        tag.put("PlayerData", player.saveWithoutId(new CompoundTag()));
        ListTag items = new ListTag();
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            add(items, "inventory", i, player.getInventory().getItem(i), player);
        }
        for (int i = 0; i < player.getEnderChestInventory().getContainerSize(); i++) {
            add(items, "ender", i, player.getEnderChestInventory().getItem(i), player);
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
        CompoundTag entry = new CompoundTag();
        entry.putString("Group", group); entry.putInt("Slot", slot);
        if (!stack.isEmpty()) entry.put("Stack", stack.save(player.registryAccess()));
        items.add(entry);
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
        List<ItemStack> keep = new ArrayList<>();
        for (int i = 0; i < target.getInventory().getContainerSize(); i++) {
            ItemStack current = target.getInventory().getItem(i);
            if (KeptOnDeath.is(current)) keep.add(current.copy());
        }
        List<Runnable> changes = new ArrayList<>();
        ListTag entries = tag.getList("Items", Tag.TAG_COMPOUND);
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
