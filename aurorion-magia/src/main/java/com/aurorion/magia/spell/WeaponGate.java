package com.aurorion.magia.spell;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.SwordItem;

/**
 * Magias que so saem com a arma certa na mao: a Justica Demaciana pede espada, e as flechas do Sova
 * pedem arco (ou besta — o que atira flecha).
 *
 * <p>Espada e o que estiver na tag {@code minecraft:swords} ou estender {@link SwordItem} — cobre as
 * espadas do Simply Swords e de outros mods do pack. Mob conjurador passa sempre: a IA nao escolhe o
 * que segura.
 */
public final class WeaponGate {
    private WeaponGate() {
    }

    public static boolean holdsSword(LivingEntity caster) {
        ItemStack stack = caster.getMainHandItem();
        return !stack.isEmpty() && (stack.is(ItemTags.SWORDS) || stack.getItem() instanceof SwordItem);
    }

    public static boolean holdsBow(LivingEntity caster) {
        return caster.getMainHandItem().getItem() instanceof ProjectileWeaponItem
                || caster.getOffhandItem().getItem() instanceof ProjectileWeaponItem;
    }

    /** Confere a espada e avisa na barra de acao quando falta. */
    public static boolean requireSword(LivingEntity caster) {
        if (!(caster instanceof ServerPlayer player) || holdsSword(caster)) return true;
        player.displayClientMessage(Component.translatable("aurorion_magia.precisa_espada").withStyle(ChatFormatting.GOLD), true);
        return false;
    }

    /** Confere o arco e avisa na barra de acao quando falta. */
    public static boolean requireBow(LivingEntity caster) {
        if (!(caster instanceof ServerPlayer player) || holdsBow(caster)) return true;
        player.displayClientMessage(Component.translatable("aurorion_magia.precisa_arco").withStyle(ChatFormatting.AQUA), true);
        return false;
    }
}
