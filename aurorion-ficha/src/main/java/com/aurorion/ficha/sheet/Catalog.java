package com.aurorion.ficha.sheet;

import org.jetbrains.annotations.Nullable;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Os 6 atributos, as 22 perícias, as condições e os 11 Elementos do motor RPG v0.3, na ordem de
 * exibição — espelho de {@code internal/rpg/rules/catalog.go} e {@code rules/conditions.go} do
 * backend (nomes, acentos e textos iguais; {@code CatalogTest} confere).
 *
 * <p>Serve para as sugestões dos comandos, para agrupar as perícias na tela e para validar o que o
 * cliente pede antes de gastar uma chamada ao site. Os <b>números</b> vêm sempre da ficha do site;
 * aqui só moram ids e nomes. Perícia nova no backend sem entrada aqui continua aparecendo na tela
 * (agrupada pelo atributo que a ficha informa), só não ganha sugestão de comando.
 *
 * <p>Tudo é montado uma vez no carregamento da classe e é imutável: leitura de qualquer thread.
 */
public final class Catalog {
    public record Attribute(String id, String label, String abbreviation) {
    }

    public record Skill(String id, String label, String attribute, List<String> aliases) {
        public Skill {
            aliases = List.copyOf(aliases);
        }
    }

    public static final List<Attribute> ATTRIBUTES = List.of(
            new Attribute("for", "Força", "FOR"),
            new Attribute("des", "Destreza", "DES"),
            new Attribute("vig", "Vigor", "VIG"),
            new Attribute("sab", "Sabedoria", "SAB"),
            new Attribute("int", "Intelecto", "INT"),
            new Attribute("vit", "Vitral", "VIT"));

    public static final List<Skill> SKILLS = List.of(
            entry("atletismo", "Atletismo", "for"),
            entry("corpo_a_corpo", "Corpo a Corpo", "for", "corpo", "corpoacorpo", "melee"),
            entry("acrobacia", "Acrobacia", "des"),
            entry("furtividade", "Furtividade", "des"),
            entry("pontaria", "Pontaria", "des"),
            entry("resistencia", "Resistência", "vig"),
            entry("astros_e_mistos", "Astros & Mistos", "sab", "astros", "astrosemistos", "astros_e_misticos", "astrosemisticos"),
            entry("historia", "História", "sab"),
            entry("ciencia", "Ciência", "sab"),
            entry("natureza", "Natureza", "sab"),
            entry("medicina", "Medicina", "sab"),
            entry("criacao", "Criação", "sab"),
            entry("investigacao", "Investigação", "int"),
            entry("linguistica", "Linguística", "int"),
            entry("psicologia", "Psicologia", "int"),
            entry("tecnologia", "Tecnologia", "int"),
            entry("diplomacia", "Diplomacia", "vit"),
            entry("enganacao", "Enganação", "vit"),
            entry("intimidacao", "Intimidação", "vit"),
            entry("procurar", "Procurar", "vit"),
            entry("intencao", "Intenção", "vit"),
            entry("vontade", "Vontade", "vit"));

    private static final Pattern MARKS = Pattern.compile("\\p{M}+");
    private static final Map<String, Skill> SKILL_INDEX = new HashMap<>();
    private static final Map<String, Attribute> ATTRIBUTE_INDEX = new HashMap<>();
    private static final Map<String, List<Skill>> SKILLS_BY_ATTRIBUTE;
    private static final List<String> ROLL_TARGETS;

    static {
        for (Skill s : SKILLS) {
            SKILL_INDEX.put(s.id(), s);
            SKILL_INDEX.put(normalize(s.id()), s);
            SKILL_INDEX.put(normalize(s.label()), s);
            for (String alias : s.aliases()) SKILL_INDEX.put(normalize(alias), s);
        }
        for (Attribute a : ATTRIBUTES) {
            ATTRIBUTE_INDEX.put(a.id(), a);
            ATTRIBUTE_INDEX.put(normalize(a.label()), a);
        }
        Map<String, List<Skill>> grouped = new LinkedHashMap<>();
        for (Attribute a : ATTRIBUTES) grouped.put(a.id(), new ArrayList<>());
        for (Skill s : SKILLS) grouped.get(s.attribute()).add(s);
        Map<String, List<Skill>> frozen = new LinkedHashMap<>();
        grouped.forEach((k, v) -> frozen.put(k, List.copyOf(v)));
        SKILLS_BY_ATTRIBUTE = java.util.Collections.unmodifiableMap(frozen);
        List<String> targets = new ArrayList<>(SKILLS.size() + ATTRIBUTES.size());
        for (Skill s : SKILLS) targets.add(s.id());
        for (Attribute a : ATTRIBUTES) targets.add(a.id());
        ROLL_TARGETS = List.copyOf(targets);
    }

