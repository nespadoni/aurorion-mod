package com.aurorion.profissoes.registry;

import com.aurorion.profissoes.AurorionProfissoes;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ProfessionItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(AurorionProfissoes.MOD_ID);

    /**
     * O que sobra de uma fabricação que falhou. Sair <b>alguma coisa</b> importa: a máquina não trava
     * esperando um resultado, e o jogador vê na mão que a peça se perdeu.
     */
    public static final DeferredItem<Item> RUINED_PIECE = ITEMS.registerSimpleItem("peca_arruinada", new Item.Properties());

    private ProfessionItems() {
    }
}
