package com.aurorion.ethereal;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

/**
 * O acoplamento com o mod de conteudo e uma tag, nao uma classe.
 *
 * <p>Este mod nunca importa {@code com.aurorion.aeonita}: ele so pergunta "esse bloco esta na tag de
 * altares?". Consequencias praticas: da para desligar o mod de conteudo sem este quebrar, da para um
 * datapack promover qualquer bloco a altar (um bloco de comando, uma mesa de encantamento, um bloco
 * de outro mod do modpack) e da para um evento usar um altar temporario sem tocar em codigo.
 */
public final class EtherealTags {
    /** Blocos que iniciam a Cerimonia de Vinculacao ao serem clicados. */
    public static final TagKey<Block> HOUSE_ALTARS = TagKey.create(
            Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath(AurorionEthereal.MOD_ID, "house_altars"));

    private EtherealTags() {
    }
}
