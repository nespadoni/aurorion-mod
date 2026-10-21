package com.aurorion.ethereal.house;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    /** Enxurrada de depositos nao tira o saque da vitrine; deposito minusculo nem entra. */
    @Test
    void depositsNeverEvictWithdrawals() {
        HouseUpgradeData data = new HouseUpgradeData();
        ResourceLocation nyx = ResourceLocation.parse("aurorion_ethereal:nyx");
        data.recordMovement(nyx, new HouseUpgradeData.Movement(1L, "Ladrao", HouseUpgradeData.Kind.WITHDRAW, 900L));
        data.recordMovement(nyx, new HouseUpgradeData.Movement(2L, "Ladrao", HouseUpgradeData.Kind.DEPOSIT, 1L));
        assertEquals(1, data.movements(nyx).size());

        for (int i = 0; i < 20; i++) {
            data.recordMovement(nyx, new HouseUpgradeData.Movement(10L + i, "Ladrao",
                    HouseUpgradeData.Kind.DEPOSIT, HouseUpgradeData.HISTORY_MIN_DEPOSIT));
        }
        List<HouseUpgradeData.Movement> lines = data.movements(nyx);
        assertEquals(HouseUpgradeData.MAX_DEPOSIT_LINES + 1, lines.size());
        assertEquals(HouseUpgradeData.Kind.DEPOSIT, lines.get(0).kind());
        assertEquals(29L, lines.get(0).at());
        assertTrue(lines.stream().anyMatch(line -> line.kind() == HouseUpgradeData.Kind.WITHDRAW));
    }

    @Test
    void dailyWithdrawalsAreCountedPerDayAndHouse() {
        HouseUpgradeData data = new HouseUpgradeData();
        ResourceLocation nyx = ResourceLocation.parse("aurorion_ethereal:nyx");
        ResourceLocation ignivar = ResourceLocation.parse("aurorion_ethereal:ignivar");
        UUID member = UUID.randomUUID();

        data.recordWithdrawal(member, nyx, 100L, 30L);
        data.recordWithdrawal(member, nyx, 100L, 20L);
        assertEquals(50L, data.withdrawnToday(member, nyx, 100L));
        assertEquals(0L, data.withdrawnToday(member, nyx, 101L), "dia novo zera");
        assertEquals(0L, data.withdrawnToday(member, ignivar, 100L), "outra Casa nao herda o uso");
    }

    @Test
    void sanitizeStripsLogInjection() {
        String name = "Ana\n[INFO] falso" + (char) 0x00A7 + "c" + (char) 0x2028 + "\r\tFim";
        assertEquals("Ana[INFO] falsocFim", HouseMuralManager.sanitize(name));
        assertEquals(64, HouseMuralManager.sanitize("x".repeat(200)).length());
    }
}
