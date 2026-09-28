package com.aurorion.essentials.voice;

import com.aurorion.essentials.AurorionEssentials;
import de.maxhenkel.voicechat.api.ForgeVoicechatPlugin;
import de.maxhenkel.voicechat.api.Group;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.events.CreateGroupEvent;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.GroupEvent;
import de.maxhenkel.voicechat.api.events.JoinGroupEvent;
import de.maxhenkel.voicechat.api.events.VoiceDistanceEvent;
import de.maxhenkel.voicechat.api.events.VoicechatServerStartedEvent;
import de.maxhenkel.voicechat.api.events.VoicechatServerStoppedEvent;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

/**
 * Plugin do Simple Voice Chat com as regras de voz do servidor:
 * <ul>
 *   <li><b>So grupo aberto.</b> Criar ou entrar em grupo Normal/Isolado (ou com senha) e recusado — ver
 *       {@link VoiceGroupPolicy} e {@link VoiceConfig}.</li>
 *   <li><b>{@code /gritar}.</b> Aumenta o alcance da voz de quem a staff escolher — ver
 *       {@link ShoutRegistry}.</li>
 *   <li><b>API para as ligacoes do telefone</b>, que passam a usar grupo aberto — ver
 *       {@link PhoneCallGroups}.</li>
 * </ul>
 *
 * <p>Sem o Voice Chat instalado, esta classe nunca e carregada: quem procura a anotacao
 * {@link ForgeVoicechatPlugin} e o proprio Voice Chat.
 */
@ForgeVoicechatPlugin
public final class EssentialsVoicePlugin implements VoicechatPlugin {
    @Nullable
    private static volatile VoicechatServerApi api;

    /** A API do servidor de voz, ou {@code null} antes de ele subir. */
    @Nullable
    public static VoicechatServerApi api() {
        return api;
    }

    @Override
    public String getPluginId() {
        return AurorionEssentials.MOD_ID;
    }

    @Override
    public void registerEvents(EventRegistration registration) {
        registration.registerEvent(VoicechatServerStartedEvent.class, event -> api = event.getVoicechat());
        registration.registerEvent(VoicechatServerStoppedEvent.class, event -> api = null);
        registration.registerEvent(CreateGroupEvent.class, EssentialsVoicePlugin::enforceOpenGroup);
        // Tambem na entrada: o setGroup da API tenta entrar no grupo mesmo quando a criacao foi recusada,
        // e sem esta checagem a pessoa ficaria "dentro" de um grupo que o servidor nao registrou.
        registration.registerEvent(JoinGroupEvent.class, EssentialsVoicePlugin::enforceOpenGroup);
        registration.registerEvent(VoiceDistanceEvent.class, EssentialsVoicePlugin::applyShout);
    }

    private static void enforceOpenGroup(GroupEvent event) {
        Group group = event.getGroup();
        if (group == null) return;
        String refusal = VoiceGroupPolicy.refusal(Group.Type.OPEN.equals(group.getType()), group.hasPassword(),
                VoiceConfig.ONLY_OPEN_GROUPS.get(), VoiceConfig.ALLOW_GROUP_PASSWORDS.get());
        if (refusal == null) return;
        event.cancel();
        tell(event.getConnection(), refusal);
    }

    /**
     * O gancho oficial de alcance do Voice Chat: roda a cada pacote de microfone, antes de ele decidir
     * quem recebe o audio. Alcance maior aqui significa mais gente recebendo e o cliente atenuando com
     * a distancia nova — sem reenviar audio nem cancelar nada.
     */
    private static void applyShout(VoiceDistanceEvent event) {
        VoicechatConnection sender = event.getSenderConnection();
        if (sender == null) return;
        float normal = event.getDistance();
        float distance = ShoutRegistry.distanceFor(sender.getPlayer().getUuid(), normal, event.getPacket().isWhispering());
        if (distance != normal) event.setDistance(distance);
    }

    /** O Voice Chat pode chamar de fora da thread do servidor; a mensagem vai pela fila dele. */
    private static void tell(@Nullable VoicechatConnection connection, String message) {
        if (connection == null) return;
        if (!(connection.getPlayer().getPlayer() instanceof ServerPlayer player)) return;
        var server = player.getServer();
        if (server == null) return;
        server.execute(() -> player.sendSystemMessage(Component.literal(message).withStyle(ChatFormatting.RED)));
    }
}
