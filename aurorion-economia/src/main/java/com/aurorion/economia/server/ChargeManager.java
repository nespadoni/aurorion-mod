package com.aurorion.economia.server;

import com.aurorion.economia.money.Money;
import com.aurorion.economia.money.Transfer;
import com.aurorion.economia.network.EconomyPayloads;
import net.minecraft.Util;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Autoridade do fluxo de cobranca. Cliente nenhum escolhe saldo, alcance ou resultado. */
public final class ChargeManager {
    private static final double MAX_DISTANCE_SQUARED = 25.0D;
    private static final double LOOK_DISTANCE = 5.0D;
    private static final long LIFETIME_MS = 30_000L;
    private static final long OPEN_COOLDOWN_MS = 300L;
    private static final ChargeBook CHARGES = new ChargeBook();
    private static final Map<UUID, Long> LAST_OPEN = new HashMap<>();

    private ChargeManager() { }

    public static void open(ServerPlayer charger, UUID targetId) {
        long now = Util.getMillis();
        Long previous = LAST_OPEN.get(charger.getUUID());
        if (previous != null && now - previous < OPEN_COOLDOWN_MS) return;
        LAST_OPEN.put(charger.getUUID(), now);

        ServerPlayer target = charger.server.getPlayerList().getPlayer(targetId);
        String refusal = validatePair(charger, target, true);
        if (refusal != null) {
            status(charger, "Cobrança indisponível", refusal, false);
            return;
        }
        if (!supports(target)) {
            status(charger, "Cobrança indisponível",
                    "O outro jogador não possui a interface de cobrança instalada.", false);
            return;
        }
        PacketDistributor.sendToPlayer(charger, new EconomyPayloads.OpenMenu(
                target.getUUID(), displayName(target), java.util.List.of(
                new EconomyPayloads.OpenMenu.Option("charge", "Fazer cobrança",
                        "Informe um valor; o outro jogador poderá pagar ou recusar.", true))));
    }

    public static void select(ServerPlayer charger, UUID targetId, String action) {
        if (!"charge".equals(action)) {
            status(charger, "Opção indisponível", "Essa interação não está disponível.", false);
            return;
        }
        ServerPlayer target = charger.server.getPlayerList().getPlayer(targetId);
        String refusal = validatePair(charger, target, true);
        if (refusal != null) {
            status(charger, "Cobrança indisponível", refusal, false);
            return;
        }
        PacketDistributor.sendToPlayer(charger,
                new EconomyPayloads.OpenComposer(target.getUUID(), displayName(target)));
    }

    public static void submit(ServerPlayer charger, UUID targetId, String amountText) {
        ServerPlayer payer = charger.server.getPlayerList().getPlayer(targetId);
        String refusal = validatePair(charger, payer, true);
        if (refusal != null) {
            status(charger, "Cobrança não enviada", refusal, false);
            return;
        }

        long amount;
        try {
            amount = Money.parse(amountText);
        } catch (Money.MoneyFormatException ignored) {
            status(charger, "Valor inválido",
                    "Use Óbolos com no máximo um Fragmento decimal, por exemplo 4, 4,5 ou 0,5.", false);
            return;
        }

        ChargeBook.Charge charge = CHARGES.put(
                charger.getUUID(), payer.getUUID(), amount, Util.getMillis(), LIFETIME_MS);
        PacketDistributor.sendToPlayer(payer, new EconomyPayloads.OpenApproval(
                charge.token(), displayName(charger), amount, Wallet.balance(payer.server, payer.getUUID())));
        status(charger, "Cobrança enviada",
                displayName(payer) + " recebeu a cobrança de " + Money.describe(amount)
                        + ". Ela vale por 30 segundos.", true);
    }

