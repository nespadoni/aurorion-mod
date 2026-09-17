package com.aurorion.economia.api;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.Event;

import java.util.ArrayList;
import java.util.List;

/** Extensao do menu presencial Shift+G, sempre na thread do servidor. */
public abstract class InteractionMenuEvent extends Event {
    public static final int MAX_OPTIONS = 8;

    private final ServerPlayer actor;
    private final ServerPlayer target;

    protected InteractionMenuEvent(ServerPlayer actor, ServerPlayer target) {
        this.actor = actor;
        this.target = target;
    }

    public final ServerPlayer actor() { return actor; }
    public final ServerPlayer target() { return target; }

    public record Option(String action, String title, String detail, boolean enabled) { }

    /** Outros mods acrescentam apresentacao; a autoridade continua no handler da acao. */
    public static final class Collect extends InteractionMenuEvent {
        private final List<Option> options = new ArrayList<>();

        public Collect(ServerPlayer actor, ServerPlayer target) {
            super(actor, target);
        }

        public void add(String action, String title, String detail, boolean enabled) {
            if (options.size() >= MAX_OPTIONS || action == null || action.isBlank()
                    || action.length() > 48 || title == null || title.length() > 96
                    || detail == null || detail.length() > 256
                    || options.stream().anyMatch(option -> option.action().equals(action))) return;
            options.add(new Option(action, title, detail, enabled));
        }

        public List<Option> options() { return List.copyOf(options); }
    }

    /** O primeiro consumidor que reconhecer a acao a marca como tratada. */
    public static final class Action extends InteractionMenuEvent {
        private final String action;
        private boolean handled;

        public Action(ServerPlayer actor, ServerPlayer target, String action) {
            super(actor, target);
            this.action = action;
        }

        public String action() { return action; }
        public boolean handled() { return handled; }
        public void markHandled() { handled = true; }
    }
}
