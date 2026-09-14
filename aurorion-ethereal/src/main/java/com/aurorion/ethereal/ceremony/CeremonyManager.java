package com.aurorion.ethereal.ceremony;

import com.aurorion.ethereal.house.House;
import com.aurorion.ethereal.house.HouseCatalog;
import com.aurorion.ethereal.house.HouseManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/**
 * A Cerimonia de Vinculacao: a staff diz qual e a casa, e o mundo assiste.
 *
 * <h2>O que saiu</h2>
 *
 * <p>Havia aqui um questionario de oito perguntas com contagem de pontos por casa, uma fila de
 * vereditos esperando decisao e quatro subcomandos de staff em volta disso. Tudo isso existia para
 * <b>sugerir</b> uma casa que, na pratica, ja era decidida fora do jogo — e cobrava oito telas de
 * leitura antes do unico instante que as pessoas lembram depois. As perguntas sairam; o instante
 * ficou, e agora dura treze segundos ({@link BindingRite}).
 *
 * <h2>Por que ainda existe uma classe aqui</h2>
 *
 * <p>Ela e a costura entre tres coisas que nao deveriam se conhecer: o catalogo de casas (datapack),
 * a lista de quem e de qual casa ({@code HouseManager}) e a cena ({@code BindingRite}). E tambem o
 * lugar de uma unica regra: definir a casa de quem esta offline nao perde o rito — ele espera o
 * login.
 */
public final class CeremonyManager {
    public enum Result {
        /** A cena comecou agora. */
        OK,
        /** A casa foi gravada, mas a pessoa esta offline; o rito toca no proximo login. */
        QUEUED,
        /** Id de casa que nao existe em datapack nenhum. */
        UNKNOWN_HOUSE,
        /** Ja esta no meio de um rito. */
        ALREADY_RUNNING
    }

    private CeremonyManager() {
    }

    /**
     * Grava a casa e toca a cena.
     *
     * <p>A casa e gravada <b>antes</b> do rito, e nao no fim dele: sair no meio da cena, cair a
     * conexao ou o servidor reiniciar nao pode desfazer uma decisao da staff. A cena e apresentacao;
     * o dado nao depende dela.
     */
    public static Result bind(MinecraftServer server, UUID player, ResourceLocation houseId) {
        House house = HouseCatalog.get(houseId);
        if (house == null) return Result.UNKNOWN_HOUSE;
        if (BindingRite.isBinding(player)) return Result.ALREADY_RUNNING;

        HouseManager.assign(server, player, houseId);

        ServerPlayer online = server.getPlayerList().getPlayer(player);
        if (online == null) {
            CeremonyData.get(server).queue(player, houseId);
            return Result.QUEUED;
        }

        CeremonyData.get(server).cancel(player);
        BindingRite.start(online, house);
        return Result.OK;
    }

    /** Um rito que ficou esperando. Chamada no login. */
    public static void playPending(ServerPlayer player) {
        ResourceLocation pending = CeremonyData.get(player.server).take(player.getUUID());
        if (pending == null) return;

        House house = HouseCatalog.get(pending);
        // Casa apagada do datapack entre o agendamento e o login: sem cena, e sem travar o login.
        if (house != null) BindingRite.start(player, house);
    }

    /** @return true se havia mesmo algo para cancelar. */
    public static boolean cancel(MinecraftServer server, UUID player) {
        boolean queued = CeremonyData.get(server).cancel(player);
        boolean running = BindingRite.isBinding(player);

        BindingRite.forget(player);
        return queued || running;
    }

    public static boolean isRunning(UUID player) {
        return BindingRite.isBinding(player);
    }

    public static void forget(UUID player) {
        BindingRite.forget(player);
    }

    public static void clear() {
        BindingRite.clear();
    }
}
