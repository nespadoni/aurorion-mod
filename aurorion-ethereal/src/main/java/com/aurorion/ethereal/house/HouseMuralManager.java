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

/** Regras autoritativas do mural, executadas somente no thread do servidor e apenas por interacao. */
public final class HouseMuralManager {
    private static final double MAX_DISTANCE_SQR = 64.0D;
    private static final long DAY_MILLIS = 86_400_000L;
    /**
     * Intervalo minimo entre acoes do cofre por jogador. Cada acao custa reflexao e o reenvio do
     * estado inteiro; um cliente modificado mandando pacote por tick nao pode transformar isso em
     * carga.
     */
    private static final PlayerThrottle ACTIONS = new PlayerThrottle(250L);
    /**
     * Aberturas (clique no mural, lista do Protetor) tem relogio proprio: abrir a tela e logo depois
     * clicar em sacar nao pode fazer o saque ser descartado em silencio.
     */
    private static final PlayerThrottle OPENS = new PlayerThrottle(500L);
    /** Nome em log e no historico do mural: o mesmo teto do pacote ({@code Movement.actor}). */
    private static final int MAX_NAME_LENGTH = 64;
    private static final char SECTION_SIGN = (char) 0x00A7;
    private static final char LINE_SEPARATOR = (char) 0x2028;
    private static final char PARAGRAPH_SEPARATOR = (char) 0x2029;

    private HouseMuralManager() { }

