package com.aurorion.essentials.help;

import com.aurorion.essentials.fakename.FakeName;
import com.aurorion.essentials.fakename.FakeNameRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.Locale;

/**
 * Monta e distribui o aviso de {@code /ajuda}: nome real, fakename, descricao e localizacao de
 * quem pediu socorro, mandado direto para quem tem OP (nivel 2+) online. Devolve quantos
 * operadores receberam para o comando decidir se avisa "manda no Discord" em vez de deixar o
 * pedido cair no vazio.
 */
public final class HelpRequestManager {
    private static final int OP_PERMISSION_LEVEL = 2;

    private HelpRequestManager() {
    }

    public static int notifyOps(ServerPlayer sender, String description) {
        MinecraftServer server = sender.getServer();
        if (server == null) return 0;

        Component notification = buildNotification(sender, description);

        int notified = 0;
        for (ServerPlayer op : server.getPlayerList().getPlayers()) {
            if (!op.hasPermissions(OP_PERMISSION_LEVEL)) continue;
            op.sendSystemMessage(notification);
            notified++;
        }
        return notified;
    }

    private static Component buildNotification(ServerPlayer sender, String description) {
        String realName = sender.getGameProfile().getName();
        FakeName fakeName = FakeNameRegistry.get(sender.getUUID());
        Component fakeNameValue = fakeName != null
                ? fakeName.component()
                : Component.translatable("commands.aurorion_essentials.ajuda.notify.fakename.none")
                        .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC);

        BlockPos pos = sender.blockPosition();
        // Locale.ROOT: a coordenada e um numero de jogo, nao um numero formatado para leitura. Sem
        // ele, um servidor com locale de digitos nao-arabicos escreveria a posicao em outro alfabeto.
        String location = String.format(Locale.ROOT, "%s (%d, %d, %d)",
                dimensionName(sender), pos.getX(), pos.getY(), pos.getZ());

        return Component.translatable("commands.aurorion_essentials.ajuda.notify.header")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                .append(field("commands.aurorion_essentials.ajuda.notify.player", Component.literal(realName).withStyle(ChatFormatting.WHITE)))
                .append(field("commands.aurorion_essentials.ajuda.notify.fakename", fakeNameValue))
                .append(field("commands.aurorion_essentials.ajuda.notify.description", Component.literal(description).withStyle(ChatFormatting.WHITE)))
                .append(field("commands.aurorion_essentials.ajuda.notify.location", Component.literal(location).withStyle(ChatFormatting.WHITE)));
    }

    private static Component field(String labelKey, Component value) {
        return Component.literal("\n")
                .append(Component.translatable(labelKey).withStyle(ChatFormatting.GRAY))
                .append(Component.literal(" "))
                .append(value);
    }

    private static String dimensionName(ServerPlayer player) {
        String path = player.level().dimension().location().getPath();
        if (path.isEmpty()) return path;

        String spaced = path.replace('_', ' ');
        return Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
    }
}
