package com.aurorion.limbo.report;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AuditLogTest {
    @TempDir Path directory;

    @Test void returnsNewestFirstWithUtf8AcrossBufferBoundary() throws Exception {
        Path file = directory.resolve("auditoria.jsonl");
        String longLine = "ç".repeat(5000);
        Files.writeString(file, "antiga\n" + longLine + "\núltima saída\n", StandardCharsets.UTF_8);
        assertEquals(List.of("última saída", longLine), AuditLog.tail(file, 2));
        assertEquals(3, AuditLog.tail(file, 10).size());
    }

    @Test void skipsInterruptedLastWriteAndUnderstandsWindowsNewlines() throws Exception {
        Path file = directory.resolve("auditoria.jsonl");
        Files.writeString(file, "primeira\r\nsegunda\r\n{incompleta");
        assertEquals(List.of("segunda", "primeira"), AuditLog.tail(file, 10));
    }

    @Test void readsOnlyTailEvenWhenOldHistoryContainsAnOversizedLine() throws Exception {
        Path file = directory.resolve("auditoria.jsonl");
        Files.writeString(file, "x".repeat(3_000_000) + "\nrecente\n");
        assertEquals(List.of("recente"), AuditLog.tail(file, 1));
    }

    @Test void emptyFileAndZeroLimitReturnEmpty() throws Exception {
        Path file = Files.createFile(directory.resolve("auditoria.jsonl"));
        assertTrue(AuditLog.tail(file, 10).isEmpty());
        assertTrue(AuditLog.tail(file, 0).isEmpty());
    }

    @Test void timestampBelongsToEventInsteadOfDeliveryTime() {
        AuditEvent event = new AuditEvent(AuditEvent.Type.PORTA_ATRAVESSADA,
                UUID.randomUUID(), "Dev1", 1, 0, 1500, 1, "");
        assertEquals(event.occurredAt().toString(), event.toJson().get("ts").getAsString());
        assertEquals(event.toJson(), event.toJson());
    }
}
