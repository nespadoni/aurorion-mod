package com.aurorion.ethereal.house;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HouseUpgradeDataTest {
    @Test
    void protectorStateBelongsToHouseAndLevelsAreBounded() {
        HouseUpgradeData data = new HouseUpgradeData();
        ResourceLocation ignivar = ResourceLocation.parse("aurorion_ethereal:ignivar");
        ResourceLocation nyx = ResourceLocation.parse("aurorion_ethereal:nyx");

        assertEquals(0, data.state(ignivar).protectorLevel());
        assertEquals(2, data.setProtectorLevel(ignivar, 9));
        data.recordLifeGrant(ignivar, 1234L);

        assertEquals(2, data.state(ignivar).protectorLevel());
        assertEquals(1234L, data.state(ignivar).lastLifeGrantAt());
        assertEquals(HouseUpgradeData.State.EMPTY, data.state(nyx));
    }
}
