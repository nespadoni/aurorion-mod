package com.aurorion.limbo.registry;

import com.aurorion.limbo.AurorionLimbo;
import com.aurorion.limbo.item.ReliquaryItem;
import com.aurorion.limbo.item.ReturnThreadItem;
import com.aurorion.limbo.item.SoulBondItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Os itens do Limbo.
 *
 * <p>Item registrado e conteudo que nunca mais sai do modpack: uma vez que exista um no bau de alguem,
 * remover o mod deixa um buraco no save (SDD §6.1). Por isso a lista e curta e cada item aqui tem
 * uma mecanica do Limbo por tras — o Vinculo do resgate, e o Fio e o Relicario do espolio, que o
 * Oraculo vende.
 */
public final class LimboItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(AurorionLimbo.MOD_ID);

    /**
     * O Vinculo de Alma: o que transforma achar a pessoa em traze-la de volta.
     *
     * <p>Nao empilha, e de proposito. Empilhavel viraria moeda — alguem acumularia vinte e o resgate
     * deixaria de custar uma decisao. Um por resgate, e o que sobrar da viagem some junto com a
     * passagem.
     */
    public static final DeferredItem<Item> SOUL_BOND = ITEMS.register("vinculo_de_alma",
            () -> new SoulBondItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant()));

    /**
     * O Fio da Volta: uso unico, leva ao lugar exato da ultima morte.
     *
     * <p>Empilha, ao contrario do Vinculo: aqui quem limita e o preco no Oraculo, nao a escassez no
     * bolso. Nao cai na morte: os dois estao na tag {@code aurorion_core:kept_on_death}, em
     * {@code data/aurorion_core/tags/item/kept_on_death.json} deste mod.
     */
    public static final DeferredItem<Item> FIO_DA_VOLTA = ITEMS.register("fio_da_volta",
            () -> new ReturnThreadItem(new Item.Properties().stacksTo(16).rarity(Rarity.RARE).fireResistant()));

    /** O Relicario: uso unico, chama de volta o que a ultima morte deixou e ainda existe. */
    public static final DeferredItem<Item> RELICARIO = ITEMS.register("relicario",
            () -> new ReliquaryItem(new Item.Properties().stacksTo(16).rarity(Rarity.EPIC).fireResistant()));

    private LimboItems() {
    }
}
