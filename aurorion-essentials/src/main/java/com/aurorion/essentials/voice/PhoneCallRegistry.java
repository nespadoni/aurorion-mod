package com.aurorion.essentials.voice;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Quais grupos de voz sao ligacoes do telefone, e quem pode estar dentro de cada um.
 *
 * <p>O grupo de ligacao e oculto, mas "oculto" e so o menu do Voice Chat que nao lista: o servidor
 * anuncia todo grupo a todo cliente ({@code AddGroupPacket}), com o id dentro. Um cliente modificado
 * le o id e pede para entrar — e escuta a ligacao dos outros de qualquer lugar do mapa. Por isso o
 * grupo tem senha aleatoria e, alem dela, o {@code JoinGroupEvent} recusa quem nao e um dos dois lados
 * da chamada (ver {@link EssentialsVoicePlugin}).
 *
 * <p>Os ids registrados aqui tambem sao a excecao da regra "grupo com senha e recusado" da
 * {@link VoiceConfig}: a senha da ligacao e do servidor, nao de um jogador.
 *
 * <p>Classe pura (nao conhece o Voice Chat), e com teto: o telefone pode montar um grupo e desistir
 * de usar (um dos lados nao conseguiu entrar), e esse id nunca recebe o evento de remocao.
 */
public final class PhoneCallRegistry {
    /** Bem acima de 80 jogadores em ligacao ao mesmo tempo (40 chamadas). */
    static final int MAX_CALLS = 512;

    private record Call(Set<String> participants, long createdAt) {
    }

    private final Map<UUID, Call> calls = new LinkedHashMap<>();

    /** Registra a ligacao. Os nicks sao comparados sem diferenca de maiuscula, como a Mojang faz. */
    public synchronized void register(UUID group, String callerNick, String targetNick, long now) {
        if (calls.size() >= MAX_CALLS) {
            Iterator<UUID> oldest = calls.keySet().iterator();
            oldest.next();
            oldest.remove();
        }
        calls.put(group, new Call(Set.copyOf(List.of(normalize(callerNick), normalize(targetNick))), now));
    }

    public synchronized boolean isCall(UUID group) {
        return group != null && calls.containsKey(group);
    }

    /** {@code true} quando o grupo e de ligacao e o nick nao e de nenhum dos dois lados. */
    public synchronized boolean refuses(UUID group, String nick) {
        Call call = group == null ? null : calls.get(group);
        if (call == null) return false;
        return nick == null || !call.participants().contains(normalize(nick));
    }

    public synchronized void forget(UUID group) {
        if (group != null) calls.remove(group);
    }

    /**
     * Esquece as ligacoes montadas ha mais de {@code graceMillis} cujo grupo nao existe mais no Voice
     * Chat. A carencia cobre o intervalo entre montar o grupo e o telefone colocar os dois dentro.
     */
    public synchronized void prune(long now, long graceMillis, Predicate<UUID> stillExists) {
        calls.entrySet().removeIf(entry -> now - entry.getValue().createdAt() >= graceMillis
                && !stillExists.test(entry.getKey()));
    }

    public synchronized void clear() {
        calls.clear();
    }

    synchronized int size() {
        return calls.size();
    }

    private static String normalize(String nick) {
        return nick == null ? "" : nick.toLowerCase(Locale.ROOT);
    }
}
