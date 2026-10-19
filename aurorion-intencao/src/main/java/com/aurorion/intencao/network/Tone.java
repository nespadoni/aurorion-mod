package com.aurorion.intencao.network;

/**
 * O tom da cena, escolhido pelo mestre com {@code /intencao clima}. Nao diz quem esta ganhando — so
 * acompanha a narracao: o ar fecha, o som pesa, o chao treme.
 *
 * <p>O ordinal vai na rede e no disco: tons novos entram <b>sempre no fim</b>.
 */
public enum Tone {
    CALMO,
    TENSO,
    RUPTURA;

    public static Tone byId(int id) {
        Tone[] values = values();
        return id >= 0 && id < values.length ? values[id] : CALMO;
    }

    public String key() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }
}
