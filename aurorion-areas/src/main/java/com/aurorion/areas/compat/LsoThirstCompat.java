package com.aurorion.areas.compat;

import com.aurorion.areas.AurorionAreas;
import com.aurorion.areas.api.AreaApi;
import com.aurorion.areas.server.AreaTags;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.fml.ModList;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;

/**
 * Ponte opcional com o Legendary Survival Overhaul: <b>cantil enchido dentro de uma area que concede
 * {@code agua_pura} sai purificado</b>.
 *
 * <p>No LSO 2.4.7.2, {@code CanteenItem.fill} grava {@code PURIFIED} apenas quando o cantil tem o
 * encantamento {@code Purity}; toda outra fonte de agua grava {@code NORMAL}. O encantamento resolve
 * o problema pelo lado errado: ele purifica <em>qualquer</em> agua, inclusive a do rio e a do pantano,
 * e com isso apaga a diferenca entre lugar seguro e lugar perigoso — que e exatamente a mecanica que
 * este modulo existe para sustentar. Aqui quem purifica e o <b>lugar</b>.
 *
 * <p>Quem concede e a area, e ela escolhe o quanto concede:
 *
 * <ul>
 *   <li>{@link #PURE_WATER} — <b>toda</b> agua de dentro sai tratada. E o oasis: a agua e limpa por
 *       ser dali.</li>
 *   <li>{@link #PURE_WATER_TAP} — so a agua que sai de um bloco de {@link AreaTags#WATER_TAPS}. E o
 *       predio: a torneira e potavel, e o rio que passa ao lado continua sendo rio.</li>
 * </ul>
 *
 * <p>A segunda existe porque a primeira sozinha obrigaria a staff a desenhar uma area minuscula em
 * cima de cada pia para dizer "aqui a agua e tratada, tres blocos para o lado nao" — e a Academia
 * inteira tem dezenas delas.
 *
 * <h2>Por que o gancho e o {@code fill}, e nao o clique</h2>
 *
 * <p>Enganchar no {@code useOn} teria falso positivo obvio: clicar numa parede com um cantil de agua
 * de rio na mao tambem "termina" ali dentro, e a agua velha seria purificada sem ninguem encher nada.
 * O {@code fill} e o enchimento de verdade, e todos os caminhos passam por ele — agua natural,
 * caldeirao e a integracao de pia/bacia do Refurbished Furniture, inclusive uma que o LSO acrescente
 * depois.
 *
 * <p>O preco e que {@code fill(ItemStack, Level)} nao recebe jogador nem posicao. Por isso a
 * {@link #beginInteraction} guarda quem clicou, onde, e em que tick — o evento de clique dispara
 * imediatamente antes, na mesma thread e no mesmo tick. A comparacao de tick e o que impede um
 * {@code fill} de outra origem (automacao, comando, outro mod) pegar carona num clique antigo.
 *
 * <p>Sem o LSO instalado nada disto e carregado: o mixin so e aplicado com
 * {@code requiredMods = ["legendarysurvivaloverhaul"]}, e nenhuma classe do LSO aparece em assinatura
 * nossa — mesmo desenho do {@link IronSpellsCompat}.
 */
public final class LsoThirstCompat {
    /**
     * Toda agua da area sai tratada. Concessao, nao proibicao: sem area que conceda, nao existe.
     */
    public static final String PURE_WATER = "agua_pura";
    /**
     * So a agua de encanamento da area sai tratada — o bloco clicado precisa estar em
     * {@link AreaTags#WATER_TAPS}. E a regra para um predio onde a torneira e potavel e o rio que
     * passa ao lado nao e; {@link #PURE_WATER} e para um oasis, onde a agua e limpa por ser dali.
     *
     * <p>As duas sao independentes e somam: uma area pode conceder as duas, e a de cima vence sem
     * precisar de ordem definida, porque qualquer uma que conceda ja basta.
     */
    public static final String PURE_WATER_TAP = "agua_pura_pia";

    private static Method getHydration, setHydration, getCapacity;
    private static Object normal, purified;

    /**
     * Ultimo clique de jogador visto neste tick. Confinado a thread do servidor.
     *
     * <p>E uma referencia forte a um {@code ServerPlayer}, entao ela e <b>soltada assim que usada</b>
     * e tambem no logout: sem isso, o ultimo jogador que clicou em alguma coisa ficaria vivo em
     * memoria depois de desconectar — com inventario, conexao e o mundo que ele referencia — ate
     * alguem clicar de novo. Num servidor que esvazia de madrugada, "ate alguem clicar de novo" e o
     * proximo restart.
     */
    @Nullable private static Player interacting;
    @Nullable private static BlockPos clicked;
    private static long interactionTick = Long.MIN_VALUE;

