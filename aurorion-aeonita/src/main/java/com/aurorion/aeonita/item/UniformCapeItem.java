package com.aurorion.aeonita.item;

import com.aurorion.aeonita.AurorionAeonita;
import com.aurorion.aeonita.registry.AeonitaArmorMaterials;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.resources.ResourceLocation;
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

import java.util.List;
import java.util.function.Consumer;

/**
 * A capa do uniforme da escola: peitoral animado, uma cor por casa mais uma preta para quem ainda
 * nao tem casa.
 *
 * <p>Sao seis itens registrados, e nao um item com a casa guardada num componente da
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
 * textura usar, entao as seis cores compartilham um geo e um arquivo de animacao so.
 *
 * <p>A resistencia ao frio das capas nao esta aqui, e sim num datapack proprio em
 * {@code data/aurorion_aeonita/legendarysurvivaloverhaul/temperature/items/}. O Legendary Survival
 * Overhaul le aquilo sozinho; sem ele instalado os arquivos ficam parados no jar sem serem lidos
 * por ninguem. E o jeito de a capa aquecer sem este mod passar a depender daquele (SDD §3).
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

    /**
     * O nome de cada capa, na ordem da aba do criativo, que e tambem o nome do PNG dela.
     *
     * <p>Mora aqui, e nao no {@code AeonitaItems} ao lado dos registros, por um motivo mecanico:
     * o {@code UniformCapeAssetsTest} le esta lista, e tocar em {@code AeonitaItems} de um teste
     * comum dispara o {@code static} dele, que precisa dos registros do Minecraft de pe. Esta
     * classe nao, entao e daqui que o teste consegue ler.
     *
     * <p>O teste percorre a lista e confere, uma a uma, se a capa tem as duas texturas, o modelo
     * de item, as duas traducoes e o arquivo de temperatura. Nada disso quebra o build nem levanta
     * excecao: no jogo uma textura que falta vira capa roxa e preta, uma traducao que falta vira a
     * chave crua na tooltip, e um arquivo de temperatura que falta so deixa o jogador morrer de
     * frio sem aviso nenhum. Ele tambem confere que esta lista tem o mesmo tamanho que o numero de
     * capas registradas no {@code AeonitaItems}, para as duas nao se separarem em silencio.
     */
    static final List<String> CAPAS =
            List.of("sem_casa", "venthra", "sylvara", "nyx", "ignivar", "aetheris");

    /** Duracao da mistura entre duas animacoes, em ticks. */
    private static final int TRANSITION_TICKS = 5;

    /**
     * Balanco de membros a partir do qual a capa passa a andar, e nao a balancar parada. Serve
     * so para nao trocar de animacao com o tranco de um pixel — agachar, empurrar ou virar no
     * lugar mexem o {@code walkAnimation} um pouco sem ser caminhada.
     */
    private static final float MOTION_THRESHOLD = 0.01f;

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
        if (!(state.getData(DataTickets.ENTITY) instanceof LivingEntity wearer)) {
            // Render fora do mundo (tela de inventario, mao de um mob sem contexto): nao ha estado
            // de movimento para ler, entao a capa so balanca parada.
            return state.setAndContinue(IDLE);
        }
        if (!wearer.onGround() && !wearer.isInWater()) {
            return state.setAndContinue(JUMPING);
        }
        if (!isMoving(wearer, state.getPartialTick())) {
            return state.setAndContinue(IDLE);
        }
        return state.setAndContinue(wearer.isSprinting() ? RUNNING : WALKING);
    }

    /**
     * Se quem esta de capa esta andando, medido pelo balanco dos membros da propria entidade.
     *
     * <p>Nao da para usar {@code state.isMoving()} aqui. O {@code GeoArmorRenderer} monta o
     * {@code AnimationState} com {@code new AnimationState<>(animatable, 0, 0, partialTick, false)}
     * — limbSwing, limbSwingAmount e isMoving entram zerados e fixos, porque o render de armadura
     * nao recebe o balanco dos membros de quem veste. A propria documentacao do GeckoLib diz que o
     * limiar de movimento e "currently unused" para item. Com isso {@code isMoving()} e sempre
     * falso, e um handler que dependa dele nunca sai de idle: as animacoes de andar e correr viram
     * codigo inalcancavel, sem erro nenhum que apareca no build ou no log.
     *
     * <p>{@code walkAnimation} resolve porque o vanilla a alimenta em
     * {@code LivingEntity.calculateEntityAnimation} a partir do deslocamento real entre dois
     * ticks. Vale tanto para quem joga aqui quanto para os outros jogadores na tela, ao contrario
     * de {@code getDeltaMovement()}, que para uma entidade remota costuma vir zerado — quem nao e
     * o jogador local anda por interpolacao de posicao, nao por delta aplicado no cliente.
     */
    private static boolean isMoving(LivingEntity wearer, float partialTick) {
        return wearer.walkAnimation.speed(partialTick) > MOTION_THRESHOLD;
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
