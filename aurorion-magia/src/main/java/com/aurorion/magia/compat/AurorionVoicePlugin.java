package com.aurorion.magia.compat;

import com.aurorion.magia.AurorionMagia;
import de.maxhenkel.voicechat.api.ForgeVoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.MicrophonePacketEvent;

/**
 * Plugin do Simple Voice Chat para o Vox Interdicta: o microfone de quem esta sem voz nao e
 * repassado a ninguem — proximidade, grupo ou sussurro.
 *
 * <p>Sem o Voice Chat instalado, esta classe nunca e carregada: quem procura a anotacao
 * {@link ForgeVoicechatPlugin} e o proprio Voice Chat.
 */
@ForgeVoicechatPlugin
public final class AurorionVoicePlugin implements VoicechatPlugin {
    @Override
    public String getPluginId() {
        return AurorionMagia.MOD_ID;
    }

    @Override
    public void registerEvents(EventRegistration registration) {
        registration.registerEvent(MicrophonePacketEvent.class, AurorionVoicePlugin::onMicrophone);
    }

    /** Roda na thread de audio do Voice Chat, nunca na do servidor. */
    private static void onMicrophone(MicrophonePacketEvent event) {
        VoicechatConnection sender = event.getSenderConnection();
        if (sender != null && VoiceMute.isMuted(sender.getPlayer().getUuid())) event.cancel();
    }
}
