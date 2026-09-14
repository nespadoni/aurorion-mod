package com.aurorion.limbo.network;

import com.aurorion.limbo.finale.FinaleScript;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class FinalePayloadTest {
    @Test void viewingProgressAndServerAuthoritySurviveTheWire() {
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        var payload = new FinalePayload(false, 31_500,
                new FinaleScript(25, 15, 140, "A vida", List.of("Um sopro."), "aurorion_limbo:finale"));
        try {
            FinalePayload.STREAM_CODEC.encode(buffer, payload);
            assertEquals(payload, FinalePayload.STREAM_CODEC.decode(buffer));
            assertFalse(buffer.isReadable());
        } finally { buffer.release(); }
    }

    @Test void oversizedParagraphCountIsRejectedBeforeAllocating() {
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            buffer.writeVarInt(25); buffer.writeVarInt(15); buffer.writeVarInt(140);
            buffer.writeUtf("A vida"); buffer.writeVarInt(Integer.MAX_VALUE);
            assertThrows(IllegalArgumentException.class, () -> FinaleScript.read(buffer));
        } finally { buffer.release(); }
    }
}
