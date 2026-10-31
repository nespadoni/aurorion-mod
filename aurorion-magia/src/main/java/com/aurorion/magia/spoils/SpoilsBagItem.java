package com.aurorion.magia.spoils;

import com.aurorion.magia.registry.MagiaComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * A <b>Mochila de Espolios</b>: tudo o que o Spoliatio arrancou de alguem, num item so.
 *
 * <p>O conteudo mora no {@code CONTAINER} vanilla (o mesmo do shulker), entao sobrevive a reinicio, a
 * baus e a morte de quem carrega sem nada nosso no meio. Abre como um bau de seis fileiras; esvaziada, some.
 *
 * <p>Nao entra em mochila de espolios nem em bundle/shulker ({@link #canFitInsideContainerItems}): uma
 * dentro da outra seria o jeito de esconder pertences de um sequestro sem que ninguem os ache. Destruida
 * no chao (cacto, explosao), derrama tudo, como o shulker — o que era de alguem nunca some com ela.
 */
public class SpoilsBagItem extends Item {
    /** Seis fileiras de bau. Quem carregava mais do que isso rende varias mochilas. */
    public static final int SIZE = 54;
    private static final int PREVIEW = 5;

    public SpoilsBagItem(Properties properties) {
        super(properties);
    }

    public static boolean isBag(ItemStack stack) {
        return stack.getItem() instanceof SpoilsBagItem;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack bag = player.getItemInHand(hand);
        if (!level.isClientSide) {
            player.openMenu(new SimpleMenuProvider((id, inventory, viewer) -> new SpoilsMenu(id, inventory, bag, hand),
                    bag.getHoverName()));
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BUNDLE_DROP_CONTENTS,
                    SoundSource.PLAYERS, .6F, .8F);
        }
        return InteractionResultHolder.sidedSuccess(bag, level.isClientSide);
    }

    @Override
    public Component getName(ItemStack stack) {
        SpoilsInfo info = stack.get(MagiaComponents.SPOILS.get());
        if (info == null) return super.getName(stack);
        return info.parts() > 1
                ? Component.translatable("item.aurorion_magia.mochila_espolios.de_parte", info.owner(), info.part(), info.parts())
                : Component.translatable("item.aurorion_magia.mochila_espolios.de", info.owner());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        ItemContainerContents contents = stack.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY);
        int shown = 0, total = 0;
        for (ItemStack item : contents.nonEmptyItems()) {
            if (shown < PREVIEW) {
                tooltip.add(Component.translatable("item.aurorion_magia.mochila_espolios.linha",
                        item.getHoverName(), item.getCount()).withStyle(ChatFormatting.GRAY));
                shown++;
            }
            total++;
        }
        if (total > shown) {
            tooltip.add(Component.translatable("item.aurorion_magia.mochila_espolios.mais", total - shown)
                    .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        }
        if (total == 0) {
            tooltip.add(Component.translatable("item.aurorion_magia.mochila_espolios.vazia").withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    @Override
    public boolean canFitInsideContainerItems(ItemStack stack) {
        return false;
    }

    @Override
    public void onDestroyed(ItemEntity entity, DamageSource source) {
        ItemContainerContents contents = entity.getItem().set(DataComponents.CONTAINER, ItemContainerContents.EMPTY);
        if (contents != null) ItemUtils.onContainerDestroyed(entity, contents.nonEmptyItemsCopy());
    }
}
