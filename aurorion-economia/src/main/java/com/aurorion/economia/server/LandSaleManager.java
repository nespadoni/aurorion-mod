package com.aurorion.economia.server;

import com.aurorion.core.character.CharacterData;
import com.aurorion.economia.AurorionEconomia;
import com.aurorion.economia.api.BrokerAuthorizationEvent;
import com.aurorion.economia.config.EconomyConfig;
import com.aurorion.economia.land.LandDeed;
import com.aurorion.economia.money.Money;
import com.aurorion.economia.network.LandPayloads;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** One editor per broker, one pending offer per buyer; expiry is checked only on interaction. */
public final class LandSaleManager {
    private record Session(UUID token, UUID broker, UUID buyer, UUID brokerCharacter, UUID buyerCharacter,
                           ResourceLocation dimension, BlockPos origin, List<Long> rates,
                           long floor, int maxSide, long expires) { }
    private record Offer(UUID token, Session session, LandDeed deed, long expires) { }
    private static final Map<UUID, Session> EDITORS = new HashMap<>();
    private static final Map<UUID, Offer> OFFERS = new HashMap<>();
    private LandSaleManager() { }

    public static void open(ServerPlayer broker, ServerPlayer buyer) {
        if (!authorized(broker) || !validCharacter(broker) || !validCharacter(buyer)) return;
        String refusal = ChargeManager.validatePair(broker, buyer, true);
        if (refusal != null) { fail(broker, refusal); return; }
        if (!buyer.connection.hasChannel(LandPayloads.Approval.TYPE.id())
                || !broker.connection.hasChannel(LandPayloads.Open.TYPE.id())) return;
        forget(broker.getUUID());
        var characters = CharacterData.get(broker.server);
        var session = new Session(UUID.randomUUID(), broker.getUUID(), buyer.getUUID(),
                characters.find(broker.getUUID()).id(), characters.find(buyer.getUUID()).id(),
                broker.level().dimension().location(), broker.blockPosition().immutable(),
                Arrays.stream(EconomyConfig.ZONE_RATES).map(value -> value.get()).toList(),
                EconomyConfig.MIN_PLOT_PRICE.get(), EconomyConfig.MAX_PLOT_SIDE.get(), Util.getMillis() + 300_000);
        EDITORS.put(broker.getUUID(), session);
        PacketDistributor.sendToPlayer(broker, new LandPayloads.Open(session.token, name(buyer),
                session.origin, session.rates, session.floor, session.maxSide));
    }

    public static void submit(ServerPlayer broker, LandPayloads.Submit request) {
        Session session = EDITORS.get(broker.getUUID());
        if (session == null || !session.token.equals(request.token())) return;
        EDITORS.remove(broker.getUUID());
        ServerPlayer buyer = broker.server.getPlayerList().getPlayer(session.buyer);
        if (!validSession(session, broker, buyer) || session.expires < Util.getMillis()) {
            fail(broker, "Sessão expirada ou personagens indisponíveis. Abra o menu novamente."); return;
        }
        String refusal = ChargeManager.validatePair(broker, buyer, true);
        if (refusal != null) { fail(broker, refusal); return; }
        try {
            if (request.zone() < 0 || request.zone() >= 5 || !samePrices(session, request.zone())
                    || request.width() > session.maxSide || request.length() > session.maxSide)
                throw new IllegalArgumentException("Dimensões ou tabela de preços alteradas. Reabra a venda.");
            long price = Money.parse(request.amount());
            long min = LandDeed.minimum(request.width(), request.length(), session.rates.get(request.zone()), session.floor);
            if (price < min) throw new IllegalArgumentException("O preço mínimo é " + Money.describe(min) + ".");
            var deed = new LandDeed(UUID.randomUUID(), session.buyerCharacter, session.brokerCharacter,
                    name(buyer), session.dimension, request.x(), request.z(), request.width(), request.length(),
                    request.zone(), price, System.currentTimeMillis());
            if (!validLocation(broker, session, deed))
                throw new IllegalArgumentException("O primeiro canto deve estar a até 8 blocos de você, dentro da borda do mundo.");
            if (!WalletData.get(broker.server).canSell(deed))
                throw new IllegalArgumentException("Lote sobreposto a outro terreno, ou cadastro indisponível/cheio.");
            Offer previous = OFFERS.get(buyer.getUUID());
            if (previous != null && previous.expires > Util.getMillis())
                throw new IllegalArgumentException("O comprador já está avaliando uma venda. Aguarde.");
            var offer = new Offer(UUID.randomUUID(), session, deed, Util.getMillis() + 60_000);
            OFFERS.put(buyer.getUUID(), offer);
            PacketDistributor.sendToPlayer(buyer, new LandPayloads.Approval(offer.token, name(broker), name(buyer),
                    deed.dimension(), deed.x(), deed.z(), deed.width(), deed.length(), deed.zone(),
                    price, Wallet.balance(broker.server, buyer.getUUID())));
            ChargeManager.status(broker, "Proposta enviada", "O comprador tem 60 segundos para confirmar o terreno.", true);
        } catch (IllegalArgumentException exception) {
            fail(broker, exception instanceof Money.MoneyFormatException
                    ? "Valor inválido. Informe Óbolos, por exemplo 80 ou 80,5." : exception.getMessage());
        }
    }