    private Catalog() {
    }

    private static Skill entry(String id, String label, String attribute, String... aliases) {
        return new Skill(id, label, attribute, List.of(aliases));
    }

    /** Minúsculas sem acento, espaço ou pontuação — o mesmo {@code NormalizeKey} do backend. */
    public static String normalize(@Nullable String text) {
        if (text == null || text.isEmpty()) return "";
        String plain = MARKS.matcher(Normalizer.normalize(text, Normalizer.Form.NFD)).replaceAll("");
        StringBuilder out = new StringBuilder(plain.length());
        for (char c : plain.toLowerCase(Locale.ROOT).toCharArray()) {
            if (Character.isLetterOrDigit(c)) out.append(c);
        }
        return out.toString();
    }

    /** Perícia por id, nome ({@code "Investigação"}) ou apelido ({@code "melee"}), como o {@code LookupSkill}. */
    @Nullable
    public static Skill skill(@Nullable String key) {
        if (key == null || key.isEmpty()) return null;
        Skill exact = SKILL_INDEX.get(key);
        return exact != null ? exact : SKILL_INDEX.get(normalize(key));
    }

    /** Atributo por id ou nome, como o {@code LookupAttribute}. */
    @Nullable
    public static Attribute attribute(@Nullable String key) {
        if (key == null || key.isEmpty()) return null;
        Attribute exact = ATTRIBUTE_INDEX.get(key);
        return exact != null ? exact : ATTRIBUTE_INDEX.get(normalize(key));
    }

    /** Perícias de um atributo, na ordem do catálogo (lista imutável, montada uma vez). */
    public static List<Skill> skillsOf(@Nullable String attribute) {
        Attribute a = attribute(attribute);
        return a == null ? List.of() : SKILLS_BY_ATTRIBUTE.get(a.id());
    }

    /** Ids aceitos por {@code /ficha rolar}: as 22 perícias e os 6 atributos (lista imutável). */
    public static List<String> rollTargets() {
        return ROLL_TARGETS;
    }

    public static String attributeLabel(@Nullable String id) {
        Attribute a = attribute(id);
        return a != null ? a.label() : id == null ? "" : id.toUpperCase(Locale.ROOT);
    }

    public static String attributeAbbreviation(@Nullable String id) {
        Attribute a = attribute(id);
        return a != null ? a.abbreviation() : id == null ? "" : id.toUpperCase(Locale.ROOT);
    }

    /** Nome da perícia; id desconhecido volta como veio (a ficha do site pode ter perícia nova). */
    public static String skillLabel(@Nullable String id) {
        Skill s = skill(id);
        return s != null ? s.label() : id == null ? "" : id;
    }

    // ── Condições (rules/conditions.go) ─────────────────────────────────────────

    /**
     * Severidade decide a cor do selo ({@code leve}, {@code moderada}, {@code grave}, {@code estado});
     * nome, resumo e como sair ({@code removal}) aparecem no tooltip. Os textos são os do motor.
     */
    public record Condition(String id, String label, String severity, String summary, String removal) {
        /** "Medo — Desvantagem em ataques… · Sai: Fogo Vivo…" — o tooltip inteiro, numa linha. */
        public String tooltip() {
            StringBuilder out = new StringBuilder(label);
            if (!summary.isEmpty()) out.append(" — ").append(summary);
            if (!removal.isEmpty()) out.append(" Como sair: ").append(removal);
            return out.toString();
        }
    }

