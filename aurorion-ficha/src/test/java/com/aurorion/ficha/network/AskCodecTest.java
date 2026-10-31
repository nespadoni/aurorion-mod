package com.aurorion.ficha.network;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * O formato do pedido do mestre no fio (protocolo 2: com a fórmula dos dados) vai e volta igual,
 * escrever corta em vez de lançar e ler recusa o que passa do teto. Pelo {@code AskWire}, o mesmo que o
 * {@code Ask.STREAM_CODEC} usa: o record carrega o {@code ResourceLocation} do canal, que precisa do FML.
 */
class AskCodecTest {

    /** O que a leitura devolve, no lugar do {@code Ask}. */
    private record Read(List<UUID> targets, String kind, String skill, String attribute, int dc, boolean showDc, String mode,
                        boolean secret, String label, String formula) {
    }

    private static FriendlyByteBuf buffer() {
        return new FriendlyByteBuf(Unpooled.buffer());
    }

    private static Read roundTrip(Read in) {
        FriendlyByteBuf buf = buffer();
        FichaPayloads.AskWire.write(buf, in.targets(), in.kind(), in.skill(), in.attribute(), in.dc(), in.showDc(), in.mode(), in.secret(),
                in.label(), in.formula());
        Read back = FichaPayloads.AskWire.read(buf, Read::new);
        assertEquals(0, buf.readableBytes(), "nada sobra no buffer");
        return back;
    }

    @Test
    void diceAskRoundTrips() {
        Read ask = new Read(List.of(UUID.randomUUID(), UUID.randomUUID()), "free", "", "", 8, false, "normal", true, "seguir o rastro", "2d6+3");
        assertEquals(ask, roundTrip(ask));
    }

    @Test
    void testAskKeepsAnEmptyFormula() {
        Read ask = new Read(List.of(UUID.randomUUID()), "skill", "furtividade", "des", 15, true, "vantagem", false, "a porta", "");
        assertEquals(ask, roundTrip(ask));
    }

    @Test
    void writingClipsInsteadOfThrowing() {
        List<UUID> many = new ArrayList<>();
        for (int i = 0; i < FichaPayloads.MAX_TABLE + 5; i++) many.add(UUID.randomUUID());
        many.set(3, null);
        Read back = roundTrip(new Read(many, "free", null, null, 3, true, null, false, "x".repeat(500), "1d6+".repeat(40) + "1"));
        assertEquals(FichaPayloads.MAX_TABLE, back.targets().size(), "alvos além do teto ficam de fora");
        assertEquals(new UUID(0L, 0L), back.targets().get(3), "alvo nulo vira o UUID zero, não exceção");
        assertEquals(FichaPayloads.FORMULA, back.formula().length(), "fórmula cortada no teto (o servidor revalida)");
        assertEquals(FichaPayloads.LABEL, back.label().length());
        assertEquals("", back.skill());
    }

    @Test
    void readingRefusesAFormulaAboveTheCap() {
        FriendlyByteBuf buf = buffer();
        buf.writeVarInt(0);
        buf.writeUtf("free", 12);
        buf.writeUtf("", FichaPayloads.KEY);
        buf.writeUtf("", 8);
        buf.writeVarInt(0);
        buf.writeBoolean(true);
        buf.writeUtf("normal", 12);
        buf.writeBoolean(false);
        buf.writeUtf("", FichaPayloads.LABEL);
        buf.writeUtf("1d6+".repeat(30) + "1", 200);
        assertThrows(RuntimeException.class, () -> FichaPayloads.AskWire.read(buf, Read::new), "cliente adulterado não passa do teto");
    }

    @Test
    void readingRefusesTooManyTargets() {
        FriendlyByteBuf buf = buffer();
        buf.writeVarInt(FichaPayloads.MAX_TABLE + 1);
        assertThrows(RuntimeException.class, () -> FichaPayloads.AskWire.read(buf, Read::new));
    }
}
