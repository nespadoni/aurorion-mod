package com.aurorion.magia.compat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VoiceMuteTest {
    private final UUID player = UUID.randomUUID();

    @AfterEach
    void limpar() {
        VoiceMute.clear();
    }

    @Test
    void quemNuncaFoiSilenciadoFala() {
        assertFalse(VoiceMute.isMuted(player));
    }

    @Test
    void silencioValeAteOPrazo() {
        VoiceMute.mute(player, 60_000);
        assertTrue(VoiceMute.isMuted(player));
    }

    @Test
    void prazoVencidoDevolveAVozSemNinguemLimpar() {
        VoiceMute.mute(player, -1);
        assertFalse(VoiceMute.isMuted(player));
        // A consulta vencida tambem remove a entrada: a proxima nem acha nada.
        assertFalse(VoiceMute.isMuted(player));
    }

    @Test
    void fimAntecipadoDoEfeitoDevolveAVoz() {
        VoiceMute.mute(player, 60_000);
        VoiceMute.unmute(player);
        assertFalse(VoiceMute.isMuted(player));
    }
}
