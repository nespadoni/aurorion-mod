package com.aurorion.ficha.sheet;

import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * O {@code GameSheet} do site ({@code /rpg/sheet}, {@code /rpg/bind}, {@code /rpg/peek}), já
 * conferido e com tetos.
 *
 * <p>O servidor do jogo lê a resposta com {@link #parse} e manda ao cliente o {@link #toJson} — os
 * mesmos nomes de campo do contrato, só com o que a tela usa. O cliente lê de novo com
 * {@link #parse}: um parser só, testado sem o jogo, e nada do JSON original (campo novo, texto
 * enorme) atravessa a rede sem passar por aqui.
 *
 * <p>Sem build aprovada ({@code has_build=false}) atributos, perícias e derivados chegam vazios; o
 * resto (nome, alma, recursos, técnicas, lore) continua preenchido.
 *
 * <p>Tipos do backend que não são os óbvios: {@code legacy_level} é <b>texto</b> (o nível da ficha
 * antiga, ex. {@code "anomalia"}) — vai em {@link #legacy}; {@link #legacyLevel} só tem número quando
 * o texto é um número. {@code rarity} da técnica é o tier 1–5 (vira estrelas) e {@code lore.idade} é
 * número (0 = não informado, vira vazio).
 */
public record FichaSheet(
        long rpgCharacterId, String subjectType, long subjectId, String name, String title, String portraitUrl,
        String sitePath, int soul, int legacyLevel, @Nullable Element element, Grade grade,
        boolean hasBuild, boolean progressionPending, boolean pendingReview,
        List<Attribute> attributes, List<Skill> skills, @Nullable Derived derived, Resources resources,
        List<Ability> abilities, Lore lore, String version, String legacy) {

    /**
     * Nada nulo e listas imutáveis: a ficha passa da thread da rede para a do servidor e para a do
     * render sem cópia nem trava, e quem lê nunca vê lista mudando nem {@code null} inesperado.
     */
    public FichaSheet {
        subjectType = text(subjectType);
        name = text(name);
        title = text(title);
        portraitUrl = text(portraitUrl);
        sitePath = text(sitePath);
        if (grade == null) grade = new Grade(0, "");
        attributes = list(attributes);
        skills = list(skills);
        if (resources == null) resources = Resources.EMPTY;
        abilities = list(abilities);
        if (lore == null) lore = Lore.EMPTY;
        version = text(version);
        legacy = text(legacy);
    }

    /** A forma sem {@link #legacy} (a ficha de teste do cliente): o texto sai do número, se houver. */
    public FichaSheet(long rpgCharacterId, String subjectType, long subjectId, String name, String title, String portraitUrl,
                      String sitePath, int soul, int legacyLevel, @Nullable Element element, Grade grade,
                      boolean hasBuild, boolean progressionPending, boolean pendingReview,
                      List<Attribute> attributes, List<Skill> skills, @Nullable Derived derived, Resources resources,
                      List<Ability> abilities, Lore lore, String version) {
        this(rpgCharacterId, subjectType, subjectId, name, title, portraitUrl, sitePath, soul, legacyLevel, element, grade,
                hasBuild, progressionPending, pendingReview, attributes, skills, derived, resources, abilities, lore, version,
                legacyLevel > 0 ? String.valueOf(legacyLevel) : "");
    }

    public static final int MAX_NAME = 80;
    public static final int MAX_TITLE = 120;
    /** Endereço maior que isso vira vazio (não cortado): ver {@link Json#link}. */
    public static final int MAX_URL = 400;
    public static final int MAX_LORE = 600;
    public static final int MAX_DESCRIPTION = 320;
    public static final int MAX_ABILITIES = 48;
    public static final int MAX_SKILLS = 40;
    public static final int MAX_ATTRIBUTES = 12;
    public static final int MAX_CONDITIONS = 24;

    public record Element(String id, String label) {
        public Element {
            id = text(id);
            label = text(label);
        }
    }

    public record Grade(int value, String label) {
        public Grade {
            label = text(label);
        }
    }

    public record Attribute(String id, String label, int base, int effective) {
        public Attribute {
            id = text(id);
            label = text(label);
        }
    }

    /** {@code bonus} é o bônus total do teste (atributo + treino + Marca), como o motor soma. */
    public record Skill(String id, String label, String attribute, int value, int bonus) {
        public Skill {
            id = text(id);
            label = text(label);
            attribute = text(attribute);
        }
    }

    public record Derived(int vidaMax, int staminaMax, int sanidadeMax, int peMax, int defesa, int rd, int movimentoM,
                          int iniciativaBonus, int fortitude, int markCharges, int resonanceB) {
    }

    public record Resources(boolean initialized, int vida, int stamina, int sanidade, int pe, int tempHp, String vital,
                            int exhaustion, List<String> conditions, int chargesUsed, int deathSuccesses,
                            int deathFailures, boolean ultimateAvailable) {
        public Resources {
            vital = text(vital);
            conditions = list(conditions);
        }

        public static final Resources EMPTY = new Resources(false, 0, 0, 0, 0, 0, "", 0, List.of(), 0, 0, 0, false);
    }

    public record Ability(String name, String shortDescription, String element, String rarity, int abilityLevel, boolean equipped) {
        public Ability {
            name = text(name);
            shortDescription = text(shortDescription);
            element = text(element);
            rarity = text(rarity);
        }
    }

    public record Lore(String idade, String origem, String alinhamento, String personalidade, String aparencia) {
        public Lore {
            idade = text(idade);
            origem = text(origem);
            alinhamento = text(alinhamento);
            personalidade = text(personalidade);
            aparencia = text(aparencia);
        }

        public static final Lore EMPTY = new Lore("", "", "", "", "");

        public boolean isEmpty() {
            return idade.isEmpty() && origem.isEmpty() && alinhamento.isEmpty() && personalidade.isEmpty() && aparencia.isEmpty();
        }
    }

    public boolean npc() {
        return "npc".equals(subjectType);
    }

    @Nullable
    public Skill skill(String id) {
        for (Skill s : skills) if (s.id().equals(id)) return s;
        return null;
    }

    @Nullable
    public Attribute attribute(String id) {
        for (Attribute a : attributes) if (a.id().equals(id)) return a;
        return null;
    }

    /**
     * Perícias agrupadas pelo atributo, na ordem dos atributos da ficha. Perícia de atributo que a
     * ficha não lista ainda aparece, num grupo próprio no fim — nada some da tela.
     */
    public Map<String, List<Skill>> skillsByAttribute() {
        Map<String, List<Skill>> out = new LinkedHashMap<>();
        for (Attribute a : attributes) out.put(a.id(), new ArrayList<>());
        if (attributes.isEmpty()) for (Catalog.Attribute a : Catalog.ATTRIBUTES) out.put(a.id(), new ArrayList<>());
        for (Skill s : skills) out.computeIfAbsent(s.attribute(), k -> new ArrayList<>()).add(s);
        out.values().removeIf(List::isEmpty);
        return out;
    }

    // ── JSON ────────────────────────────────────────────────────────────────────

    @Nullable
    public static FichaSheet parse(@Nullable JsonObject o) {
        if (o == null) return null;
        Element element = element(o);
        JsonObject gr = Json.object(o, "grade");
        int gradeValue = Json.integer(gr, "value");
        String gradeLabel = Json.str(gr, "label", 48);
        Grade grade = new Grade(gradeValue, gradeLabel.isEmpty() ? Catalog.gradeLabel(gradeValue) : gradeLabel);

        // Nome vazio (backend sem rótulo, campo de outro tipo) cai no catálogo; id repetido entra uma vez.
        Set<String> seen = new HashSet<>();
        List<Attribute> attributes = Json.objects(Json.array(o, "attributes"), MAX_ATTRIBUTES, a -> {
            String id = Json.str(a, "id", 16);
            if (id.isEmpty() || !seen.add("a:" + id)) return null;
            String label = Json.str(a, "label", 32);
            return new Attribute(id, label.isEmpty() ? Catalog.attributeLabel(id) : label, Json.integer(a, "base"),
                    Json.integer(a, "effective"));
        });
        List<Skill> skills = Json.objects(Json.array(o, "skills"), MAX_SKILLS, s -> {
            String id = Json.str(s, "id", 40);
            if (id.isEmpty() || !seen.add("s:" + id)) return null;
            Catalog.Skill known = Catalog.skill(id);
            String label = Json.str(s, "label", 48);
            String attribute = Json.str(s, "attribute", 16);
            if (label.isEmpty()) label = known != null ? known.label() : id;
            if (attribute.isEmpty() && known != null) attribute = known.attribute();
            return new Skill(id, label, attribute, Json.integer(s, "value"), Json.integer(s, "bonus"));
        });

        JsonObject d = Json.object(o, "derived");
        Derived derived = d == null ? null : new Derived(Json.integer(d, "vida_max"), Json.integer(d, "stamina_max"),
                Json.integer(d, "sanidade_max"), Json.integer(d, "pe_max"), Json.integer(d, "defesa"), Json.integer(d, "rd"),
                Json.integer(d, "movimento_m"), Json.integer(d, "iniciativa_bonus"), Json.integer(d, "fortitude"),
                Json.integer(d, "mark_charges"), Json.integer(d, "resonance_b"));

        JsonObject r = Json.object(o, "resources");
        Resources resources = r == null ? Resources.EMPTY : new Resources(Json.bool(r, "initialized"),
                Json.integer(r, "vida"), Json.integer(r, "stamina"), Json.integer(r, "sanidade"), Json.integer(r, "pe"),
                Json.integer(r, "temp_hp"), Json.str(r, "vital", 16), Json.integer(r, "exhaustion"),
                distinct(Json.strings(Json.array(r, "conditions"), MAX_CONDITIONS, 32)), Json.integer(r, "charges_used"),
                Json.integer(r, "death_successes"), Json.integer(r, "death_failures"), Json.bool(r, "ultimate_available"));

        List<Ability> abilities = Json.objects(Json.array(o, "abilities"), MAX_ABILITIES, a -> new Ability(
                Json.str(a, "name", MAX_NAME), Json.strClipped(a, "short_description", MAX_DESCRIPTION), Json.str(a, "element", 32),
                rarity(Json.str(a, "rarity", 32)), Json.integer(a, "ability_level"), Json.bool(a, "equipped")));

        JsonObject l = Json.object(o, "lore");
        Lore lore = l == null ? Lore.EMPTY : new Lore(age(Json.str(l, "idade", MAX_LORE)), Json.str(l, "origem", MAX_LORE),
                Json.str(l, "alinhamento", MAX_LORE), Json.str(l, "personalidade", MAX_LORE), Json.str(l, "aparencia", MAX_LORE));

        String legacy = Json.str(o, "legacy_level", 24);
        return new FichaSheet(Json.number(o, "rpg_character_id"), Json.str(o, "subject_type", 8), Json.number(o, "subject_id"),
                Json.str(o, "name", MAX_NAME), Json.str(o, "title", MAX_TITLE), Json.link(o, "portrait_url", MAX_URL),
                Json.link(o, "site_path", MAX_URL), Json.integer(o, "soul"), Math.max(0, Json.integer(o, "legacy_level")),
                element, grade, Json.bool(o, "has_build"), Json.bool(o, "progression_pending"), Json.bool(o, "pending_review"),
                attributes, skills, derived, resources, abilities, lore, Json.str(o, "version", 64), "0".equals(legacy) ? "" : legacy);
    }

    /** {@code element}: o objeto do contrato; aceita também só o id em texto. Nome vazio vem do catálogo. */
    @Nullable
    private static Element element(JsonObject o) {
        JsonObject el = Json.object(o, "element");
        String id = el != null ? Json.str(el, "id", 32) : Json.str(o, "element", 32);
        String label = el != null ? Json.str(el, "label", 48) : "";
        if (id.isEmpty() && label.isEmpty()) return null;
        return new Element(id, label.isEmpty() ? Catalog.elementLabel(id) : label);
    }

    /** {@code lore.idade} é inteiro no Go: 0 (ou negativo) = não informada. Texto livre passa. */
    private static String age(String raw) {
        if (raw.isEmpty()) return "";
        try {
            return Double.parseDouble(raw) <= 0 ? "" : raw;
        } catch (NumberFormatException e) {
            return raw;
        }
    }

    private static List<String> distinct(List<String> values) {
        return values.size() < 2 ? values : List.copyOf(new LinkedHashSet<>(values));
    }

    private static final Pattern TIER = Pattern.compile("[1-5]");
    private static final Pattern DIGITS = Pattern.compile("-?\\d+");

    /** O tier 1–5 do backend vira estrelas; texto (um rótulo já pronto) passa como veio. */
    static String rarity(String raw) {
        if (TIER.matcher(raw).matches()) return "★".repeat(raw.charAt(0) - '0');
        return DIGITS.matcher(raw).matches() ? "" : raw;
    }

    static String text(@Nullable String value) {
        return value == null ? "" : value;
    }

    static <T> List<T> list(@Nullable List<T> values) {
        return values == null ? List.of() : List.copyOf(values);
    }

    public JsonObject toJson() {
        JsonObject o = new JsonObject();
        o.addProperty("rpg_character_id", rpgCharacterId);
        o.addProperty("subject_type", subjectType);
        o.addProperty("subject_id", subjectId);
        o.addProperty("name", name);
        o.addProperty("title", title);
        o.addProperty("portrait_url", portraitUrl);
        o.addProperty("site_path", sitePath);
        o.addProperty("soul", soul);
        // O backend manda texto; o número só sobra para fichas sem texto (a de teste do cliente).
        if (!legacy.isEmpty()) o.addProperty("legacy_level", legacy);
        else o.addProperty("legacy_level", legacyLevel);
        if (element == null) o.add("element", JsonNull.INSTANCE);
        else {
            JsonObject el = new JsonObject();
            el.addProperty("id", element.id());
            el.addProperty("label", element.label());
            o.add("element", el);
        }
        JsonObject gr = new JsonObject();
        gr.addProperty("value", grade.value());
        gr.addProperty("label", grade.label());
        o.add("grade", gr);
        o.addProperty("has_build", hasBuild);
        o.addProperty("progression_pending", progressionPending);
        o.addProperty("pending_review", pendingReview);

        JsonArray attrs = new JsonArray();
        for (Attribute a : attributes) {
            JsonObject j = new JsonObject();
            j.addProperty("id", a.id());
            j.addProperty("label", a.label());
            j.addProperty("base", a.base());
            j.addProperty("effective", a.effective());
            attrs.add(j);
        }
        o.add("attributes", attrs);
        JsonArray sk = new JsonArray();
        for (Skill s : skills) {
            JsonObject j = new JsonObject();
            j.addProperty("id", s.id());
            j.addProperty("label", s.label());
            j.addProperty("attribute", s.attribute());
            j.addProperty("value", s.value());
            j.addProperty("bonus", s.bonus());
            sk.add(j);
        }
        o.add("skills", sk);
        if (derived == null) o.add("derived", JsonNull.INSTANCE);
        else {
            JsonObject d = new JsonObject();
            d.addProperty("vida_max", derived.vidaMax());
            d.addProperty("stamina_max", derived.staminaMax());
            d.addProperty("sanidade_max", derived.sanidadeMax());
            d.addProperty("pe_max", derived.peMax());
            d.addProperty("defesa", derived.defesa());
            d.addProperty("rd", derived.rd());
            d.addProperty("movimento_m", derived.movimentoM());
            d.addProperty("iniciativa_bonus", derived.iniciativaBonus());
            d.addProperty("fortitude", derived.fortitude());
            d.addProperty("mark_charges", derived.markCharges());
            d.addProperty("resonance_b", derived.resonanceB());
            o.add("derived", d);
        }
        JsonObject r = new JsonObject();
        r.addProperty("initialized", resources.initialized());
        r.addProperty("vida", resources.vida());
        r.addProperty("stamina", resources.stamina());
        r.addProperty("sanidade", resources.sanidade());
        r.addProperty("pe", resources.pe());
        r.addProperty("temp_hp", resources.tempHp());
        r.addProperty("vital", resources.vital());
        r.addProperty("exhaustion", resources.exhaustion());
        r.add("conditions", Json.toArray(resources.conditions()));
        r.addProperty("charges_used", resources.chargesUsed());
        r.addProperty("death_successes", resources.deathSuccesses());
        r.addProperty("death_failures", resources.deathFailures());
        r.addProperty("ultimate_available", resources.ultimateAvailable());
        o.add("resources", r);
        JsonArray ab = new JsonArray();
        for (Ability a : abilities) {
            JsonObject j = new JsonObject();
            j.addProperty("name", a.name());
            j.addProperty("short_description", a.shortDescription());
            j.addProperty("element", a.element());
            j.addProperty("rarity", a.rarity());
            j.addProperty("ability_level", a.abilityLevel());
            j.addProperty("equipped", a.equipped());
            ab.add(j);
        }
        o.add("abilities", ab);
        JsonObject l = new JsonObject();
        l.addProperty("idade", lore.idade());
        l.addProperty("origem", lore.origem());
        l.addProperty("alinhamento", lore.alinhamento());
        l.addProperty("personalidade", lore.personalidade());
        l.addProperty("aparencia", lore.aparencia());
        o.add("lore", l);
        o.addProperty("version", version);
        return o;
    }
}
