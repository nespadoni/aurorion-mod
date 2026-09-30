package com.aurorion.servicos.data;

public enum PedidoStatus {
    /** Esperando alguem aceitar: o profissional escolhido (direto) ou qualquer um da area (aberto). */
    ABERTO("Aguardando"),
    ACEITO("Aceito"),
    CONCLUIDO("Concluído"),
    CANCELADO("Cancelado"),
    RECUSADO("Recusado"),
    EXPIRADO("Expirado");

    private final String label;

    PedidoStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public boolean finished() {
        return this != ABERTO && this != ACEITO;
    }

    public static PedidoStatus parse(String name) {
        for (PedidoStatus status : values()) if (status.name().equals(name)) return status;
        return CANCELADO;
    }
}