    /** Na ordem do motor (leves, moderadas, graves, estados). */
    public static final List<Condition> CONDITIONS = List.of(
            new Condition("sangramento", "Sangramento", "leve",
                    "Perde 3% da Vida de referência no início de cada turno (ignora RD).",
                    "Medicina CD 12 (Ação), qualquer cura ou Sutura de Campo."),
            new Condition("lentidao", "Lentidão", "leve", "Movimento reduzido à metade.", "Termina com a duração."),
            new Condition("queimando", "Queimando", "leve",
                    "Perde 3% da Vida de referência (elemental) no início de cada turno.",
                    "Gastar o movimento para apagar, água ou fim da duração."),
            new Condition("ofuscado", "Ofuscado", "leve", "Desvantagem nos próprios ataques.", "Termina com a duração."),
            new Condition("vulneravel", "Vulnerável", "moderada",
                    "Recebe dano físico e elemental com vulnerabilidade (×1,5).", "Termina com a duração."),
            new Condition("exposto", "Exposto", "moderada", "Ataques contra o alvo têm vantagem.", "Termina com a duração."),
            new Condition("caido", "Caído", "moderada",
                    "Desvantagem nos próprios ataques; corpo a corpo contra tem vantagem, à distância tem desvantagem.",
                    "Levantar gasta metade do movimento."),
            new Condition("agarrado", "Agarrado", "moderada", "Movimento 0.",
                    "Atletismo ou Acrobacia contra a CD de quem agarra (Ação)."),
            new Condition("medo", "Medo", "moderada",
                    "Desvantagem em ataques e testes; não pode se aproximar voluntariamente da fonte.",
                    "Fogo Vivo (Solaris), Vontade CD da fonte no fim do turno ou duração."),
            new Condition("panico", "Pânico", "moderada", "Como Medo e sem Reação.", "Fogo Vivo (Solaris) ou duração."),
            new Condition("confusao", "Confusão", "moderada",
                    "Desvantagem em testes mentais; o mestre decide com o jogador o que a ação faz.",
                    "Duração ou ajuda de aliado (Intenção CD 12)."),
            new Condition("envenenado", "Envenenado", "moderada",
                    "Desvantagem em testes físicos; perde 2% da Vida de referência no início do turno.",
                    "Antídoto, Medicina CD 15 ou duração."),
            new Condition("enfraquecido", "Enfraquecido", "moderada", "Dano causado × 3/4.", "Duração."),
            new Condition("exaustao", "Exaustão", "moderada",
                    "Nível 1: desvantagem em testes. Nível 2: + movimento à metade. Nível 3: + desvantagem nos ataques.",
                    "Descanso Longo remove um nível."),
            new Condition("efeito_agudo", "Efeito agudo de Sanidade", "moderada",
                    "Por uma rodada: desvantagem em ataques e testes.", "Termina após uma rodada."),
            new Condition("atordoado", "Atordoado", "grave",
                    "Perde a Ação Padrão e a Reação; falha em esquiva; ataques contra têm vantagem.",
                    "Termina com a duração (normalmente 1 turno)."),
            new Condition("fratura", "Fratura de Sanidade", "grave",
                    "Definida pelo mestre; não é curada automaticamente.", "Tratamento narrativo."),
            new Condition("sem_decisao", "Sanidade em zero", "grave",
                    "Perde a decisão normal; exige intervenção narrativa.", "Recuperar Sanidade e intervenção narrativa."),
            new Condition("canalizando", "Canalizando", "estado",
                    "Mantém efeito; interrupção decidida pelo mestre.", "Encerrar a canalização."),
            new Condition("transformado", "Transformado", "estado", "Estado ativo de transformação.", "Fim do estado."));

    private static final Map<String, Condition> CONDITION_INDEX = new HashMap<>();

    static {
        for (Condition c : CONDITIONS) CONDITION_INDEX.put(c.id(), c);
    }

    /** Condição conhecida, ou uma genérica com o próprio id como nome ({@code "algo_novo"} → "Algo novo"). */
    public static Condition condition(@Nullable String id) {
        if (id == null) id = "";
        Condition known = CONDITION_INDEX.get(id);
        if (known != null) return known;
        String spaced = id.replace('_', ' ').strip();
        String label = spaced.isEmpty() ? "?" : Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
        return new Condition(id, label, "leve", "", "");
    }

