package com.aurorion.limbo.item;

import com.aurorion.limbo.narrate.LimboText;
import com.aurorion.limbo.recall.DeathRecall;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * O Fio da Volta: segurar puxa o fio, e o fio leva ao lugar exato da ultima morte.
 *
 * <p>Nao e instantaneo, e de proposito. Um teleporte de um clique e uma fuga de combate; dois
 * segundos parado, com o braco puxando, sao tempo para quem estava na briga reagir.
 *
 * <p>A regra toda mora no {@link DeathRecall}, no servidor. O item so diz "terminou de puxar".
 */
public class ReturnThreadItem extends Item {
    private static final int USE_TICKS = 40;

    public ReturnThreadItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        // O cliente nao sabe onde a pessoa morreu; so o servidor recusa cedo. Se ele recusar, o
        // cliente anima os dois segundos e nada acontece no fim — o finishUsingItem confere de novo.
        if (player instanceof ServerPlayer server && !DeathRecall.canReturn(server)) {
            return InteractionResultHolder.fail(player.getItemInHand(hand));
        }
        return ItemUtils.startUsingInstantly(level, player, hand);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (entity instanceof ServerPlayer player && DeathRecall.returnTo(player)) {
            stack.consume(1, player);
        }
        return stack;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return USE_TICKS;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.BOW;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.aurorion_limbo.fio_da_volta.desc")
                .withStyle(style -> style.withColor(LimboText.COLD).withItalic(true)));
    }
}
