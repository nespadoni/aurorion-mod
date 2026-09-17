package com.aurorion.limbo.oracle;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class OracleDataTest {
    @Test
    void pendingMovementSurvivesRestartAndDisappearsWithItsSpot() {
        OracleData source = new OracleData();
        source.add(new OracleData.Spot("porto", ResourceLocation.parse("minecraft:overworld"),
                new BlockPos(10, 70, -30), 45));
        source.setPending("porto");

        OracleData restored = OracleData.load(source.save(new CompoundTag(), null), null);
        assertEquals("porto", restored.pendingSpot().name());
        assertEquals(new BlockPos(10, 70, -30), restored.pendingSpot().pos());

        restored.remove("porto");
        assertNull(restored.pendingSpot());
    }
}
