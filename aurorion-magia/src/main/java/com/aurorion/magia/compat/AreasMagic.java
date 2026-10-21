package com.aurorion.magia.compat;

import com.aurorion.magia.AurorionMagia;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Ponte opcional com o {@code aurorion-areas}: a regra {@code magia} vale tambem onde a magia
 * <b>cai</b>, e nao so onde ela e conjurada; a regra {@code pvp} vale onde a vitima esta; e a protecao
 * de blocos ({@code construir}, {@code interagir}) vale para magia que mexe em bloco (Devorar Luz,
 * Lacre Profano).
 *
 * <p>O {@code aurorion-areas} ja recusa conjurar dentro de area que proibe magia (ele depende deste
 * modulo, nao o contrario — por isso a ponte e reflexiva). Mas a Esfera Espiritual e conjurada de
 * fora e voa ate o alvo; sem esta consulta, ela abriria cratera dentro da praça protegida.
 *
 * <h2>Quando a ponte falha</h2>
 *
 * <p>Sem o {@code aurorion-areas} instalado, tudo e permitido — nao ha area nenhuma. Com ele instalado
 * e a ponte quebrada (metodo renomeado, excecao dentro dele), a resposta e <b>nao</b>: antes era sim, e
 * uma atualizacao do outro modulo transformava toda zona segura em arena sem ninguem perceber. O erro
 * vai para o log uma vez so por consulta, e nao a cada golpe.
 */
public final class AreasMagic {
    private static final String API = "com.aurorion.areas.api.AreaApi";
    private static final String MAGIC = "magia";
    private static final String PVP = "pvp";

    private static boolean initialized;
    private static boolean present;
    @Nullable
    private static Method allowsAt;
    @Nullable
    private static Method mayBuild;
    @Nullable
    private static Method mayInteract;
    private static boolean warnedCall;
    private static boolean warnedProtection;

    private AreasMagic() {
    }

    public static boolean allowedAt(ServerLevel level, Vec3 at, @Nullable UUID actor) {
        return allows(level, at, actor, MAGIC);
    }

    /**
     * A regra {@code pvp} no lugar onde a vitima esta. Magia de controle (puxar, silenciar, virar
     * bicho, possuir) nao fere, entao o cancelamento de dano da area nao a pega; esta consulta e o que
     * impede alguem de arrastar outro jogador para fora da zona segura.
     */
    public static boolean pvpAt(ServerLevel level, Vec3 at, @Nullable UUID victim) {
        return allows(level, at, victim, PVP);
    }

    /**
     * {@code player} pode mexer neste bloco como quem quebra ou poe: modo de jogo que constroi,
     * protecao de spawn do vanilla e regra {@code construir} do {@code aurorion-areas} no bloco.
     */
    public static boolean mayBuild(ServerLevel level, ServerPlayer player, BlockPos pos) {
        return vanillaMay(level, player, pos) && protection(level, player, pos, true);
    }

    /** O mesmo para abrir/usar o bloco (regra {@code interagir}). */
    public static boolean mayInteract(ServerLevel level, ServerPlayer player, BlockPos pos) {
        return vanillaMay(level, player, pos) && protection(level, player, pos, false);
    }

    private static boolean vanillaMay(ServerLevel level, ServerPlayer player, BlockPos pos) {
        return player.mayBuild() && level.mayInteract(player, pos);
    }

    private static boolean protection(ServerLevel level, ServerPlayer player, BlockPos pos, boolean build) {
        init();
        if (!present) return true;
        Method check = build ? mayBuild : mayInteract;
        if (check == null) return false;
        try {
            return (boolean) check.invoke(null, level, player, pos);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            if (!warnedProtection) {
                warnedProtection = true;
                AurorionMagia.LOGGER.error("Magia: falha ao consultar a protecao de blocos do aurorion-areas — "
                        + "magia nao mexe em bloco enquanto isso.", exception);
            }
            return false;
        }
    }

    private static boolean allows(ServerLevel level, Vec3 at, @Nullable UUID actor, String key) {
        init();
        if (!present) return true;
        if (allowsAt == null) return false;
        try {
            return (boolean) allowsAt.invoke(null, level, at.x, at.y, at.z, actor, key);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            if (!warnedCall) {
                warnedCall = true;
                AurorionMagia.LOGGER.error("Magia: falha ao consultar o aurorion-areas — PvP e magia ficam negados "
                        + "ate a ponte voltar.", exception);
            }
            return false;
        }
    }

    private static void init() {
        if (initialized) return;
        initialized = true;
        present = ModList.get().isLoaded("aurorion_areas");
        if (!present) return;
        try {
            allowsAt = Class.forName(API).getMethod("allowsAt", ServerLevel.class, double.class, double.class,
                    double.class, UUID.class, String.class);
        } catch (ReflectiveOperationException | LinkageError exception) {
            AurorionMagia.LOGGER.error("Magia: aurorion-areas presente, mas sem AreaApi.allowsAt — "
                    + "PvP e magia ficam negados em todo lugar ate corrigir.");
        }
        try {
            Class<?> api = Class.forName(API);
            mayBuild = api.getMethod("mayBuild", ServerLevel.class, ServerPlayer.class, BlockPos.class);
            mayInteract = api.getMethod("mayInteract", ServerLevel.class, ServerPlayer.class, BlockPos.class);
        } catch (ReflectiveOperationException | LinkageError exception) {
            AurorionMagia.LOGGER.error("Magia: aurorion-areas presente, mas sem AreaApi.mayBuild/mayInteract — "
                    + "magia nao mexe em bloco ate corrigir.");
        }
    }
}
