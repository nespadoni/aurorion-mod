package com.aurorion.economia.server;

import com.aurorion.core.character.AltData;
import com.aurorion.core.rate.ActionCooldown;
import com.aurorion.economia.api.InteractionMenuEvent;
import com.aurorion.economia.money.Money;
import com.aurorion.economia.money.Transfer;
import com.aurorion.economia.network.EconomyPayloads;
import net.minecraft.Util;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

/** Autoridade do fluxo de cobranca. Cliente nenhum escolhe saldo, alcance ou resultado. */
public final class ChargeManager {
    private static final double MAX_DISTANCE_SQUARED = 25.0D;
    private static final double LOOK_DISTANCE = 5.0D;
    private static final long LIFETIME_MS = 30_000L;
    private static final ChargeBook CHARGES = new ChargeBook();
    private static final ActionCooldown OPENS = new ActionCooldown(300);
    private static final ActionCooldown SELECTIONS = new ActionCooldown(250);
    private static final ActionCooldown SUBMISSIONS = new ActionCooldown(5_000);
    private static final ActionCooldown SUBMISSION_NOTICES = new ActionCooldown(2_000);
    private static final ActionCooldown RESPONSES = new ActionCooldown(250);
    public enum PacketAction { OPEN, SELECT, SUBMIT, RESPOND, LAND_SUBMIT, LAND_RESPOND }
    private static final Map<PacketAction, ActionCooldown> PACKETS = new EnumMap<>(PacketAction.class);
    static {
        for (PacketAction action : PacketAction.values()) PACKETS.put(action, new ActionCooldown(100));
    }

    private ChargeManager() { }

    /** Admission before enqueueWork; only the authenticated profile UUID is read on the network thread. */
    public static boolean admitPacket(ServerPlayer player, PacketAction action) {
        return PACKETS.get(action).acquire(player.getUUID(), Util.getMillis());
    }

    private static UUID account(ServerPlayer player) {
        UUID owner = AltData.get(player.server).ownerOf(player.getUUID());
        return owner == null ? player.getUUID() : owner;
    }

    public static void open(ServerPlayer charger, UUID targetId) {
        if (!OPENS.acquire(account(charger), Util.getMillis())) return;

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
        InteractionMenuEvent.Collect event = new InteractionMenuEvent.Collect(charger, target);
        event.add("charge", "Fazer cobrança",
                "Informe um valor; o outro jogador poderá pagar ou recusar.", true);
        NeoForge.EVENT_BUS.post(event);
        var options = event.options().stream().map(option -> new EconomyPayloads.OpenMenu.Option(
                option.action(), option.title(), option.detail(), option.enabled())).toList();
        PacketDistributor.sendToPlayer(charger, new EconomyPayloads.OpenMenu(
                target.getUUID(), displayName(target), options));
    }

    public static void select(ServerPlayer charger, UUID targetId, String action) {
        if (!SELECTIONS.acquire(account(charger), Util.getMillis())) return;
        ServerPlayer target = charger.server.getPlayerList().getPlayer(targetId);
        String refusal = validatePair(charger, target, true);
        if (refusal != null) {
            status(charger, "Cobrança indisponível", refusal, false);
            return;
        }
        if ("charge".equals(action)) {
            PacketDistributor.sendToPlayer(charger,
                    new EconomyPayloads.OpenComposer(target.getUUID(), displayName(target)));
            return;
        }

        InteractionMenuEvent.Action event = new InteractionMenuEvent.Action(charger, target, action);
        NeoForge.EVENT_BUS.post(event);
        if (!event.handled())
            status(charger, "Opção indisponível", "Essa interação não está disponível.", false);
    }

    public static void submit(ServerPlayer charger, UUID targetId, String amountText) {
        UUID account = account(charger);
        long now = Util.getMillis();
        if (!SUBMISSIONS.acquire(account, now)) {
            if (SUBMISSION_NOTICES.acquire(account, now)) {
                long seconds = Math.max(1, (SUBMISSIONS.remainingMillis(account, now) + 999) / 1_000);
                status(charger, "Aguarde para cobrar", "Aguarde " + seconds + " segundo(s) antes de enviar outra cobrança.", false);
            }
            return;
        }
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
        if (charge == null) {
            status(charger, "Cobrança pendente", "Um dos participantes já tem uma cobrança aguardando resposta. Aguarde o pagamento, a recusa ou o prazo de 30 segundos.", false);
            return;
        }
        PacketDistributor.sendToPlayer(payer, new EconomyPayloads.OpenApproval(
                charge.token(), displayName(charger), amount, Wallet.balance(payer.server, payer.getUUID())));
        status(charger, "Cobrança enviada",
                displayName(payer) + " recebeu a cobrança de " + Money.describe(amount)
                        + ". Ela vale por 30 segundos.", true);
    }

    public static void respond(ServerPlayer payer, UUID token, boolean accepted) {
        if (!RESPONSES.acquire(account(payer), Util.getMillis())) return;
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
        LandSaleManager.forget(player);
        CHARGES.remove(player);
    }

    public static void clear() {
        LandSaleManager.clear();
        CHARGES.clear();
        OPENS.clear();
        SELECTIONS.clear();
        SUBMISSIONS.clear();
        SUBMISSION_NOTICES.clear();
        RESPONSES.clear();
        PACKETS.values().forEach(ActionCooldown::clear);
    }

    public static String validatePair(ServerPlayer charger, ServerPlayer payer, boolean requireAim) {
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
