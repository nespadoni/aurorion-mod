package com.aurorion.areas.rules;

/** Regras usadas em caminhos frequentes. Regras de extensoes tambem podem usar nomes namespaced. */
public enum AreaRule {
    FLIGHT("voo"), MAGIC("magia"), HOSTILE_SPAWN("monstros"), HOSTILE_DAMAGE("dano_monstros"), PVP("pvp"),
    /** Negada, vira a mesma parede da barreira de casa ({@code HouseBarrier}) no limite da area. */
    ENTRY("entrar");
    public static final AreaRule[] ALL = values();
    private final String key;
    AreaRule(String key) { this.key = key; }
    public String key() { return key; }
}
