package com.aurorion.ato2;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

/**
 * O acoplamento com o mod de conteudo e uma tag, nao uma classe.
 *
 * <p>Este mod nunca importa {@code com.aurorion.aeonita}: ele so pergunta "esse bloco esta na tag de
 * altares?". Consequencias praticas: da para desligar um dos dois mods sem o outro quebrar, da para
 * um datapack promover qualquer bloco a altar (um bloco de comando, uma mesa de encantamento, um
 * bloco de outro mod do modpack) e da para o Ato 3 reaproveitar o mesmo Altar de Selecao com outra
 * mecanica sem tocar em nada aqui.
 */
public final class Ato2Tags {
    /** Blocos que abrem a escolha de casa ao serem clicados. */
    public static final TagKey<Block> HOUSE_ALTARS = TagKey.create(
            Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath(AurorionAto2.MOD_ID, "house_altars"));

    private Ato2Tags() {
    }
}
