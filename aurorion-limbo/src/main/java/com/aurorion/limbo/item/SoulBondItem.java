package com.aurorion.limbo.item;

import com.aurorion.limbo.rescue.RescueManager;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * O Vinculo de Alma: usado no exilado, traz os dois de volta.
 *
 * <p>Este item e o unico lugar do ecossistema onde uma acao de <b>jogador</b> decide o destino de
 * <b>outro</b>. Por isso nada aqui confia no cliente: o metodo so existe para dizer "houve um clique
 * neste alvo", e quem decide se o resgate acontece e o {@link RescueManager}, no servidor, checando
 * de novo tudo — se o alvo esta mesmo exilado, se quem usou esta mesmo no Limbo, se o resgate ja nao
 * foi feito por outra pessoa no mesmo tick.
 */
public class SoulBondItem extends Item {
    public SoulBondItem(Properties properties) {
        super(properties);
    }

    /**
     * Clique direito num jogador.
     *
     * <p>{@code interactLivingEntity} roda <b>nos dois lados</b>. O lado do cliente devolve
     * {@code SUCCESS} apenas para o braco balancar e o clique nao virar um clique "vazio" — nenhuma
     * regra acontece ali.
     */
    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        if (!(target instanceof ServerPlayer exiled) || !(player instanceof ServerPlayer rescuer)) {
            return player.level().isClientSide ? InteractionResult.SUCCESS : InteractionResult.PASS;
        }

        return RescueManager.completeRescue(rescuer, exiled, stack)
                ? InteractionResult.CONSUME
                : InteractionResult.FAIL;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.aurorion_limbo.vinculo_de_alma.desc")
                .withStyle(style -> style.withColor(0x82B0CD).withItalic(true)));
    }
}
