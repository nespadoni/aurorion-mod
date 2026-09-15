package com.aurorion.aeonita.item;

import com.aurorion.aeonita.AurorionAeonita;
import com.aurorion.aeonita.registry.AeonitaArmorMaterials;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.client.GeoRenderProvider;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.renderer.GeoArmorRenderer;
import software.bernie.geckolib.util.GeckoLibUtil;
import software.bernie.geckolib.util.RenderUtil;

import java.util.function.Consumer;

/**
 * A capa do uniforme da escola: peitoral animado, uma cor por casa.
 *
 * <p>Sao cinco itens registrados, e nao um item com a casa guardada num componente da
 * {@code ItemStack}. Cada capa e uma peca de roupa distinta que a staff entrega, um jogador
 * guarda no bau e outro pode receber de presente — com id proprio ela aparece em {@code /give},
 * em receita, em loot table e em advancement sem nenhum predicado de componente no meio. O preco
 * assumido e que uma casa nova exige recompilar, diferente das casas em si, que sao datapack no
 * {@code aurorion-ethereal}.
 *
 * <p>Este mod <strong>nao</strong> conhece o {@code aurorion-ethereal}: a capa nao pergunta em que
 * casa o jogador esta nem se trava na cor dele. Quem veste o que e decisao de quem entrega a capa.
 * Isso mantem os dois mods ligaveis e desligaveis um sem o outro (SDD §3), e e a mesma fronteira
 * de "conteudo x comportamento" que ja separa o Altar de Selecao (deste mod) da escolha de casa
 * (do outro).
 *
 * <p>A textura viaja no proprio item, e nao no modelo: o {@code GeoModel} pergunta a capa que
 * textura usar, entao as cinco cores compartilham um geo e um arquivo de animacao so.
 */
public class UniformCapeItem extends ArmorItem implements GeoItem {
    /**
     * Estas quatro constantes existem porque o handler abaixo roda <em>por capa visivel, por
     * frame</em>. {@code RawAnimation.begin().thenLoop(...)} aloca uma lista e um record a cada
     * chamada; construi-las dentro do handler seria lixo novo 60x por segundo por jogador na tela,
     * exatamente o que o SDD §2 proibe no caminho quente do cliente.
     *
     * <p>Sao visiveis ao pacote porque {@code UniformCapeAssetsTest} le os nomes daqui e confere um
     * a um contra o arquivo de animacao do jar. Um nome escrito errado nao levanta erro nenhum no
     * GeckoLib — a capa so fica parada no jogo —, entao o teste e o unico lugar onde esse erro
     * aparece. Sem isso, os nomes estariam escritos duas vezes e o teste passaria a mentir junto.
     */
    static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
    static final RawAnimation WALKING = RawAnimation.begin().thenLoop("walking");
    static final RawAnimation RUNNING = RawAnimation.begin().thenLoop("running");
    static final RawAnimation JUMPING = RawAnimation.begin().thenLoop("jumping");

    /** Duracao da mistura entre duas animacoes, em ticks. */
    private static final int TRANSITION_TICKS = 5;

    /** Durabilidade de couro: 5x o valor base do tipo de peca (16 para peitoral). */
    private static final int LEATHER_DURABILITY = 5;

    private final ResourceLocation texture;
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public UniformCapeItem(String house) {
        super(AeonitaArmorMaterials.UNIFORM, Type.CHESTPLATE, properties());
        this.texture = ResourceLocation.fromNamespaceAndPath(
                AurorionAeonita.MOD_ID, "textures/entity/armor/uniform_cape/" + house + ".png");
    }

    private static Item.Properties properties() {
        return new Item.Properties()
                .durability(Type.CHESTPLATE.getDurability(LEATHER_DURABILITY))
                .rarity(Rarity.UNCOMMON);
    }

    /** A textura desta casa. Lida pelo {@code GeoModel} no cliente. */
    public ResourceLocation texture() {
        return texture;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "uniform", TRANSITION_TICKS, this::animate));
    }

    /**
     * Escolhe a animacao a partir do estado de quem esta vestindo. Le so o que ja esta na copia
     * local da entidade — nada disso pergunta nada ao servidor, entao nao ha pacote nenhum por
     * troca de animacao.
     */
    private PlayState animate(AnimationState<UniformCapeItem> state) {
        Entity wearer = state.getData(DataTickets.ENTITY);

        if (wearer == null) {
            // Render fora do mundo (tela de inventario, mao de um mob sem contexto): nao ha estado
            // de movimento para ler, entao a capa so balanca parada.
            return state.setAndContinue(IDLE);
        }
        if (!wearer.onGround() && !wearer.isInWater()) {
            return state.setAndContinue(JUMPING);
        }
        if (!state.isMoving()) {
            return state.setAndContinue(IDLE);
        }
        return state.setAndContinue(wearer.isSprinting() ? RUNNING : WALKING);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    @Override
    public double getTick(Object animatable) {
        return RenderUtil.getCurrentTick();
    }

    /**
     * Ponto de entrada do render, e a unica parte desta classe que toca em classe de cliente.
     *
     * <p>O GeckoLib so chama este metodo depois de checar {@code isPhysicalClient()}, e a checagem
     * mora dentro de um {@code Supplier} preguicoso. Por isso a classe anonima abaixo — que
     * referencia {@code GeoArmorRenderer} e {@code HumanoidModel}, ambos client-only — so e
     * carregada pela JVM quando o corpo do metodo realmente executa, ou seja, nunca no servidor
     * dedicado. E a mesma disciplina que o SDD §3.1 descreve para os lambdas de registro de rede:
     * a referencia a classe de cliente nasce <em>depois</em> da checagem de {@code Dist}, nao antes.
     */
    @Override
    public void createGeoRenderer(Consumer<GeoRenderProvider> consumer) {
        consumer.accept(new GeoRenderProvider() {
            private GeoArmorRenderer<?> renderer;

            @Override
            public <T extends LivingEntity> HumanoidModel<?> getGeoArmorRenderer(
                    T entity, ItemStack stack, EquipmentSlot slot, HumanoidModel<T> original) {
                if (renderer == null) {
                    renderer = new com.aurorion.aeonita.client.UniformCapeRenderer();
                }
                return renderer;
            }
        });
    }
}
