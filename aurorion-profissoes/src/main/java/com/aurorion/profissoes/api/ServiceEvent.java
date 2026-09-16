package com.aurorion.profissoes.api;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;
import java.util.UUID;

/** Somente na thread do servidor. Validate veta; Completed registra o servico efetivamente realizado. */
public abstract class ServiceEvent extends Event {
    public final UUID receipt;
    public final ServerPlayer professional, customer;
    public final String service;
    protected ServiceEvent(UUID receipt, ServerPlayer professional, ServerPlayer customer, String service) {
        this.receipt = receipt; this.professional = professional; this.customer = customer; this.service = service;
    }
    /** Integracoes podem validar/reservar; nao cobrar irrevogavelmente antes de Completed. */
    public static final class Validate extends ServiceEvent implements ICancellableEvent {
        public Validate(UUID receipt, ServerPlayer professional, ServerPlayer customer, String service) {
            super(receipt, professional, customer, service);
        }
    }
    public static final class Completed extends ServiceEvent {
        public Completed(UUID receipt, ServerPlayer professional, ServerPlayer customer, String service) {
            super(receipt, professional, customer, service);
        }
    }
}
