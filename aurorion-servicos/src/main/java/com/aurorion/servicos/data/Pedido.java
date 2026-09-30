package com.aurorion.servicos.data;

import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Um pedido de servico.
 *
 * <ul>
 *   <li><b>Direto</b> ({@code target} preenchido): feito a partir de um anuncio, so aquele profissional
 *       aceita ou recusa.</li>
 *   <li><b>Aberto</b> ({@code target} vazio): vai para a area inteira, e todo mundo que esta
 *       "Trabalhando" nela e avisado. O primeiro que aceitar leva.</li>
 * </ul>
 *
 * @param adId     o anuncio de onde saiu o pedido direto, ou 0
 * @param provider quem aceitou
 */
public record Pedido(long id, UUID requester, String category, String text, @Nullable UUID target, long adId,
                     PedidoStatus status, @Nullable UUID provider, long createdAt, long updatedAt) {

    public boolean direct() {
        return target != null;
    }

    public Pedido with(PedidoStatus status, @Nullable UUID provider, long now) {
        return new Pedido(id, requester, category, text, target, adId, status, provider, createdAt, now);
    }

    /** Quem esta do outro lado para {@code who}: o profissional para o cliente, o cliente para o profissional. */
    @Nullable
    public UUID counterpart(UUID who) {
        if (who.equals(requester)) return provider != null ? provider : target;
        return requester;
    }

    public boolean involves(UUID account) {
        return account.equals(requester) || account.equals(target) || account.equals(provider);
    }
}
