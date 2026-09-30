package com.aurorion.servicos.data;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Vaga de trabalho: alguem (a taverna, a forja, a guarda da cidade) procura gente. Candidatar-se
 * avisa o dono e abre a conversa; o dono ve a lista de candidatos e conversa com cada um.
 *
 * @param salary texto livre, como o preco do anuncio
 */
public record Vaga(long id, UUID owner, String category, String title, String description, String salary,
                   long createdAt, List<UUID> candidates) {

    public Vaga {
        candidates = List.copyOf(candidates);
    }

    public boolean applied(UUID account) {
        return candidates.contains(account);
    }

    public Vaga withCandidate(UUID account) {
        if (applied(account)) return this;
        List<UUID> next = new ArrayList<>(candidates);
        next.add(account);
        return new Vaga(id, owner, category, title, description, salary, createdAt, next);
    }

    public Vaga withoutCandidate(UUID account) {
        if (!applied(account)) return this;
        List<UUID> next = new ArrayList<>(candidates);
        next.remove(account);
        return new Vaga(id, owner, category, title, description, salary, createdAt, next);
    }
}
