package com.aurorion.essentials.client;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

/**
 * O que a limpeza da agenda faz com a lista de personagens aposentados. Classe pura, sem Minecraft
 * nem telefone, para a regra poder ser testada.
 *
 * <ul>
 *   <li>Cada reset e processado <b>uma vez</b>. O contato que existe quando o cliente fica sabendo do
 *       reset e do personagem morto; um adicionado depois e do personagem novo e nao pode sumir.</li>
 *   <li>Reset da propria conta: a agenda inteira e do personagem anterior, e o novo comeca sem ela.</li>
 *   <li>Reset de outra conta: sai so o contato daquele nick.</li>
 * </ul>
 *
 * @param wipeAll        a conta deste cliente foi resetada
 * @param nicks          nicks de outras contas a esquecer (sem diferenca de maiuscula)
 * @param newlyProcessed ids de reset que este cliente ainda nao tinha tratado
 */
public record RetiredContacts(boolean wipeAll, Set<String> nicks, Set<UUID> newlyProcessed) {

    public static RetiredContacts plan(Map<UUID, String> retired, Set<UUID> processed, String selfNick) {
        boolean wipeAll = false;
        Set<String> nicks = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        Set<UUID> fresh = new LinkedHashSet<>();
        for (Map.Entry<UUID, String> entry : retired.entrySet()) {
            if (processed.contains(entry.getKey())) continue;
            fresh.add(entry.getKey());
            String nick = entry.getValue() == null ? "" : entry.getValue().trim();
            if (nick.isEmpty()) continue;
            if (selfNick != null && nick.equalsIgnoreCase(selfNick.trim())) {
                wipeAll = true;
            } else {
                nicks.add(nick);
            }
        }
        return new RetiredContacts(wipeAll, nicks, fresh);
    }

    public boolean isEmpty() {
        return newlyProcessed.isEmpty();
    }

    /** O contato (ou favorito de PIX) com este nome tem de sair. */
    public boolean forgets(String contactName) {
        if (contactName == null) return false;
        return wipeAll || nicks.contains(contactName.trim());
    }
}
