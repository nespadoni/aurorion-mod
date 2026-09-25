package com.aurorion.magia.passive;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

/**
 * A porta de entrada do sistema de passivas: e por aqui que o item, o comando e os eventos falam com
 * o cadastro.
 *
 * <p>Tudo o que muda estado passa por um destes metodos, e nao pelo {@link PassiveData} direto, porque
 * conceder e revogar tem <b>consequencia imediata</b> alem de gravar a linha: revogar a Presenca
 * Aterradora de quem esta com ela ligada tem que apagar a aura no mesmo tick, e nao no proximo login.
 */
public final class Passives {
    private Passives() {
    }

    @Nullable
    public static Passive byId(ResourceLocation id) {
        for (Passive passive : Passive.values()) {
            if (passive.id().equals(id)) return passive;
        }
        return null;
    }

    public static boolean has(ServerPlayer player, Passive passive) {
        return PassiveData.get(player.server).has(player.getUUID(), passive);
    }

    public static boolean isActive(ServerPlayer player, Passive passive) {
        return PassiveData.get(player.server).isActive(player.getUUID(), passive);
    }

    /**
     * Da a marca ao personagem. Passiva com interruptor nasce <b>desligada</b>: quem recebeu uma aura
     * de terror escolhe quando entrar em cena, e nao a recebe ja aterrorizando a sala da aula.
     *
     * @return se mudou alguma coisa; {@code false} quando a pessoa ja tinha a passiva.
     */
    public static boolean grant(ServerPlayer player, Passive passive) {
        if (!PassiveData.get(player.server).grant(player.getUUID(), passive)) return false;
        player.sendSystemMessage(Component.translatable("aurorion_magia.passiva_recebida", passive.displayName())
                .withStyle(ChatFormatting.GOLD));
        player.sendSystemMessage(passive.description());
        if (passive.isToggleable()) {
            player.sendSystemMessage(Component.translatable("aurorion_magia.passiva_como_ligar",
                    "/aurorion passivas ligar " + passive.id()).withStyle(ChatFormatting.DARK_GRAY));
        }
        return true;
    }

    /** @return se mudou alguma coisa. */
    public static boolean revoke(ServerPlayer player, Passive passive) {
        if (!PassiveData.get(player.server).revoke(player.getUUID(), passive)) return false;
        stop(player, passive);
        player.sendSystemMessage(Component.translatable("aurorion_magia.passiva_perdida", passive.displayName())
                .withStyle(ChatFormatting.GRAY));
        return true;
    }

    /** @return se mudou alguma coisa; {@code false} quando ja estava no estado pedido. */
    public static boolean setActive(ServerPlayer player, Passive passive, boolean on) {
        if (!PassiveData.get(player.server).setActive(player.getUUID(), passive, on)) return false;
        if (on) {
            start(player, passive);
        } else {
            stop(player, passive);
        }
        return true;
    }

    /**
     * Entrou no servidor: o que precisa de um estado vivo no mundo (a aura) volta a existir. As outras
     * nao fazem nada aqui — elas so acontecem quando a pessoa age.
     */
    public static void onLogin(ServerPlayer player) {
        if (PassiveData.get(player.server).has(player.getUUID(), Passive.DREAD)) {
            DreadAura.restore(player);
        }
    }

    private static void start(ServerPlayer player, Passive passive) {
        if (passive == Passive.DREAD) DreadAura.enable(player);
    }

    private static void stop(ServerPlayer player, Passive passive) {
        if (passive == Passive.DREAD) DreadAura.disable(player);
    }
}