    /** Exaustão é nível (0–3), não item da lista: o resumo do nível atual, acumulado como no motor. */
    public static String exhaustionSummary(int level) {
        return switch (Math.max(0, Math.min(3, level))) {
            case 0 -> "";
            case 1 -> "Desvantagem em testes.";
            case 2 -> "Desvantagem em testes e movimento à metade.";
            default -> "Desvantagem em testes e nos ataques; movimento à metade.";
        };
    }

    // ── Elementos (rules/catalog.go) ────────────────────────────────────────────

    public record Element(String id, String label) {
    }

    public static final List<Element> ELEMENT_LIST = List.of(
            new Element("martirio", "Martírio"),
            new Element("anacronico", "Anacrônico"),
            new Element("quantico", "Quântico"),
            new Element("esoterico", "Esotérico"),
            new Element("minguantis", "Minguantis"),
            new Element("vazio", "Vazio"),
            new Element("determinacao", "Determinação"),
            new Element("rancor", "Rancor"),
            new Element("abissal", "Abissal"),
            new Element("solaris", "Solaris"),
            new Element("chaos", "Chaos"));

    /** Só os ids, na ordem do motor (mantido para quem já usa). */
    public static final List<String> ELEMENTS = ELEMENT_LIST.stream().map(Element::id).toList();

    private static final Map<String, Element> ELEMENT_INDEX = new HashMap<>();

    static {
        for (Element e : ELEMENT_LIST) {
            ELEMENT_INDEX.put(e.id(), e);
            ELEMENT_INDEX.put(normalize(e.label()), e);
        }
    }

    /**
     * Elemento por id, nome ou grafia antiga com acento — o {@code LookupElement} do backend. A técnica
     * do códex guarda {@code "martírio"} (com acento), a ficha guarda {@code "martirio"}: os dois valem.
     */
    @Nullable
    public static Element element(@Nullable String key) {
        if (key == null || key.isEmpty()) return null;
        Element exact = ELEMENT_INDEX.get(key);
        return exact != null ? exact : ELEMENT_INDEX.get(normalize(key));
    }

    /** Classe CSS do elemento; elemento desconhecido usa o tom neutro. */
    public static String elementClass(@Nullable String id) {
        Element e = element(id);
        return e == null ? "neutro" : e.id();
    }

    /** Nome do elemento com acento; desconhecido volta com a inicial maiúscula. */
    public static String elementLabel(@Nullable String id) {
        Element e = element(id);
        if (e != null) return e.label();
        if (id == null || id.isBlank()) return "";
        String t = id.strip();
        return t.substring(0, 1).toUpperCase(Locale.ROOT) + t.substring(1);
    }

    // ── Grau da Marca (rules/catalog.go) ────────────────────────────────────────

    private static final List<String> GRADE_LABELS = List.of("Sem Marca", "Marcado Desperto", "Marcado Vinculado",
            "Marcado Primaz", "Representante do Elemento");

    /** O {@code MarkGrade.Label()} do motor: 0 Sem Marca … 4 Representante do Elemento; fora disso, vazio. */
    public static String gradeLabel(int grade) {
        return grade >= 0 && grade < GRADE_LABELS.size() ? GRADE_LABELS.get(grade) : "";
    }

    // ── Ficha antiga e estado vital ─────────────────────────────────────────────

    /** {@code legacy_level} da ficha antiga ({@code sheet/rules.go}): anomalia, entidade, deus, celestial. */
    public static String legacyLabel(@Nullable String level) {
        if (level == null || level.isBlank()) return "";
        return switch (normalize(level)) {
            case "anomalia" -> "Anomalia";
            case "entidade" -> "Entidade";
            case "deus" -> "Deus";
            case "celestial" -> "Celestial";
            default -> {
                String t = level.strip();
                yield t.substring(0, 1).toUpperCase(Locale.ROOT) + t.substring(1);
            }
        };
    }

    /** {@code resources.vital} ({@code rules/states.go}); "consciente" é o normal e volta vazio. */
    public static String vitalLabel(@Nullable String vital) {
        if (vital == null) return "";
        return switch (vital) {
            case "", "consciente" -> "";
            case "queda" -> "Em queda";
            case "estabilizado" -> "Estabilizado";
            case "morto" -> "Morto";
            default -> vital.substring(0, 1).toUpperCase(Locale.ROOT) + vital.substring(1);
        };
    }
}
