package com.aurorion.ethereal.house;

import com.aurorion.core.character.CharacterData;
import com.aurorion.ethereal.AurorionEthereal;
import com.aurorion.ethereal.block.entity.HouseMuralBlockEntity;
import com.aurorion.ethereal.compat.HouseEconomyBridge;
import com.aurorion.ethereal.compat.HouseLivesBridge;
import com.aurorion.ethereal.config.EtherealConfig;
import com.aurorion.ethereal.network.HouseMuralPayloads;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** Regras autoritativas do mural, executadas somente no thread do servidor e apenas por interação. */
public final class HouseMuralManager {
    private static final double MAX_DISTANCE_SQR = 64.0D;
    private static final long DAY_MILLIS = 86_400_000L;

    private HouseMuralManager() { }

    public static void open(ServerPlayer player, BlockPos pos) {
        HouseMuralBlockEntity mural = accessibleMural(player, pos, true);
        if (mural == null) return;
        ResourceLocation houseId = mural.house();
        House house = houseId == null ? null : HouseCatalog.get(houseId);
        if (house == null) {
            tell(player, "aurorion_ethereal.mural.error.unlinked", ChatFormatting.RED);
            return;
        }
        if (!player.connection.hasChannel(HouseMuralPayloads.Open.TYPE.id())) return;

        HouseUpgradeData.State upgrade = HouseUpgradeData.get(player.server).state(houseId);
        HouseEconomyBridge.Snapshot economy = HouseEconomyBridge.snapshot(player.server, houseId);
        long remaining = remainingMillis(upgrade, System.currentTimeMillis());
        PacketDistributor.sendToPlayer(player, new HouseMuralPayloads.Open(
                pos, house.name(), house.color(), economy.available(), economy.balance(), economy.capacity(),
                economy.vaultLevel(), upgrade.protectorLevel(), remaining, HouseLivesBridge.available()));
    }

    public static void openProtector(ServerPlayer player, BlockPos pos) {
        HouseMuralBlockEntity mural = accessibleMural(player, pos, false);
        if (mural == null) return;
        ResourceLocation houseId = mural.house();
        House house = houseId == null ? null : HouseCatalog.get(houseId);
        if (house == null || !memberOf(player, houseId)) {
            tell(player, "aurorion_ethereal.mural.error.not_member", ChatFormatting.RED);
            return;
        }

        HouseUpgradeData.State state = HouseUpgradeData.get(player.server).state(houseId);
        if (state.protectorLevel() <= 0) {
            tell(player, "aurorion_ethereal.mural.protector.locked", ChatFormatting.DARK_RED);
            return;
        }
        if (!HouseLivesBridge.available()) {
            tell(player, "aurorion_ethereal.mural.error.lives_unavailable", ChatFormatting.RED);
            return;
        }
        if (remainingMillis(state, System.currentTimeMillis()) > 0L) {
            tell(player, "aurorion_ethereal.mural.protector.cooldown", ChatFormatting.DARK_RED);
            return;
        }
        if (!player.connection.hasChannel(HouseMuralPayloads.OpenTargets.TYPE.id())) return;

        CharacterData characters = CharacterData.get(player.server);
        int maxLives = HouseLivesBridge.maxLives();
        List<HouseMuralPayloads.Target> targets = characters.characters().entrySet().stream()
                .filter(entry -> entry.getValue().named() && !entry.getValue().dead())
                .sorted(Comparator.comparing(entry -> entry.getValue().fullName(), String.CASE_INSENSITIVE_ORDER))
                .limit(HouseMuralPayloads.OpenTargets.MAX_TARGETS)
                .map(entry -> new HouseMuralPayloads.Target(entry.getKey(), entry.getValue().fullName(),
                        HouseLivesBridge.livesOf(player.server, entry.getKey()), maxLives,
                        player.server.getPlayerList().getPlayer(entry.getKey()) != null))
                .toList();
        PacketDistributor.sendToPlayer(player, new HouseMuralPayloads.OpenTargets(pos, house.name(), targets));
    }

