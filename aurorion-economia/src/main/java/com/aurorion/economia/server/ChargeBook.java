package com.aurorion.economia.server;

import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/** Sessoes curtas de cobranca, sem tick: vencimentos sao limpos na proxima acao. */
final class ChargeBook {
    record Charge(UUID token, UUID charger, UUID payer, long amount, long expiresAt) { }

    private final Map<UUID, Charge> byPayer = new HashMap<>();
    private final Map<UUID, Charge> byCharger = new HashMap<>();

    Charge put(UUID charger, UUID payer, long amount, long now, long lifetime) {
        purge(now);
        remove(charger);
        remove(payer);

        Charge charge = new Charge(UUID.randomUUID(), charger, payer, amount, now + lifetime);
        byPayer.put(payer, charge);
        byCharger.put(charger, charge);
        return charge;
    }

    @Nullable
    Charge take(UUID payer, UUID token, long now) {
        purge(now);
        Charge charge = byPayer.get(payer);
        if (charge == null || !charge.token().equals(token)) return null;
        removeCharge(charge);
        return charge;
    }

    void remove(UUID participant) {
        Charge charge = byPayer.get(participant);
        if (charge == null) charge = byCharger.get(participant);
        if (charge != null) removeCharge(charge);
    }

    void clear() {
        byPayer.clear();
        byCharger.clear();
    }

    int size() {
        return byPayer.size();
    }

    private void purge(long now) {
        Iterator<Charge> iterator = byPayer.values().iterator();
        while (iterator.hasNext()) {
            Charge charge = iterator.next();
            if (charge.expiresAt() > now) continue;
            iterator.remove();
            byCharger.remove(charge.charger(), charge);
        }
    }

    private void removeCharge(Charge charge) {
        byPayer.remove(charge.payer(), charge);
        byCharger.remove(charge.charger(), charge);
    }
}
