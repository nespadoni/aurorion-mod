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
    void walletAndVaultMoveTogetherWithoutCreatingMoney() {
        WalletData data = new WalletData();
        UUID player = UUID.randomUUID();
        ResourceLocation house = ResourceLocation.parse("aurorion_ethereal:venthra");
        data.setBalance(player, 100);

        assertTrue(data.moveBetweenWalletAndHouse(player, house, 60));
        assertEquals(40, data.balance(player));
        assertEquals(60, data.houseBalance(house));

        assertTrue(data.moveBetweenWalletAndHouse(player, house, -25));
        assertEquals(65, data.balance(player));
        assertEquals(35, data.houseBalance(house));

        assertFalse(data.moveBetweenWalletAndHouse(player, house, 66), "carteira sem saldo");
        assertFalse(data.moveBetweenWalletAndHouse(player, house, -36), "cofre sem saldo");
        assertEquals(100, data.balance(player) + data.houseBalance(house));
    }

    @Test
    void salaryIsKeptWithTheHouseAndZeroTurnsItOff() {
        WalletData data = new WalletData();
        ResourceLocation house = ResourceLocation.parse("aurorion_ethereal:nyx");

        data.setHouseSalary(house, 500, 7, 1234L);
        assertEquals(500, data.house(house).salary());
        assertEquals(7, data.house(house).salaryDays());
        assertEquals(1234L, data.house(house).lastSalaryAt());

        data.setHouseSalary(house, 0, 7, 99L);
        assertEquals(WalletData.HouseState.EMPTY, data.house(house));
        assertTrue(data.houseIds().isEmpty(), "casa sem nada nao fica gravada");
    }

    @Test
    void vaultCapacitiesFollowTheEconomyDocument() {
        assertEquals(1_000, HouseTreasury.capacityForLevel(0));
        assertEquals(2_500, HouseTreasury.capacityForLevel(1));
        assertEquals(5_000, HouseTreasury.capacityForLevel(2));
        assertEquals(10_000, HouseTreasury.capacityForLevel(3));
    }
}
