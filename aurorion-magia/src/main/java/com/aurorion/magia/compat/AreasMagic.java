package com.aurorion.magia.compat;

import com.aurorion.magia.AurorionMagia;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Ponte opcional com o {@code aurorion-areas}: a regra {@code magia} vale tambem onde a magia
 * <b>cai</b>, e nao so onde ela e conjurada.
 *
 * <p>O {@code aurorion-areas} ja recusa conjurar dentro de area que proibe magia (ele depende deste
 * modulo, nao o contrario — por isso a ponte e reflexiva). Mas a Esfera Espiritual e conjurada de
 * fora e voa ate o alvo; sem esta consulta, ela abriria cratera dentro da praça protegida. Sem o
 * {@code aurorion-areas}, tudo e permitido.
 */
public final class AreasMagic {
    private static final String API = "com.aurorion.areas.api.AreaApi";
    private static final String MAGIC = "magia";
    private static final String PVP = "pvp";

    private static boolean initialized;
    @Nullable
    private static Method allowsAt;

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

    private static boolean allows(ServerLevel level, Vec3 at, @Nullable UUID actor, String key) {
        Method method = method();
        if (method == null) return true;
        try {
            return (boolean) method.invoke(null, level, at.x, at.y, at.z, actor, key);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            return true;
        }
    }

    @Nullable
    private static Method method() {
        if (initialized) return allowsAt;
        initialized = true;
        if (!ModList.get().isLoaded("aurorion_areas")) return null;
        try {
            allowsAt = Class.forName(API).getMethod("allowsAt", ServerLevel.class, double.class, double.class,
                    double.class, UUID.class, String.class);
        } catch (ReflectiveOperationException exception) {
            AurorionMagia.LOGGER.warn("Magia: aurorion-areas presente, mas sem AreaApi.allowsAt — areas ignoradas na Esfera.");
        }
        return allowsAt;
    }
}
