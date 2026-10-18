package com.aurorion.areas.server;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class AreaWatchTest {
    private static final ResourceLocation WORLD = ResourceLocation.parse("minecraft:overworld");
    private static AreaWatch.Entry entry(String name) {
        return new AreaWatch.Entry(1_760_000_000_000L, "Fulano", new UUID(0, 1), "Fulano (alt de Ciclano)",
                "cofre", name, WORLD, 10, -64, -300, false);
    }
    @Test void lineIsAsciiAndKeepsSpacesAndAccentsInsideOneField() {
        String line = entry("Cofre da Guilda & Cia").line();
        assertTrue(line.startsWith("evento t=1760000000000&area=cofre&nome=Cofre+da+Guilda+%26+Cia&jogador=Fulano"));
        assertTrue(line.contains("&rotulo=Fulano+%28alt+de+Ciclano%29&dim=minecraft%3Aoverworld&x=10&y=-64&z=-300"));
        assertFalse(line.contains("teste="));
        assertEquals(line.length(), line.getBytes(StandardCharsets.UTF_8).length);
        assertFalse(entry("Praça Ñ").line().contains(" "));
    }
    @Test void emptyQueueAnswersHeaderOnly() {
        assertEquals("avisos=0 restantes=0 descartados=0", AreaWatch.drain(new ArrayDeque<>(), AreaWatch.PULL_BUDGET, 0));
    }
    @Test void drainStopsAtBudgetAndLeavesTheRestQueued() {
        Deque<AreaWatch.Entry> queue = new ArrayDeque<>();
        for (int i = 0; i < 40; i++) queue.add(entry("Área vigiada número " + i));
        String out = AreaWatch.drain(queue, AreaWatch.PULL_BUDGET, 3);
        assertTrue(out.getBytes(StandardCharsets.UTF_8).length < 4096);
        String[] lines = out.split("\n");
        int sent = lines.length - 1;
        assertTrue(sent > 0 && sent < 40);
        assertEquals("avisos=" + sent + " restantes=" + (40 - sent) + " descartados=3", lines[0]);
        assertEquals(40 - sent, queue.size());
    }
    @Test void oneOversizedLineStillLeavesTheQueue() {
        Deque<AreaWatch.Entry> queue = new ArrayDeque<>(List.of(entry("x".repeat(96))));
        String out = AreaWatch.drain(queue, 10, 0);
        assertTrue(out.startsWith("avisos=1 restantes=0"));
        assertTrue(queue.isEmpty());
    }
}
