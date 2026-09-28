package com.aurorion.essentials.voice;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** As regras de voz que o {@code EssentialsVoicePlugin} aplica, sem o Voice Chat. */
class VoiceRulesTest {
    private final UUID bella = UUID.fromString("00000000-0000-0000-0000-0000000000b1");

    @AfterEach
    void clear() {
        ShoutRegistry.clearAll();
    }

    @Test
    void grupoAbertoSemSenhaPassa() {
        assertNull(VoiceGroupPolicy.refusal(true, false, true, false));
    }

    @Test
    void grupoNormalOuIsoladoERecusado() {
        assertNotNull(VoiceGroupPolicy.refusal(false, false, true, false));
    }

    @Test
    void senhaERecusadaMesmoEmGrupoAberto() {
        assertNotNull(VoiceGroupPolicy.refusal(true, true, true, false));
        assertNull(VoiceGroupPolicy.refusal(true, true, true, true));
    }

    @Test
    void comARegraDesligadaQualquerTipoPassa() {
        assertNull(VoiceGroupPolicy.refusal(false, false, false, true));
    }

    @Test
    void semGritoAVozFicaNormal() {
        assertEquals(48F, ShoutRegistry.distanceFor(bella, 48F, false));
    }

    @Test
    void gritoAumentaOAlcance() {
        ShoutRegistry.set(bella, 120F);
        assertEquals(120F, ShoutRegistry.distanceFor(bella, 48F, false));
    }

    @Test
    void gritoMenorQueAVozNormalNaoEncurta() {
        ShoutRegistry.set(bella, 10F);
        assertEquals(48F, ShoutRegistry.distanceFor(bella, 48F, false));
    }

    @Test
    void sussurroContinuaSussurro() {
        ShoutRegistry.set(bella, 120F);
        assertEquals(24F, ShoutRegistry.distanceFor(bella, 24F, true));
    }

    @Test
    void desligarDevolveSeEstavaGritando() {
        ShoutRegistry.set(bella, 120F);
        assertTrue(ShoutRegistry.clear(bella));
        assertFalse(ShoutRegistry.clear(bella));
        assertEquals(48F, ShoutRegistry.distanceFor(bella, 48F, false));
    }
}
