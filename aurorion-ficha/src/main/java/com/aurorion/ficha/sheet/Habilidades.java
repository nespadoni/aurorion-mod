package com.aurorion.ficha.sheet;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.locale.Language;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * O que o personagem já tem DENTRO do jogo, vindo dos outros mods do servidor (magias liberadas,
 * profissão, Marca, passivas…) — não do site. O servidor monta e manda junto da View, no campo
 * {@code habilidades}; a aba Habilidades da ficha só desenha. Grupo vazio não aparece.
 *
 * <p>{@code estado}: {@code ativa} (em uso agora), {@code liberada}, {@code progresso} (com
 * {@code progresso} 0–100) ou {@code bloqueada} (com o motivo em {@code detalhe}).
 * {@code icone}: id de item ({@code minecraft:blaze_powder}) ou vazio. {@code cor}: {@code #RRGGBB} ou vazio.
 *
 * <p>{@code nome_chave}/{@code detalhe_chave} (opcionais): chave de tradução. O servidor dedicado só
 * conhece o {@code en_us}, então {@code nome}/{@code detalhe} vêm em inglês quando a fonte é um texto
 * traduzível (magia do Iron's, passiva); o {@link #parse} do cliente troca pelo idioma do jogador
 * quando conhece a chave. A tela não precisa fazer nada.
 *
 * <p>Quem preenche: {@code server/HabilidadesColeta}, a partir do {@code CharacterAbilities} do core.
 */
public record Habilidades(List<Grupo> grupos) {
    public static final Habilidades EMPTY = new Habilidades(List.of());
    public static final int MAX_GRUPOS = 8;
    public static final int MAX_ITENS = 64;
    public static final Set<String> ESTADOS = Set.of("ativa", "liberada", "progresso", "bloqueada");

    public Habilidades {
        grupos = FichaSheet.list(grupos);
    }

    public boolean isEmpty() {
        for (Grupo g : grupos) if (!g.itens().isEmpty()) return false;
        return true;
    }

    public int total() {
        int n = 0;
        for (Grupo g : grupos) n += g.itens().size();
        return n;
    }

    public record Grupo(String id, String titulo, String cor, List<Item> itens) {
        public Grupo {
            id = FichaSheet.text(id);
            titulo = FichaSheet.text(titulo);
            cor = FichaSheet.text(cor);
            itens = FichaSheet.list(itens);
        }

        /** Grupo sem item vira {@code null} (e some da lista). */
        @Nullable
        static Grupo parse(JsonObject o, Function<String, String> translate) {
            List<Item> itens = Json.objects(Json.array(o, "itens"), MAX_ITENS, i -> Item.parse(i, translate));
            if (itens.isEmpty()) return null;
            String cor = Json.str(o, "cor", 7);
            return new Grupo(Json.str(o, "id", 32), Json.str(o, "titulo", 48), cor.matches("#[0-9A-Fa-f]{6}") ? cor : "", itens);
        }

        JsonObject toJson() {
            JsonObject o = new JsonObject();
            o.addProperty("id", id);
            o.addProperty("titulo", titulo);
            o.addProperty("cor", cor);
            JsonArray a = new JsonArray();
            for (Item i : itens) a.add(i.toJson());
            o.add("itens", a);
            return o;
        }
    }

    public record Item(String nome, String detalhe, String nivel, String estado, int progresso, String icone,
                       String nomeChave, String detalheChave) {
        public Item {
            nome = FichaSheet.text(nome);
            detalhe = FichaSheet.text(detalhe);
            nivel = FichaSheet.text(nivel);
            estado = estado == null || !ESTADOS.contains(estado) ? "liberada" : estado;
            progresso = Math.max(-1, Math.min(100, progresso));
            icone = FichaSheet.text(icone);
            nomeChave = FichaSheet.text(nomeChave);
            detalheChave = FichaSheet.text(detalheChave);
        }

        /** Sem chave de tradução (o formato de antes). */
        public Item(String nome, String detalhe, String nivel, String estado, int progresso, String icone) {
            this(nome, detalhe, nivel, estado, progresso, icone, "", "");
        }

        static Item parse(JsonObject o, Function<String, String> translate) {
            String nomeChave = Json.str(o, "nome_chave", 128);
            String detalheChave = Json.str(o, "detalhe_chave", 128);
            String nome = Json.str(o, "nome", 64);
            String detalhe = Json.strClipped(o, "detalhe", 400);
            String nomeLocal = nomeChave.isEmpty() ? null : translate.apply(nomeChave);
            if (nomeLocal != null && !Json.clean(nomeLocal, 64).isEmpty()) nome = Json.clean(nomeLocal, 64);
            String detalheLocal = detalheChave.isEmpty() ? null : translate.apply(detalheChave);
            if (detalheLocal != null && !Json.clip(detalheLocal, 400).isEmpty()) detalhe = Json.clip(detalheLocal, 400);
            return new Item(nome, detalhe, Json.str(o, "nivel", 24),
                    Json.str(o, "estado", 12), o != null && o.has("progresso") ? Json.integer(o, "progresso") : -1,
                    Json.str(o, "icone", 80), nomeChave, detalheChave);
        }

        JsonObject toJson() {
            JsonObject o = new JsonObject();
            o.addProperty("nome", nome);
            o.addProperty("detalhe", detalhe);
            o.addProperty("nivel", nivel);
            o.addProperty("estado", estado);
            if (progresso >= 0) o.addProperty("progresso", progresso);
            o.addProperty("icone", icone);
            if (!nomeChave.isEmpty()) o.addProperty("nome_chave", nomeChave);
            if (!detalheChave.isEmpty()) o.addProperty("detalhe_chave", detalheChave);
            return o;
        }
    }

    /** Lê o campo {@code habilidades} da View; ausente ou torto vira {@link #EMPTY}. Traduz pelo idioma do jogo. */
    public static Habilidades parse(@Nullable JsonObject view) {
        return parse(view, Habilidades::translate);
    }

    /** Com tradutor explícito (teste, ou quem não quer tradução): devolve o texto, ou {@code null} se não conhece. */
    public static Habilidades parse(@Nullable JsonObject view, Function<String, String> translate) {
        JsonArray array = Json.array(view, "habilidades");
        if (array == null || array.isEmpty()) return EMPTY;
        return new Habilidades(Json.objects(array, MAX_GRUPOS, g -> Grupo.parse(g, translate)));
    }

    public JsonArray toJson() {
        JsonArray a = new JsonArray();
        for (Grupo g : grupos) a.add(g.toJson());
        return a;
    }

    /**
     * A chave no idioma em uso: no cliente, o escolhido pelo jogador. {@code null} quando não conhece
     * — ou quando não há {@link Language} carregado (teste sem Minecraft).
     */
    @Nullable
    static String translate(String key) {
        try {
            Language language = Language.getInstance();
            return language.has(key) ? language.getOrDefault(key, key) : null;
        } catch (RuntimeException | LinkageError unavailable) {
            return null;
        }
    }
}
