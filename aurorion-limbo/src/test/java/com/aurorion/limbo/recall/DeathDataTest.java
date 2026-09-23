package com.aurorion.limbo.recall;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class DeathDataTest {
    @Test
    void lastDeathSurvivesRestartWithExactPosition() {
        UUID character = UUID.randomUUID();
        UUID deathId = UUID.randomUUID();
        DeathData source = new DeathData();
        source.record(character, new DeathData.Death(deathId, ResourceLocation.parse("minecraft:the_nether"),
                new Vec3(10.25D, 64.5D, -30.75D), 1234L));

        DeathData restored = DeathData.load(source.save(new CompoundTag(), null), null);
        DeathData.Death death = restored.last(character);
        assertEquals(deathId, death.id());
        assertEquals(ResourceLocation.parse("minecraft:the_nether"), death.dimension());
        assertEquals(new Vec3(10.25D, 64.5D, -30.75D), death.position());
        assertEquals(1234L, death.diedAt());
    }

    @Test
    void newDeathReplacesThePreviousOne() {
        UUID character = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        DeathData data = new DeathData();
        data.record(character, new DeathData.Death(UUID.randomUUID(), ResourceLocation.parse("minecraft:overworld"),
                Vec3.ZERO, 1L));
        data.record(character, new DeathData.Death(second, ResourceLocation.parse("minecraft:overworld"),
                new Vec3(1, 2, 3), 2L));

        assertEquals(second, data.last(character).id());

        data.clear(character);
        assertNull(data.last(character));
    }
}
