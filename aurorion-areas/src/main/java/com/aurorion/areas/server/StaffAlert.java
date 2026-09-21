package com.aurorion.areas.server;

import com.aurorion.areas.AurorionAreas;
import com.aurorion.core.text.TimeFormat;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Avisa quem modera que alguem entrou — e continua — num ambiente perigoso.
 *
 * <h2>Por que chat, e nao barra de acao</h2>
 *
 * <p>E o inverso de tudo o mais que este modulo manda para o jogador. Os avisos do ambiente somem
 * sozinhos de proposito, porque se repetem; este precisa <b>ficar no historico</b>: quem entra na
 * conta dez minutos depois tem que conseguir rolar para cima e ver que alguem entrou na floresta e
 * nao saiu. Tambem vai para o log do servidor, pela mesma razao do {@code /ajuda}: um aviso que
 * ninguem estava online para ver nao pode desaparecer.
 *
 * <h2>Custo</h2>
 *
 * <p>Tres momentos por permanencia (entrou, virou letal, saiu), nunca por tick, e so para ambientes
 * que pedem aviso no datapack. Percorrer a lista de jogadores para achar quem tem OP acontece nesses
 * tres momentos, nao no tick — a mesma conta que o {@code /ajuda} do {@code aurorion-essentials} ja
 * fazia (SDD §2: por evento, nao por jogador por tick).
 */
final class StaffAlert {
    private static final int OP_PERMISSION_LEVEL = 2;
    private static final Component PREFIX = Component.literal("[Áreas] ").withStyle(ChatFormatting.GOLD);

    private StaffAlert() {
    }

    static void entered(ServerPlayer player, Component area) {
        send(player, Component.literal(name(player)).withStyle(ChatFormatting.WHITE)
                .append(Component.literal(" entrou em ").withStyle(ChatFormatting.GRAY))
                .append(area.copy().withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(" — " + where(player)).withStyle(ChatFormatting.DARK_GRAY)));
    }

    /** O momento que importa: dali em diante o ambiente pode matar e gastar uma vida. */
    static void lethal(ServerPlayer player, Component area, long ticksInside) {
        send(player, Component.literal(name(player)).withStyle(ChatFormatting.WHITE)
                .append(Component.literal(" está há ").withStyle(ChatFormatting.RED))
                .append(TimeFormat.duration(ticksInside * 50).copy().withStyle(ChatFormatting.RED))
                .append(Component.literal(" em ").withStyle(ChatFormatting.RED))
                .append(area.copy().withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(": os ataques já podem matar.").withStyle(ChatFormatting.RED))
                .append(Component.literal(" — " + where(player)).withStyle(ChatFormatting.DARK_GRAY)));
    }

    static void left(ServerPlayer player, Component area, long ticksInside) {
        send(player, Component.literal(name(player)).withStyle(ChatFormatting.WHITE)
                .append(Component.literal(" saiu de ").withStyle(ChatFormatting.GRAY))
                .append(area.copy().withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(" depois de ").withStyle(ChatFormatting.GRAY))
                .append(TimeFormat.duration(ticksInside * 50).copy().withStyle(ChatFormatting.GRAY))
                .append(Component.literal(".").withStyle(ChatFormatting.GRAY)));
    }

    static void disconnected(ServerPlayer player, Component area, long ticksInside) {
        send(player, Component.literal(name(player)).withStyle(ChatFormatting.WHITE)
                .append(Component.literal(" desconectou dentro de ").withStyle(ChatFormatting.GOLD))
                .append(area.copy().withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(", depois de ").withStyle(ChatFormatting.GOLD))
                .append(TimeFormat.duration(ticksInside * 50).copy().withStyle(ChatFormatting.GOLD))
                .append(Component.literal(".").withStyle(ChatFormatting.GOLD)));
    }

    private static void send(ServerPlayer about, Component message) {
        Component line = PREFIX.copy().append(message);
        for (ServerPlayer staff : about.server.getPlayerList().getPlayers()) {
            if (staff.hasPermissions(OP_PERMISSION_LEVEL)) {
                staff.sendSystemMessage(line);
            }
        }
        AurorionAreas.LOGGER.info("Aviso de area: {}", line.getString());
    }

    /**
     * Nome da conta, e nao {@code getDisplayName()}: com o fakename do {@code aurorion-essentials}
     * ligado, o nome exibido pode ser qualquer coisa, e quem modera precisa de quem a pessoa e —
     * mesma fronteira que sustenta o {@code /realname} (SDD §5.1).
     */
    private static String name(ServerPlayer player) {
        return player.getGameProfile().getName();
    }

    private static String where(ServerPlayer player) {
        BlockPos pos = player.blockPosition();
        return player.level().dimension().location() + " " + pos.getX() + " " + pos.getY() + " " + pos.getZ();
    }
}
