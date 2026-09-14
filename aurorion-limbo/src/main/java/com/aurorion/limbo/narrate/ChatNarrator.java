package com.aurorion.limbo.narrate;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * O Limbo falando por chat e actionbar. O fundo do poco garantido.
 *
 * <p>Nao e o caminho desejado — ver {@link LimboNarrator} —, e sim o que garante que o mod funcione
 * sozinho. Tudo que da, sai pela <b>actionbar</b> e nao pelo chat: aviso de prazo e de coleira
 * competem com a conversa do servidor, e a actionbar e o unico lugar do HUD vanilla que nao some
 * sob 80 pessoas falando.
 *
 * <p>As excecoes vao para o chat de proposito: queda, saida e vencimento de prazo sao momentos que a
 * pessoa precisa poder rolar para tras e reler.
 */
class ChatNarrator implements LimboNarrator {
    @Override
    public void fall(ServerPlayer player) {
        player.sendSystemMessage(LimboText.fall().withStyle(ChatFormatting.DARK_RED));
    }

    @Override
    public void announceFall(MinecraftServer server, String name, boolean withName) {
        MutableComponent text = withName ? LimboText.fallPublicNamed(name) : LimboText.fallPublic();
        server.getPlayerList().broadcastSystemMessage(text.withStyle(ChatFormatting.DARK_RED), false);
    }

    @Override
    public void arrival(ServerPlayer player, long remainingMillis) {
        player.sendSystemMessage(LimboText.arrival(remainingMillis).withStyle(ChatFormatting.GRAY));
    }

    @Override
    public void deadlineBand(ServerPlayer player, long remainingMillis) {
        player.displayClientMessage(LimboText.deadline(remainingMillis).withStyle(ChatFormatting.GOLD), true);
    }

    @Override
    public void leash(ServerPlayer player) {
        player.displayClientMessage(LimboText.leash().withStyle(ChatFormatting.DARK_GRAY), true);
    }

    @Override
    public void doorWindowOpen(ServerPlayer player) {
        player.sendSystemMessage(LimboText.doorWindowOpen().withStyle(ChatFormatting.AQUA));
    }

    @Override
    public void doorAppeared(ServerPlayer player, BlockPos pos) {
        player.sendSystemMessage(LimboText.doorAppeared(pos).withStyle(ChatFormatting.AQUA));
    }

    @Override
    public void escaped(ServerPlayer player, int timesForgotten) {
        player.sendSystemMessage(LimboText.escaped(timesForgotten).withStyle(ChatFormatting.DARK_AQUA));
    }

    @Override
    public void rescued(ServerPlayer player) {
        player.sendSystemMessage(LimboText.rescued().withStyle(ChatFormatting.GOLD));
    }

    @Override
    public void expired(ServerPlayer player) {
        player.sendSystemMessage(LimboText.expired().withStyle(ChatFormatting.DARK_RED));
    }

    @Override
    public void passageOpened(ServerPlayer rescuer, String target, int cost) {
        rescuer.sendSystemMessage(LimboText.passageOpened(target, cost).withStyle(ChatFormatting.AQUA));
    }

    @Override
    public void passageCrossed(ServerPlayer rescuer) {
        rescuer.sendSystemMessage(LimboText.passageCrossed().withStyle(ChatFormatting.DARK_AQUA));
    }

    @Override
    public void bondUsed(ServerPlayer rescuer, String target) {
        rescuer.sendSystemMessage(LimboText.bondUsed(target).withStyle(ChatFormatting.GOLD));
    }
}