    public static void grantLife(ServerPlayer actor, BlockPos pos, UUID target) {
        HouseMuralBlockEntity mural = accessibleMural(actor, pos, false);
        if (mural == null) return;
        ResourceLocation houseId = mural.house();
        House house = houseId == null ? null : HouseCatalog.get(houseId);
        if (house == null || !memberOf(actor, houseId)) {
            tell(actor, "aurorion_ethereal.mural.error.not_member", ChatFormatting.RED);
            return;
        }

        HouseUpgradeData upgrades = HouseUpgradeData.get(actor.server);
        HouseUpgradeData.State state = upgrades.state(houseId);
        long now = System.currentTimeMillis();
        if (state.protectorLevel() <= 0 || remainingMillis(state, now) > 0L) {
            tell(actor, "aurorion_ethereal.mural.protector.unavailable", ChatFormatting.DARK_RED);
            return;
        }

        CharacterData.Character character = CharacterData.get(actor.server).find(target);
        if (character == null || !character.named() || character.dead()
                || HouseLivesBridge.livesOf(actor.server, target) >= HouseLivesBridge.maxLives()) {
            tell(actor, "aurorion_ethereal.mural.protector.invalid_target", ChatFormatting.RED);
            return;
        }
        if (!HouseLivesBridge.addOne(actor.server, target)) {
            tell(actor, "aurorion_ethereal.mural.protector.failed", ChatFormatting.RED);
            return;
        }

        upgrades.recordLifeGrant(houseId, now);
        tell(actor, "aurorion_ethereal.mural.protector.success", ChatFormatting.DARK_PURPLE, character.fullName());
        notifyRecipient(actor.server, target, house);
        audit(actor, house, character.fullName(), pos);
    }

    public static long cooldownMillis(int protectorLevel) {
        int days = protectorLevel >= 2
                ? EtherealConfig.PROTECTOR_II_COOLDOWN_DAYS.get()
                : EtherealConfig.PROTECTOR_I_COOLDOWN_DAYS.get();
        return days * DAY_MILLIS;
    }

    private static long remainingMillis(HouseUpgradeData.State state, long now) {
        if (state.protectorLevel() <= 0 || state.lastLifeGrantAt() <= 0L) return 0L;
        return Math.max(0L, state.lastLifeGrantAt() + cooldownMillis(state.protectorLevel()) - now);
    }

    @Nullable
    private static HouseMuralBlockEntity accessibleMural(ServerPlayer player, BlockPos pos, boolean allowStaff) {
        if (player.blockPosition().distSqr(pos) > MAX_DISTANCE_SQR) {
            tell(player, "aurorion_ethereal.mural.error.too_far", ChatFormatting.RED);
            return null;
        }
        if (!(player.level().getBlockEntity(pos) instanceof HouseMuralBlockEntity mural)) return null;
        ResourceLocation house = mural.house();
        if (house != null && !memberOf(player, house) && !(allowStaff && player.hasPermissions(2))) {
            tell(player, "aurorion_ethereal.mural.error.not_member", ChatFormatting.RED);
            return null;
        }
        return mural;
    }

    private static boolean memberOf(ServerPlayer player, ResourceLocation house) {
        return house.equals(HouseManager.houseIdOf(player.server, player.getUUID()));
    }

    private static void notifyRecipient(MinecraftServer server, UUID target, House house) {
        ServerPlayer recipient = server.getPlayerList().getPlayer(target);
        if (recipient != null) {
            recipient.sendSystemMessage(Component.translatable(
                    "aurorion_ethereal.mural.protector.received", house.coloredName())
                    .withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.BOLD));
        }
    }

    private static void audit(ServerPlayer actor, House house, String target, BlockPos pos) {
        Component alert = Component.translatable("aurorion_ethereal.mural.protector.admin_alert",
                actor.getDisplayName(), house.coloredName(), target,
                pos.getX(), pos.getY(), pos.getZ())
                .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD);
        for (ServerPlayer online : actor.server.getPlayerList().getPlayers()) {
            if (online.hasPermissions(2)) online.sendSystemMessage(alert);
        }
        AurorionEthereal.LOGGER.warn("PROTETOR_ARCANO actor={} house={} target={} pos={}",
                actor.getGameProfile().getName(), house.id(), target, pos.toShortString());
    }

    private static void tell(ServerPlayer player, String key, ChatFormatting color, Object... args) {
        player.sendSystemMessage(Component.translatable(key, args).withStyle(color));
    }
}
