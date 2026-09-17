package com.aurorion.economia.server;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ChargeBookTest {
    @Test
    void onlyThePayerCanConsumeTheMatchingCharge() {
        ChargeBook book = new ChargeBook();
        UUID charger = UUID.randomUUID();
        UUID payer = UUID.randomUUID();
        ChargeBook.Charge charge = book.put(charger, payer, 40, 1_000, 30_000);

        assertNull(book.take(charger, charge.token(), 1_001));
        assertNull(book.take(payer, UUID.randomUUID(), 1_001));
        assertEquals(charge, book.take(payer, charge.token(), 1_001));
        assertNull(book.take(payer, charge.token(), 1_002));
    }

    @Test
    void replacingEitherParticipantsChargeRemovesTheOldToken() {
        ChargeBook book = new ChargeBook();
        UUID charger = UUID.randomUUID();
        UUID firstPayer = UUID.randomUUID();
        UUID secondPayer = UUID.randomUUID();
        ChargeBook.Charge old = book.put(charger, firstPayer, 10, 1_000, 30_000);
        ChargeBook.Charge replacement = book.put(charger, secondPayer, 20, 1_001, 30_000);

        assertNull(book.take(firstPayer, old.token(), 1_002));
        assertEquals(replacement, book.take(secondPayer, replacement.token(), 1_002));
    }

    @Test
    void expiredChargesDisappearWithoutATick() {
        ChargeBook book = new ChargeBook();
        UUID charger = UUID.randomUUID();
        UUID payer = UUID.randomUUID();
        ChargeBook.Charge charge = book.put(charger, payer, 10, 1_000, 30_000);

        assertNull(book.take(payer, charge.token(), 31_000));
        assertEquals(0, book.size());
    }
}
