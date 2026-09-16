package com.aurorion.profissoes.data;

import java.util.Locale;

public enum Profession {
    NONE("nenhuma", "Sem profissão"), DOCTOR("medico", "Médico"),
    SMITH("ferreiro", "Ferreiro"), CHEF("cozinheiro", "Cozinheiro"), ARCANIST("arcanista", "Arcanista");
    private final String id, label;
    Profession(String id, String label) { this.id = id; this.label = label; }
    public String id() { return id; }
    public String label() { return label; }
    public static Profession parse(String value) {
        for (var profession : values()) if (profession.id.equals(value.toLowerCase(Locale.ROOT))) return profession;
        throw new IllegalArgumentException("Profissão desconhecida: " + value);
    }
}
