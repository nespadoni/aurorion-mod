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
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/** Regras autoritativas do mural, executadas somente no thread do servidor e apenas por interação. */
public final class HouseMuralManager {
    private static final double MAX_DISTANCE_SQR = 64.0D;
    private static final long DAY_MILLIS = 86_400_000L;
    /**
     * Intervalo minimo entre acoes do cofre por jogador. Cada acao custa reflexao e o reenvio do
     * estado inteiro; um cliente modificado mandando pacote por tick nao pode transformar isso em
     * carga. Chave fraca: o ServerPlayer some do mapa quando sai ou renasce.
     */
    private static final long ACTION_INTERVAL_MILLIS = 250L;
    private static final Map<ServerPlayer, Long> LAST_ACTION = new WeakHashMap<>();

    private HouseMuralManager() { }

    public static void open(ServerPlayer player, BlockPos pos) {
        open(player, pos, HouseMuralPayloads.TAB_VAULT, Component.empty(), false);
    }

    private static void open(ServerPlayer player, BlockPos pos, int tab, Component notice, boolean error) {
        HouseMuralBlockEntity mural = accessibleMural(player, pos, true);
        if (mural == null) return;
        ResourceLocation houseId = mural.house();
        House house = houseId == null ? null : HouseCatalog.get(houseId);
        if (house == null) {
            tell(player, "aurorion_ethereal.mural.error.unlinked", ChatFormatting.RED);
            return;
        }
        if (!player.connection.hasChannel(HouseMuralPayloads.Open.TYPE.id())) return;

        HouseUpgradeData upgrades = HouseUpgradeData.get(player.server);
        HouseUpgradeData.State upgrade = upgrades.state(houseId);
        HouseEconomyBridge.Snapshot economy = HouseEconomyBridge.snapshot(player.server, houseId, player.getUUID());
        long now = System.currentTimeMillis();
        long remaining = remainingMillis(upgrade, now);
        List<HouseMuralPayloads.Tier> tiers = economy.tiers().stream()
                .limit(HouseMuralPayloads.Tier.MAX)
                .map(tier -> new HouseMuralPayloads.Tier(tier.capacity(), tier.price()))
                .toList();
        List<HouseMuralPayloads.Movement> movements = upgrades.movements(houseId).stream()
                .limit(HouseMuralPayloads.Movement.MAX)
                .map(movement -> new HouseMuralPayloads.Movement(clip(movement.actor()), movement.kind().ordinal(),
                        movement.amount(), Math.max(0L, now - movement.at())))
                .toList();
        PacketDistributor.sendToPlayer(player, new HouseMuralPayloads.Open(
                pos, house.name(), house.color(), memberOf(player, houseId), economy.available(), economy.balance(), economy.capacity(),
                economy.vaultLevel(), economy.wallet(), economy.salary(), economy.salaryDays(),
                economy.nextSalaryMillis(), tiers, movements,
                upgrade.protectorLevel(), remaining, HouseLivesBridge.available(), tab, notice, error));
    }

    /** Depositar ou sacar: qualquer membro pode, e toda operacao fica no log e no historico do mural. */
    public static void transfer(ServerPlayer player, BlockPos pos, boolean deposit, long amount) {
        if (throttled(player)) return;
        ResourceLocation houseId = memberHouse(player, pos);
        if (houseId == null) return;
        long result = amount <= 0L ? HouseEconomyBridge.ERR_INVALID : deposit
                ? HouseEconomyBridge.deposit(player.server, player.getUUID(), houseId, amount)
                : HouseEconomyBridge.withdraw(player.server, player.getUUID(), houseId, amount);
        if (result <= 0L) {
            reply(player, pos, HouseMuralPayloads.TAB_VAULT, failure(result), true);
            return;
        }

        String actor = actorName(player);
        HouseUpgradeData.get(player.server).recordMovement(houseId, new HouseUpgradeData.Movement(
                System.currentTimeMillis(), actor,
                deposit ? HouseUpgradeData.Kind.DEPOSIT : HouseUpgradeData.Kind.WITHDRAW, result));
        AurorionEthereal.LOGGER.info("COFRE_CASA {} actor={} personagem={} house={} quantia={}",
                deposit ? "deposito" : "saque", player.getGameProfile().getName(), actor, houseId, result);

        Component notice = deposit && result < amount
                ? Component.translatable("aurorion_ethereal.mural.vault.deposit_partial",
                        HouseMuralPayloads.formatMoney(result))
                : Component.translatable(deposit ? "aurorion_ethereal.mural.vault.deposited"
                        : "aurorion_ethereal.mural.vault.withdrawn", HouseMuralPayloads.formatMoney(result));
        reply(player, pos, HouseMuralPayloads.TAB_VAULT, notice, false);
    }

