package com.aurorion.aeonita.client;

import com.aurorion.aeonita.AurorionAeonita;
import com.aurorion.aeonita.item.UniformCapeItem;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

/**
 * Onde o GeckoLib acha a geometria, as animacoes e a textura da capa.
 *
 * <p>Geometria e animacao sao as mesmas para as cinco casas — o que muda e so a textura, que vem
 * do proprio item. Por isso existe um {@code .geo.json} e um {@code .animation.json} no jar, e nao
 * cinco copias: o cache de modelo assado do GeckoLib e por {@code ResourceLocation}, entao cinco
 * caminhos iguais em conteudo seriam cinco modelos assados na memoria do cliente fazendo o mesmo.
 *
 * <p>Os dois {@link ResourceLocation} sao constantes porque {@code getModelResource} e
 * {@code getAnimationResource} sao chamados no caminho de render — montar a string a cada frame
 * seria alocacao por frame (SDD §2).
 */
public class UniformCapeModel extends GeoModel<UniformCapeItem> {
    private static final ResourceLocation MODEL = ResourceLocation.fromNamespaceAndPath(
            AurorionAeonita.MOD_ID, "geo/armor/uniform_cape.geo.json");

    private static final ResourceLocation ANIMATIONS = ResourceLocation.fromNamespaceAndPath(
            AurorionAeonita.MOD_ID, "animations/armor/uniform_cape.animation.json");

    /**
     * Marcados como obsoletos pelo GeckoLib em favor das versoes que tambem recebem o
     * {@code GeoRenderer}, mas continuam {@code abstract}: nao ha como nao implementa-los na 4.9.
     * A sobrecarga nova so serve para quem precisa decidir modelo ou textura a partir de quem esta
     * renderizando, e aqui nem uma nem outra dependem disso. A supressao e por metodo, e nao na
     * classe, para nao esconder um aviso de verdade que apareca depois em outro ponto do arquivo.
     */
    @SuppressWarnings("deprecation")
    @Override
    public ResourceLocation getModelResource(UniformCapeItem cape) {
        return MODEL;
    }

    @SuppressWarnings("deprecation")
    @Override
    public ResourceLocation getTextureResource(UniformCapeItem cape) {
        return cape.texture();
    }

    @Override
    public ResourceLocation getAnimationResource(UniformCapeItem cape) {
        return ANIMATIONS;
    }
}