    public static void respond(ServerPlayer buyer, UUID token, boolean accepted) {
        Offer offer = OFFERS.get(buyer.getUUID());
        if (offer == null || !offer.token.equals(token)) { fail(buyer, "Essa proposta não está mais disponível."); return; }
        OFFERS.remove(buyer.getUUID()); // Consume before any debit: duplicate packets cannot pay twice.
        ServerPlayer broker = buyer.server.getPlayerList().getPlayer(offer.session.broker);
        if (!accepted) { fail(broker, "O comprador recusou. Nenhum dinheiro foi debitado."); return; }
        if (offer.expires < Util.getMillis() || !validSession(offer.session, broker, buyer)
                || ChargeManager.validatePair(broker, buyer, false) != null
                || !samePrices(offer.session, offer.deed.zone()) || !validLocation(broker, offer.session, offer.deed)) {
            fail(buyer, "Proposta expirada: posição, personagens ou preços mudaram. Solicite outra proposta.");
            fail(broker, "A proposta perdeu a validade. Nenhum dinheiro foi debitado."); return;
        }
        if (!WalletData.get(buyer.server).buyLand(buyer.getUUID(), offer.deed)) {
            fail(buyer, "Saldo insuficiente, lote já vendido ou cadastro indisponível.");
            fail(broker, "A compra não foi concluída. Nenhum dinheiro foi debitado."); return;
        }
        EconomyProjectorNotifier.refresh(buyer.server);
        AurorionEconomia.LOGGER.info("LAND_SALE deed={} buyerCharacter={} brokerCharacter={} fragments={} destination=SYSTEM",
                offer.deed.id(), offer.deed.ownerCharacter(), offer.deed.brokerCharacter(), offer.deed.paid());
        ChargeManager.status(buyer, "Terreno adquirido", "Escritura " + offer.deed.id() + ". Pagamento ao sistema: "
                + Money.describe(offer.deed.paid()) + ". O terreno pertence ao personagem " + name(buyer) + ".", true);
        ChargeManager.status(broker, "Venda concluída", "Terreno registrado para " + name(buyer)
                + ". O valor foi retirado de circulação pelo sistema.", true);
    }

    private static boolean validLocation(ServerPlayer broker, Session session, LandDeed deed) {
        if (!broker.level().dimension().location().equals(session.dimension)) return false;
        long dx = (long) deed.x() - broker.blockPosition().getX();
        long dz = (long) deed.z() - broker.blockPosition().getZ();
        var border = broker.level().getWorldBorder();
        return dx * dx + dz * dz <= 64 && border.isWithinBounds(new BlockPos(deed.x(), 0, deed.z()))
                && border.isWithinBounds(new BlockPos(deed.endX(), 0, deed.endZ()));
    }
    private static boolean samePrices(Session session, int zone) {
        return session.rates.get(zone).longValue() == EconomyConfig.ZONE_RATES[zone].get()
                && session.floor == EconomyConfig.MIN_PLOT_PRICE.get()
                && session.maxSide == EconomyConfig.MAX_PLOT_SIDE.get();
    }
    private static boolean validSession(Session session, ServerPlayer broker, ServerPlayer buyer) {
        if (broker == null || buyer == null || !authorized(broker) || !validCharacter(broker) || !validCharacter(buyer)) return false;
        var characters = CharacterData.get(broker.server);
        return characters.find(broker.getUUID()).id().equals(session.brokerCharacter)
                && characters.find(buyer.getUUID()).id().equals(session.buyerCharacter);
    }
    private static boolean authorized(ServerPlayer broker) {
        BrokerAuthorizationEvent event = new BrokerAuthorizationEvent(broker);
        NeoForge.EVENT_BUS.post(event);
        return event.authorized();
    }
    private static boolean validCharacter(ServerPlayer player) {
        if (player == null) return false;
        var character = CharacterData.get(player.server).find(player.getUUID());
        return character != null && character.named() && !character.dead();
    }
    private static String name(ServerPlayer player) {
        return CharacterData.get(player.server).find(player.getUUID()).fullName();
    }
    private static void fail(ServerPlayer player, String message) {
        ChargeManager.status(player, "Venda de terreno", message, false);
    }
    public static void forget(UUID account) {
        EDITORS.entrySet().removeIf(entry -> entry.getValue().broker.equals(account) || entry.getValue().buyer.equals(account));
        OFFERS.entrySet().removeIf(entry -> entry.getValue().session.broker.equals(account) || entry.getKey().equals(account));
    }
    public static void clear() { EDITORS.clear(); OFFERS.clear(); }
}
