package com.aurorion.limbo.oracle;

import com.aurorion.limbo.AurorionLimbo;
import com.aurorion.limbo.compat.EconomiaCompat;
import com.aurorion.limbo.config.LimboConfig;
import com.aurorion.limbo.finale.FinaleManager;
import com.aurorion.limbo.narrate.LimboText;
import com.aurorion.limbo.network.LimboNetwork;
import com.aurorion.limbo.registry.LimboItems;
import com.aurorion.limbo.report.AuditEvent;
import com.aurorion.limbo.report.AuditLog;
import com.aurorion.vidas.lives.LivesManager;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.Locale;
import java.util.function.Supplier;

/**
 * A banca do Oraculo: o Fio da Volta e o Relicario, pagos com a carteira do {@code aurorion_economia}.
 *
 * <p>A compra chega por {@code /oraculo comprar <item>}, que a escolha do dialogo do ADM roda na mao
 * de jogador comum. Como na lista do resgate, a trava nao e de permissao, e de posicao: so vende
 * perto de um Oraculo — sem isso, o comando venderia de qualquer lugar do mundo.
 *
 * <p>Ordem: confere, cobra, entrega. Conferir e cobrar acontecem no mesmo tick, entao nao divergem;
 * a entrega nunca falha (o que nao cabe cai aos pes), entao nao ha estorno a fazer depois.
 */
public final class OracleShop {
    private static final String ECONOMIA = "aurorion_economia";
    private static final int STAFF_LEVEL = 2;

    /** O que o Oraculo vende. O nome e o argumento do comando, entao e contrato com o dialogo. */
    public enum Ware {
        FIO("fio", LimboItems.FIO_DA_VOLTA, () -> LimboConfig.FIO_PRICE),
        RELICARIO("relicario", LimboItems.RELICARIO, () -> LimboConfig.RELIC_PRICE);

        private final String id;
        private final Supplier<? extends Item> item;
        private final Supplier<ModConfigSpec.IntValue> price;

        Ware(String id, Supplier<? extends Item> item, Supplier<ModConfigSpec.IntValue> price) {
            this.id = id;
            this.item = item;
            this.price = price;
        }

        public String id() {
            return id;
        }

        public static Ware byId(String id) {
            for (Ware ware : values()) {
                if (ware.id.equals(id.toLowerCase(Locale.ROOT))) return ware;
            }
            return null;
        }
    }

    private OracleShop() {
    }

    /** @return {@code true} se a compra aconteceu. */
    public static boolean buy(ServerPlayer player, Ware ware) {
        if (FinaleManager.isDead(player)) return false;
        if (!LimboNetwork.nearOracle(player)) {
            player.displayClientMessage(LimboText.oracleTooFar(), true);
            return false;
        }

        int obolos = ware.price.get().get();
        if (obolos <= 0) {
            tell(player, Component.translatable("aurorion_limbo.loja.fora_de_venda", itemName(ware)), true);
            return false;
        }
        if (!ModList.get().isLoaded(ECONOMIA)) {
            AurorionLimbo.LOGGER.warn("Compra no Oraculo recusada: aurorion_economia nao esta instalado.");
            tell(player, Component.translatable("aurorion_limbo.loja.sem_economia"), true);
            return false;
        }

        long cost = EconomiaCompat.fromObolos(obolos);
        // Staff em criativo nao paga, como nos NPCs do aurorion_profissoes.
        boolean free = player.isCreative() && player.hasPermissions(STAFF_LEVEL);
        if (!free && !EconomiaCompat.charge(player, cost)) {
            tell(player, Component.translatable("aurorion_limbo.loja.sem_saldo", itemName(ware),
                    EconomiaCompat.describe(cost), EconomiaCompat.describe(EconomiaCompat.balance(player))), true);
            return false;
        }

        ItemStack stack = new ItemStack(ware.item.get());
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }

        tell(player, Component.translatable("aurorion_limbo.loja.comprou", itemName(ware),
                EconomiaCompat.describe(free ? 0L : cost)), false);
        AuditLog.record(player.server, new AuditEvent(AuditEvent.Type.COMPRA_ORACULO, player.getUUID(),
                player.getGameProfile().getName(), LivesManager.livesOf(player.server, player.getUUID()),
                0L, 0, 0, ware.id() + " por " + EconomiaCompat.describe(free ? 0L : cost)
                        + (free ? " (staff em criativo)" : "")));
        return true;
    }

    private static Component itemName(Ware ware) {
        return ware.item.get().getDescription();
    }

    private static void tell(ServerPlayer player, Component message, boolean refusal) {
        player.sendSystemMessage(message.copy().withStyle(style ->
                style.withColor(refusal ? LimboText.RUST : LimboText.COLD)));
    }
}