    private LsoThirstCompat() {
    }

    public static void register() {
        if (!ModList.get().isLoaded("legendarysurvivaloverhaul")) return;
        try {
            Class<?> thirst = Class.forName("sfiomn.legendarysurvivaloverhaul.api.thirst.ThirstUtil");
            Class<?> hydration = Class.forName("sfiomn.legendarysurvivaloverhaul.api.thirst.HydrationEnum");
            getHydration = thirst.getMethod("getHydrationEnumTag", ItemStack.class);
            setHydration = thirst.getMethod("setHydrationEnumTag", ItemStack.class, hydration);
            getCapacity = thirst.getMethod("getCapacityTag", ItemStack.class);
            normal = hydration.getField("NORMAL").get(null);
            purified = hydration.getField("PURIFIED").get(null);
            AurorionAreas.LOGGER.info("Areas: integracao de agua pura do Legendary Survival Overhaul registrada.");
        } catch (ReflectiveOperationException | LinkageError exception) {
            getHydration = null; setHydration = null; getCapacity = null;
            AurorionAreas.LOGGER.error("Areas: API de sede do Legendary Survival Overhaul incompativel."
                    + " A regra '{}' nao vai purificar nada; confira a versao instalada.", PURE_WATER, exception);
        }
    }

    /**
     * Basta uma das duas concessoes. A da torneira e checada depois porque ela custa uma leitura de
     * bloco a mais, e so faz sentido quando ha bloco clicado — agua olhada de longe nunca e torneira.
     */
    private static boolean purifiesHere(ServerLevel level, java.util.UUID actor,
                                        double x, double y, double z, @Nullable BlockPos source) {
        if (AreaApi.grantedAt(level, x, y, z, actor, PURE_WATER)) return true;
        return source != null
                && AreaApi.grantedAt(level, x, y, z, actor, PURE_WATER_TAP)
                && level.getBlockState(source).is(AreaTags.WATER_TAPS);
    }

    /**
     * Guarda o clique que pode estar prestes a encher um cantil.
     *
     * <p>Roda em <b>todo clique direito de todo jogador</b>, entao a primeira linha e a que importa:
     * sem o LSO no pack, {@code setHydration} e nulo e a chamada acaba ali, sem tocar em mais nada.
     */
    public static void beginInteraction(Player player, @Nullable BlockPos pos) {
        if (setHydration == null || player.level().isClientSide()) return;
        interacting = player;
        clicked = pos;
        interactionTick = player.level().getGameTime();
    }

    /** Solta o trinco quando o jogador sai, para nao segurar uma entidade morta num campo estatico. */
    public static void forget(Player player) {
        if (interacting == player) clearLatch();
    }

    private static void clearLatch() {
        interacting = null;
        clicked = null;
        interactionTick = Long.MIN_VALUE;
    }

    /**
     * Chamado na saida de {@code CanteenItem.fill}. Promove a agua recem-posta a purificada quando o
     * lugar concede.
     *
     * <p>So mexe em {@code NORMAL}: cantil vazio, agua de chuva, pocao e agua ja purificada ficam como
     * estao — o objetivo e dizer de onde a agua veio, nao reescrever o conteudo do cantil.
     */
    public static void afterFill(ItemStack canteen, @Nullable Level level) {
        if (setHydration == null || level == null || level.isClientSide()) return;

        Player player = interacting;
        if (player == null || level.getGameTime() != interactionTick) return;
        // Um clique enche um cantil: consumir aqui solta a referencia no caminho normal, e o logout
        // cobre o clique que nunca chegou a encher nada.
        clearLatch();
        if (!(player instanceof ServerPlayer server) || !(level instanceof ServerLevel serverLevel)) return;

        try {
            if (getHydration.invoke(null, canteen) != normal) return;
            if ((Integer) getCapacity.invoke(null, canteen) <= 0) return;

            // A posicao clicada e a fonte; sem ela (agua olhada de longe), vale onde o jogador esta.
            BlockPos source = clicked;
            double x = source == null ? server.getX() : source.getX() + .5;
            double y = source == null ? server.getY() : source.getY() + .5;
            double z = source == null ? server.getZ() : source.getZ() + .5;
            if (!purifiesHere(serverLevel, server.getUUID(), x, y, z, source)) return;

            setHydration.invoke(null, canteen, purified);
        } catch (ReflectiveOperationException exception) {
            // Uma falha por enchimento nao pode virar uma excecao por clique: desliga e avisa uma vez.
            setHydration = null;
            AurorionAreas.LOGGER.error("Areas: falha ao purificar cantil; integracao de agua desligada.", exception);
        }
    }
}