    public static void respond(ServerPlayer payer, UUID token, boolean accepted) {
        ChargeBook.Charge charge = CHARGES.take(payer.getUUID(), token, Util.getMillis());
        if (charge == null) {
            status(payer, "Cobrança expirada", "Essa cobrança não está mais disponível.", false);
            return;
        }

        ServerPlayer charger = payer.server.getPlayerList().getPlayer(charge.charger());
        if (!accepted) {
            status(payer, "Cobrança recusada", "Nenhum dinheiro foi transferido.", false);
            if (charger != null) status(charger, "Cobrança recusada",
                    displayName(payer) + " recusou a cobrança.", false);
            return;
        }

        String refusal = validatePair(charger, payer, false);
        if (refusal != null) {
            status(payer, "Pagamento interrompido", refusal, false);
            if (charger != null) status(charger, "Pagamento interrompido", refusal, false);
            return;
        }

        Transfer.Result result = Wallet.transfer(payer.server, payer.getUUID(), charger.getUUID(), charge.amount());
        if (!result.ok()) {
            String message = switch (result) {
                case INSUFFICIENT -> "Saldo insuficiente: você possui "
                        + Money.describe(Wallet.balance(payer.server, payer.getUUID())) + ".";
                case TARGET_FULL -> "A carteira de quem cobrou não comporta esse valor.";
                case SAME_ACCOUNT -> "Não é possível pagar a si mesmo.";
                case INVALID_AMOUNT, OK -> "A quantia da cobrança é inválida.";
            };
            status(payer, "Pagamento não realizado", message, false);
            status(charger, "Pagamento não realizado", message, false);
            return;
        }

        String amount = Money.describe(charge.amount());
        status(payer, "Pagamento realizado", "Você pagou " + amount + " para " + displayName(charger) + ".", true);
        status(charger, "Pagamento recebido", "Você recebeu " + amount + " de " + displayName(payer) + ".", true);
    }

    public static void forget(UUID player) {
        CHARGES.remove(player);
        LAST_OPEN.remove(player);
    }

    public static void clear() {
        CHARGES.clear();
        LAST_OPEN.clear();
    }

    private static String validatePair(ServerPlayer charger, ServerPlayer payer, boolean requireAim) {
        if (charger == null || payer == null) return "O outro jogador não está mais disponível.";
        if (charger == payer) return "Não é possível cobrar a si mesmo.";
        if (charger instanceof FakePlayer || payer instanceof FakePlayer
                || !charger.isAlive() || !payer.isAlive()
                || charger.isSpectator() || payer.isSpectator())
            return "Os dois personagens precisam estar presentes e ativos.";
        if (charger.serverLevel() != payer.serverLevel()
                || charger.distanceToSqr(payer) > MAX_DISTANCE_SQUARED)
            return "Fiquem frente a frente, a no máximo 5 blocos de distância.";
        if (charger.containerMenu != charger.inventoryMenu || payer.containerMenu != payer.inventoryMenu)
            return "Fechem outras telas antes de realizar a cobrança.";
        if (!charger.hasLineOfSight(payer) || !payer.hasLineOfSight(charger))
            return "Os dois jogadores precisam conseguir se ver.";
        if (requireAim && !lookingAt(charger, payer))
            return "Olhe diretamente para o jogador que deseja cobrar.";
        return null;
    }

    private static boolean lookingAt(Player player, Player target) {
        Vec3 start = player.getEyePosition();
        Vec3 end = start.add(player.getViewVector(1.0F).scale(LOOK_DISTANCE));
        return target.getBoundingBox().inflate(0.3D).clip(start, end).isPresent();
    }

    private static boolean supports(ServerPlayer player) {
        return player != null && player.connection.hasChannel(EconomyPayloads.OpenApproval.TYPE.id());
    }

    private static String displayName(ServerPlayer player) {
        String name = player.getDisplayName().getString();
        return name.length() > 80 ? name.substring(0, 80) : name;
    }

    public static void status(ServerPlayer player, String title, String message, boolean success) {
        if (player == null) return;
        if (player.connection.hasChannel(EconomyPayloads.Status.TYPE.id())) {
            PacketDistributor.sendToPlayer(player, new EconomyPayloads.Status(title, message, success));
        } else {
            player.sendSystemMessage(Component.literal(title + ": " + message));
        }
    }
}
