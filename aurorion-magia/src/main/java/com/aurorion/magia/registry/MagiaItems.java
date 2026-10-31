package com.aurorion.magia.registry;

import com.aurorion.magia.AurorionMagia;
import com.aurorion.magia.passive.PassiveScrollItem;
import com.aurorion.magia.spoils.SpoilsBagItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Os itens do mod: o pergaminho que ensina uma passiva e a mochila de espolios do Spoliatio.
 *
 * <p>Um item so, com a passiva num componente ({@link MagiaComponents}), e nao um item por passiva:
 * item registrado e conteudo que nunca mais sai do modpack (SDD §6.1), e uma passiva nova nao deveria
 * custar uma entrada permanente no registro.
 *
 * <p>{@code stacksTo(1)} de proposito: pergaminho de passiva nao e moeda. Empilhavel, a staff acabaria
 * entregando dezesseis de uma vez e a marca deixaria de ser um momento na mesa.
 */
public final class MagiaItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(AurorionMagia.MOD_ID);

    public static final DeferredItem<Item> PASSIVE_SCROLL = ITEMS.register("pergaminho_passiva",
            () -> new PassiveScrollItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant()));

    /**
     * Onde o Spoliatio guarda o que tomou. Nao vai para a aba criativa: so existe saindo de alguem.
     * {@code fireResistant} porque lava levaria junto os pertences de uma pessoa inteira.
     */
    public static final DeferredItem<Item> SPOILS_BAG = ITEMS.register("mochila_espolios",
            () -> new SpoilsBagItem(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON).fireResistant()));

    private MagiaItems() {
    }
}
