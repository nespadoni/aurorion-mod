package com.aurorion.aeonita.registry;

import com.aurorion.aeonita.AurorionAeonita;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;
import java.util.Map;

public final class AeonitaArmorMaterials {
    public static final DeferredRegister<ArmorMaterial> ARMOR_MATERIALS =
            DeferredRegister.create(Registries.ARMOR_MATERIAL, AurorionAeonita.MOD_ID);

    /**
     * Material da capa de uniforme: protecao de couro, e nada alem disso.
     *
     * <p>A capa e roupa de escola, nao equipamento. Num modpack pesado, um peitoral novo com
     * protecao competitiva mudaria o balanceamento de PvP e de mob de outros mods sem ninguem ter
     * pedido — o tipo de interferencia fora do escopo proprio que o SDD §2 lista como meta.
     * Protecao de couro deixa vestir a capa custar alguma coisa sem que ela vire a melhor peca
     * do pack.
     *
     * <p>A lista de camadas e vazia de proposito. Quem desenha a capa e o {@code GeoArmorRenderer}
     * do GeckoLib, que cancela o caminho vanilla de render de armadura antes de ele resolver
     * textura nenhuma — declarar camadas aqui seria apontar para um PNG que nunca e lido. Com a
     * lista vazia, o pior caso (GeckoLib ausente) e a capa ficar invisivel, e nao virar textura
     * faltando roxa e preta em cima do jogador.
     */
    public static final Holder<ArmorMaterial> UNIFORM = ARMOR_MATERIALS.register("uniform", () -> new ArmorMaterial(
            Map.of(ArmorItem.Type.CHESTPLATE, 3),
            15,
            SoundEvents.ARMOR_EQUIP_LEATHER,
            () -> Ingredient.of(Items.LEATHER),
            List.of(),
            0.0f,
            0.0f));

    private AeonitaArmorMaterials() {
    }
}
