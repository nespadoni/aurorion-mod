package com.aurorion.essentials.voice;

import com.aurorion.essentials.AurorionEssentials;
import de.maxhenkel.voicechat.api.Group;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import org.jetbrains.annotations.Nullable;

/**
 * O grupo de voz de uma ligacao do telefone Mattupolis, chamado pelo {@code PhoneCallGroupMixin}.
 *
 * <ul>
 *   <li><b>Aberto</b>: os dois lados se ouvem de qualquer distancia, e quem esta perto de cada um
 *       ouve o que ele fala — como alguem falando ao telefone na rua.</li>
 *   <li><b>Oculto</b>: nao aparece na lista de grupos, entao ninguem entra na ligacao dos outros
 *       pelo menu do Voice Chat. Escutar continua possivel do jeito certo: chegando perto.</li>
 *   <li><b>Persistente</b>, como o original: quem apaga o grupo e o telefone, ao desligar.</li>
 * </ul>
 *
 * <p>So e carregada quando o telefone ja tem a API do Voice Chat em maos, entao citar tipos do
 * Voice Chat aqui e seguro mesmo com ele sendo opcional para este mod.
 */
public final class PhoneCallGroups {
    private static final String NAME = "Ligação";

    private PhoneCallGroups() {
    }

    /** O grupo pronto, ou {@code null} para deixar o telefone montar o dele. */
    @Nullable
    public static Object build() {
        VoicechatServerApi api = EssentialsVoicePlugin.api();
        if (api == null) return null;
        try {
            return api.groupBuilder()
                    .setName(NAME)
                    .setType(Group.Type.OPEN)
                    .setHidden(true)
                    .setPersistent(true)
                    .build();
        } catch (RuntimeException e) {
            AurorionEssentials.LOGGER.warn("Nao foi possivel montar o grupo aberto da ligacao; usando o do telefone.", e);
            return null;
        }
    }
}
