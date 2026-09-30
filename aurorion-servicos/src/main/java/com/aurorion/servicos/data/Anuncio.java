package com.aurorion.servicos.data;

import java.util.UUID;

/**
 * O que um profissional oferece: area, titulo, descricao e preco em texto livre ("50 obolos a
 * consulta", "a combinar"). O preco nao e cobrado pelo app — e o que se combina na conversa.
 *
 * @param owner  a conta do personagem que anuncia (o alt e outra conta)
 * @param seenAt ultima vez que o dono entrou no servidor; anuncio de quem sumiu expira
 */
public record Anuncio(long id, UUID owner, String category, String title, String description, String price,
                      long createdAt, long seenAt) {

    public Anuncio edit(String category, String title, String description, String price, long now) {
        return new Anuncio(id, owner, category, title, description, price, createdAt, now);
    }

    public Anuncio seen(long now) {
        return new Anuncio(id, owner, category, title, description, price, createdAt, now);
    }
}
