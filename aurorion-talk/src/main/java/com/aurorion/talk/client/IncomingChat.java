package com.aurorion.talk.client;

import com.aurorion.talk.config.TalkConfig;
import com.aurorion.talk.util.BalloonHolder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * Cola entre os mixins de chat e os baloes: transforma uma fala recebida em balao e decide se
 * ela ainda deve aparecer no HUD.
 */
public final class IncomingChat {
    private IncomingChat() {
    }

    /**
     * Chamado imediatamente antes da mensagem entrar no HUD do chat.
     *
     * @param sender  autor da fala, ou {@code null} se nao deu para identificar
     * @param message texto puro da fala (sem o "&lt;nick&gt;")
     */
    public static void handle(@Nullable UUID sender, String message) {
        boolean balloonShown = createBalloon(sender, message);

        if (!TalkConfig.HIDE_PLAYER_CHAT.get()) return;
        if (!balloonShown && TalkConfig.HIDE_ONLY_WHEN_BALLOON_SHOWN.get()) return;

        ChatSuppressor.arm();
    }

    private static boolean createBalloon(@Nullable UUID sender, String message) {
        if (sender == null || message.isBlank()) return false;

        Minecraft minecraft = Minecraft.getInstance();
        Level level = minecraft.level;
        if (level == null) return false;

        if (minecraft.player != null
                && minecraft.player.getUUID().equals(sender)
                && !TalkConfig.SHOW_OWN_BALLOON.get()) {
            return false;
        }

        // Fora do alcance de renderizacao o jogador nem existe no cliente — nao ha balao para criar.
        Player player = level.getPlayerByUUID(sender);
        if (player == null) return false;

        ((BalloonHolder) player).aurorion_talk$addBalloon(build(minecraft.font, message, level.getGameTime()));
        return true;
    }

    private static BalloonMessage build(Font font, String message, long gameTime) {
        int maxWidth = TalkConfig.MAX_BALLOON_WIDTH.get();
        List<FormattedCharSequence> lines = font.split(FormattedText.of(message), maxWidth);

        int widest = 0;
        for (FormattedCharSequence line : lines) {
            widest = Math.max(widest, font.width(line));
        }

        long lifetime = TalkConfig.BALLOON_AGE_SECONDS.get() * 20L;
        return new BalloonMessage(lines, widest, gameTime + lifetime);
    }
}
