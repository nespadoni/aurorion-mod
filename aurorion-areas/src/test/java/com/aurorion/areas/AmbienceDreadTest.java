package com.aurorion.areas;

import com.aurorion.areas.profile.AmbientProfile;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A escalada de medo da Floresta Negra.
 *
 * <p>Tudo aqui e aritmetica pura, e e justamente por isso que precisa de teste: um sinal trocado na
 * interpolacao nao quebra nada no boot — a floresta so fica boba (ou letal) em jogo, e ninguem
 * descobre sem passar cinco minutos la dentro.
 */
class AmbienceDreadTest {

    @Test void dreadGoesFromZeroOnEntryToOneAtTheEndOfTheRamp() {
        var dread = new AmbientProfile.Dread(300, 5, .35F, 9, 5, .3F, .3F, 0);
        assertEquals(0, dread.level(0));
        assertEquals(.5F, dread.level(300 * 20 / 2), 1e-4);
        assertEquals(1, dread.level(300 * 20), 1e-4);
        assertEquals(1, dread.level(300 * 20 * 99), 1e-4, "passado o auge nao continua subindo");
    }

    @Test void theRampInterpolatesUpForDamageAndDownForPaceAndFog() {
        var dread = new AmbientProfile.Dread(300, 5, .35F, 9, 5, .3F, .3F, 0);
        // Dano: 1x na entrada, 5x no auge.
        assertEquals(1, dread.ramp(dread.damageScale(), 0), 1e-4);
        assertEquals(3, dread.ramp(dread.damageScale(), .5F), 1e-4);
        assertEquals(5, dread.ramp(dread.damageScale(), 1), 1e-4);
        // Intervalo e neblina andam para baixo com a mesma conta.
        assertEquals(1, dread.ramp(dread.intervalScale(), 0), 1e-4);
        assertEquals(.35F, dread.ramp(dread.intervalScale(), 1), 1e-4);
        assertEquals(.3F, dread.ramp(dread.fogScale(), 1), 1e-4);
    }

    @Test void anAmbienceWithoutDreadBehavesExactlyAsBefore() {
        var none = AmbientProfile.Dread.NONE;
        for (long ticks : new long[]{0, 20, 20_000}) {
            float level = none.level(ticks);
            assertEquals(1, none.ramp(1, level), 1e-6);
            assertEquals(0, none.darknessBonus());
            assertEquals(0, none.blindnessBonus());
            assertFalse(none.lethalNow(ticks), "sem lethal_after_seconds, nunca vira letal");
        }
    }

    @Test void lethalOnlyArrivesWhenTheDatapackAsksForIt() {
        var dread = new AmbientProfile.Dread(60, 2, 1, 0, 0, 0, 1, 420);
        assertFalse(dread.lethalNow(419 * 20));
        assertTrue(dread.lethalNow(420 * 20));
        assertFalse(new AmbientProfile.Dread(60, 2, 1, 0, 0, 0, 1, 0).lethalNow(Integer.MAX_VALUE),
                "zero desliga a letalidade por tempo, nao a liga na hora zero");
    }

    /** Sem o bloco no JSON, nenhum ambiente antigo comeca a avisar a staff do nada. */
    @Test void anAmbienceWithoutAnAlertBlockStaysSilent() {
        var none = AmbientProfile.Alert.NONE;
        assertFalse(none.enter());
        assertFalse(none.exit());
        assertFalse(none.lethal());
    }

    @Test void theIntervalShrinksWithTheScaleButNeverBelowOneSecond() {
        var interval = new AmbientProfile.Interval(10, 10);
        var random = RandomSource.create(1234);
        assertEquals(200, interval.next(0, random) - 0);
        assertEquals(200, interval.next(0, random, 1));
        assertEquals(70, interval.next(0, random, .35F));
        // Um intervalo curto com escala agressiva bate no piso em vez de virar evento por tick.
        assertEquals(20, new AmbientProfile.Interval(1, 1).next(0, random, .1F));
    }

    @Test void whisperColorSlidesFromCalmToPeak() {
        var whispers = new AmbientProfile.Whispers(List.of("Eu deveria sair daqui."),
                new AmbientProfile.Interval(20, 40), 0x000000, 0xFFFFFF);
        assertEquals(0x000000, whispers.colorAt(0));
        assertEquals(0xFFFFFF, whispers.colorAt(1));
        assertEquals(0x808080, whispers.colorAt(.5F));
    }

    @Test void aBlankOrOversizedWhisperIsRejectedInsteadOfShippedToPlayers() {
        var interval = new AmbientProfile.Interval(20, 40);
        assertThrows(IllegalArgumentException.class,
                () -> new AmbientProfile.Whispers(List.of("   "), interval, 0, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new AmbientProfile.Whispers(List.of("x".repeat(97)), interval, 0, 0));
    }
}
