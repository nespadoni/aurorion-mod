package com.aurorion.essentials.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

import java.util.UUID;

/**
 * O mesmo problema do lado do cliente: {@code Player#getDisplayName()} guarda o nome calculado num
 * campo e nunca mais o recalcula sozinho.
 *
 * <p>Sem isto, um nome falso definido enquanto o jogador ja estava na tela continuaria aparecendo
 * como o nick da Mojang em tudo que o cliente monta a partir de {@code getDisplayName()}.</p>
 *
 * <p>Classe de cliente: nao deve ser carregada em servidor dedicado.</p>
 */
public final class FakeNameDisplayRefresh {
    private FakeNameDisplayRefresh() {
    }

    public static void refresh(UUID player) {
        var level = Minecraft.getInstance().level;
        if (level == null) return;

        Player target = level.getPlayerByUUID(player);
        if (target != null) target.refreshDisplayName();
    }

    /** Para o snapshot de entrada, que troca a tabela inteira de uma vez. */
    public static void refreshAll() {
        var level = Minecraft.getInstance().level;
        if (level == null) return;

        for (Player player : level.players()) {
            player.refreshDisplayName();
        }
    }
}
