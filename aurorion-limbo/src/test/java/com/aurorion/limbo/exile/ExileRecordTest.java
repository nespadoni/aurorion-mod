package com.aurorion.limbo.exile;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ExileRecordTest {
    private static final long[] BANDS = {21_600_000L, 3_600_000L, 900_000L, 300_000L, 60_000L};

    private static ExileRecord copy(ExileRecord record) {
        CompoundTag tag = new CompoundTag();
        record.write(tag);
        return ExileRecord.read(tag);
    }

    /**
     * A garantia de que o painel do exilado nao vira um pacote por segundo.
     *
     * <p>E uma propriedade de performance facil de regredir sem ninguem notar: o relogio na tela
     * continuaria certo (o cliente conta sozinho), so que a um pacote por exilado por segundo. O
     * teste falha no dia em que alguem tirar a checagem de etapa.
     */
    @Test void unchangedStageDoesNotResend() {
        ExileRecord record = new ExileRecord(0, 3_600_000L, "Dev1");

        assertTrue(record.syncDue(0, 0), "O primeiro envio sempre acontece");
        record.markSynced(0, 0);

        assertFalse(record.syncDue(0, 20), "Mesma etapa um segundo depois nao reenvia");
        assertFalse(record.syncDue(0, 1199), "Nem perto do fim da janela de reenvio");
        assertTrue(record.syncDue(0, 1200), "Passado um minuto, reenvia para corrigir a deriva");
    }

    @Test void stageChangeSendsImmediately() {
        ExileRecord record = new ExileRecord(0, 3_600_000L, "Dev1");
        record.markSynced(0, 0);

        // Armar a Porta e revela-la sao os dois momentos que nao podem chegar atrasados na tela.
        assertTrue(record.syncDue(1, 20), "Armar a Porta reenvia na hora");
        record.markSynced(1, 20);
        assertTrue(record.syncDue(2, 40), "A Porta aparecendo reenvia na hora");
    }

    @Test void invalidateForcesResendAfterTheScreenForgets() {
        ExileRecord record = new ExileRecord(0, 3_600_000L, "Dev1");
        record.markSynced(2, 0);
        assertFalse(record.syncDue(2, 20));

        // Login, respawn e troca de dimensao zeram o que o cliente sabia.
        record.invalidateSync();
        assertTrue(record.syncDue(2, 20), "Depois de invalidar, a mesma etapa precisa ser reenviada");
    }

    /** Reiniciar o servidor tem que reenviar: o cliente nao guarda painel entre sessoes. */
    @Test void syncStateIsNotPersisted() {
        ExileRecord record = new ExileRecord(0, 3_600_000L, "Dev1");
        record.markSynced(2, 0);

        assertTrue(copy(record).syncDue(2, 20), "Estado de sincronia nao pode sobreviver ao save");
    }

    @Test void expirationFromManualZeroIsReportedOnceAcrossRestart() {
        ExileRecord record = new ExileRecord(0, 1000, "Dev1");
        record.setRemaining(0);
        assertTrue(record.reportExpiration());
        assertFalse(record.reportExpiration());
        assertFalse(copy(record).reportExpiration());
    }

    @Test void extensionReopensExpirationAndWarningCycle() {
        ExileRecord record = new ExileRecord(0, 1000, "Dev1");
        assertTrue(record.advanceWarning(BANDS));
        assertEquals(5, record.warnStage());
        assertFalse(record.advanceWarning(BANDS));
        record.drain(1000);
        assertTrue(record.reportExpiration());
        record.setRemaining(1000);
        assertTrue(record.advanceWarning(BANDS));
        record.drain(1000);
        assertTrue(record.reportExpiration());
    }

    @Test void legacyExpiredSaveDoesNotRepeatAudit() {
        CompoundTag tag = new CompoundTag();
        tag.putLong("Remaining", 0);
        assertFalse(ExileRecord.read(tag).reportExpiration());
    }

    @Test void rescueAttemptDisarmsAnAlreadyOpenDoorAcrossRestart() {
        ExileRecord record = new ExileRecord(0, 1000, "Dev1");
        record.arm(1500, 987_654L);
        record.markRescueAttempted();
        assertTrue(copy(record).rescueAttempted());
        assertFalse(copy(record).doorArmed());
    }

    @Test void persistedWalkDoesNotRerollOrCountPastTravel() {
        ExileRecord record = new ExileRecord(0, 1000, "Dev1");
        record.arm(1500, 5_000_000_000L);
        record = copy(record);
        assertEquals(1500, record.doorTarget());
        assertEquals(1499, record.walkedSince(5_000_149_999L));
        assertEquals(1500, record.walkedSince(5_000_150_000L));
        assertEquals(0, record.walkedSince(0));
    }

    @Test void frameIdentitySurvivesConfigAndDimensionChanges() {
        ExileRecord record = new ExileRecord(0, 1000, "Dev1");
        record.setDoor(new BlockPos(1, 5, 20), Direction.EAST,
                "minecraft:crying_obsidian", "aurorion_limbo:limbo");
        record = copy(record);
        assertEquals(Direction.EAST, record.doorFacing());
        assertEquals("minecraft:crying_obsidian", record.doorBlock());
        assertEquals("aurorion_limbo:limbo", record.doorDimension());
        assertEquals(new BlockPos(1, 5, 20), record.doorPos());
    }

    @Test void negativeElapsedCannotIncreaseDeadline() {
        ExileRecord record = new ExileRecord(0, 1000, "Dev1");
        record.drain(-1000);
        assertEquals(1000, record.remainingMillis());
    }
}
