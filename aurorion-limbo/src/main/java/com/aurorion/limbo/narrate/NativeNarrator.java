package com.aurorion.limbo.narrate;

import com.aurorion.limbo.network.LimboNetwork;
import com.aurorion.limbo.network.LimboNoticePayload;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** Apresentacao nativa: funciona sem mods de tipografia ou mensagens. */
class NativeNarrator extends ChatNarrator {
    @Override public void fall(ServerPlayer player) {
        if (!LimboNetwork.notice(player, LimboNoticePayload.FALL, LimboText.fall())) super.fall(player);
    }
    @Override public void announceFall(MinecraftServer server, String name, boolean withName) {
        var body = withName ? LimboText.fallPublicNamed(name) : LimboText.fallPublic();
        for (var player : server.getPlayerList().getPlayers()) {
            if (!LimboNetwork.notice(player, LimboNoticePayload.PUBLIC, body)) player.sendSystemMessage(body);
        }
    }
    @Override public void arrival(ServerPlayer player, long millis) {
        if (!LimboNetwork.notice(player, LimboNoticePayload.ARRIVAL, LimboText.arrival(millis))) super.arrival(player, millis);
    }
    @Override public void deadlineBand(ServerPlayer player, long millis) {
        if (!LimboNetwork.notice(player, LimboNoticePayload.DEADLINE, LimboText.deadline(millis))) super.deadlineBand(player, millis);
    }
    @Override public void leash(ServerPlayer player) {
        if (!LimboNetwork.notice(player, LimboNoticePayload.LEASH, LimboText.leash())) super.leash(player);
    }
    @Override public void doorWindowOpen(ServerPlayer player) {
        if (!LimboNetwork.notice(player, LimboNoticePayload.WINDOW, LimboText.doorWindowOpen())) super.doorWindowOpen(player);
    }
    @Override public void doorAppeared(ServerPlayer player, BlockPos pos) {
        if (!LimboNetwork.notice(player, LimboNoticePayload.DOOR, LimboText.doorAppeared(pos))) super.doorAppeared(player, pos);
    }
    @Override public void escaped(ServerPlayer player, int total) {
        if (!LimboNetwork.notice(player, LimboNoticePayload.ESCAPED, LimboText.escaped(total))) super.escaped(player, total);
    }
    @Override public void rescued(ServerPlayer player) {
        if (!LimboNetwork.notice(player, LimboNoticePayload.RESCUED, LimboText.rescued())) super.rescued(player);
    }
    @Override public void expired(ServerPlayer player) {
        if (!LimboNetwork.notice(player, LimboNoticePayload.EXPIRED, LimboText.expired())) super.expired(player);
    }
}
