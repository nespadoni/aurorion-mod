package com.aurorion.essentials.compat;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PhoneRetirementsTest {
    private final UUID morta = UUID.fromString("00000000-0000-0000-0000-0000000000b1");

    @Test
    void resetRepetidoNaoDuplicaNemTrocaAData() {
        PhoneRetirements data = new PhoneRetirements();
        data.add(morta, "BellaNoob", 1_000L);
        data.add(morta, "BellaNoob", 2_000L);
        assertEquals(Map.of(morta, "BellaNoob"), data.active(2_000L));
    }

    @Test
    void entradaVencidaSaiDaLista() {
        PhoneRetirements data = new PhoneRetirements();
        data.add(morta, "BellaNoob", 0L);
        long depois = TimeUnit.DAYS.toMillis(PhoneRetirements.KEEP_DAYS) + 1;
        assertTrue(data.active(depois).isEmpty());
    }
}
