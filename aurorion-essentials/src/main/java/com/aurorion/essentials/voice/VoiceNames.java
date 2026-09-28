package com.aurorion.essentials.voice;

import com.aurorion.essentials.AurorionEssentials;
import de.maxhenkel.voicechat.Voicechat;
import de.maxhenkel.voicechat.voice.common.PlayerState;
import de.maxhenkel.voicechat.voice.server.PlayerStateManager;
import de.maxhenkel.voicechat.voice.server.Server;
import net.minecraft.server.level.ServerPlayer;

/**
 * A parte de {@link VoiceNameSync} que mexe no Voice Chat. So e carregada com ele instalado.
 *
 * <p>Usa classes internas do Voice Chat ({@code Voicechat.SERVER}, {@code PlayerStateManager}),
 * porque a API publica nao tem como trocar o nome de ninguem. Conferido com {@code javap} nas versoes
 * 2.6.22 e 2.6.24. Se uma versao futura mudar isso, o erro e registrado uma vez e o nome fica como o
 * Voice Chat mandar — nada quebra alem da exibicao.
 */
final class VoiceNames {
    private static boolean reportedFailure;

    private VoiceNames() {
    }

    static void refresh(ServerPlayer player) {
        try {
            Server server = Voicechat.SERVER == null ? null : Voicechat.SERVER.getServer();
            if (server == null) return;
            PlayerStateManager states = server.getPlayerStateManager();
            PlayerState state = states.getState(player.getUUID());
            if (state == null) return;

            String name = VoiceNameSync.displayName(player);
            if (name.equals(state.getName())) return;
            state.setName(name);
            states.broadcastState(player, state);
        } catch (LinkageError | RuntimeException e) {
            if (!reportedFailure) {
                reportedFailure = true;
                AurorionEssentials.LOGGER.warn("Nao consegui atualizar o nome de {} no Simple Voice Chat; os menus dele "
                        + "podem mostrar o nick ate a pessoa relogar.", player.getGameProfile().getName(), e);
            }
        }
    }
}
