package com.aurorion.profissoes.api;

import com.aurorion.profissoes.data.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.common.util.FakePlayer;

public final class ProfessionApi {
    public static volatile Profession clientProfession = Profession.NONE;
    private ProfessionApi() {}
    public static Profession of(Player player) {
        if (player.level().isClientSide()) return clientProfession;
        return player instanceof ServerPlayer serverPlayer && !(player instanceof FakePlayer)
                ? ProfessionData.get(serverPlayer.server).of(player.getUUID()) : Profession.NONE;
    }
    public static boolean has(Player player, Profession profession) { return of(player) == profession; }
    public static boolean staff(Player player) { return player.isCreative() && player.hasPermissions(2) && !(player instanceof FakePlayer); }
}
