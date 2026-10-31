package com.aurorion.core.character;

import com.aurorion.core.AurorionCore;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * O que o personagem sabe fazer <b>dentro do jogo</b> — magias liberadas, passivas, profissão, nós da
 * Trama —, reunido de vários mods para quem quiser mostrar (hoje, a aba Habilidades da ficha).
 *
 * <p>Mesmo desenho do {@link com.aurorion.core.diagnostics.ServerDiagnostics}: cada mod dono do dado
 * registra um {@link Provider} na construção dele, e quem mostra chama {@link #collect}. A ficha não
 * importa a magia, e a magia não sabe que a ficha existe — a regra de independência da SDD §3 vale.
 *
 * <p><b>E se ninguém registrar?</b> {@link #collect} devolve lista vazia, e a aba diz que não há nada.
 * Um provedor que lança não derruba os outros: o grupo dele some daquela abertura, e o erro vai uma vez
 * para o log.
 *
 * <h2>Custo</h2>
 * Coleta na thread do servidor, <b>só quando alguém abre ou atualiza a ficha</b> — nunca por tick. O
 * provedor lê o que já está em memória (SavedData, attachment, registro): nada de disco, rede, busca
 * em área ou varredura do mundo. Os tetos ({@link #MAX_GROUPS}, {@link #MAX_ITEMS} e o tamanho de cada
 * texto) seguram o pacote mesmo com provedor descuidado.
 */
public final class CharacterAbilities {
    public static final int MAX_GROUPS = 8;
    public static final int MAX_ITEMS = 64;
    /** Item sem barra de progresso. */
    public static final int NO_PROGRESS = -1;

    private static final int MAX_ID = 32;
    private static final int MAX_TITLE = 48;
    private static final int MAX_NAME = 64;
    private static final int MAX_DETAIL = 400;
    private static final int MAX_LEVEL = 24;
    private static final int MAX_KEY = 128;
    private static final int MAX_ICON = 80;

    private static final Map<String, Provider> PROVIDERS = new ConcurrentHashMap<>();
    /** Provedores que já lançaram nesta execução: o log avisa uma vez, não a cada abertura de ficha. */
    private static final Set<String> FAILED = ConcurrentHashMap.newKeySet();

    private CharacterAbilities() {
    }

    /** Em que pé a habilidade está. {@link #wire()} é o texto do contrato com a tela da ficha. */
    public enum State {
        /** Em uso agora (passiva ligada, profissão atual, nó comprado). */
        ACTIVE("ativa"),
        /** O personagem tem, mas não está em uso. */
        UNLOCKED("liberada"),
        /** A caminho: vem com {@code progress} de 0 a 100. */
        PROGRESS("progresso"),
        /** Ainda não; o motivo vai no detalhe. */
        LOCKED("bloqueada");

        private final String wire;

        State(String wire) {
            this.wire = wire;
        }

        public String wire() {
            return wire;
        }
    }

    /**
     * Uma habilidade.
     *
     * @param nameKey   chave de tradução do nome, ou vazio. O servidor dedicado só conhece o
     *                  {@code en_us}: com a chave, o cliente mostra no idioma dele, e {@code name} fica
     *                  de reserva.
     * @param detailKey idem para o detalhe.
     * @param level     rótulo curto ao lado do nome ("Escola", "3/45", "Proibida").
     * @param progress  0–100, ou {@link #NO_PROGRESS}.
     * @param icon      id de item ({@code minecraft:enchanted_book}), ou vazio.
     */
    public record Item(String name, String nameKey, String detail, String detailKey, String level,
                       State state, int progress, String icon) {
        public Item {
            name = cap(name, MAX_NAME);
            nameKey = cap(nameKey, MAX_KEY);
            detail = cap(detail, MAX_DETAIL);
            detailKey = cap(detailKey, MAX_KEY);
            level = cap(level, MAX_LEVEL);
            state = state == null ? State.UNLOCKED : state;
            progress = Math.max(NO_PROGRESS, Math.min(100, progress));
            icon = cap(icon, MAX_ICON);
        }

        /** Texto pronto, sem tradução. */
        public static Item of(String name, String detail, String level, State state, String icon) {
            return new Item(name, "", detail, "", level, state, NO_PROGRESS, icon);
        }

        public Item withProgress(int value) {
            return new Item(name, nameKey, detail, detailKey, level, state, value, icon);
        }

        public Item withKeys(String nameKey, String detailKey) {
            return new Item(name, nameKey, detail, detailKey, level, state, progress, icon);
        }
    }

    /**
     * Um bloco da aba. Grupo sem item não aparece.
     *
     * @param id    estável ({@code magias}, {@code profissao}); a tela pode lembrar qual estava aberto.
     * @param color {@code #RRGGBB} ou vazio (ver {@link #color}).
     * @param order posição na aba: menor primeiro. Magia 10, Profissão 20, Trama 30, Personagem 90.
     */
    public record Group(String id, String title, String color, int order, List<Item> items) {
        public Group {
            id = cap(id, MAX_ID).toLowerCase(Locale.ROOT);
            title = cap(title, MAX_TITLE);
            color = color != null && color.matches("#[0-9A-Fa-f]{6}") ? color.toUpperCase(Locale.ROOT) : "";
            items = items == null ? List.of() : List.copyOf(items.size() > MAX_ITEMS ? items.subList(0, MAX_ITEMS) : items);
        }
    }

    /**
     * Quem sabe o que o personagem tem. Roda na thread do servidor, só lê memória, devolve na hora.
     * Lista vazia quando o personagem não tem nada desse mod — nunca {@code null}.
     */
    @FunctionalInterface
    public interface Provider {
        List<Group> collect(ServerPlayer player);
    }

    /** Chamado uma vez pelo mod dono do dado, na construção dele. O id é o do mod ({@code aurorion_magia}). */
    public static void register(String id, Provider provider) {
        PROVIDERS.put(id, provider);
    }

    public static void unregister(String id) {
        PROVIDERS.remove(id);
        FAILED.remove(id);
    }

    /** Há algum provedor? Sem nenhum, nem vale montar a lista. */
    public static boolean installed() {
        return !PROVIDERS.isEmpty();
    }

    /** Tudo o que o personagem tem, em ordem, dentro dos tetos. Nunca lança. */
    public static List<Group> collect(ServerPlayer player) {
        if (PROVIDERS.isEmpty()) return List.of();
        return collect(player, new TreeMap<>(PROVIDERS));
    }

    /** O miolo, separado para teste: provedores em ordem de id, grupos em ordem de {@code order}. */
    static List<Group> collect(@Nullable ServerPlayer player, Map<String, Provider> providers) {
        List<Group> groups = new ArrayList<>();
        providers.forEach((id, provider) -> {
            List<Group> found;
            try {
                found = provider.collect(player);
            } catch (RuntimeException e) {
                if (FAILED.add(id)) AurorionCore.LOGGER.warn("Habilidades: o provedor {} falhou; o grupo dele fica de fora", id, e);
                return;
            }
            if (found == null) return;
            for (Group group : found) {
                if (group != null && !group.items().isEmpty()) groups.add(group);
            }
        });
        // Ordenação estável: empate de order mantém a ordem dos provedores.
        groups.sort(Comparator.comparingInt(Group::order));
        return groups.size() > MAX_GROUPS ? List.copyOf(groups.subList(0, MAX_GROUPS)) : List.copyOf(groups);
    }

    /** {@code 0xRRGGBB} → {@code #RRGGBB}. */
    public static String color(int rgb) {
        return String.format(Locale.ROOT, "#%06X", rgb & 0xFFFFFF);
    }

    /** Nulo vira vazio; texto acima do teto é cortado sem partir par substituto. */
    static String cap(@Nullable String text, int max) {
        if (text == null) return "";
        String value = text.strip();
        if (value.codePointCount(0, value.length()) <= max) return value;
        return value.substring(0, value.offsetByCodePoints(0, max));
    }
}
