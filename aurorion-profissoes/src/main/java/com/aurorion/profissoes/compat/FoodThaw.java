package com.aurorion.profissoes.compat;

import com.aurorion.profissoes.AurorionProfissoes;
import com.aurorion.profissoes.config.ProfessionsConfig;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

/**
 * Remendo de um bug do FoodSpoil 1.1.7: <b>comida congelada apodrece toda de uma vez ao descongelar</b>.
 *
 * <h2>O bug</h2>
 *
 * <p>Congelar, no FoodSpoil, nao para o relogio — ele so <em>esconde</em> o resultado. Em
 * {@code FoodData.calculateFreshness}, os estados {@code FROZEN} e {@code THAWING} devolvem 100% sem
 * calcular nada; o resto do tempo, o frescor sai de
 * {@code SnapshotFreshness - (agora - SnapshotTime) * taxa}.
 *
 * <p>Ao congelar, o mod grava um snapshot correto ({@code saveSnapshot(stack, frescor, agora)}). Ao
 * <b>descongelar</b>, nos tres lugares onde isso acontece ({@code onContainerClose},
 * {@code onPlayerTick} e {@code processContainerThawing}), ele faz apenas
 * {@code setState(FRESH)} + {@code setThawStart(0)} — e <b>nunca reancora o {@code SnapshotTime}</b>.
 *
 * <p>Resultado: no instante em que o estado deixa de ser congelado, {@code agora - SnapshotTime} vale
 * <em>todo o tempo que a comida passou congelada</em>, e ele e cobrado de uma vez. Com a taxa padrao
 * (0,3% por minuto de Minecraft, ou 6% por dia de jogo), qualquer comida congelada por mais de ~17
 * dias de jogo sai do congelador em 0% e vira carne podre na hora, pelo
 * {@code RottenConversionHandler}. Num servidor que fica dias no ar, isso e o caso normal — e e
 * exatamente o "congelei, descongelou e estragou sozinho".
 *
 * <h2>O remendo</h2>
 *
 * <p>Ao sair de {@code FROZEN}/{@code THAWING}, reancoramos o {@code SnapshotTime} para agora,
 * <b>mantendo o {@code SnapshotFreshness}</b> gravado no congelamento. O tempo congelado deixa de ser
 * cobrado e a comida volta do congelador com o frescor que entrou — que e o que congelar deveria
 * fazer.
 *
 * <p>Escrevemos so o campo do tempo, e nenhum outro: se o FoodSpoil consertar isso numa versao nova,
 * o conserto dele grava o mesmo valor que o nosso e os dois convivem sem brigar. O interruptor
 * {@code fixFoodSpoilThaw} existe para o dia em que isso deixar de ser verdade.
 */
public final class FoodThaw {
    private static final String STATE = "FoodState";
    private static final String SNAPSHOT_FRESHNESS = "SnapshotFreshness";
    private static final String SNAPSHOT_TIME = "SnapshotTime";
    private static final String FROZEN = "FROZEN";
    private static final String THAWING = "THAWING";
    private static boolean reportedMissingSnapshot;

    private FoodThaw() {
    }

    /**
     * Chamado na entrada de {@code FoodData.setState}, antes de o estado novo ser gravado.
     *
     * @param newState nome do estado que esta entrando, lido do {@code enum} do proprio FoodSpoil.
     */
    public static void beforeStateChange(ItemStack stack, String newState) {
        if (!ProfessionsConfig.fixFoodSpoilThaw() || FROZEN.equals(newState) || THAWING.equals(newState)) {
            return;
        }
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return;

        // getUnsafe: leitura pura, sem copiar NBT no caminho que roda a cada troca de estado.
        CompoundTag tag = data.getUnsafe();
        String previous = tag.getString(STATE);
        if (!FROZEN.equals(previous) && !THAWING.equals(previous)) return;

        if (!tag.contains(SNAPSHOT_FRESHNESS)) {
            // Sem snapshot nao da para saber com quanto frescor a comida entrou no gelo, e chutar 100%
            // daria comida eterna a quem congelasse uma vez. O caminho de congelamento do FoodSpoil
            // sempre grava um, entao isto aqui e sinal de que algo mudou no mod.
            if (!reportedMissingSnapshot) {
                reportedMissingSnapshot = true;
                AurorionProfissoes.LOGGER.warn("Profissoes: comida saindo de '{}' sem SnapshotFreshness."
                        + " O remendo de descongelamento do FoodSpoil nao tem o que reancorar.", previous);
            }
            return;
        }

        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null || !server.isSameThread()) return;
        long now = server.overworld().getGameTime();

        CustomData.update(DataComponents.CUSTOM_DATA, stack, changed -> changed.putLong(SNAPSHOT_TIME, now));
    }
}
