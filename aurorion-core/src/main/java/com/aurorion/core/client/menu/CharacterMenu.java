package com.aurorion.core.client.menu;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * O menu do personagem, do lado do cliente: um registro de <b>seções</b> que cada mod alimenta (a ficha
 * registra Perfil e Perícias; o duelo, Duelo e Ações; a magia, Passivas...) e um <b>host</b> que desenha
 * tudo ({@code aurorion-menu}). Contrato no molde dos gates do core: ninguém importa ninguém.
 *
 * <p><b>E se o host não estiver no pack?</b> {@link #installed()} responde {@code false}, {@link #open} e
 * {@link #prompt} devolvem {@code false}, e cada mod segue com o caminho antigo (chat, tela própria). As
 * seções continuam registradas, sem custo.
 *
 * <p>Só cliente: registre em evento de cliente ({@code FMLClientSetupEvent}). Nada aqui roda por tick; as
 * seções montam as listas quando a pessoa abre o menu ou escolhe um item.
 */
public final class CharacterMenu {
    /**
     * Um item do menu: círculo na raiz, cartão nas listas. Com {@code children}, abre uma lista ao lado;
     * com {@code action}, faz algo; com {@code panel}, mostra um cartão grande à esquerda.
     */
    public record Entry(String label, String icon, String detail, boolean enabled,
                        @Nullable Supplier<List<Entry>> children, @Nullable Runnable action, @Nullable Supplier<Panel> panel) {
        public static Entry branch(String label, String icon, boolean enabled, Supplier<List<Entry>> children) {
            return new Entry(label, icon, "›", enabled, children, null, null);
        }

        public static Entry action(String label, String icon, String detail, boolean enabled, Runnable action) {
            return new Entry(label, icon, detail, enabled, null, action, null);
        }

        public static Entry panel(String label, String icon, String detail, Supplier<Panel> panel) {
            return new Entry(label, icon, detail, true, null, null, panel);
        }

        public static Entry disabled(String label, String icon) {
            return new Entry(label, icon, "", false, null, null, null);
        }
    }

    /** Uma seção da raiz: um círculo com ícone. */
    public interface Section {
        String id();

        String title();

        /** Nome do ícone em {@code aurorion_core:textures/gui/menu/icon_<nome>.png}. */
        String icon();

        int order();

        default boolean visible() {
            return true;
        }

        /** Os itens ao lado do círculo. Lista vazia + {@link #panel()} = só o cartão grande. */
        List<Entry> entries();

        /** Cartão grande à esquerda quando a seção é escolhida (o Perfil). */
        @Nullable
        default Panel panel() {
            return null;
        }

        /** A seção foi escolhida: hora de pedir dados frescos ao servidor. */
        default void opened() {
        }
    }

    /** Conteúdo livre num cartão: o host dá o retângulo e repassa o mouse e o teclado. */
    public interface Panel {
        default float width() {
            return 240;
        }

        default float height() {
            return 210;
        }

        void render(GuiGraphics g, float x, float y, float w, float h, int mouseX, int mouseY, float alpha);

        default boolean mouseClicked(double mouseX, double mouseY, int button) {
            return false;
        }

        default boolean mouseScrolled(double mouseX, double mouseY, double delta) {
            return false;
        }

        default boolean keyPressed(int key, int scan, int modifiers) {
            return false;
        }

        default boolean charTyped(char c, int modifiers) {
            return false;
        }

        /** Saiu da tela (outra seção, menu fechado). */
        default void closed() {
        }
    }

    /**
     * Janela de confirmação ○ / ×. {@code seconds} &gt; 0: some sozinha (e conta como "não") quando o tempo
     * acaba; o convite de duelo usa isso.
     */
    public record Modal(String title, String body, int bodyColor, Runnable yes, @Nullable Runnable no, int seconds) {
    }

    /**
     * Uma ação sobre outro jogador, na seção Interações (desafiar para duelo...). {@code entry} recebe o
     * jogador escolhido e devolve o item (ou {@code null} para não oferecer).
     */
    public record Interaction(String id, int order, Function<Player, @Nullable Entry> entry) {
    }

    /**
     * Um aviso que aparece na frente da pessoa, no cartão do menu, com o personagem dela ao lado: conquista,
     * aviso da ficha, resultado. {@code kicker} é a linha pequena de cima ("CONQUISTA"), {@code rows} são pares
     * rótulo/valor separados por linhas finas (valor vazio = a linha inteira é texto), {@code item} um ícone
     * opcional.
     */
    public record Notice(String kicker, String title, List<String[]> rows, @Nullable ItemStack item, int seconds) {
        public Notice {
            rows = List.copyOf(rows);
        }
    }

    /** O que o host precisa saber fazer. */
    public interface Host {
        /** Abre o menu (já na seção, se não for {@code null}). */
        void open(@Nullable String section);

        /** Mostra a janela ○ / ×, abrindo o menu se preciso. */
        void prompt(Modal modal);

        /** Os dados de uma seção mudaram: o host redesenha as listas abertas. */
        void refresh();

        boolean isOpen();

        /** Fecha o menu (o painel some da frente da pessoa). */
        default void close() {
        }

        /** Some com a janela ○/× aberta, sem responder (já respondida por outro caminho). */
        default void dismissPrompt() {
        }

        /** Mostra um aviso; {@code false} se o host não mostra avisos (quem chamou usa o caminho dele). */
        default boolean notice(Notice notice) {
            return false;
        }
    }

    private static final Map<String, Section> SECTIONS = new ConcurrentHashMap<>();
    private static final Map<String, Interaction> INTERACTIONS = new ConcurrentHashMap<>();
    @Nullable
    private static volatile Host host;

    private CharacterMenu() {
    }

    public static void register(Section section) {
        SECTIONS.put(section.id(), section);
    }

    public static void registerInteraction(Interaction interaction) {
        INTERACTIONS.put(interaction.id(), interaction);
    }

    /** As seções visíveis agora, na ordem. Uma seção que lança some desta abertura. */
    public static List<Section> sections() {
        List<Section> out = new ArrayList<>(SECTIONS.size());
        for (Section section : SECTIONS.values()) {
            try {
                if (section.visible()) out.add(section);
            } catch (RuntimeException ignored) {
                // Seção quebrada não derruba o menu.
            }
        }
        out.sort(Comparator.comparingInt(Section::order).thenComparing(Section::id));
        return out;
    }

    @Nullable
    public static Section section(String id) {
        return SECTIONS.get(id);
    }

    public static List<Interaction> interactions() {
        List<Interaction> out = new ArrayList<>(INTERACTIONS.values());
        out.sort(Comparator.comparingInt(Interaction::order).thenComparing(Interaction::id));
        return out;
    }

    /** Chamado uma vez pelo host, na montagem do cliente. */
    public static void provide(Host value) {
        host = value;
    }

    public static boolean installed() {
        return host != null;
    }

    public static boolean isOpen() {
        Host current = host;
        return current != null && current.isOpen();
    }

    /** @return {@code false} sem host (quem chamou segue o caminho antigo). */
    public static boolean open(@Nullable String section) {
        Host current = host;
        if (current == null) return false;
        current.open(section);
        return true;
    }

    public static boolean prompt(Modal modal) {
        Host current = host;
        if (current == null) return false;
        current.prompt(modal);
        return true;
    }

    public static void refresh() {
        Host current = host;
        if (current != null) current.refresh();
    }

    /** @return {@code false} sem host: o aviso vai pelo caminho antigo de quem chamou. */
    public static boolean notice(Notice notice) {
        Host current = host;
        return current != null && current.notice(notice);
    }

    /** A janela ○/× perdeu o sentido (o convite foi aceito pelo chat, por exemplo): some sem responder. */
    public static void dismissPrompt() {
        Host current = host;
        if (current != null) current.dismissPrompt();
    }

    /** Fecha o menu, se estiver aberto. O menu não é uma tela: {@code setScreen(null)} não o fecha. */
    public static void close() {
        Host current = host;
        if (current != null) current.close();
    }
}
