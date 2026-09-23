package com.aurorion.limbo.item;

import com.aurorion.limbo.narrate.LimboText;
import com.aurorion.limbo.recall.DeathRecall;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * O Relicario: chama de volta o que a ultima morte deixou no chao e ainda existe.
 *
 * <p>O item e gasto no clique, e o chamado termina alguns ticks depois (ver {@link DeathRecall}).
 * Se nada daquela morte restar no mundo, o Relicario volta para o bolso — pagar caro por um chao
 * vazio seria punicao, nao preco.
 */
public class ReliquaryItem extends Item {
    public ReliquaryItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(player instanceof ServerPlayer server)) return InteractionResultHolder.success(stack);

        if (!DeathRecall.recall(server, !server.hasInfiniteMaterials())) {
            return InteractionResultHolder.fail(stack);
        }
        stack.consume(1, server);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.aurorion_limbo.relicario.desc")
                .withStyle(style -> style.withColor(LimboText.AMBER).withItalic(true)));
    }
}