    public static void open(ServerPlayer player, BlockPos pos) {
        if (OPENS.throttled(player)) return;
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

    /**
     * Depositar ou sacar: qualquer membro pode, e toda operacao fica no log e no historico do mural.
     *
     * <p>Saque tem teto diario por membro ({@link #dailyLimit}): sem ele, qualquer um que entrasse na
     * Casa esvaziava o cofre coletivo num clique. Staff (nivel 2) nao tem teto. Saque grande alerta a
     * staff online, como o Protetor Arcano.
     */
    public static void transfer(ServerPlayer player, BlockPos pos, boolean deposit, long amount) {
        if (ACTIONS.throttled(player)) return;
        ResourceLocation houseId = memberHouse(player, pos);
        if (houseId == null) return;
        if (amount <= 0L) {
            reply(player, pos, HouseMuralPayloads.TAB_VAULT, failure(HouseEconomyBridge.ERR_INVALID), true);
            return;
        }

        HouseUpgradeData upgrades = HouseUpgradeData.get(player.server);
        long day = HouseUpgradeData.currentDay();
        long capacity = HouseEconomyBridge.capacity(player.server, houseId);
        if (!deposit && overLimit(player, upgrades, houseId, day, capacity, amount)) {
            reply(player, pos, HouseMuralPayloads.TAB_VAULT, limitNotice(player, upgrades, houseId, day, capacity), true);
            return;
        }

        long result = deposit
                ? HouseEconomyBridge.deposit(player.server, player.getUUID(), houseId, amount)
                : HouseEconomyBridge.withdraw(player.server, player.getUUID(), houseId, amount);
        if (result <= 0L) {
            reply(player, pos, HouseMuralPayloads.TAB_VAULT, failure(result), true);
            return;
        }

        String actor = actorName(player);
        if (!deposit) upgrades.recordWithdrawal(player.getUUID(), houseId, day, result);
        upgrades.recordMovement(houseId, new HouseUpgradeData.Movement(
                System.currentTimeMillis(), actor,
                deposit ? HouseUpgradeData.Kind.DEPOSIT : HouseUpgradeData.Kind.WITHDRAW, result));
        AurorionEthereal.LOGGER.info("COFRE_CASA {} conta={} uuid={} personagem={} house={} quantia={}",
                deposit ? "deposito" : "saque", player.getGameProfile().getName(), player.getUUID(),
                actor, houseId, result);
        House house = HouseCatalog.get(houseId);
        if (!deposit && house != null && isLarge(result, capacity)) {
            long today = upgrades.withdrawnToday(player.getUUID(), houseId, day);
            staffAlert(player, Component.translatable("aurorion_ethereal.mural.vault.admin_alert",
                    accountLabel(player, actor), HouseMuralPayloads.formatMoney(result), house.coloredName(),
                    HouseMuralPayloads.formatMoney(today), pos.getX(), pos.getY(), pos.getZ()));
            AurorionEthereal.LOGGER.warn("COFRE_CASA_ALERTA saque conta={} uuid={} personagem={} house={} quantia={} hoje={} pos={}",
                    player.getGameProfile().getName(), player.getUUID(), actor, houseId, result, today,
                    pos.toShortString());
        }

        Component notice = deposit && result < amount
                ? Component.translatable("aurorion_ethereal.mural.vault.deposit_partial",
                        HouseMuralPayloads.formatMoney(result))
                : Component.translatable(deposit ? "aurorion_ethereal.mural.vault.deposited"
                        : "aurorion_ethereal.mural.vault.withdrawn", HouseMuralPayloads.formatMoney(result));
        reply(player, pos, HouseMuralPayloads.TAB_VAULT, notice, false);
    }

    /**
     * Melhoria paga com o saldo do cofre; o preco citado pela tela precisa bater com o atual.
     *
     * <p>Gastar o cofre numa melhoria tambem e tirar dinheiro dele: conta no mesmo teto diario do
     * saque, para que um membro sozinho nao torre o cofre inteiro. Acima do teto, quem compra e a
     * staff (ou ela sobe o teto com {@code /casa cofre limite}).
     */
    public static void buyVaultUpgrade(ServerPlayer player, BlockPos pos, int nextLevel, long quotedPrice) {
        if (ACTIONS.throttled(player)) return;
        ResourceLocation houseId = memberHouse(player, pos);
        if (houseId == null) return;

        HouseUpgradeData upgrades = HouseUpgradeData.get(player.server);
        long day = HouseUpgradeData.currentDay();
        long capacity = HouseEconomyBridge.capacity(player.server, houseId);
        if (quotedPrice > 0L && overLimit(player, upgrades, houseId, day, capacity, quotedPrice)) {
            reply(player, pos, HouseMuralPayloads.TAB_UPGRADES, limitNotice(player, upgrades, houseId, day, capacity), true);
            return;
        }
        if (!HouseEconomyBridge.buyVaultUpgrade(player.server, houseId, nextLevel, quotedPrice)) {
            reply(player, pos, HouseMuralPayloads.TAB_UPGRADES,
                    Component.translatable("aurorion_ethereal.mural.upgrade.failed"), true);
            return;
        }

        String actor = actorName(player);
        upgrades.recordWithdrawal(player.getUUID(), houseId, day, quotedPrice);
        upgrades.recordMovement(houseId, new HouseUpgradeData.Movement(
                System.currentTimeMillis(), actor, HouseUpgradeData.Kind.UPGRADE, quotedPrice));
        AurorionEthereal.LOGGER.info("COFRE_CASA melhoria conta={} uuid={} personagem={} house={} nivel={} preco={}",
                player.getGameProfile().getName(), player.getUUID(), actor, houseId, nextLevel, quotedPrice);
        House house = HouseCatalog.get(houseId);
        if (house != null && isLarge(quotedPrice, capacity)) {
            staffAlert(player, Component.translatable("aurorion_ethereal.mural.upgrade.admin_alert",
                    accountLabel(player, actor), HouseMuralPayloads.formatMoney(quotedPrice), house.coloredName(),
                    nextLevel, pos.getX(), pos.getY(), pos.getZ()));
            AurorionEthereal.LOGGER.warn("COFRE_CASA_ALERTA melhoria conta={} uuid={} personagem={} house={} nivel={} preco={} pos={}",
                    player.getGameProfile().getName(), player.getUUID(), actor, houseId, nextLevel, quotedPrice,
                    pos.toShortString());
        }
        reply(player, pos, HouseMuralPayloads.TAB_UPGRADES,
                Component.translatable("aurorion_ethereal.mural.upgrade.bought", nextLevel), false);
    }

    /**
     * Quanto um membro pode tirar do cofre por dia: a porcentagem configurada da capacidade atual. A
     * capacidade, e nao o saldo, para que o teto nao dependa de quem sacou primeiro.
     */
    public static long dailyLimit(long capacity) {
        return percentOf(capacity, EtherealConfig.VAULT_DAILY_WITHDRAW_PERCENT.get());
    }

    /** {@code value * percent / 100} sem estourar long. */
    static long percentOf(long value, int percent) {
        long safe = Math.max(0L, value);
        return safe / 100L * percent + safe % 100L * percent / 100L;
    }

    private static boolean overLimit(ServerPlayer player, HouseUpgradeData upgrades, ResourceLocation house,
                                     long day, long capacity, long amount) {
        // Staff nao tem teto; sem economia a operacao falha adiante com a mensagem certa.
        if (player.hasPermissions(2) || !HouseEconomyBridge.available()) return false;
        return amount > remainingToday(player, upgrades, house, day, capacity);
    }

    private static long remainingToday(ServerPlayer player, HouseUpgradeData upgrades, ResourceLocation house,
                                       long day, long capacity) {
        return Math.max(0L, dailyLimit(capacity) - upgrades.withdrawnToday(player.getUUID(), house, day));
    }

    private static Component limitNotice(ServerPlayer player, HouseUpgradeData upgrades, ResourceLocation house,
                                         long day, long capacity) {
        return Component.translatable("aurorion_ethereal.mural.vault.error.daily_limit",
                HouseMuralPayloads.formatMoney(remainingToday(player, upgrades, house, day, capacity)),
                HouseMuralPayloads.formatMoney(dailyLimit(capacity)));
    }

    private static boolean isLarge(long amount, long capacity) {
        return amount >= Math.max(1L, percentOf(capacity, EtherealConfig.VAULT_ALERT_PERCENT.get()));
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
        return sanitize(character != null && character.named() ? character.fullName() : player.getGameProfile().getName());
    }

    /** Conta real e personagem, para a staff: o nome exibido pode ser um nome falso. */
    private static Component accountLabel(ServerPlayer player, String character) {
        return Component.literal(player.getGameProfile().getName() + " / " + character);
    }

    /**
     * Nome de personagem pronto para log e para o historico: sem quebra de linha nem caractere de
     * controle (que forjariam linhas no log) e sem o simbolo de secao (que mudaria a formatacao no
     * cliente). Corta no mesmo tamanho que o pacote aceita.
     */
    public static String sanitize(@Nullable String text) {
        if (text == null) return "";
        StringBuilder out = new StringBuilder(Math.min(text.length(), MAX_NAME_LENGTH));
        for (int i = 0; i < text.length() && out.length() < MAX_NAME_LENGTH; i++) {
            char c = text.charAt(i);
            if (c == SECTION_SIGN || Character.isISOControl(c) || c == LINE_SEPARATOR || c == PARAGRAPH_SEPARATOR) {
                continue;
            }
            out.append(c);
        }
        return out.toString();
    }

    private static String clip(String text) {
        return text.length() <= MAX_NAME_LENGTH ? text : text.substring(0, MAX_NAME_LENGTH);
    }

    public static void openProtector(ServerPlayer player, BlockPos pos) {
        if (OPENS.throttled(player)) return;
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

        // Sem o "esta online": a lista cobre todo personagem do servidor, e o mural viraria um
        // rastreador de quem esta conectado (inclusive staff em vanish).
        CharacterData characters = CharacterData.get(player.server);
        int maxLives = HouseLivesBridge.maxLives();
        List<HouseMuralPayloads.Target> targets = characters.characters().entrySet().stream()
                .filter(entry -> entry.getValue().named() && !entry.getValue().dead())
                .sorted(Comparator.comparing(entry -> entry.getValue().fullName(), String.CASE_INSENSITIVE_ORDER))
                .limit(HouseMuralPayloads.OpenTargets.MAX_TARGETS)
                .map(entry -> new HouseMuralPayloads.Target(entry.getKey(), entry.getValue().fullName(),
                        HouseLivesBridge.livesOf(player.server, entry.getKey()), maxLives))
                .toList();
        PacketDistributor.sendToPlayer(player, new HouseMuralPayloads.OpenTargets(pos, house.name(), targets));
    }

    public static void grantLife(ServerPlayer actor, BlockPos pos, UUID target) {
        if (ACTIONS.throttled(actor)) return;
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
        audit(actor, house, sanitize(character.fullName()), target, pos);
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

    /**
     * Membro pela casa <b>revelada</b>: com o cadastro oculto o mural da casa certa abriria e o dos
     * outros recusaria, e o jogador descobriria a casa antes da cerimonia.
     */
    private static boolean memberOf(ServerPlayer player, ResourceLocation house) {
        return house.equals(HouseManager.publicHouseIdOf(player.server, player.getUUID()));
    }

    private static void notifyRecipient(MinecraftServer server, UUID target, House house) {
        ServerPlayer recipient = server.getPlayerList().getPlayer(target);
        if (recipient != null) {
            recipient.sendSystemMessage(Component.translatable(
                    "aurorion_ethereal.mural.protector.received", house.coloredName())
                    .withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.BOLD));
        }
    }

    private static void audit(ServerPlayer actor, House house, String target, UUID targetId, BlockPos pos) {
        String character = actorName(actor);
        staffAlert(actor, Component.translatable("aurorion_ethereal.mural.protector.admin_alert",
                accountLabel(actor, character), house.coloredName(), target,
                pos.getX(), pos.getY(), pos.getZ()));
        AurorionEthereal.LOGGER.warn("PROTETOR_ARCANO conta={} uuid={} personagem={} house={} alvo={} alvo_uuid={} pos={}",
                actor.getGameProfile().getName(), actor.getUUID(), character, house.id(), target, targetId,
                pos.toShortString());
    }

    /** Alerta em vermelho para toda a staff online (conta real, nao o nome exibido). */
    private static void staffAlert(ServerPlayer actor, Component message) {
        Component alert = message.copy().withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD);
        for (ServerPlayer online : actor.server.getPlayerList().getPlayers()) {
            if (online.hasPermissions(2)) online.sendSystemMessage(alert);
        }
    }

    private static void tell(ServerPlayer player, String key, ChatFormatting color, Object... args) {
        player.sendSystemMessage(Component.translatable(key, args).withStyle(color));
    }
}