    /** Melhoria paga com o saldo do cofre; o preco citado pela tela precisa bater com o atual. */
    public static void buyVaultUpgrade(ServerPlayer player, BlockPos pos, int nextLevel, long quotedPrice) {
        if (throttled(player)) return;
        ResourceLocation houseId = memberHouse(player, pos);
        if (houseId == null) return;
        if (!HouseEconomyBridge.buyVaultUpgrade(player.server, houseId, nextLevel, quotedPrice)) {
            reply(player, pos, HouseMuralPayloads.TAB_UPGRADES,
                    Component.translatable("aurorion_ethereal.mural.upgrade.failed"), true);
            return;
        }

        String actor = actorName(player);
        HouseUpgradeData.get(player.server).recordMovement(houseId, new HouseUpgradeData.Movement(
                System.currentTimeMillis(), actor, HouseUpgradeData.Kind.UPGRADE, quotedPrice));
        AurorionEthereal.LOGGER.info("COFRE_CASA melhoria actor={} personagem={} house={} nivel={} preco={}",
                player.getGameProfile().getName(), actor, houseId, nextLevel, quotedPrice);
        reply(player, pos, HouseMuralPayloads.TAB_UPGRADES,
                Component.translatable("aurorion_ethereal.mural.upgrade.bought", nextLevel), false);
    }

    private static boolean throttled(ServerPlayer player) {
        long now = System.currentTimeMillis();
        Long last = LAST_ACTION.get(player);
        if (last != null && now - last < ACTION_INTERVAL_MILLIS) return true;
        LAST_ACTION.put(player, now);
        return false;
    }

    private static void reply(ServerPlayer player, BlockPos pos, int tab, Component notice, boolean error) {
        open(player, pos, tab, notice, error);
    }

    private static Component failure(long code) {
        String key;
        if (code == HouseEconomyBridge.ERR_WALLET_LOW) key = "wallet_low";
        else if (code == HouseEconomyBridge.ERR_VAULT_FULL) key = "vault_full";
        else if (code == HouseEconomyBridge.ERR_VAULT_LOW) key = "vault_low";
        else if (code == HouseEconomyBridge.ERR_WALLET_FULL) key = "wallet_full";
        else if (code == HouseEconomyBridge.ERR_UNAVAILABLE) key = "unavailable";
        else key = "invalid";
        return Component.translatable("aurorion_ethereal.mural.vault.error." + key);
    }

    /** Mural acessivel e pertencente a Casa de quem age; staff inspeciona, mas nao movimenta. */
    @Nullable
    private static ResourceLocation memberHouse(ServerPlayer player, BlockPos pos) {
        HouseMuralBlockEntity mural = accessibleMural(player, pos, false);
        if (mural == null) return null;
        ResourceLocation houseId = mural.house();
        if (houseId == null || HouseCatalog.get(houseId) == null || !memberOf(player, houseId)) {
            tell(player, "aurorion_ethereal.mural.error.not_member", ChatFormatting.RED);
            return null;
        }
        return houseId;
    }

    /** Nome do personagem, que e como a staff identifica gente (o nick da conta nao serve). */
    private static String actorName(ServerPlayer player) {
        CharacterData.Character character = CharacterData.get(player.server).find(player.getUUID());
        return character != null && character.named() ? character.fullName() : player.getGameProfile().getName();
    }

    private static String clip(String text) {
        return text.length() <= 64 ? text : text.substring(0, 64);
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
