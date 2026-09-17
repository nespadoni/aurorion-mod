package com.aurorion.economia.server;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EconomyFoundationTest {
    @Test
    void starterBalanceIsGrantedOncePerCharacterRatherThanOncePerAccount() {
        WalletData data = new WalletData();
        UUID account = UUID.randomUUID();
        UUID firstCharacter = UUID.randomUUID();

        assertTrue(data.grantStarter(firstCharacter, account, 40));
        assertFalse(data.grantStarter(firstCharacter, account, 40));
        assertEquals(40, data.balance(account));

        assertTrue(data.grantStarter(UUID.randomUUID(), account, 40));
        assertEquals(40, data.balance(account));
    }

    @Test
    void vaultCannotBeDowngradedBelowItsCurrentBalance() {
        WalletData data = new WalletData();
        ResourceLocation house = ResourceLocation.parse("aurorion_ethereal:ignivar");

        assertTrue(data.setHouseVaultLevel(house, 3));
        assertTrue(data.setHouseBalance(house, 6_000));
        assertFalse(data.setHouseVaultLevel(house, 1));
        assertEquals(3, data.houseVaultLevel(house));
        assertEquals(6_000, data.houseBalance(house));
    }

    @Test
    void vaultCapacitiesFollowTheEconomyDocument() {
        assertEquals(1_000, HouseTreasury.capacityForLevel(0));
        assertEquals(2_500, HouseTreasury.capacityForLevel(1));
        assertEquals(5_000, HouseTreasury.capacityForLevel(2));
        assertEquals(10_000, HouseTreasury.capacityForLevel(3));
    }
}
