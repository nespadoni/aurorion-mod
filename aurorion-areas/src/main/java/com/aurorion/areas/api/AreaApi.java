package com.aurorion.areas.api;

import com.aurorion.areas.config.AreasConfig;
import com.aurorion.areas.data.AreaData;
import com.aurorion.areas.rules.AreaRule;
import com.aurorion.areas.server.AreaRuntime;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;
import java.util.UUID;

/** Server-thread API for spells, vehicles and future professions. An ALLOW never grants an ability. */
public final class AreaApi {
    private AreaApi() {}
    public static boolean bypass(ServerPlayer player) {
        return player.isSpectator() || player instanceof net.neoforged.neoforge.common.util.FakePlayer
                || player.getTags().contains("aurorion_areas_npc") || (AreasConfig.CREATIVE_BYPASS.get()
                && player.isCreative() && player.hasPermissions(2));
    }
    public static boolean allows(ServerPlayer player, AreaRule rule) {
        return !AreasConfig.ENABLED.get() || bypass(player) || AreaRuntime.rules(player).allows(rule);
    }
    public static boolean allows(ServerPlayer player, String key) {
        return !AreasConfig.ENABLED.get() || bypass(player)
                || allowsAt(player.serverLevel(), player.getX(), player.getY(), player.getZ(), player.getUUID(), key);
    }
    /**
     * Mesma politica de {@link #allowsAt} na posicao da entidade, reaproveitando a resolucao que o
     * tick do jogador ja fez. Evita percorrer a arvore e testar poligonos de novo a cada golpe ou
     * troca de alvo, que e onde este modulo mais e consultado num servidor cheio.
     */
    public static boolean allowsFor(Entity entity, AreaRule rule) {
        if (!AreasConfig.ENABLED.get()) return true;
        // Fake players nunca entram no cache por jogador: nao ha logout que os remova de la.
        if (entity instanceof ServerPlayer player && !(player instanceof net.neoforged.neoforge.common.util.FakePlayer)) {
            return AreaRuntime.rules(player).allows(rule);
        }
        return !(entity.level() instanceof ServerLevel level)
                || allowsAt(level, entity.getX(), entity.getY(), entity.getZ(), entity.getUUID(), rule.key());
    }
    /**
     * Poder que so existe onde uma area concede, em vez de restricao que vale ate alguem negar.
     *
     * <p>E a consulta certa para uma regra que <b>da</b> alguma coisa (a agua pura da Academia, por
     * exemplo): sem area que conceda, a resposta e nao. Nao ha bypass de criativo — quem concede e o
     * lugar, nao a permissao de quem esta nele.
     */
    public static boolean grantedAt(ServerLevel level, double x, double y, double z, @Nullable UUID actor, String key) {
        return AreasConfig.ENABLED.get()
                && AreaData.get(level.getServer()).granted(level.dimension().location(), x, y, z, actor, key);
    }
    /** Raw location policy, including a character's explicit exceptions but without creative bypass. */
    public static boolean allowsAt(ServerLevel level, double x, double y, double z, @Nullable UUID actor, String key) {
        return !AreasConfig.ENABLED.get()
                || AreaData.get(level.getServer()).allows(level.dimension().location(), x, y, z, actor, key);
    }
}
