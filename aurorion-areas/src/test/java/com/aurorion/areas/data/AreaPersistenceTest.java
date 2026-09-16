package com.aurorion.areas.data;

import com.aurorion.areas.geometry.*;
import com.aurorion.areas.region.AreaRegion;
import com.aurorion.areas.rules.*;
import com.google.gson.JsonParser;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class AreaPersistenceTest {
    private static final ResourceLocation WORLD = ResourceLocation.parse("minecraft:overworld");
    private static CompoundTag tagOf(String json) {
        CompoundTag tag = new CompoundTag();
        tag.putByteArray("DefinitionsUtf8", json.getBytes(StandardCharsets.UTF_8));
        return tag;
    }
    private static AreaRegion region(String id) {
        return new AreaRegion(id, id, WORLD, 0, true,
                new AreaVolume(List.of(AreaShape.circle(0, 0, 10, 0, 100)), List.of()),
                AreaRules.INHERIT.withFlag("voo", Decision.DENY), Map.of());
    }

    @Test void aRoundTripKeepsEveryArea() {
        AreaData source = new AreaData();
        source.put(region("escola"));
        source.setDefaults(WORLD, AreaRules.INHERIT.withFlag("pvp", Decision.DENY));
        AreaData restored = AreaData.load(source.save(new CompoundTag(), null), null);
        assertFalse(restored.readOnly());
        assertEquals(1, restored.all().size());
        assertEquals("escola", restored.require("escola").id());
        assertEquals(Decision.DENY, restored.defaults(WORLD).flag("pvp"));
    }

    /** O vanilla engole a excecao da leitura; o arquivo nao pode virar vazio no proximo autosave. */
    @Test void unreadableDataIsPreservedAndBlocksEditingInsteadOfBeingOverwritten() {
        for (String broken : List.of(
                "{\"version\":99,\"areas\":[],\"dimensions\":{}}",      // versao futura
                "isto nao e json",                                      // arquivo corrompido
                "{\"version\":1,\"areas\":[{\"id\":\"MAIUSCULA\"}],\"dimensions\":{}}")) { // area invalida
            CompoundTag original = tagOf(broken);
            AreaData data = AreaData.load(original, null);
            assertTrue(data.readOnly(), broken);
            assertTrue(data.all().isEmpty());
            assertArrayEquals(original.getByteArray("DefinitionsUtf8"),
                    data.save(new CompoundTag(), null).getByteArray("DefinitionsUtf8"), broken);
            assertThrows(IllegalArgumentException.class, () -> data.put(region("nova")));
            assertThrows(IllegalArgumentException.class, () -> data.delete("nova"));
            assertThrows(IllegalArgumentException.class, () -> data.setDefaults(WORLD, AreaRules.INHERIT));
            assertThrows(IllegalArgumentException.class, () -> data.clearCharacter(UUID.randomUUID()));
        }
    }

    @Test void anUnexpectedNbtFormatIsPreservedInFull() {
        CompoundTag wrongType = new CompoundTag();
        wrongType.putString("DefinitionsUtf8", "conteudo recuperavel");
        wrongType.putInt("FutureVersion", 2);
        CompoundTag missingDefinitions = new CompoundTag();
        missingDefinitions.putString("FutureDefinitions", "conteudo recuperavel");
        for (CompoundTag original : List.of(wrongType, missingDefinitions, new CompoundTag())) {
            AreaData data = AreaData.load(original, null);
            assertTrue(data.readOnly());
            assertEquals(original, data.save(new CompoundTag(), null));
            // O chamador nao pode alterar a copia preservada pelo SavedData.
            data.save(new CompoundTag(), null).putInt("Mutation", 1);
            assertEquals(original, data.save(new CompoundTag(), null));
        }
    }

    /** Uma area invalida no meio da lista nao pode deixar as anteriores parcialmente em vigor. */
    @Test void aPartialFailureLeavesNoAreaInEffect() {
        AreaData source = new AreaData();
        source.put(region("escola"));
        source.put(region("vila"));
        var json = JsonParser.parseString(new String(source.save(new CompoundTag(), null).getByteArray("DefinitionsUtf8"),
                StandardCharsets.UTF_8)).getAsJsonObject();
        // A primeira area deve carregar antes de a segunda falhar.
        json.getAsJsonArray("areas").get(1).getAsJsonObject().addProperty("priority", 999999);
        AreaData data = AreaData.load(tagOf(json.toString()), null);
        assertTrue(data.readOnly());
        assertTrue(data.all().isEmpty());
        assertTrue(data.allows(WORLD, 0, 50, 0, null, "voo"));
    }

    @Test void invalidDimensionDefaultsDoNotPublishAlreadyParsedAreas() {
        AreaData source = new AreaData();
        source.put(region("escola"));
        var json = JsonParser.parseString(new String(source.save(new CompoundTag(), null).getByteArray("DefinitionsUtf8"),
                StandardCharsets.UTF_8)).getAsJsonObject();
        json.getAsJsonObject("dimensions").add("INVALID DIMENSION", AreaJson.writeRules(AreaRules.INHERIT));
        AreaData data = AreaData.load(tagOf(json.toString()), null);
        assertTrue(data.readOnly());
        assertTrue(data.all().isEmpty());
        assertTrue(data.allows(WORLD, 0, 50, 0, null, "voo"));
    }
}
