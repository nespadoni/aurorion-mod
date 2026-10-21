package com.aurorion.essentials.voice;

import com.aurorion.essentials.AurorionEssentials;
import de.maxhenkel.voicechat.api.Group;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import net.minecraft.Util;
import org.jetbrains.annotations.Nullable;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.UUID;

/**
 * O grupo de voz de uma ligacao do telefone Mattupolis, chamado pelo {@code PhoneCallGroupMixin}.
 *
 * <ul>
 *   <li><b>Aberto</b>: os dois lados se ouvem de qualquer distancia, e quem esta perto de cada um
 *       ouve o que ele fala — como alguem falando ao telefone na rua.</li>
 *   <li><b>Oculto</b>: nao aparece na lista de grupos do menu do Voice Chat.</li>
 *   <li><b>Com senha aleatoria</b>, que ninguem conhece. Oculto nao bastava: o Voice Chat anuncia o
 *       grupo (com o id) a todo cliente, e um cliente modificado entrava na ligacao alheia e escutava
 *       de qualquer lugar do mapa. O telefone coloca os dois lados com {@code setGroup}, que entra com
 *       a senha do proprio grupo — a senha so barra quem pede para entrar de fora. Escutar continua
 *       possivel do jeito certo: chegando perto de quem fala.</li>
 *   <li><b>Persistente</b>, como o original: quem apaga o grupo e o telefone, ao desligar.</li>
 * </ul>
 *
 * <p>Alem da senha, o id fica no {@link PhoneCallRegistry} com os dois nicks da chamada: o
 * {@link EssentialsVoicePlugin} recusa a entrada de qualquer outro, e isenta o grupo da regra que
 * proibe grupo com senha.
 *
 * <p>So e carregada quando o telefone ja tem a API do Voice Chat em maos, entao citar tipos do
 * Voice Chat aqui e seguro mesmo com ele sendo opcional para este mod.
 */
public final class PhoneCallGroups {
    private static final String NAME = "Ligação";
    private static final SecureRandom RANDOM = new SecureRandom();
    /** Tempo para o telefone colocar os dois lados no grupo depois de monta-lo. */
    private static final long JOIN_GRACE_MS = 60_000L;

    static final PhoneCallRegistry CALLS = new PhoneCallRegistry();

    private PhoneCallGroups() {
    }

    /** O grupo pronto, ou {@code null} para deixar o telefone montar o dele. */
    @Nullable
    public static Object build(String callerNick, String targetNick) {
        VoicechatServerApi api = EssentialsVoicePlugin.api();
        if (api == null) return null;
        try {
            long now = Util.getMillis();
            CALLS.prune(now, JOIN_GRACE_MS, id -> api.getGroup(id) != null);
            UUID id = UUID.randomUUID();
            // Registrado antes do build: o CreateGroupEvent do setGroup ja precisa saber que e ligacao.
            CALLS.register(id, callerNick, targetNick, now);
            return api.groupBuilder()
                    .setId(id)
                    .setName(NAME)
                    .setType(Group.Type.OPEN)
                    .setHidden(true)
                    .setPassword(randomPassword())
                    .setPersistent(true)
                    .build();
        } catch (RuntimeException e) {
            AurorionEssentials.LOGGER.warn("Nao foi possivel montar o grupo aberto da ligacao; usando o do telefone.", e);
            return null;
        }
    }

    public static boolean isCall(@Nullable UUID group) {
        return CALLS.isCall(group);
    }

    /** O grupo e de ligacao e este nick nao e de nenhum dos dois lados. */
    public static boolean refuses(@Nullable UUID group, @Nullable String nick) {
        return CALLS.refuses(group, nick);
    }

    static void forget(@Nullable UUID group) {
        CALLS.forget(group);
    }

    static void clear() {
        CALLS.clear();
    }

    private static String randomPassword() {
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }
}
