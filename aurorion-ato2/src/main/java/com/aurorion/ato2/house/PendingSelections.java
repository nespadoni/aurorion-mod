package com.aurorion.ato2.house;

import com.aurorion.ato2.Ato2Tags;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Quem tem uma tela de escolha aberta, e em qual altar.
 *
 * <p>Existe por um motivo so: {@code ChooseHousePayload} chega pela rede e, sozinho, seria um
 * "escolho minha casa de qualquer lugar do mundo, sem nunca ter visto um altar". O ritual precisa
 * ter acontecido de verdade — SDD §7.5, entrada de rede e hostil ate ser validada no servidor.
 *
 * <p>Nao ha tick nem timer aqui: o prazo e um {@code long} comparado no momento em que a escolha
 * chega. Uma entrada vencida nao custa nada ate alguem tentar usa-la, e some no logout.
 */
public final class PendingSelections {
    /** Um minuto para decidir. Depois disso, e so clicar no altar de novo. */
    private static final int TIMEOUT_TICKS = 20 * 60;

    /** O jogador pode andar um pouco enquanto escolhe, mas nao pode sair de perto do altar. */
    private static final double MAX_DISTANCE_SQR = 8.0 * 8.0;

    private record Pending(ResourceKey<Level> dimension, BlockPos altar, long expiresAtGameTime) {
    }

    private static final Map<UUID, Pending> PENDING = new HashMap<>();

    private PendingSelections() {
    }

    public static void open(ServerPlayer player, BlockPos altar) {
        PENDING.put(player.getUUID(), new Pending(
                player.level().dimension(),
                altar.immutable(),
                player.level().getGameTime() + TIMEOUT_TICKS));
    }

    /**
     * Gasta a permissao de escolher, se ela ainda valer. Sempre remove a entrada: uma tela aberta da
     * direito a exatamente uma escolha.
     *
     * @return true se este jogador realmente esta num altar valido, dentro do prazo.
     */
    public static boolean consume(ServerPlayer player) {
        Pending pending = PENDING.remove(player.getUUID());
        if (pending == null) {
            return false;
        }
        if (!player.level().dimension().equals(pending.dimension())) {
            return false;
        }
        if (player.level().getGameTime() > pending.expiresAtGameTime()) {
            return false;
        }
        if (player.blockPosition().distSqr(pending.altar()) > MAX_DISTANCE_SQR) {
            return false;
        }
        // O altar pode ter sido quebrado (ou tirado da tag por /reload) com a tela aberta.
        return player.level().getBlockState(pending.altar()).is(Ato2Tags.HOUSE_ALTARS);
    }

    public static void forget(UUID player) {
        PENDING.remove(player);
    }

    public static void clear() {
        PENDING.clear();
    }
}
