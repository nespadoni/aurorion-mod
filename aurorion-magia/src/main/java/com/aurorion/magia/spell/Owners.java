package com.aurorion.magia.spell;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Quem lancou um efeito que segue rodando sozinho (corrente, afogamento, arremesso), achado pelo UUID
 * guardado na vitima.
 *
 * <p>Jogador primeiro pela lista do servidor: {@code level.getEntity} so enxerga a dimensao da vitima,
 * e quem prendeu alguem e depois atravessou um portal continua sendo o dono. Criatura so na dimensao
 * da vitima. {@code null} quer dizer "o dono nao esta aqui" — saiu do jogo, morreu, descarregou —, e
 * cada efeito decide o que fazer com isso; nenhum deles fere sem dono (ver {@link Hits#pvpAllowedFrom}).
 */
public final class Owners {
    private Owners() {
    }

    @Nullable
    public static Entity find(ServerLevel level, UUID id) {
        ServerPlayer player = level.getServer().getPlayerList().getPlayer(id);
        if (player != null) return player.isAlive() ? player : null;
        Entity entity = level.getEntity(id);
        return entity != null && entity.isAlive() ? entity : null;
    }
}
