package com.aurorion.servicos.data;

import com.aurorion.profissoes.data.Profession;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * As areas do app: toda profissao do {@link Profession} (menos "sem profissao") e mais "Outros", para
 * o servico que nao e oficio registrado — entrega, construcao, escolta, aula.
 *
 * <p>A lista sai do enum, que existe igual no cliente e no servidor: profissao nova no
 * aurorion-profissoes aparece aqui sem mexer em nada.
 */
public final class Categorias {
    public static final String OUTROS = "outros";
    private static final List<Categoria> ALL = build();

    private Categorias() {
    }

    public record Categoria(String id, String label) {
    }

    public static List<Categoria> all() {
        return ALL;
    }

    public static boolean exists(String id) {
        return find(id) != null;
    }

    public static String label(String id) {
        Categoria categoria = find(id);
        return categoria == null ? id : categoria.label();
    }

    /** A profissao por tras da categoria, ou {@code null} para "Outros". */
    @Nullable
    public static Profession profession(String id) {
        for (Profession profession : Profession.values()) {
            if (profession != Profession.NONE && profession.id().equals(id)) return profession;
        }
        return null;
    }

    @Nullable
    private static Categoria find(String id) {
        if (id == null) return null;
        for (Categoria categoria : ALL) if (categoria.id().equals(id)) return categoria;
        return null;
    }

    private static List<Categoria> build() {
        List<Categoria> list = new ArrayList<>();
        for (Profession profession : Profession.values()) {
            if (profession != Profession.NONE) list.add(new Categoria(profession.id(), profession.label()));
        }
        list.add(new Categoria(OUTROS, "Outros"));
        return List.copyOf(list);
    }
}
