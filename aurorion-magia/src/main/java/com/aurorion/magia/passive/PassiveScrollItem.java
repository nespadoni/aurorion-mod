package com.aurorion.magia.passive;

import com.aurorion.magia.registry.MagiaComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * O <b>Pergaminho de Passiva</b>: o jeito de uma passiva chegar a um personagem.
 *
 * <p>Ele parece o pergaminho do Iron's (usa a mesma textura de proposito), mas faz o contrario dele:
 * o do Iron's e gasto <i>toda vez</i> que a magia sai, ou e transcrito para um livro. Este se le
 * <b>uma vez</b> e some — e o que ele deixa nao e uma magia no livro, e uma marca no personagem, que
 * nao precisa de mana, nao entra em recarga e nao pode ser desequipada.
 *
 * <p>Ter o pergaminho nao e ter a passiva: ler e que e. E ler de novo nao faz nada, e o pergaminho nao
 * se gasta a toa.
 *
 * <p>Morte definitiva apaga tudo ({@code CharacterResetEvent}), como a aula de magia. O pergaminho nao
 * volta: a marca era do personagem que morreu.
 */
public class PassiveScrollItem extends Item {
    public PassiveScrollItem(Properties properties) {
        super(properties);
    }

    /** A passiva escrita neste pergaminho, ou {@code null} num pergaminho em branco. */
    @Nullable
    public static Passive passiveOf(ItemStack stack) {
        ResourceLocation id = stack.get(MagiaComponents.PASSIVE.get());
        return id == null ? null : Passives.byId(id);
    }

    public static ItemStack of(Item item, Passive passive) {
        ItemStack stack = new ItemStack(item);
        stack.set(MagiaComponents.PASSIVE.get(), passive.id());
        return stack;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, net.minecraft.world.entity.player.Player player,
                                                  InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        Passive passive = passiveOf(stack);
        if (passive == null) {
            return InteractionResultHolder.pass(stack);
        }
        if (!(player instanceof ServerPlayer server)) {
            // O cliente nao sabe o que a pessoa ja tem; deixa o servidor decidir e nao anima nada.
            return InteractionResultHolder.consume(stack);
        }
        if (!Passives.grant(server, passive)) {
            server.displayClientMessage(Component.translatable("aurorion_magia.passiva_repetida",
                    passive.displayName()).withStyle(ChatFormatting.GRAY), true);
            return InteractionResultHolder.fail(stack);
        }

        stack.shrink(1);
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8F, 1.3F);
        return InteractionResultHolder.success(stack);
    }

    @Override
    public Component getName(ItemStack stack) {
        Passive passive = passiveOf(stack);
        return passive == null
                ? super.getName(stack)
                : Component.translatable("item.aurorion_magia.pergaminho_passiva.nomeado", passive.displayName());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        Passive passive = passiveOf(stack);
        if (passive == null) {
            lines.add(Component.translatable("item.aurorion_magia.pergaminho_passiva.vazio")
                    .withStyle(ChatFormatting.DARK_GRAY));
            return;
        }
        lines.add(passive.description());
        lines.add(Component.translatable("item.aurorion_magia.pergaminho_passiva.aviso")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
