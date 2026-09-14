package com.aurorion.limbo.registry;

import com.aurorion.limbo.AurorionLimbo;
import com.aurorion.limbo.item.SoulBondItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * O unico conteudo registrado pelo Limbo.
 *
 * <p>Item registrado e conteudo que nunca mais sai do modpack: uma vez que exista um no bau de alguem,
 * remover o mod deixa um buraco no save (SDD §6.1). Por isso aqui tem <b>um</b> item e nada mais — o
 * Oraculo e uma tag num mob que ja existe, e a passagem e uma entidade temporaria que some sozinha.
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

    private LimboItems() {
    }
}
